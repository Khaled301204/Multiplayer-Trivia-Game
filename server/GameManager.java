package server;

import model.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates and runs GameRooms (solo & team).
 *
 * Responsibilities:
 *   - Start solo and team games
 *   - Drive the question loop using TimerManager
 *   - Evaluate answers after each question closes
 *   - Display end-of-game summary
 *   - Persist final scores via ScoreManager
 *   - Handle player disconnects mid-game
 */
public class GameManager {

    private final QuestionManager questionManager;
    private final ScoreManager    scoreManager;
    private final GameConfig      config;

    // roomId -> GameRoom
    private final Map<String, GameRoom> activeRooms = new ConcurrentHashMap<>();
    // username -> roomId
    private final Map<String, String>   playerRoom  = new ConcurrentHashMap<>();

    private final AtomicInteger  counter  = new AtomicInteger(1);
    private final ExecutorService gamePool = Executors.newCachedThreadPool();

    public GameManager(QuestionManager qm, ScoreManager sm, GameConfig cfg) {
        this.questionManager = qm;
        this.scoreManager    = sm;
        this.config          = cfg;
    }

    // -----------------------------------------------------------------------
    // Start solo
    // -----------------------------------------------------------------------

    public synchronized String startSolo(String category, String difficulty,
                                          int count, ClientHandler player) {
        if (playerRoom.containsKey(player.getUsername()))
            return "You are already in an active game.";

        List<Question> qs = questionManager.getQuestions(category, difficulty, count);
        if (qs.isEmpty())
            return "No questions found for category='" + category
                 + "' difficulty='" + difficulty + "'.";

        if (qs.size() < count) {
            player.sendMessage("  Note: Only " + qs.size() + " question(s) match your filters; starting with " + qs.size() + ".");
        }

        String roomId = "ROOM-" + counter.getAndIncrement();
        GameRoom room = new GameRoom(roomId, GameRoom.Mode.SOLO, qs);
        room.setSoloPlayer(player);
        room.setActive(true);

        activeRooms.put(roomId, room);
        playerRoom.put(player.getUsername(), roomId);
        player.setCurrentRoom(room);

        gamePool.submit(() -> runGame(room));
        return null;
    }

    // -----------------------------------------------------------------------
    // Start team game
    // -----------------------------------------------------------------------

    public synchronized String startTeamGame(Team teamA, Team teamB,
                                               String category, String difficulty,
                                               int count, ClientHandler requester) {
        if (!teamA.getCreatorUsername().equals(requester.getUsername()))
            return "Only the creator of '" + teamA.getName() + "' can start a match.";

        if (teamA.getSize() != teamB.getSize())
            return "Teams must be equal size. "
                 + teamA.getName() + "=" + teamA.getSize()
                 + "  vs  " + teamB.getName() + "=" + teamB.getSize() + ".";

        for (ClientHandler h : teamA.getMembers())
            if (playerRoom.containsKey(h.getUsername()))
                return h.getUsername() + " is already in an active game.";
        for (ClientHandler h : teamB.getMembers())
            if (playerRoom.containsKey(h.getUsername()))
                return h.getUsername() + " is already in an active game.";

        List<Question> qs = questionManager.getQuestions(category, difficulty, count);
        if (qs.isEmpty())
            return "No questions found for category='" + category
                 + "' difficulty='" + difficulty + "'.";

        String roomId = "ROOM-" + counter.getAndIncrement();
        GameRoom room = new GameRoom(roomId, GameRoom.Mode.TEAM, qs);
        room.setTeams(teamA, teamB);
        room.setActive(true);

        activeRooms.put(roomId, room);
        teamA.getMembers().forEach(h -> { playerRoom.put(h.getUsername(), roomId); h.setCurrentRoom(room); });
        teamB.getMembers().forEach(h -> { playerRoom.put(h.getUsername(), roomId); h.setCurrentRoom(room); });

        String banner = "\n\u2605 Team match: " + teamA.getName() + " vs " + teamB.getName()
                      + "  |  " + qs.size() + " Qs  |  "
                      + (category == null ? "All" : category)
                      + "  |  " + (difficulty == null ? "Any" : difficulty.toUpperCase());
        teamA.broadcast(banner);
        teamB.broadcast(banner);

        if (qs.size() < count) {
            String note = "  Note: Only " + qs.size() + " question(s) match your filters; starting with " + qs.size() + ".";
            teamA.broadcast(note);
            teamB.broadcast(note);
        }

        for (ClientHandler h : teamA.getMembers()) h.setCurrentRoom(room);
        for (ClientHandler h : teamB.getMembers()) h.setCurrentRoom(room);

        gamePool.submit(() -> runGame(room));
        return null;
    }

