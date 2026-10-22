package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.concurrent.locks.ReentrantLock;

public class TryLockBank {
    public static final class Account {
        public final int id;
        private long balance;
        public final ReentrantLock lock = new ReentrantLock();

        public Account(int id, long balance) {
            this.id = id;
            this.balance = balance;
        }

        public long balance() {
            return balance;
        }

        public void withdraw(long a) {
            balance -= a;
        }

        public void deposit(long a) {
            balance += a;
        }
    }

    public static boolean transfer(Account from, Account to, long amount, LongAdder retryCounter) throws InterruptedException {
        if (from == to) {
            return true;
        }

        while (!Thread.currentThread().isInterrupted()) {
            if (from.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                try {
                    if (to.lock.tryLock(10, TimeUnit.MILLISECONDS)) {
                        try {
                            if (from.balance() >= amount) {
                                from.withdraw(amount);
                                to.deposit(amount);
                            }
                            return true;
                        } finally {
                            to.lock.unlock();
                        }
                    }
                } finally {
                    from.lock.unlock();
                }
            }

            retryCounter.increment();
            try {
                Thread.sleep(ThreadLocalRandom.current().nextInt(1, 10));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    public static void runBenchmark(int threadCount, int transfersPerThread) throws InterruptedException {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, 1_000);
        }

        LongAdder retries = new LongAdder();
        long t0 = System.currentTimeMillis();
        Thread[] workers = new Thread[threadCount];

        for (int w = 0; w < threadCount; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < transfersPerThread; i++) {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        try {
                            transfer(a, b, rnd.nextInt(1, 50), retries);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            break;
                        }
                    }
                }
            }, "trylock-transfer-" + w);
            workers[w].start();
        }

        for (Thread t : workers) {
            t.join();
        }

        long elapsed = System.currentTimeMillis() - t0;
        long total = 0;
        for (Account a : accounts) {
            total += a.balance();
        }

        int totalTransfers = threadCount * transfersPerThread;
        System.out.printf("# TryLockBank%ntransfers=%d retries=%d total=%d (expected 4000) in %d ms%n",
                totalTransfers, retries.sum(), total, elapsed);

        if (total != 4000) {
            throw new AssertionError("Invariant broken! Expected balance 4000 but got " + total);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        runBenchmark(8, 100_000);
    }
}
