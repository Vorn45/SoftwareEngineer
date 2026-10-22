package edu.itc.se.lab01.challenge;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Random;

public class DataGenerator {
    private static final String[] VOCABULARY = generateVocabulary(2000);

    private static String[] generateVocabulary(int size) {
        String[] common = {
            "the", "of", "and", "a", "to", "in", "is", "you", "that", "it",
            "he", "was", "for", "on", "are", "as", "with", "his", "they", "i",
            "at", "be", "this", "have", "from", "or", "one", "had", "by", "word",
            "but", "not", "what", "all", "were", "we", "when", "your", "can", "said"
        };
        String[] vocab = new String[size];
        System.arraycopy(common, 0, vocab, 0, Math.min(common.length, size));
        for (int i = common.length; i < size; i++) {
            vocab[i] = "term" + i;
        }
        return vocab;
    }

    public static void generateFiles(Path targetDir, int numFiles, int wordsPerFile) throws IOException {
        Files.createDirectories(targetDir);
        Random rnd = new Random(42);

        for (int f = 0; f < numFiles; f++) {
            Path file = targetDir.resolve(String.format("file_%04d.txt", f));
            try (BufferedWriter writer = Files.newBufferedWriter(file)) {
                int written = 0;
                while (written < wordsPerFile) {
                    int lineLen = rnd.nextInt(10, 25);
                    StringBuilder sb = new StringBuilder();
                    for (int w = 0; w < lineLen && written < wordsPerFile; w++, written++) {
                        int idx = (int) Math.min(VOCABULARY.length - 1, Math.floor(Math.pow(rnd.nextDouble(), 3) * VOCABULARY.length));
                        sb.append(VOCABULARY[idx]).append(' ');
                    }
                    writer.write(sb.toString().trim());
                    writer.newLine();
                }
            }
        }
    }

    public static void main(String[] args) throws IOException {
        Path out = Path.of(args.length > 0 ? args[0] : "./data");
        int files = args.length > 1 ? Integer.parseInt(args[1]) : 200;
        int words = args.length > 2 ? Integer.parseInt(args[2]) : 50_000;
        System.out.printf("Generating %d files (~%d words each) in %s...%n", files, words, out);
        generateFiles(out, files, words);
        System.out.println("Generation complete.");
    }
}
