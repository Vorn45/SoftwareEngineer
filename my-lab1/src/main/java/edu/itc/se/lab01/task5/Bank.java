package edu.itc.se.lab01.task5;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class Bank {
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
        synchronized (from) {
            synchronized (to) {
                if (from.balance() >= amount) {
                    from.withdraw(amount);
                    to.deposit(amount);
                }
            }
        }
    }

    public static void startWatchdog() {
        Thread watchdog = new Thread(() -> {
            ThreadMXBean bean = ManagementFactory.getThreadMXBean();
            while (true) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                long[] deadlocked = bean.findDeadlockedThreads();
                if (deadlocked != null && deadlocked.length > 0) {
                    ThreadInfo[] infos = bean.getThreadInfo(deadlocked);
                    String names = Arrays.stream(infos)
                            .map(ThreadInfo::getThreadName)
                            .collect(Collectors.joining(", "));
                    System.out.println("[watchdog] DEADLOCK detected between: " + names);
                }
            }
        }, "watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    public static void main(String[] args) throws InterruptedException {
        startWatchdog();

        Account[] accounts = new Account[4];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new Account(i, 1_000);
        }

        Thread[] workers = new Thread[8];
        for (int w = 0; w < workers.length; w++) {
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                for (int i = 0; i < 100_000; i++) {
                    Account a = accounts[rnd.nextInt(accounts.length)];
                    Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        transfer(a, b, rnd.nextInt(1, 50));
                    }
                }
            }, "transfer-" + w);
            workers[w].start();
        }

        for (Thread t : workers) {
            t.join();
        }

        long total = 0;
        for (Account a : accounts) {
            total += a.balance();
        }
        System.out.println("total = " + total + " (expected 4000)");
    }
}
