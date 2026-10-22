package edu.itc.se.lab01.task3;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;

public class Producer implements Runnable {
    private final String name;
    private final OrderQueue queue;
    private final int startId;
    private final int count;
    private final int poisonPillsToSend;
    private final CountDownLatch completionLatch;

    public Producer(String name, OrderQueue queue, int startId, int count, int poisonPillsToSend, CountDownLatch completionLatch) {
        this.name = name;
        this.queue = queue;
        this.startId = startId;
        this.count = count;
        this.poisonPillsToSend = poisonPillsToSend;
        this.completionLatch = completionLatch;
    }

    public Producer(String name, OrderQueue queue, int startId, int count, int poisonPillsToSend) {
        this(name, queue, startId, count, poisonPillsToSend, null);
    }

    @Override
    public void run() {
        try {
            for (int i = 0; i < count; i++) {
                int id = startId + i;
                int qty = ThreadLocalRandom.current().nextInt(1, 6);
                Order order = new Order(id, "item-" + id, qty);
                queue.put(order);
                Thread.sleep(ThreadLocalRandom.current().nextInt(0, 21));
            }
            if (completionLatch != null) {
                completionLatch.countDown();

                completionLatch.await();
            }
            for (int p = 0; p < poisonPillsToSend; p++) {
                queue.put(new Order(-1, "POISON", 0));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
