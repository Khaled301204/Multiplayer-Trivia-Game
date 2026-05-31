package client;

import java.io.*;
import java.net.*;

/**
 * Command-line Trivia Game Client.
 *
 * Two threads run concurrently:
 *   - Main thread  : reads lines from the server and prints them to stdout
 *   - Input thread : reads lines from the keyboard and sends them to the server
 *
 * Usage:
 *   java client.TriviaClient [host] [port]
 *
 * Defaults:  host=localhost  port=5555
 */
public class TriviaClient {

    private static final String DEFAULT_HOST = "localhost";
    private static final int    DEFAULT_PORT = 5555;

    private final String host;
    private final int    port;

    public TriviaClient(String host, int port) {
        this.host = host;
        this.port = port;
    }

    // -----------------------------------------------------------------------
    // Connect and run
    // -----------------------------------------------------------------------

    public void connect() {
        try (
            Socket         socket     = new Socket(host, port);
            BufferedReader fromServer = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter    toServer   = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader keyboard   = new BufferedReader(new InputStreamReader(System.in))
        ) {
            System.out.println("[Client] Connected to " + host + ":" + port);

            // ── Input thread: keyboard → server ────────────────────────────
            Thread inputThread = new Thread(() -> {
                try {
                    String line;
                    while ((line = keyboard.readLine()) != null) {
                        toServer.println(line);
                        if (line.trim().equals("-")) break;
                    }
                } catch (IOException ignored) {
                    // socket closed — exit quietly
                }
            });
            inputThread.setDaemon(true);   // dies when main thread exits
            inputThread.start();

            // ── Main thread: server → stdout ────────────────────────────────
            String serverLine;
            while ((serverLine = fromServer.readLine()) != null) {
                System.out.println(serverLine);
            }

            System.out.println("\n[Client] Disconnected from server.");

        } catch (ConnectException e) {
            System.err.println("[Client] Cannot connect to " + host + ":" + port
                + " — is the server running?");
        } catch (IOException e) {
            System.err.println("[Client] Connection error: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Entry point
    // -----------------------------------------------------------------------

    public static void main(String[] args) {
        String host = DEFAULT_HOST;
        int    port = DEFAULT_PORT;

        if (args.length >= 1) host = args[0];
        if (args.length >= 2) {
            try { port = Integer.parseInt(args[1]); }
            catch (NumberFormatException e) {
                System.err.println("Invalid port '" + args[1] + "', using " + DEFAULT_PORT);
            }
        }

        new TriviaClient(host, port).connect();
    }
}
