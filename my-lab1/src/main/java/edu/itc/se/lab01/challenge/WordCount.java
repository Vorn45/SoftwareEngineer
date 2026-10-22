package edu.itc.se.lab01.challenge;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class WordCount {
    private static final Pattern NON_LETTER_PATTERN = Pattern.compile("[^\\p{L}]+");
    private static final Path POISON = Path.of("__POISON__");

    private final BlockingQueue<Path> queue = new ArrayBlockingQueue<>(16);
    private final ConcurrentHashMap<String, LongAdder> counts = new ConcurrentHashMap<>();
    private final AtomicInteger filesDone = new AtomicInteger();
    private final LongAdder wordsDone = new LongAdder();
    private final AtomicBoolean stopping = new AtomicBoolean();

    private final Path dir;
    private final int workers;
    private final boolean virtual;
    private final int top;
    private final long timeoutSec;

    private int totalFiles = 0;
    private long startTimeNano;
    private volatile boolean isPartial = false;

    private ExecutorService pool;
    private Thread scanner;
    private ScheduledExecutorService reporter;
    private Thread shutdownHook;

    public WordCount(Path dir, int workers, boolean virtual, int top, long timeoutSec) {
        this.dir = dir;
        this.workers = workers > 0 ? workers : Runtime.getRuntime().availableProcessors();
        this.virtual = virtual;
        this.top = top > 0 ? top : 10;
        this.timeoutSec = timeoutSec;
    }

    public void run() throws InterruptedException {
        startTimeNano = System.nanoTime();

        try (Stream<Path> stream = Files.walk(dir)) {
            totalFiles = (int) stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().endsWith(".csv"))
                    .count();
        } catch (IOException e) {
            System.err.println("Error reading directory: " + e.getMessage());
            return;
        }

        pool = virtual
                ? Executors.newVirtualThreadPerTaskExecutor()
                : Executors.newFixedThreadPool(workers);

        scanner = Thread.ofPlatform().name("scanner").start(this::scan);

        reporter = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "reporter");
            t.setDaemon(true);
            return t;
        });
        reporter.scheduleAtFixedRate(this::report, 500, 500, TimeUnit.MILLISECONDS);

        shutdownHook = new Thread(() -> shutdown(true), "shutdown-hook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);

        CountDownLatch workersLatch = new CountDownLatch(workers);
        for (int i = 0; i < workers; i++) {
            pool.submit(() -> {
                try {
                    work();
                } finally {
                    workersLatch.countDown();
                }
            });
        }

        boolean finishedInTime;
        if (timeoutSec > 0 && timeoutSec < Long.MAX_VALUE) {
            finishedInTime = workersLatch.await(timeoutSec, TimeUnit.SECONDS);
            if (!finishedInTime) {
                double elapsedSec = (System.nanoTime() - startTimeNano) / 1_000_000_000.0;
                System.out.printf("[%.1fs] deadline reached – stopping%n", elapsedSec);
                shutdown(true);
                return;
            }
        } else {
            workersLatch.await();
            finishedInTime = true;
        }

        if (stopping.compareAndSet(false, true)) {
            cleanShutdown();
            printResult(false);
            deregisterShutdownHook();
        }
    }

    private void scan() {
        try (Stream<Path> stream = Files.walk(dir)) {
            Iterator<Path> it = stream.filter(Files::isRegularFile)
                    .filter(p -> !p.getFileName().toString().endsWith(".csv"))
                    .iterator();
            while (it.hasNext() && !Thread.currentThread().isInterrupted() && !stopping.get()) {
                Path p = it.next();
                while (!stopping.get() && !Thread.currentThread().isInterrupted()) {
                    if (queue.offer(p, 100, TimeUnit.MILLISECONDS)) {
                        break;
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException e) {
            System.err.println("Scanner error: " + e.getMessage());
        } finally {
            if (!stopping.get()) {
                for (int i = 0; i < workers; i++) {
                    try {
                        queue.offer(POISON, 100, TimeUnit.MILLISECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
    }

    private void work() {
        try {
            while (!Thread.currentThread().isInterrupted() && !stopping.get()) {
                Path p = queue.poll(100, TimeUnit.MILLISECONDS);
                if (p == null) {
                    continue;
                }
                if (p == POISON) {
                    return;
                }
                countFile(p);
                filesDone.incrementAndGet();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void countFile(Path p) {
        long wordsInFile = 0;
        try (BufferedReader reader = Files.newBufferedReader(p)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] tokens = NON_LETTER_PATTERN.split(line);
                for (String token : tokens) {
                    if (!token.isEmpty()) {
                        String word = token.toLowerCase();
                        counts.computeIfAbsent(word, k -> new LongAdder()).increment();
                        wordsInFile++;
                    }
                }
            }
            wordsDone.add(wordsInFile);
        } catch (IOException e) {
            System.err.println("Error reading " + p + ": " + e.getMessage());
        }
    }

    private void report() {
        if (stopping.get()) {
            return;
        }
        double elapsedSec = (System.nanoTime() - startTimeNano) / 1_000_000_000.0;
        if (elapsedSec <= 0.05) return;

        long currentWords = wordsDone.sum();
        long rate = (long) (currentWords / elapsedSec);

        String top3 = counts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().sum(), e1.getValue().sum()))
                .limit(3)
                .map(e -> String.format("%s(%,d)", e.getKey(), e.getValue().sum()))
                .collect(Collectors.joining(" "));

        System.out.printf("[%.1fs] files %d/%d words %,d %,d w/s top: %s%n",
                elapsedSec, filesDone.get(), totalFiles, currentWords, rate, top3);
    }

    public void shutdown() {
        shutdown(true);
    }

    private void shutdown(boolean markPartial) {
        if (stopping.compareAndSet(false, true)) {
            this.isPartial = markPartial;
            cleanShutdown();
            printResult(isPartial);
        }
    }

    private void cleanShutdown() {
        if (reporter != null) {
            reporter.shutdownNow();
        }
        if (scanner != null && scanner.isAlive()) {
            scanner.interrupt();
        }
        if (pool != null) {
            pool.shutdownNow();
            try {
                pool.awaitTermination(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        queue.clear();
    }

    private void deregisterShutdownHook() {
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
            }
        }
    }

    public void printResult(boolean partial) {
        double elapsedSec = (System.nanoTime() - startTimeNano) / 1_000_000_000.0;
        long totalWordsCounted = wordsDone.sum();
        int totalFilesCounted = filesDone.get();
        String mode = virtual ? "virtual" : ("platform(" + workers + ")");

        if (partial) {
            System.out.printf("[PARTIAL] files=%d words=%,d elapsed=%.2fs mode=%s%n",
                    totalFilesCounted, totalWordsCounted, elapsedSec, mode);
        } else {
            System.out.printf("DONE files=%d words=%,d elapsed=%.2fs mode=%s%n",
                    totalFilesCounted, totalWordsCounted, elapsedSec, mode);
        }

        List<Map.Entry<String, LongAdder>> sorted = counts.entrySet().stream()
                .sorted((e1, e2) -> Long.compare(e2.getValue().sum(), e1.getValue().sum()))
                .toList();

        System.out.printf("%-6s %-15s %-12s %-10s%n", "rank", "word", "count", "share");
        int rank = 1;
        List<String> csvLines = new ArrayList<>();
        csvLines.add("rank,word,count,share_pct");

        for (Map.Entry<String, LongAdder> entry : sorted) {
            long count = entry.getValue().sum();
            double share = totalWordsCounted > 0 ? (100.0 * count / totalWordsCounted) : 0.0;

            if (rank <= top) {
                System.out.printf("%-6d %-15s %-12s %.2f %%%n",
                        rank, entry.getKey(), String.format("%,d", count), share);
            }

            csvLines.add(String.format("%d,%s,%d,%.4f", rank, entry.getKey(), count, share));
            rank++;
        }

        try (BufferedWriter writer = Files.newBufferedWriter(Path.of("result.csv"))) {
            for (String line : csvLines) {
                writer.write(line);
                writer.newLine();
            }
            System.out.println("result.csv written");
        } catch (IOException e) {
            System.err.println("Error writing result.csv: " + e.getMessage());
        }
    }

    public Map<String, Long> getWordCounts() {
        Map<String, Long> result = new HashMap<>();
        counts.forEach((k, v) -> result.put(k, v.sum()));
        return result;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 0 || args[0].equals("--help") || args[0].equals("-h")) {
            System.out.println("Usage: java edu.itc.se.lab01.challenge.WordCount <dir> [--top N] [--timeout SECONDS] [--virtual] [--workers N]");
            return;
        }

        Path dir = Path.of(args[0]);
        int top = 5;
        long timeout = 0;
        boolean virtual = false;
        int workers = Runtime.getRuntime().availableProcessors();

        for (int i = 1; i < args.length; i++) {
            switch (args[i]) {
                case "--top" -> {
                    if (i + 1 < args.length) top = Integer.parseInt(args[++i]);
                }
                case "--timeout" -> {
                    if (i + 1 < args.length) timeout = Long.parseLong(args[++i]);
                }
                case "--virtual" -> virtual = true;
                case "--workers" -> {
                    if (i + 1 < args.length) workers = Integer.parseInt(args[++i]);
                }
            }
        }

        WordCount wc = new WordCount(dir, workers, virtual, top, timeout);
        wc.run();
    }
}
