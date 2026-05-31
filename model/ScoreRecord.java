package model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ScoreRecord {
    private final String username;
    private final String gameMode;        // "solo" or "team"
    private final int    score;
    private final int    correctAnswers;
    private final int    totalQuestions;
    private final String category;
    private final String difficulty;
    private final String timestamp;

    private static final DateTimeFormatter FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /** Constructor used when creating a new record at game-end. */
    public ScoreRecord(String username, String gameMode, int score,
                       int correctAnswers, int totalQuestions,
                       String category, String difficulty) {
        this.username       = username;
        this.gameMode       = gameMode;
        this.score          = score;
        this.correctAnswers = correctAnswers;
        this.totalQuestions = totalQuestions;
        this.category       = category;
        this.difficulty     = difficulty;
        this.timestamp      = LocalDateTime.now().format(FMT);
    }

    /** Constructor used when loading a record from scores.txt. */
    public ScoreRecord(String username, String gameMode, int score,
                       int correctAnswers, int totalQuestions,
                       String category, String difficulty, String timestamp) {
        this.username       = username;
        this.gameMode       = gameMode;
        this.score          = score;
        this.correctAnswers = correctAnswers;
        this.totalQuestions = totalQuestions;
        this.category       = category;
        this.difficulty     = difficulty;
        this.timestamp      = timestamp;
    }

    // -----------------------------------------------------------------------
    // File serialisation
    // -----------------------------------------------------------------------

    /** One line in scores.txt */
    public String toFileLine() {
        return username + "|" + gameMode + "|" + score + "|"
             + correctAnswers + "|" + totalQuestions + "|"
             + category + "|" + difficulty + "|" + timestamp;
    }

    /** Pretty one-liner for displaying to the user. */
    public String format() {
        return String.format("  [%s]  Mode=%-5s  Category=%-12s  Diff=%-6s  Score=%3d  (%d/%d correct)",
            timestamp, gameMode, category, difficulty, score, correctAnswers, totalQuestions);
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------
    public String getUsername()       { return username; }
    public String getGameMode()       { return gameMode; }
    public int    getScore()          { return score; }
    public int    getCorrectAnswers() { return correctAnswers; }
    public int    getTotalQuestions() { return totalQuestions; }
    public String getCategory()       { return category; }
    public String getDifficulty()     { return difficulty; }
    public String getTimestamp()      { return timestamp; }
}
