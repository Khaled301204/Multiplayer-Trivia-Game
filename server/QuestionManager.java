package server;

import model.Question;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Loads and serves questions from data/questions.txt.
 *
 * File format (one question per line, # lines are comments):
 *   id|category|difficulty|text|choiceA|choiceB|choiceC|choiceD|answer
 */
public class QuestionManager {

    private final List<Question> allQuestions = new ArrayList<>();

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    public void load(String filePath) throws IOException {
        allQuestions.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            int lineNum = 0;
            while ((line = br.readLine()) != null) {
                lineNum++;
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length != 9) {
                    System.err.println("[QuestionManager] Skipping malformed line " + lineNum + ": " + line);
                    continue;
                }
                try {
                    int id = Integer.parseInt(p[0].trim());
                    allQuestions.add(new Question(
                        id,
                        p[1].trim(), p[2].trim(), p[3].trim(),
                        p[4].trim(), p[5].trim(), p[6].trim(), p[7].trim(),
                        p[8].trim()
                    ));
                } catch (NumberFormatException e) {
                    System.err.println("[QuestionManager] Bad id on line " + lineNum);
                }
            }
        }
        System.out.println("[QuestionManager] Loaded " + allQuestions.size() + " questions.");
    }

    // -----------------------------------------------------------------------
    // Query
    // -----------------------------------------------------------------------

    /**
     * Returns up to {@code count} randomly selected questions that match
     * the given category and difficulty.  Pass null to skip a filter.
     */
    public List<Question> getQuestions(String category, String difficulty, int count) {
        List<Question> filtered = allQuestions.stream()
            .filter(q -> category   == null || q.getCategory().equalsIgnoreCase(category))
            .filter(q -> difficulty == null || q.getDifficulty().equalsIgnoreCase(difficulty))
            .collect(Collectors.toList());

        Collections.shuffle(filtered);
        return filtered.subList(0, Math.min(count, filtered.size()));
    }

    /** Returns all distinct categories found in the question bank. */
    public List<String> getCategories() {
        return allQuestions.stream()
            .map(Question::getCategory)
            .distinct()
            .sorted()
            .collect(Collectors.toList());
    }

    public List<Question> getAllQuestions() {
        return Collections.unmodifiableList(allQuestions);
    }
}