    // -----------------------------------------------------------------------
    // Game loop  (runs on its own thread via gamePool)
    // -----------------------------------------------------------------------

    private void runGame(GameRoom room) {
        room.broadcastAll("\n" + "=".repeat(56));
        room.broadcastAll("  GAME STARTING!  "
                        + room.getQuestions().size() + " questions  |  "
                        + config.questionDurationSeconds + "s each");
        room.broadcastAll("=".repeat(56));
        sleep(2000);

        List<Question> questions = room.getQuestions();
        for (int i = 0; i < questions.size(); i++) {
            if (!room.isActive()) break;
            room.setCurrentIndex(i);
            runQuestion(room, questions.get(i), i + 1);
            if (i < questions.size() - 1) sleep(3000);
        }

        if (room.isActive()) endGame(room);
    }

    // -----------------------------------------------------------------------
    // Single question
    // -----------------------------------------------------------------------

    private void runQuestion(GameRoom room, Question q, int qNum) {
        room.openQuestion(q);

        room.broadcastAll("\n" + "-".repeat(56));
        room.broadcastAll("  Question " + qNum + " of " + room.getQuestions().size());
        room.broadcastAll(q.format());
        room.broadcastAll("\n  You have " + config.questionDurationSeconds
                        + " seconds — enter A, B, C or D:");
        room.broadcastAll("-".repeat(56));

        // Latch blocks the game-loop thread until the question is closed
        CountDownLatch latch = new CountDownLatch(1);
        room.setCurrentLatch(latch);

        TimerManager timer = new TimerManager(
            room,
            config.questionDurationSeconds,
            config.timerWarnings,
            () -> {
                room.setLastCloseReason(GameRoom.CloseReason.TIMEOUT);
                room.closeQuestion();
                latch.countDown();   // timeout path: wake game loop
            }
        );
        room.setCurrentTimer(timer);
        timer.start();

        try { latch.await(); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }

        evaluateQuestion(room, q);
    }

    // -----------------------------------------------------------------------
    // Evaluate
    // -----------------------------------------------------------------------

    private void evaluateQuestion(GameRoom room, Question q) {
        if (room.getLastCloseReason() == GameRoom.CloseReason.TIMEOUT) {
            room.broadcastAll("\n  \u23F0 Time's up!");
        } else {
            room.broadcastAll("\n  \u270B Question closed (answered early).");
        }
        room.broadcastAll("  Correct answer: " + q.getAnswer());

        List<String> correct = new ArrayList<>();
        Map<String, Boolean> answered = new HashMap<>(room.getAnsweredThisRound());

        for (Map.Entry<String, Boolean> e : answered.entrySet()) {
            if (e.getValue()) correct.add(e.getKey());
        }

        if (!correct.isEmpty()) room.broadcastAll("  \u2713 Correct: " + String.join(", ", correct));
        else                     room.broadcastAll("  \u2717 Nobody answered correctly.");

        room.logQuestionResult(q.getText(), correct);

        // Display current scores
        room.broadcastAll("\n  Scores:");
        if (room.getMode() == GameRoom.Mode.SOLO) {
            ClientHandler p = room.getSoloPlayer();
            room.broadcastAll("    " + p.getUsername() + ": " + room.getScore(p.getUsername()) + " pts");
        } else {
            Team a = room.getTeamA(), b = room.getTeamB();
            room.broadcastAll("    Team " + a.getName() + ": " + a.getTeamScore() + " pts");
            a.getMembers().forEach(h ->
                room.broadcastAll("      - " + h.getUsername() + ": " + room.getScore(h.getUsername()) + " pts"));
            room.broadcastAll("    Team " + b.getName() + ": " + b.getTeamScore() + " pts");
            b.getMembers().forEach(h ->
                room.broadcastAll("      - " + h.getUsername() + ": " + room.getScore(h.getUsername()) + " pts"));
        }
    }

