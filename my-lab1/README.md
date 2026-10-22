# Lab 01: Multithreading in Java

**Course:** Software Engineering  
**Student Project:** `lab01-multithreading`  
**Java Version:** OpenJDK 21 LTS (Temurin)

---

## Table of Contents
1. [Task 1: Creating and Running Threads](#task-1-creating-and-running-threads)
2. [Task 2: Fixing a Race Condition](#task-2-fixing-a-race-condition)
3. [Task 3: Producer–Consumer with wait()/notify() and BlockingQueue](#task-3-producerconsumer-with-waitnotify-and-blockingqueue)
4. [Task 4: Thread Pools, Future, and Virtual Threads](#task-4-thread-pools-future-and-virtual-threads)
5. [Task 5: Diagnosing a Deadlock and Shutting Down Gracefully](#task-5-diagnosing-a-deadlock-and-shutting-down-gracefully)
6. [Challenge: Parallel Word-Frequency Analyser](#challenge-parallel-word-frequency-analyser)

---

## Task 1: Creating and Running Threads

### Overview
In this task, four threads were instantiated using four distinct Java techniques:
1. **Thread Subclass:** `Countdown extends Thread` (`apollo`)
2. **Runnable Implementation:** `CountdownTask implements Runnable` (`gemini`)
3. **Lambda Expression:** `Thread` wrapping a lambda invoking `CountdownTask.run()` (`mercury`)
4. **Virtual Thread Builder:** `Thread.ofVirtual().name("artemis").start(...)` (`artemis`)

### Concurrent vs Sequential Execution (`start()` vs `run()`)

#### 1. Concurrent Mode (`Thread.start()`)
- **Mechanism:** Calling `thread.start()` requests the operating system (or carrier thread scheduler for virtual threads) to allocate execution context, stack frames, and initiate a new asynchronous thread of execution.
- **Execution Flow:** The main thread launches all four threads and immediately prints `"Mission control: all launched"`. The threads execute concurrently with interleaved output, sleeping 200 ms between countdown numbers.
- **Completion:** The main thread calls `join()` on all four threads, blocking until each thread terminates. Only after all four countdowns finish is `"Mission control: all landed"` printed.
- **Output:**
```
Mission control: all launched
apollo: 3
gemini: 3
mercury: 3
artemis: 3
mercury: 2
gemini: 2
apollo: 2
artemis: 2
gemini: 1
apollo: 1
mercury: 1
artemis: 1
apollo: liftoff!
gemini: liftoff!
artemis: liftoff!
mercury: liftoff!
Mission control: all landed
apollo    virtual=false state=TERMINATED
gemini    virtual=false state=TERMINATED
mercury   virtual=false state=TERMINATED
artemis   virtual=true  state=TERMINATED
```

#### 2. Sequential Mode (`Thread.run()` with `--sequential`)
- **Mechanism:** Calling `thread.run()` directly does **not** create or start a new thread. It simply executes the target method synchronously on the caller's thread (`main`).
- **Execution Flow:** 
  - `apollo.run()` executes entirely on the `main` thread (`apollo: 3, 2, 1, liftoff!`).
  - Next, `gemini.run()` executes synchronously on the `main` thread. In `CountdownTask`, `Thread.currentThread().getName()` evaluates to `"main"`, printing `main: 3, 2, 1, liftoff!`.
  - Next, `mercury.run()` executes synchronously on the `main` thread, also printing `main: 3, 2, 1, liftoff!`.
  - For `artemis` (an unstarted virtual thread), calling `run()` directly is a no-op because virtual threads depend on OpenJDK continuations managed by carrier thread workers upon `start()`.
  - Only **after** all synchronous calls finish does `"Mission control: all launched"` print.
  - The calls to `join()` return immediately because the threads were never started and remain in the `NEW` state.
- **Output:**
```
apollo: 3
apollo: 2
apollo: 1
apollo: liftoff!
main: 3
main: 2
main: 1
main: liftoff!
main: 3
main: 2
main: 1
main: liftoff!
Mission control: all launched
Mission control: all landed
apollo    virtual=false state=NEW
gemini    virtual=false state=NEW
mercury   virtual=false state=NEW
artemis   virtual=true  state=NEW
```

---

## Task 2: Fixing a Race Condition

### UnsafeCounter Lost-Update Anomaly
When 8 threads concurrently increment a shared `long count` field 1,000,000 times each (target: 8,000,000), `count++` fails catastrophically because it is **not atomic**. At the bytecode level, `count++` executes three separate instructions:
1. `getfield count`: Read the current value from memory/L1 cache into a register.
2. `ladd`: Add 1 to the register value.
3. `putfield count`: Write the new value back to memory.

When multiple threads interleave their reads and writes across CPU cores, two or more threads read the same value simultaneously, compute the same incremented value, and overwrite each other's writes, silently discarding hundreds of thousands of updates.

#### Recorded Test Runs on UnsafeCounter
- **Run 1:** `value=1,095,776` (correct=false, 101 ms)
- **Run 2:** `value=2,120,000` (correct=false, 5 ms)
- **Run 3:** `value=5,989,094` (correct=false, 5 ms)

### Benchmark Comparison (8 threads × 1,000,000 increments)

| Implementation | Description | Final Value | Correct? | Elapsed Time |
| :--- | :--- | :---: | :---: | :---: |
| **UnsafeCounter** | Plain `long count++` (data race) | 1,095,776 | `false` | 101 ms |
| **SynchronizedCounter** | `synchronized` methods (monitor lock) | 8,000,000 | `true` | 721 ms |
| **LockCounter** | `ReentrantLock` with `finally` unlock | 8,000,000 | `true` | 346 ms |
| **AtomicCounter** | `AtomicLong.incrementAndGet()` (CAS) | 8,000,000 | `true` | 236 ms |
| **LongAdderCounter** | `LongAdder` (striped cell counters) | 8,000,000 | `true` | 33 ms |

### Question: Why does making only `increment()` synchronized but not `get()` still leave a bug?
Making only `increment()` synchronized guarantees mutual exclusion among writers, but leaves `get()` completely unsynchronized, which introduces critical concurrency bugs under the Java Memory Model (JMM):
1. **Lack of Memory Visibility (Stale Reads):** Synchronizing a method establishes a *happens-before* edge only when another thread acquires the *same monitor*. If `get()` does not acquire the lock, it has no happens-before relationship with the writer threads. The reading thread may read a stale, cached value from CPU registers or local L1/L2 cache that was never flushed or invalidated.
2. **Compiler and Hardware Reordering:** Without synchronization or volatile semantics on `get()`, the JIT compiler and CPU out-of-order execution engine are permitted to hoist, reorder, or cache the read of `count`, potentially returning an outdated value.
3. **64-bit Word Tearing (Non-Atomic 64-bit Writes):** Under Section 17.7 of the Java Language Specification, writes to 64-bit `long` or `double` fields on 32-bit JVMs are treated as two separate 32-bit operations (high 32 bits and low 32 bits). An unsynchronized `get()` can read the high 32 bits from one write and the low 32 bits from another, producing a completely corrupted numeric value ("word tearing").

### Bonus: Why `LongAdder` is Faster Under Contention
- `AtomicLong` uses a single memory location updated via hardware Compare-And-Swap (`CAS`). When 8 cores hammer the same address, only one CAS succeeds while 7 fail and retry in a tight spin loop. This induces severe **cache line bouncing** (continuous MESI protocol cache invalidation traffic across the interconnect).
- `LongAdder` avoids this bottleneck by maintaining a **striped array of `Cell` objects**, each padded to prevent false sharing across cache lines. Each thread hashes to a separate cell and increments its own slot with zero contention.
- The total sum is only computed upon calling `sum()` / `get()`, amortizing aggregation overhead. In write-heavy workloads, `LongAdder` scales linearly with CPU core count, achieving a ~22x speedup over `SynchronizedCounter` (33 ms vs 721 ms).

---

## Task 3: Producer–Consumer with wait()/notify() and BlockingQueue

### Bounded Buffer Implementation
- `BoundedBuffer<T>` is backed by an `ArrayDeque<Order>` with a fixed capacity of 5.
- Concurrency control is achieved using guarded blocks:
  - `put(Order)`: loops `while (items.size() == capacity) { wait(); }`, enqueues order, asserts invariant, and calls `notifyAll()`.
  - `take()`: loops `while (items.isEmpty()) { wait(); }`, dequeues order, asserts invariant, and calls `notifyAll()`.
- Thread coordination:
  - 2 producers produce 50 orders each (100 total orders).
  - 3 consumers process orders with randomized work delays (5–30 ms).
  - A `CountDownLatch` synchronizes all producers so that 3 poison pills (`Order(-1, "POISON", 0)`) are enqueued only after all real orders are generated.
  - Consumers shut down cleanly upon taking a poison pill.

### Benchmark & Adapter Verification
Both `Main` (using `BoundedBuffer`) and `Main2` (using `BlockingOrderQueue` adapting `ArrayBlockingQueue`) run the identical `Producer` and `Consumer` classes via the `OrderQueue` interface:
- Custom `BoundedBuffer`: Total processed = 100 in ~668 ms.
- `ArrayBlockingQueue`: Total processed = 100 in ~683 ms.

### Experiment: What breaks when replacing `while` with `if` and `notifyAll()` with `notify()`?
Replacing `while` with `if` and `notifyAll()` with `notify()` causes two catastrophic failure modes:

1. **Buffer Invariant Violation (Spurious Wakeups & Lost State Check):**
   - With an `if` condition, once a thread wakes up from `wait()`, it immediately proceeds to `addLast()` or `removeFirst()` without re-evaluating whether the condition still holds.
   - If two consumers are waiting on an empty queue and a producer puts 1 item, or if the OS delivers a **spurious wakeup**, both consumers wake up. The first consumer removes the item; the second consumer proceeds without checking and calls `removeFirst()` on an empty deque, throwing `NoSuchElementException` or violating the buffer invariant.
2. **Deadlock / Permanent Hang (Lost Notification with `notify()`):**
   - `notify()` wakes up only **one** arbitrarily chosen waiting thread.
   - Suppose the buffer is full (capacity 5), and both producers are waiting to `put()`. Consumers are busy or waiting. A consumer takes an item and calls `notify()`.
   - If the JVM scheduler happens to wake up *another consumer* instead of a producer, the woken consumer sees an empty queue (or another condition) and goes back to sleep. The waiting producers are never signaled.
   - Eventually, all consumers are waiting for items and all producers are waiting for space. The entire application enters a **permanent deadlock / hang**.
   - `notifyAll()` guarantees that all waiting threads are awakened, ensuring that whichever thread can make progress (producers when space is made, consumers when items arrive) is notified.

---

## Task 4: Thread Pools, Future, and Virtual Threads

### 1. CPU-Bound Benchmark: Prime Counting
We computed the number of prime numbers in $[0, 4\,000\,000)$ using trial-division:
- **Single-Threaded:** 283,146 primes found in **1,826 ms**.
- **Fixed Thread Pool (16 threads, 16 chunks):** 283,146 primes found in **513 ms**.
- **Speedup:** ~3.56x faster on multi-core CPU. Both approaches yield the exact same prime count.

### 2. I/O-Bound Benchmark: 500 Network Downloads (200 ms latency)

| Executor Mode | Total Tasks | OK | Failed | Timed Out | Elapsed Time |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Fixed Platform Pool (20 threads)** | 500 | 489 | 10 | 1 | **5,271 ms** |
| **Virtual Threads (`newVirtualThreadPerTaskExecutor`)** | 500 | 489 | 10 | 1 | **1,293 ms** |
| **CompletableFuture with Fallback (Bonus)** | 500 | 500 (completed) | 11 recovered | 0 unhandled | **1,032 ms** |

### Why Virtual Threads are Several Times Faster for I/O
1. **Platform Thread Limitation:** Operating system threads are heavy resources (each consumes ~1 MB of kernel/stack memory and involves OS context switching). A fixed pool of 20 platform threads can only execute 20 blocking downloads concurrently. 500 tasks with 200 ms latency require $\lceil 500 / 20 \rceil = 25$ sequential batches, taking at minimum $25 \times 200\text{ ms} = 5,000\text{ ms}$.
2. **Virtual Thread Multiplexing:** Virtual threads are lightweight user-mode threads managed by the JVM runtime (occupying only hundreds of bytes of heap). When a virtual thread blocks on I/O or `Thread.sleep()`, the underlying carrier thread (from an internal ForkJoinPool) **unmounts** the virtual thread's continuation and executes another virtual thread immediately. All 500 downloads run concurrently in parallel, completing in approximately a single round of latency (~1,200 ms, including the 1-second timeout).
3. **Failure and Timeout Handling:**
   - Every 50th task throws `IOException("boom")`. Handled via `try { future.get(...) } catch (ExecutionException e) { failed++; }`.
   - Task 42 sleeps for 5 seconds. Handled via `future.get(1, TimeUnit.SECONDS)`. Catches `TimeoutException`, issues `future.cancel(true)`, and records `timedOut++`.
   - Program never crashes; all executors are closed via try-with-resources.

---

## Task 5: Diagnosing a Deadlock and Shutting Down Gracefully

### 1. Reproducing Deadlock and `jstack` Evidence
The starter `Bank` implementation acquired intrinsic monitor locks on `Account` objects in argument order:
```java
synchronized (from) {
    synchronized (to) { ... }
}
```
When Thread A transfers from Account 1 $\to$ Account 2 while Thread B transfers from Account 2 $\to$ Account 1, Thread A holds Lock 1 and waits for Lock 2, while Thread B holds Lock 2 and waits for Lock 1. This creates a classic **circular wait deadlock**.

#### Deadlock Detection with Watchdog
A background daemon watchdog thread polling `ManagementFactory.getThreadMXBean().findDeadlockedThreads()` instantly detects the deadlock:
```
[watchdog] DEADLOCK detected between: transfer-0, transfer-4, transfer-3, transfer-2
```

#### JDK Tool Extraction (`jstack`)
Captured and saved in `deadlock.txt`:
```
Found one Java-level deadlock:
=============================
"transfer-0":
  waiting to lock monitor 0x00000294526ae330 (object 0x00000007142e4428, a edu.itc.se.lab01.task5.Bank$Account),
  which is held by "transfer-4"
"transfer-4":
  waiting to lock monitor 0x00000294526aded0 (object 0x00000007142e4410, a edu.itc.se.lab01.task5.Bank$Account),
  which is held by "transfer-3"
"transfer-3":
  waiting to lock monitor 0x00000294526ae170 (object 0x00000007142e43f8, a edu.itc.se.lab01.task5.Bank$Account),
  which is held by "transfer-2"
"transfer-2":
  waiting to lock monitor 0x00000294526aded0 (object 0x00000007142e4410, a edu.itc.se.lab01.task5.Bank$Account),
  which is held by "transfer-3"
```

### 2. Fix A: `OrderedBank` (Lock Ordering)
- **Strategy:** Enforces a strict total ordering on locks by comparing account IDs (`Account first = from.id < to.id ? from : to; Account second = from.id < to.id ? to : from;`). Locks are always acquired in ascending ID order.
- **Coffman Condition Eliminated:** **Circular Wait**. Because every thread acquires locks in the same global sequence, a closed cycle of dependency is impossible.
- **Benchmark:** 8 threads × 100,000 transfers (800,000 total) completed in **214 ms**. Final total balance = 4,000 (invariant preserved).

### 3. Fix B: `TryLockBank` (Timed Locking with Backoff)
- **Strategy:** Uses `ReentrantLock` with `tryLock(10, TimeUnit.MILLISECONDS)`. If the second lock cannot be acquired within 10 ms, the first lock is immediately released in a `finally` block, the thread records a retry, and backs off with a randomized sleep before attempting again.
- **Coffman Condition Eliminated:** **Hold and Wait**. Threads never indefinitely hold one resource while waiting for another.
- **Benchmark:** 800,000 transfers completed in **4,634 ms** with **1,135 retries**. Total balance = 4,000 (invariant preserved).

### 4. Graceful Shutdown & Cooperative Interruption
- Transfer workers loop `while (!Thread.currentThread().isInterrupted())`.
- After 2 seconds, the main thread interrupts all workers, waits with `worker.join(1000)`, and verifies all threads stopped cleanly.
- When blocking calls throw `InterruptedException`, `Thread.currentThread().interrupt()` is always called to preserve the interrupt status.
- Output:
```
# graceful shutdown
[main] interrupting 8 workers after 2000 ms
transfer-0 stopped after 674,306 transfers
transfer-1 stopped after 656,620 transfers
transfer-2 stopped after 490,486 transfers
transfer-3 stopped after 478,191 transfers
transfer-4 stopped after 541,352 transfers
transfer-5 stopped after 553,522 transfers
transfer-6 stopped after 444,994 transfers
transfer-7 stopped after 559,342 transfers
all workers stopped=true total=4000 (expected 4000)
```

---

## Challenge: Parallel Word-Frequency Analyser

### Architectural Design
The word-frequency analyser (`edu.itc.se.lab01.challenge.WordCount`) is engineered as a bounded producer-consumer pipeline:
1. **Scanner Thread:** Walks the input directory using `Files.walk(dir)` and feeds file paths into a bounded `ArrayBlockingQueue<Path>` with capacity 16. Bounding the queue provides **back-pressure**, preventing memory exhaustion when indexing directories with thousands of files.
2. **Worker Pool:** $N$ workers (or virtual threads) drain paths, tokenize text line-by-line using Unicode regex `[^\p{L}]+`, convert words to lowercase, and update a shared `ConcurrentHashMap<String, LongAdder>`.
3. **Live Progress Reporter:** A single-threaded scheduled executor samples progress every 500 ms, calculating throughput (words/sec) and reporting top words without blocking worker threads.
4. **Clean Cancellation:** Both the JVM shutdown hook (Ctrl+C) and `--timeout` deadline invoke `shutdown()`, which interrupts workers, awaits at most 2 seconds, and outputs partial metrics marked `[PARTIAL]`.
5. **CSV Export:** Aggregated frequencies and relative shares are formatted and exported to `result.csv`.

### Benchmark Scaling Table (200 files, 10,000,000 total words)

| Execution Mode | Workers | Total Files | Total Words | Elapsed Time | Throughput |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **Platform Threads** | 1 | 200 | 10,000,000 | **2.09 s** | 4.78M words/s |
| **Platform Threads** | 2 | 200 | 10,000,000 | **1.55 s** | 6.45M words/s |
| **Platform Threads** | 4 | 200 | 10,000,000 | **1.97 s** | 5.08M words/s |
| **Platform Threads** | 8 | 200 | 10,000,000 | **1.40 s** | 7.14M words/s |
| **Virtual Threads** | dynamic | 200 | 10,000,000 | **1.51 s** | 6.62M words/s |

### Why `ConcurrentHashMap<String, LongAdder>` Beats Synchronized Maps
- Synchronizing the entire map (e.g. `Collections.synchronizedMap` or a synchronized block around `map.put()`) forces all worker threads across all CPU cores to serialize their accesses through a single lock. This creates a severe synchronization bottleneck where threads spend most of their time waiting on the monitor.
- In contrast, `ConcurrentHashMap` uses bucket-level lock striping and lock-free CAS operations for reads and node insertions.
- Combining `ConcurrentHashMap` with `LongAdder` via `counts.computeIfAbsent(word, k -> new LongAdder()).increment()` ensures that once a word entry exists, subsequent increments are distributed across the `LongAdder` cell array without locking or thread contention.

### Coffman Conditions Analysis for WordCount
The four Coffman conditions necessary for deadlock are:
1. Mutual Exclusion
2. Hold and Wait
3. No Preemption
4. Circular Wait

**How our design eliminates deadlock:**
- **No Hold and Wait:** Workers never hold locks while waiting for other resources. When reading from the bounded `ArrayBlockingQueue`, a worker only waits for a path; it holds no lock. When updating the word map, `ConcurrentHashMap` and `LongAdder` use lock-free CAS instructions and cell striping. No worker ever acquires lock A and attempts to acquire lock B while holding A.
- **No Circular Wait:** There is a strict one-directional pipeline topology: `Scanner -> Queue -> Workers -> Map`. Data flows unidirectionally; workers never synchronize on each other or on the scanner. Thus, circular wait cycles cannot form.
