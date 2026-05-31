package server;

import model.ScoreRecord;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads score history from scores.txt and appends new records.
 *
 * File format (one record per line, # lines are comments):
 *   username|gameMode|score|correctAnswers|totalQuestions|category|difficulty|timestamp
 */
public class ScoreManager {

    private final String filePath;
    private final List<ScoreRecord> records = new ArrayList<>();

    public ScoreManager(String filePath) {
        this.filePath = filePath;
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    public void load() throws IOException {
        records.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length != 8) continue;
                try {
                    records.add(new ScoreRecord(
                        p[0], p[1],
                        Integer.parseInt(p[2]),
                        Integer.parseInt(p[3]),
                        Integer.parseInt(p[4]),
                        p[5], p[6], p[7]
                    ));
                } catch (NumberFormatException e) {
                    System.err.println("[ScoreManager] Skipping malformed score line: " + line);
                }
            }
        }
        System.out.println("[ScoreManager] Loaded " + records.size() + " score records.");
    }

    // -----------------------------------------------------------------------
    // Add & persist
    // -----------------------------------------------------------------------

    public synchronized void addRecord(ScoreRecord record) {
        records.add(record);
        appendToFile(record);
    }

    private void appendToFile(ScoreRecord record) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, true))) {
            pw.println(record.toFileLine());
        } catch (IOException e) {
            System.err.println("[ScoreManager] Failed to write score: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Query
    // -----------------------------------------------------------------------

    public synchronized List<ScoreRecord> getRecords(String username) {
        return records.stream()
            .filter(r -> r.getUsername().equals(username))
            .collect(Collectors.toList());
    }
}