    // -----------------------------------------------------------------------
    // Answer submission  (called from ClientHandler thread)
    // -----------------------------------------------------------------------

    public String submitAnswer(String username, String answer) {
        String roomId = playerRoom.get(username);
        if (roomId == null) return "No active game.";

        GameRoom room = activeRooms.get(roomId);
        if (room == null || !room.isActive()) return "No active game.";

        if (!room.isQuestionOpen())
            return "ERROR: No active question — answer not accepted.";

        if (answer == null || !answer.toUpperCase().matches("[ABCD]"))
            return "Invalid answer. Please enter A, B, C, or D.";

        Boolean correct = room.recordAnswer(username, answer.toUpperCase());
        if (correct == null) return "You have already answered this question.";

        int pts = 0;
        if (correct) {
            pts = config.scorePerCorrectAnswer;
            room.addScore(username, pts);
        }

        String feedback = correct
            ? "\u2713 Correct! +" + pts + " points"
            : "\u2717 Wrong answer.";

        // Close on first correct answer (solo or team) to match assignment requirement.
        // Otherwise (team mode), close when everyone has answered.
        if (correct) {
            earlyClose(room);
        } else if (room.getMode() == GameRoom.Mode.TEAM
                && room.getAnsweredCount() >= room.getAllPlayerCount()) {
            earlyClose(room);
        }

        return feedback;
    }

    /**
     * Closes the current question early (all players answered / solo got it right).
     * Cancels the timer and counts down the latch so the game loop unblocks.
     */
    private synchronized void earlyClose(GameRoom room) {
        if (!room.isQuestionOpen()) return;
        room.setLastCloseReason(GameRoom.CloseReason.EARLY_CLOSE);
        room.closeQuestion();
        if (room.getCurrentTimer() != null) room.getCurrentTimer().cancel();
        CountDownLatch latch = room.getCurrentLatch();
        if (latch != null) latch.countDown();
    }

    // -----------------------------------------------------------------------
    // End game
    // -----------------------------------------------------------------------

