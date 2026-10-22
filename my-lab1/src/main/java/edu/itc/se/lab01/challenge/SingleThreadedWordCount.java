package edu.itc.se.lab01.challenge;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class SingleThreadedWordCount {
    private static final Pattern NON_LETTER_PATTERN = Pattern.compile("[^\\p{L}]+");

    public static Map<String, Long> countWords(Path dir) throws IOException {
        Map<String, Long> counts = new HashMap<>();

        try (Stream<Path> stream = Files.walk(dir)) {
            stream.filter(Files::isRegularFile)
                  .filter(p -> !p.getFileName().toString().endsWith(".csv"))
                  .forEach(file -> {
                      try (BufferedReader reader = Files.newBufferedReader(file)) {
                          String line;
                          while ((line = reader.readLine()) != null) {
                              String[] words = NON_LETTER_PATTERN.split(line);
                              for (String w : words) {
                                  if (!w.isEmpty()) {
                                      String lower = w.toLowerCase();
                                      counts.put(lower, counts.getOrDefault(lower, 0L) + 1L);
                                  }
                              }
                          }
                      } catch (IOException e) {
                          throw new RuntimeException("Error reading file: " + file, e);
                      }
                  });
        }
        return counts;
    }
}
