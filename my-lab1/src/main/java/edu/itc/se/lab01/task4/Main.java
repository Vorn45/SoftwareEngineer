package edu.itc.se.lab01.task4;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class Main {
    static long countPrimes(long from, long to) {
        long count = 0;
        for (long n = Math.max(from, 2); n < to; n++) {
            boolean prime = true;
            for (long d = 2; d * d <= n; d++) {
                if (n % d == 0) {
                    prime = false;
                    break;
                }
            }
            if (prime) {
                count++;
            }
        }
        return count;
    }

    private static void runDownloadsBenchmark(String label, ExecutorService executor) {
        long t0 = System.nanoTime();
        int totalTasks = 500;
        List<Future<String>> futures = new ArrayList<>(totalTasks);

        for (int i = 0; i < totalTasks; i++) {
            futures.add(executor.submit(new FakeDownload(i)));
        }

        int ok = 0;
        int failed = 0;
        int timedOut = 0;

        for (Future<String> future : futures) {
            try {
                future.get(1, TimeUnit.SECONDS);
                ok++;
            } catch (TimeoutException e) {
                future.cancel(true);
                timedOut++;
            } catch (ExecutionException e) {
                failed++;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        long ms = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("%-42s : ok=%d failed=%d timedOut=%d in %d ms%n",
                label, ok, failed, timedOut, ms);
    }

    private static void runCompletableFutureBonus(ExecutorService executor) {
        long t0 = System.nanoTime();
        int totalTasks = 500;
        List<CompletableFuture<String>> futures = new ArrayList<>(totalTasks);

        for (int i = 0; i < totalTasks; i++) {
            final int taskId = i;
            CompletableFuture<String> cf = CompletableFuture.supplyAsync(() -> {
                try {
                    return new FakeDownload(taskId).call();
                } catch (Exception e) {
                    throw new CompletionException(e);
                }
            }, executor)
            .orTimeout(1, TimeUnit.SECONDS)
            .handle((result, ex) -> {
                if (ex != null) {
                    return "fallback[error=" + ex.getClass().getSimpleName() + "]";
                }
                return result;
            });

            futures.add(cf);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        long ms = (System.nanoTime() - t0) / 1_000_000;
        long fallbacks = futures.stream().filter(f -> f.join().startsWith("fallback")).count();
        System.out.printf("%-42s : completed=%d fallbackRecovered=%d in %d ms%n",
                "bonus: CompletableFuture with fallback", futures.size(), fallbacks, ms);
    }

    public static void main(String[] args) throws Exception {
        final long LIMIT = 4_000_000;
        final int CHUNKS = 16;

        long t0 = System.nanoTime();
        long single = countPrimes(0, LIMIT);
        long singleMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("single-threaded: %d primes in %d ms%n", single, singleMs);

        int cores = Runtime.getRuntime().availableProcessors();
        t0 = System.nanoTime();
        long totalPrimes = 0;
        try (ExecutorService pool = Executors.newFixedThreadPool(cores)) {
            List<Future<Long>> futures = new ArrayList<>();
            long chunkSize = (LIMIT + CHUNKS - 1) / CHUNKS;
            for (int i = 0; i < CHUNKS; i++) {
                long from = i * chunkSize;
                long to = Math.min(from + chunkSize, LIMIT);
                futures.add(pool.submit(new PrimeCounter(from, to)));
            }

            for (Future<Long> f : futures) {
                totalPrimes += f.get();
            }
        }
        long poolMs = (System.nanoTime() - t0) / 1_000_000;
        System.out.printf("pool (%d threads): %d primes in %d ms%n", cores, totalPrimes, poolMs);

        try (ExecutorService platformPool = Executors.newFixedThreadPool(20)) {
            runDownloadsBenchmark("downloads on fixed(20) platform threads", platformPool);
        }

        try (ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor()) {
            runDownloadsBenchmark("downloads on virtual threads", virtualPool);
        }

        try (ExecutorService virtualPool = Executors.newVirtualThreadPerTaskExecutor()) {
            runCompletableFutureBonus(virtualPool);
        }
    }
}
