package edu.itc.se.lab01.task1;

public class Main {
    public static void main(String[] args) throws InterruptedException {
        boolean sequential = args.length > 0 && args[0].equals("--sequential");

        Thread apollo  = new Countdown("apollo");
        Thread gemini  = new Thread(new CountdownTask(), "gemini");
        Thread mercury = new Thread(() -> new CountdownTask().run(), "mercury");
        Thread artemis = Thread.ofVirtual().name("artemis").unstarted(new CountdownTask());

        if (sequential) {
            apollo.run();
            gemini.run();
            mercury.run();
            artemis.run();
        } else {
            apollo.start();
            gemini.start();
            mercury.start();
            artemis.start();
        }

        System.out.println("Mission control: all launched");

        apollo.join();
        gemini.join();
        mercury.join();
        artemis.join();

        System.out.println("Mission control: all landed");

        for (Thread t : new Thread[]{ apollo, gemini, mercury, artemis }) {
            System.out.printf("%-9s virtual=%-5b state=%s%n", t.getName(), t.isVirtual(), t.getState());
        }
    }
}
