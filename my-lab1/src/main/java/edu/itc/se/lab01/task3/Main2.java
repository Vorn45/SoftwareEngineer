package edu.itc.se.lab01.task3;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;

public class Main2 {
    public static void main(String[] args) throws InterruptedException {
        long t0 = System.currentTimeMillis();
        OrderQueue queue = new BlockingOrderQueue(5);
        AtomicInteger totalProcessed = new AtomicInteger();

        int numProducers = 2;
        int ordersPerProducer = 50;
        int numConsumers = 3;

        CountDownLatch producersLatch = new CountDownLatch(numProducers);
        List<Thread> threads = new ArrayList<>();

        for (int i = 0; i < numConsumers; i++) {
            Thread c = new Thread(new Consumer("consumer-" + (i + 1), queue, totalProcessed), "consumer-" + (i + 1));
            threads.add(c);
            c.start();
        }

        for (int i = 0; i < numProducers; i++) {
            int startId = i * ordersPerProducer + 1;
            int poisonPills = (i == numProducers - 1) ? numConsumers : 0;
            Thread p = new Thread(new Producer("producer-" + (i + 1), queue, startId, ordersPerProducer, poisonPills, producersLatch), "producer-" + (i + 1));
            threads.add(p);
            p.start();
        }

        for (Thread t : threads) {
            t.join();
        }

        long ms = System.currentTimeMillis() - t0;
        int expected = numProducers * ordersPerProducer;
        System.out.printf("Total processed: %d (expected %d) in %d ms%n",
                totalProcessed.get(), expected, ms);

        if (totalProcessed.get() != expected) {
            throw new AssertionError("Expected " + expected + " but got " + totalProcessed.get());
        }
    }
}
