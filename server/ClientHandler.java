package server;

import model.*;

import java.io.*;
import java.net.Socket;
import java.util.Collection;
import java.util.List;

/**
 * Handles one connected client in its own thread.
 *
 * Flow:
 *   connect → authenticate → main menu loop
 *     ├─ [1] Solo game setup → game input loop
 *     ├─ [2] Team/multiplayer menu
 *     │       ├─ create team
 *     │       ├─ join team
 *     │       ├─ start match → game input loop
 *     │       └─ view teams
 *     └─ [3] Score history
 */
public class ClientHandler implements Runnable {

    private final Socket          socket;
    private final UserManager     userManager;
    private final TeamManager     teamManager;
    private final GameManager     gameManager;
    private final QuestionManager questionManager;
    private final ScoreManager    scoreManager;
    private final GameConfig      config;

    private BufferedReader in;
    private PrintWriter    out;

    private String    username    = null;
    private Team      currentTeam = null;
    private GameRoom  currentRoom = null;

    // Flag set by GameManager/GameSession when the game ends normally
    private volatile boolean gameEnded = false;

    private final Object writeLock = new Object();

    // Prevent double logging on disconnect (exception path + finally path)
    private volatile boolean disconnectLogged = false;

    public ClientHandler(Socket socket,
                         UserManager userManager,
                         TeamManager teamManager,
                         GameManager gameManager,
                         QuestionManager questionManager,
                         ScoreManager scoreManager,
                         GameConfig config) {
        this.socket          = socket;
        this.userManager     = userManager;
        this.teamManager     = teamManager;
        this.gameManager     = gameManager;
        this.questionManager = questionManager;
        this.scoreManager    = scoreManager;
        this.config          = config;
    }

    // -----------------------------------------------------------------------
    // Main thread
    // -----------------------------------------------------------------------

