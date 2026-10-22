package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;

public class OrderedBank {
    public static final class Account {
        public final int id;
        private long balance;

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

    public static void transfer(Account from, Account to, long amount) {
        if (from == to) {
            return;
        }

        Account first = from.id < to.id ? from : to;
        Account second = from.id < to.id ? to : from;

        synchronized (first) {
            synchronized (second) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                }
            }
        }
    }

    public static void runBenchmark(int threadCount, int transfersPerThread) throws InterruptedException {
        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, 1_000);
        }

        long t0 = System.currentTimeMillis();
        Thread[] workers = new Thread[threadCount];
        for (int w = 0; w < threadCount; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < transfersPerThread; i++) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        transfer(a, b, rnd.nextInt(1, 50));
                    }
                }
            }, "ordered-transfer-" + w);
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
        System.out.printf("# OrderedBank%ntransfers=%d total=%d (expected 4000) in %d ms%n",
                totalTransfers, total, elapsed);

        if (total != 4000) {
            throw new AssertionError("Invariant broken! Expected balance 4000 but got " + total);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        runBenchmark(8, 100_000);
    }
}