    private void endGame(GameRoom room) {
        room.setActive(false);

        room.broadcastAll("\n" + "=".repeat(56));
        room.broadcastAll("  GAME OVER!");
        room.broadcastAll("=".repeat(56));

        // Per-question summary
        room.broadcastAll("\n  Question Results:");
        int i = 1;
        for (Map.Entry<String, List<String>> e : room.getQuestionResults().entrySet()) {
            String snippet = e.getKey().length() > 50
                ? e.getKey().substring(0, 47) + "..." : e.getKey();
            String who = e.getValue().isEmpty() ? "nobody" : String.join(", ", e.getValue());
            room.broadcastAll("  " + i++ + ". " + snippet);
            room.broadcastAll("     \u2713 " + who);
        }

        // Final standings + save scores
        room.broadcastAll("\n  Final Standings:");
        List<Question> qs = room.getQuestions();
        Question first    = qs.get(0);

        if (room.getMode() == GameRoom.Mode.SOLO) {
            ClientHandler p = room.getSoloPlayer();
            int sc = room.getScore(p.getUsername());
            int cc = countCorrect(room, p.getUsername());
            room.broadcastAll("    " + p.getUsername() + ": " + sc
                            + " pts  (" + cc + "/" + qs.size() + " correct)");
            saveScore(p.getUsername(), "solo", sc, cc, qs.size(),
                      first.getCategory(), first.getDifficulty());
        } else {
            Team a = room.getTeamA(), b = room.getTeamB();
            String winner = a.getTeamScore() > b.getTeamScore() ? a.getName()
                          : b.getTeamScore() > a.getTeamScore() ? b.getName()
                          : "TIE";
            printTeam(room, a, qs);
            printTeam(room, b, qs);
            room.broadcastAll("\n  \uD83C\uDFC6 Winner: " + winner);
            for (ClientHandler h : room.getAllHandlers()) {
                int sc = room.getScore(h.getUsername());
                int cc = countCorrect(room, h.getUsername());
                saveScore(h.getUsername(), "team", sc, cc, qs.size(),
                          first.getCategory(), first.getDifficulty());
            }
        }

        room.broadcastAll("\n  Scores saved. Returning to main menu...");
        room.broadcastAll("=".repeat(56));

        // Signal handlers so their gameInputLoop can exit
        room.getAllHandlers().forEach(ClientHandler::onGameEnded);

        // Clean up tracking maps
        room.getAllHandlers().forEach(h -> playerRoom.remove(h.getUsername()));
        activeRooms.remove(room.getId());
    }

    private int countCorrect(GameRoom room, String username) {
        return (int) room.getQuestionResults().values().stream()
            .filter(list -> list.contains(username)).count();
    }

    private void printTeam(GameRoom room, Team team, List<Question> qs) {
        room.broadcastAll("  Team " + team.getName() + ": " + team.getTeamScore() + " pts");
        for (ClientHandler h : team.getMembers()) {
            int cc = countCorrect(room, h.getUsername());
            room.broadcastAll("    - " + h.getUsername()
                            + ": " + room.getScore(h.getUsername())
                            + " pts  (" + cc + "/" + qs.size() + " correct)");
        }
    }

    private void saveScore(String username, String mode, int score,
                            int correct, int total, String cat, String diff) {
        scoreManager.addRecord(new ScoreRecord(username, mode, score, correct, total, cat, diff));
    }

    // -----------------------------------------------------------------------
    // Disconnect
    // -----------------------------------------------------------------------

    public synchronized void handleDisconnect(String username) {
        String roomId = playerRoom.remove(username);
        if (roomId == null) return;

        GameRoom room = activeRooms.get(roomId);
        if (room == null || !room.isActive()) return;

        room.broadcastAll("  \u26A0  Player '" + username + "' disconnected.");

        if (room.getMode() == GameRoom.Mode.SOLO) {
            shutdownRoom(room);
            return;
        }

        // Team mode: remove from their team
        Team a = room.getTeamA(), b = room.getTeamB();
        removeFromTeam(a, username);
        removeFromTeam(b, username);

        if ((a != null && a.getSize() == 0) || (b != null && b.getSize() == 0)) {
            room.broadcastAll("  A team is now empty — ending game.");
            shutdownRoom(room);
        }
    }

    private void removeFromTeam(Team team, String username) {
        if (team == null) return;
        team.getMembers().stream()
            .filter(h -> h.getUsername().equals(username))
            .findFirst()
            .ifPresent(team::removeMember);
    }

    private void shutdownRoom(GameRoom room) {
        room.setActive(false);
        room.closeQuestion();
        if (room.getCurrentTimer() != null) room.getCurrentTimer().cancel();
        CountDownLatch latch = room.getCurrentLatch();
        if (latch != null) latch.countDown();
        room.getAllHandlers().forEach(ClientHandler::onGameEnded);
        activeRooms.remove(room.getId());
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    public boolean isInGame(String username) {
        return playerRoom.containsKey(username);
    }
}
