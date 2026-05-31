package model;

public class Question {
    private final int    id;
    private final String category;
    private final String difficulty;
    private final String text;
    private final String choiceA;
    private final String choiceB;
    private final String choiceC;
    private final String choiceD;
    private final String answer;   // "A", "B", "C", or "D"

    public Question(int id, String category, String difficulty,
                    String text, String choiceA, String choiceB,
                    String choiceC, String choiceD, String answer) {
        this.id         = id;
        this.category   = category;
        this.difficulty = difficulty;
        this.text       = text;
        this.choiceA    = choiceA;
        this.choiceB    = choiceB;
        this.choiceC    = choiceC;
        this.choiceD    = choiceD;
        this.answer     = answer.toUpperCase();
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------
    public int    getId()         { return id; }
    public String getCategory()   { return category; }
    public String getDifficulty() { return difficulty; }
    public String getText()       { return text; }
    public String getAnswer()     { return answer; }

    /**
     * Returns a formatted multi-line string ready to send to clients.
     *
     * Example:
     *   [Geography | EASY]
     *   What is the capital of France?
     *     A) Berlin
     *     B) Madrid
     *     C) Paris
     *     D) Rome
     */
    public String format() {
        return String.format(
            "\n  [%s | %s]\n  %s\n    A) %s\n    B) %s\n    C) %s\n    D) %s",
            category, difficulty.toUpperCase(),
            text, choiceA, choiceB, choiceC, choiceD
        );
    }

    @Override
    public String toString() {
        return "Q" + id + "[" + category + "/" + difficulty + "]: " + text;
    }
}
