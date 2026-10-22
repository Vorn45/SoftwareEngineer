package edu.itc.se.lab01.task3;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;

public class Consumer implements Runnable {
    private final String name;
    private final OrderQueue queue;
    private final AtomicInteger processedCount;

    public Consumer(String name, OrderQueue queue, AtomicInteger processedCount) {
        this.name = name;
        this.queue = queue;
        this.processedCount = processedCount;
    }

    @Override
    public void run() {
        try {
            while (true) {
                Order order = queue.take();
                if (order.id() == -1) {
                    System.out.println(name + " received poison pill, stopping");
                    break;
                }
                Thread.sleep(ThreadLocalRandom.current().nextInt(5, 31));
                processedCount.incrementAndGet();
                System.out.printf("%s processed Order[id=%d, item=%s, qty=%d]%n",
                        name, order.id(), order.item(), order.qty());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
