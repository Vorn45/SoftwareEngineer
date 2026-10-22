package edu.itc.se.lab01.task5;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        if (args.length > 0 && args[0].equals("--deadlock")) {
            System.out.println("# broken version, with watchdog");
            Bank.main(args);
            return;
        }

        System.out.println("=== Task 5: Deadlock Solutions & Graceful Shutdown ===");
        System.out.println();

        OrderedBank.runBenchmark(8, 100_000);
        System.out.println();

        TryLockBank.runBenchmark(8, 100_000);
        System.out.println();

        GracefulBank.runDemo();
    }
}