    @Override
    public void run() {
        try {
            in  = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);

            banner();

            if (!authLoop()) return;

            mainMenu();

        } catch (IOException e) {
            System.out.println("[Server] Client disconnected: "
                + (username != null ? username : socket.getInetAddress()));
            disconnectLogged = true;
        } finally {
            cleanup();
        }
    }

    // -----------------------------------------------------------------------
    // Banner
    // -----------------------------------------------------------------------

    private void banner() {
        send("=".repeat(56));
        send("  Welcome to the Multiplayer Trivia Game!");
        send("  Type '-' at any prompt to quit.");
        send("=".repeat(56));
    }

    // -----------------------------------------------------------------------
    // Authentication loop
    // -----------------------------------------------------------------------

    private boolean authLoop() throws IOException {
        while (true) {
            send("\n  [1] Login");
            send("  [2] Register");
            send("  [-] Quit");
            String choice = prompt("  Choice");
            if (quit(choice)) { send("Goodbye!"); return false; }

            switch (choice) {
                case "1": if (doLogin())    return true; break;
                case "2": if (doRegister()) return true; break;
                default:  send("  Please enter 1 or 2.");
            }
        }
    }

    private boolean doLogin() throws IOException {
        String user = prompt("  Username");
        if (quit(user)) return false;
        String pass = prompt("  Password");
        if (quit(pass)) return false;

        String result = userManager.authenticate(user, pass);
        if (result == null) {
            username = user;
            send("\n  \u2713 Login successful! Welcome, "
               + userManager.getUser(username).getName() + "!");
            return true;
        }
        if (result.equals("404")) send("  ERROR 404: Username not found.");
        else                       send("  ERROR 401: Unauthorized — incorrect password.");
        return false;
    }

    private boolean doRegister() throws IOException {
        String name = prompt("  Full name");
        if (quit(name)) return false;
        String user = prompt("  Choose username");
        if (quit(user)) return false;
        String pass = prompt("  Choose password");
        if (quit(pass)) return false;

        String error = userManager.register(name, user, pass);
        if (error == null) {
            username = user;
            send("\n  \u2713 Registered! Welcome, " + name + "!");
            return true;
        }
        send("  " + error);
        return false;
    }

    // -----------------------------------------------------------------------
    // Main menu
    // -----------------------------------------------------------------------

    private void mainMenu() throws IOException {
        while (true) {
            gameEnded = false;
            send("\n" + "=".repeat(56));
            send("  MAIN MENU   [" + username + "]");
            send("=".repeat(56));
            send("  [1] Play Solo");
            send("  [2] Multiplayer / Teams");
            send("  [3] My Score History");
            send("  [-] Quit");

            String choice = prompt("  Choice");

            // If a game (typically a team game) has started in the background
            // while we're sitting in the main menu, treat this input as a
            // potential answer instead of a menu choice and jump into the
            // game input loop.
            if (gameManager.isInGame(username) && currentRoom != null && currentRoom.isActive()) {
                if (choice.equals("-")) {
                    send("  You left the game.");
                    gameManager.handleDisconnect(username);
                    currentRoom = null;
                    // Stay in main menu after leaving the game
                    continue;
                }
                String feedback = gameManager.submitAnswer(username, choice);
                send("  " + feedback);
                gameInputLoop();
                // After the game finishes, show the main menu again
                continue;
            }

            if (quit(choice)) { send("  Goodbye, " + username + "!"); return; }

            switch (choice) {
                case "1": soloSetup();     break;
                case "2": teamMenu();      break;
                case "3": showHistory();   break;
                default:  send("  Invalid choice.");
            }
        }
    }

    // -----------------------------------------------------------------------
    // Solo setup
    // -----------------------------------------------------------------------

    private void soloSetup() throws IOException {
        send("\n  --- Solo Game Setup ---");
        String category   = pickCategory();   if (category   == null) return;
        String difficulty = pickDifficulty(); if (difficulty == null) return;
        int    count      = pickCount();      if (count < 0)          return;

        String err = gameManager.startSolo(
            "all".equals(category)   ? null : category,
            "any".equals(difficulty) ? null : difficulty,
            count, this);

        if (err != null) { send("  Error: " + err); return; }

        gameInputLoop();
    }

    // -----------------------------------------------------------------------
    // Team menu
    // -----------------------------------------------------------------------

    private void teamMenu() throws IOException {
        while (true) {
            send("\n  --- Team / Multiplayer Menu ---");
            send("  [1] Create a team");
            send("  [2] Join a team");
            send("  [3] Start match  (team creator only)");
            send("  [4] List teams");
            send("  [B] Back");

            String choice = prompt("  Choice");

            // If a team game has started while we're still in the team menu,
            // treat this input as an answer and jump into the game loop
            // instead of interpreting it as a menu option.
            if (gameManager.isInGame(username) && currentRoom != null && currentRoom.isActive()) {
                if (choice.equals("-")) {
                    send("  You left the game.");
                    gameManager.handleDisconnect(username);
                    currentRoom = null;
                    // Stay in / return to the team menu after leaving the game
                    continue;
                }
                String feedback = gameManager.submitAnswer(username, choice);
                send("  " + feedback);
                gameInputLoop();
                // After the game ends, return to the team menu loop
                continue;
            }

            if (quit(choice)) throw new IOException("quit");

            switch (choice.toUpperCase()) {
                case "1": doCreateTeam(); break;
                case "2": doJoinTeam();   break;
                case "3": doStartMatch(); break;
                case "4": listTeams();    break;
                case "B": return;
                default:  send("  Invalid choice.");
            }
        }
    }

    private void doCreateTeam() throws IOException {
        String name = prompt("  Team name");
        if (quit(name)) return;
        String err = teamManager.createTeam(name, this);
        if (err != null) send("  Error: " + err);
        else             send("  \u2713 Team '" + name + "' created. Share the name with your teammates.");
    }

    private void doJoinTeam() throws IOException {
        listTeams();
        String name = prompt("  Team name to join");
        if (quit(name)) return;
        String err = teamManager.joinTeam(name, this);
        if (err != null) send("  Error: " + err);
        else             send("  \u2713 Joined team '" + name + "'.");
    }

    private void doStartMatch() throws IOException {
        if (currentTeam == null) {
            send("  You must be in a team first.");
            return;
        }
        if (!currentTeam.getCreatorUsername().equals(username)) {
            send("  Only the team creator can start a match.");
            return;
        }

        listTeams();
        String oppName = prompt("  Opponent team name");
        if (quit(oppName)) return;

        Team opp = teamManager.getTeam(oppName);
        if (opp == null) { send("  Team '" + oppName + "' not found."); return; }

        String category   = pickCategory();   if (category   == null) return;
        String difficulty = pickDifficulty(); if (difficulty == null) return;
        int    count      = pickCount();      if (count < 0)          return;

        String err = gameManager.startTeamGame(
            currentTeam, opp,
            "all".equals(category)   ? null : category,
            "any".equals(difficulty) ? null : difficulty,
            count, this);

        if (err != null) { send("  Error: " + err); return; }

        gameInputLoop();
    }

    private void listTeams() {
        Collection<Team> teams = teamManager.getAllTeams();
        if (teams.isEmpty()) { send("  No teams yet."); return; }
        send("\n  Available teams:");
        for (Team t : teams) {
            send("    - " + t.getName()
               + "  [" + t.getSize() + " member(s)]"
               + "  creator: " + t.getCreatorUsername());
        }
    }

    // -----------------------------------------------------------------------
    // Game input loop  (runs while a game is active)
    // -----------------------------------------------------------------------

    private void gameInputLoop() throws IOException {
        send("  Enter your answer (A/B/C/D), or '-' to quit the game:");
        while (!gameEnded) {
            String line = readLine();
            if (line == null || line.equals("-")) {
                send("  You left the game.");
                gameManager.handleDisconnect(username);
                currentRoom = null;
                return;
            }
            if (currentRoom != null && currentRoom.isActive()) {
                String feedback = gameManager.submitAnswer(username, line);
                send("  " + feedback);
            }
        }
        currentRoom = null;
    }

    // -----------------------------------------------------------------------
    // Score history
    // -----------------------------------------------------------------------

    private void showHistory() {
        List<ScoreRecord> history = scoreManager.getRecords(username);
        if (history.isEmpty()) { send("\n  No score history yet."); return; }
        send("\n  Score History for " + username + ":");
        int start = Math.max(0, history.size() - 10); // last 10
        for (int i = start; i < history.size(); i++) {
            send(history.get(i).format());
        }
    }

    // -----------------------------------------------------------------------
    // Setup helpers
    // -----------------------------------------------------------------------

    private String pickCategory() throws IOException {
        List<String> cats = questionManager.getCategories();
        send("\n  Category:");
        for (int i = 0; i < cats.size(); i++) send("    [" + (i+1) + "] " + cats.get(i));
        send("    [0] All categories");
        String in = prompt("  Choice");
        if (quit(in)) return null;
        try {
            int idx = Integer.parseInt(in);
            if (idx == 0) return "all";
            if (idx >= 1 && idx <= cats.size()) return cats.get(idx - 1);
        } catch (NumberFormatException ignored) {}
        send("  Invalid — using all categories.");
        return "all";
    }

    private String pickDifficulty() throws IOException {
        send("\n  Difficulty:  [1] Easy  [2] Medium  [3] Hard  [0] Any");
        String in = prompt("  Choice");
        if (quit(in)) return null;
        switch (in) {
            case "1": return "easy";
            case "2": return "medium";
            case "3": return "hard";
            default:  return "any";
        }
    }

    private int pickCount() throws IOException {
        int max = Math.min(20, questionManager.getAllQuestions().size());
        String in = prompt("  Number of questions (1-" + max + ")");
        if (quit(in)) return -1;
        try {
            int n = Integer.parseInt(in);
            if (n < 1) return 1;
            if (n > max) return max;
            return n;
        } catch (NumberFormatException e) {
            send("  Invalid — using default " + config.defaultQuestionCount + ".");
            return config.defaultQuestionCount;
        }
    }

    // -----------------------------------------------------------------------
    // I/O helpers
    // -----------------------------------------------------------------------

    public void sendMessage(String msg) {
        synchronized (writeLock) {
            if (out != null) out.println(msg);
        }
    }

    private void send(String msg)          { sendMessage(msg); }
    private boolean quit(String s)         { return s == null || s.equals("-"); }

    private String prompt(String label) throws IOException {
        send(label + ": ");
        return readLine();
    }

    private String readLine() throws IOException {
        String line = in.readLine();
        if (line == null) throw new IOException("Client disconnected");
        return line.trim();
    }

    // -----------------------------------------------------------------------
    // Callbacks from GameManager
    // -----------------------------------------------------------------------

    /** Called by GameManager when the game ends naturally. */
    public void onGameEnded() {
        gameEnded = true;
    }

    // -----------------------------------------------------------------------
    // Cleanup
    // -----------------------------------------------------------------------

    private void cleanup() {
        if (username != null) gameManager.handleDisconnect(username);
        if (!disconnectLogged) {
            System.out.println("[Server] Client disconnected: "
                + (username != null ? username : socket.getInetAddress()));
            disconnectLogged = true;
        }
        try { if (!socket.isClosed()) socket.close(); } catch (IOException ignored) {}
    }

    // -----------------------------------------------------------------------
    // Getters / setters  (used by managers)
    // -----------------------------------------------------------------------

    public String   getUsername()              { return username; }
    public Team     getCurrentTeam()           { return currentTeam; }
    public void     setCurrentTeam(Team t)     { this.currentTeam = t; }
    public GameRoom getCurrentRoom()           { return currentRoom; }
    public void     setCurrentRoom(GameRoom r) { this.currentRoom = r; }
}
