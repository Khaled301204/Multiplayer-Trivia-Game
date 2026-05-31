package model;

import server.ClientHandler;
import server.TimerManager;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the complete state of one active game room.
 * Both solo and team games use this model.
 * The actual game-loop logic lives in GameManager.
 */
public class GameRoom {

    public enum Mode { SOLO, TEAM }
    public enum CloseReason { TIMEOUT, EARLY_CLOSE }

    private final String id;
    private final Mode   mode;
    private final List<Question> questions;

    // Solo
    private ClientHandler soloPlayer;

    // Team
    private Team teamA;
    private Team teamB;

    // Per-question state (reset each question)
    private Question currentQuestion;
    private int      currentIndex = 0;
    private final Map<String, Boolean> answeredThisRound = new ConcurrentHashMap<>();

    // Lifetime score per player  username -> score
    private final Map<String, Integer> playerScores = new ConcurrentHashMap<>();

    // For the end-of-game summary: question text -> list of usernames who got it right
    private final Map<String, List<String>> questionResults = new LinkedHashMap<>();

    private volatile boolean active       = false;
    private volatile boolean questionOpen = false;

    // Why the most recent question was closed (timeout vs early close)
    private volatile CloseReason lastCloseReason = CloseReason.TIMEOUT;

    public GameRoom(String id, Mode mode, List<Question> questions) {
        this.id        = id;
        this.mode      = mode;
        this.questions = questions;
    }

    // -----------------------------------------------------------------------
    // Setup
    // -----------------------------------------------------------------------

    public void setSoloPlayer(ClientHandler p) {
        this.soloPlayer = p;
        playerScores.put(p.getUsername(), 0);
    }

    public void setTeams(Team a, Team b) {
        this.teamA = a;
        this.teamB = b;
        a.getMembers().forEach(h -> playerScores.put(h.getUsername(), 0));
        b.getMembers().forEach(h -> playerScores.put(h.getUsername(), 0));
    }

    // -----------------------------------------------------------------------
    // Question control
    // -----------------------------------------------------------------------

    public synchronized void openQuestion(Question q) {
        this.currentQuestion = q;
        this.answeredThisRound.clear();
        this.questionOpen = true;
        this.lastCloseReason = CloseReason.TIMEOUT; // default unless closed early
    }

    public synchronized void closeQuestion() {
        this.questionOpen = false;
    }

    public CloseReason getLastCloseReason() { return lastCloseReason; }
    public void setLastCloseReason(CloseReason r) { this.lastCloseReason = r; }

    /**
     * Record a player's answer.
     * @return true  if the answer was accepted and correct,
     *         false if accepted but wrong,
     *         null  if not accepted (already answered / question closed / invalid)
     */
    public synchronized Boolean recordAnswer(String username, String answer) {
        if (!questionOpen)                          return null;
        if (answeredThisRound.containsKey(username)) return null;
        if (answer == null || answer.length() != 1) return null;

        boolean correct = answer.equalsIgnoreCase(currentQuestion.getAnswer());
        answeredThisRound.put(username, correct);
        return correct;
    }

    public synchronized int getAllPlayerCount() {
        return playerScores.size();
    }

    public synchronized int getAnsweredCount() {
        return answeredThisRound.size();
    }

    public synchronized Map<String, Boolean> getAnsweredThisRound() {
        return Collections.unmodifiableMap(answeredThisRound);
    }

    // -----------------------------------------------------------------------
    // Scoring
    // -----------------------------------------------------------------------

    public synchronized void addScore(String username, int pts) {
        playerScores.merge(username, pts, Integer::sum);
        if (mode == Mode.TEAM) {
            if (teamA != null && teamA.hasMember(username)) teamA.addScore(username, pts);
            else if (teamB != null && teamB.hasMember(username)) teamB.addScore(username, pts);
        }
    }

    public synchronized int getScore(String username) {
        return playerScores.getOrDefault(username, 0);
    }

    public Map<String, Integer> getAllScores() {
        return Collections.unmodifiableMap(playerScores);
    }

    // -----------------------------------------------------------------------
    // Question results log
    // -----------------------------------------------------------------------

    public synchronized void logQuestionResult(String questionText, List<String> correctPlayers) {
        questionResults.put(questionText, new ArrayList<>(correctPlayers));
    }

    public Map<String, List<String>> getQuestionResults() {
        return Collections.unmodifiableMap(questionResults);
    }

    // -----------------------------------------------------------------------
    // Broadcast helpers
    // -----------------------------------------------------------------------

    public synchronized void broadcastAll(String msg) {
        for (ClientHandler h : getAllHandlers()) h.sendMessage(msg);
    }

    public synchronized List<ClientHandler> getAllHandlers() {
        if (mode == Mode.SOLO) {
            return soloPlayer != null
                ? Collections.singletonList(soloPlayer)
                : Collections.emptyList();
        }
        List<ClientHandler> all = new ArrayList<>();
        if (teamA != null) all.addAll(teamA.getMembers());
        if (teamB != null) all.addAll(teamB.getMembers());
        return all;
    }

    // -----------------------------------------------------------------------
    // Getters / setters
    // -----------------------------------------------------------------------

    // current timer (so GameManager can cancel it on early close)
    private TimerManager currentTimer;

    // latch that blocks the game loop until question is closed
    private java.util.concurrent.CountDownLatch currentLatch;

    public String           getId()              { return id; }
    public Mode             getMode()            { return mode; }
    public List<Question>   getQuestions()       { return questions; }
    public Question         getCurrentQuestion() { return currentQuestion; }
    public int              getCurrentIndex()    { return currentIndex; }
    public void             setCurrentIndex(int i){ this.currentIndex = i; }
    public boolean          isActive()           { return active; }
    public void             setActive(boolean v) { this.active = v; }
    public boolean          isQuestionOpen()     { return questionOpen; }
    public ClientHandler    getSoloPlayer()      { return soloPlayer; }
    public Team             getTeamA()           { return teamA; }
    public Team             getTeamB()           { return teamB; }
    public TimerManager     getCurrentTimer()    { return currentTimer; }
    public void             setCurrentTimer(TimerManager t) { this.currentTimer = t; }
    public java.util.concurrent.CountDownLatch getCurrentLatch() { return currentLatch; }
    public void             setCurrentLatch(java.util.concurrent.CountDownLatch l) { this.currentLatch = l; }
}
