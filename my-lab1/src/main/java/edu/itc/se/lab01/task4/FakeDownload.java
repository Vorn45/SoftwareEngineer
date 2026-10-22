package edu.itc.se.lab01.task4;

import java.io.IOException;
import java.util.concurrent.Callable;

public record FakeDownload(int taskId, String url) implements Callable<String> {
    public FakeDownload(int taskId) {
        this(taskId, "https://api.example.com/items/" + taskId);
    }

    @Override
    public String call() throws IOException, InterruptedException {
        if ((taskId + 1) % 50 == 0) {
            throw new IOException("boom (task " + taskId + ")");
        }

        if (taskId == 42) {
            Thread.sleep(5_000);
            return url + " -> slow length=99999";
        }

        Thread.sleep(200);
        return url + " -> length=" + (1000 + taskId * 7);
    }
}
