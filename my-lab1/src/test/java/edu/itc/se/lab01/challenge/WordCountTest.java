package edu.itc.se.lab01.challenge;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;

public class WordCountTest {
    @Test
    void testWordCountMatchesSingleThreaded(@TempDir Path tempDir) throws Exception {
        DataGenerator.generateFiles(tempDir, 20, 1000);

        Map<String, Long> expectedMap = SingleThreadedWordCount.countWords(tempDir);

        WordCount parallelCounter = new WordCount(tempDir, 4, false, 5, 30);
        parallelCounter.run();
        Map<String, Long> actualMap = parallelCounter.getWordCounts();

        assertFalse(expectedMap.isEmpty(), "Reference word count map should not be empty");
        assertEquals(expectedMap.size(), actualMap.size(), "Unique word counts should match");
        assertEquals(expectedMap, actualMap, "Parallel and single-threaded word counts must be strictly identical");
    }

    @Test
    void testTimeoutStopsWithinThreeSeconds(@TempDir Path tempDir) throws Exception {
        IntStream.range(0, 5000).parallel().forEach(i -> {
            Path file = tempDir.resolve("dummy_" + i + ".txt");
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                writer.write("quick brown fox jumps over the lazy dog repeat multiple words in this file ");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        long start = System.currentTimeMillis();

        WordCount timedCounter = new WordCount(tempDir, 4, false, 5, 1);
        timedCounter.run();
        long elapsedMs = System.currentTimeMillis() - start;

        assertTrue(elapsedMs < 3000, "WordCount should terminate within 3s when timeout is 1s, but took " + elapsedMs + " ms");
    }
}
