package edu.itc.se.lab01.task1;

public class CountdownTask implements Runnable {
    @Override
    public void run() {
        String name = Thread.currentThread().getName();
        for (int i = 3; i >= 1; i--) {
            System.out.println(name + ": " + i);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
        System.out.println(name + ": liftoff!");
    }
}
