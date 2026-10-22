package edu.itc.se.lab01.task1;

public class Countdown extends Thread {
    public Countdown(String name) {
        super(name);
    }

    @Override
    public void run() {
        String name = getName();
        for (int i = 3; i >= 1; i--) {
            System.out.println(name + ": " + i);
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                interrupt();
                return;
            }
        }

        System.out.println(name + ": liftoff!");
    }
}
