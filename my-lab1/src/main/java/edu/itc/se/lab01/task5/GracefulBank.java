package edu.itc.se.lab01.task5;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLongArray;

public class GracefulBank {
    public static void runDemo() throws InterruptedException {
        int workerCount = 8;
        OrderedBank.Account[] accounts = new OrderedBank.Account[4];
        for (int i = 0; i < accounts.length; i++) {
            accounts[i] = new OrderedBank.Account(i, 1_000);
        }

        Thread[] workers = new Thread[workerCount];
        AtomicLongArray completedTransfers = new AtomicLongArray(workerCount);

        for (int w = 0; w < workerCount; w++) {
            final int workerId = w;
            workers[w] = new Thread(() -> {
                var rnd = ThreadLocalRandom.current();
                long completed = 0;
                while (!Thread.currentThread().isInterrupted()) {
                    OrderedBank.Account a = accounts[rnd.nextInt(accounts.length)];
                    OrderedBank.Account b = accounts[rnd.nextInt(accounts.length)];
                    if (a != b) {
                        OrderedBank.transfer(a, b, rnd.nextInt(1, 50));
                        completed++;
                    }
                }
                completedTransfers.set(workerId, completed);
            }, "transfer-" + w);
            workers[w].start();
        }

        Thread.sleep(2000);

        System.out.println("# graceful shutdown");
        System.out.printf("[main] interrupting %d workers after 2000 ms%n", workerCount);
        for (Thread worker : workers) {
            worker.interrupt();
        }

        boolean allStopped = true;
        for (int i = 0; i < workerCount; i++) {
            workers[i].join(1000);
            if (workers[i].isAlive()) {
                allStopped = false;
            }
            System.out.printf("%s stopped after %,d transfers%n",
                    workers[i].getName(), completedTransfers.get(i));
        }

        long total = 0;
        for (OrderedBank.Account a : accounts) {
            total += a.balance();
        }

        System.out.printf("all workers stopped=%b total=%d (expected 4000)%n",
                allStopped, total);

        if (total != 4000) {
            throw new AssertionError("Invariant violated! Expected 4000 but got " + total);
        }
    }

    public static void main(String[] args) throws InterruptedException {
        runDemo();
    }
}
