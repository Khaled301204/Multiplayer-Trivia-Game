package server;

import java.io.IOException;
import java.net.*;
import java.util.concurrent.*;

/**
 * Main entry point for the Trivia Game Server.
 *
 * Startup sequence:
 *   1. Load config.txt
 *   2. Load users.txt, questions.txt, scores.txt
 *   3. Open a ServerSocket and accept client connections
 *   4. Spawn a ClientHandler thread for each connected client
 *
 * Usage:
 *   java server.TriviaServer [port] [dataDir]
 *
 * Defaults:  port=5555  dataDir=data
 */
public class TriviaServer {

    private static final int    DEFAULT_PORT     = 5555;
    private static final String DEFAULT_DATA_DIR = "data";

    private final int    port;
    private final String dataDir;

    // Shared managers (one instance each, injected into every ClientHandler)
    private final GameConfig      config;
    private final UserManager     userManager;
    private final QuestionManager questionManager;
    private final ScoreManager    scoreManager;
    private final TeamManager     teamManager;
    private final GameManager     gameManager;

    // Thread pool — one thread per connected client
    private final ExecutorService clientPool = Executors.newCachedThreadPool();

    // -----------------------------------------------------------------------
    // Constructor — loads all data files
    // -----------------------------------------------------------------------

    public TriviaServer(int port, String dataDir) throws IOException {
        this.port    = port;
        this.dataDir = dataDir;

        System.out.println("=".repeat(56));
        System.out.println("  Trivia Game Server  -  loading data...");
        System.out.println("=".repeat(56));

        config          = new GameConfig();
        userManager     = new UserManager    (dataDir + "/users.txt");
        questionManager = new QuestionManager();
        scoreManager    = new ScoreManager   (dataDir + "/scores.txt");

        config         .load(dataDir + "/config.txt");
        userManager    .load();
        questionManager.load(dataDir + "/questions.txt");
        scoreManager   .load();

        teamManager = new TeamManager(config.maxPlayersPerTeam);
        gameManager = new GameManager(questionManager, scoreManager, config);

        System.out.println("=".repeat(56));
        System.out.println("  All data loaded successfully.");
        System.out.println("=".repeat(56));
    }

    // -----------------------------------------------------------------------
    // Accept loop
    // -----------------------------------------------------------------------

    public void start() throws IOException {
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("  Server listening on port " + port);
            System.out.println("  Waiting for players...\n");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("[Server] New connection from "
                    + clientSocket.getInetAddress().getHostAddress());

                ClientHandler handler = new ClientHandler(
                    clientSocket,
                    userManager,
                    teamManager,
                    gameManager,
                    questionManager,
                    scoreManager,
                    config
                );
                clientPool.submit(handler);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        int    port    = DEFAULT_PORT;
        String dataDir = DEFAULT_DATA_DIR;

        if (args.length >= 1) {
            try { port = Integer.parseInt(args[0]); }
            catch (NumberFormatException e) {
                System.err.println("Invalid port '" + args[0] + "', using " + DEFAULT_PORT);
            }
        }
        if (args.length >= 2) {
            dataDir = args[1];
        }

        try {
            new TriviaServer(port, dataDir).start();
        } catch (IOException e) {
            System.err.println("[Server] Fatal: " + e.getMessage());
        }
    }
}
