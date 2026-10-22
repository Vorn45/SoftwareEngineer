package edu.itc.se.lab01.task2;

public class Bench {
    private static final int THREAD_COUNT = 8;
    private static final int INCREMENTS_PER_THREAD = 1_000_000;
    private static final long EXPECTED_TOTAL = (long) THREAD_COUNT * INCREMENTS_PER_THREAD;

    public static void run(String label, Counter c) {
        Thread[] threads = new Thread[THREAD_COUNT];
        long startTime = System.nanoTime();

        for (int i = 0; i < THREAD_COUNT; i++) {
            threads[i] = new Thread(() -> {
                for (int j = 0; j < INCREMENTS_PER_THREAD; j++) {
                    c.increment();
                }
            });
            threads[i].start();
        }

        for (Thread t : threads) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("Thread interrupted: " + e.getMessage());
            }
        }

        long ms = (System.nanoTime() - startTime) / 1_000_000;
        long finalValue = c.get();
        boolean equalsExpected = (finalValue == EXPECTED_TOTAL);

        System.out.printf("%-22s value=%,d correct=%-5b %d ms%n",
                label, finalValue, equalsExpected, ms);
    }

    public static void main(String[] args) throws InterruptedException {
        run("UnsafeCounter (1)", new UnsafeCounter());
        run("UnsafeCounter (2)", new UnsafeCounter());
        run("UnsafeCounter (3)", new UnsafeCounter());

        run("SynchronizedCounter", new SynchronizedCounter());
        run("LockCounter", new LockCounter());
        run("AtomicCounter", new AtomicCounter());
        run("LongAdderCounter", new LongAdderCounter());
    }
}
