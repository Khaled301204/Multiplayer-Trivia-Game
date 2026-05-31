package server;

import model.User;

import java.io.*;
import java.util.*;

/**
 * Loads users from users.txt, handles login and registration,
 * and persists new users back to the file.
 *
 * File format (one user per line, # lines are comments):
 *   name|username|password
 */
public class UserManager {

    private final String filePath;
    private final Map<String, User> users = new LinkedHashMap<>(); // username -> User

    public UserManager(String filePath) {
        this.filePath = filePath;
    }

    // -----------------------------------------------------------------------
    // Load
    // -----------------------------------------------------------------------

    public void load() throws IOException {
        users.clear();
        try (BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split("\\|", -1);
                if (p.length != 3) {
                    System.err.println("[UserManager] Skipping malformed line: " + line);
                    continue;
                }
                users.put(p[1].trim(), new User(p[0].trim(), p[1].trim(), p[2].trim()));
            }
        }
        System.out.println("[UserManager] Loaded " + users.size() + " users.");
    }

    // -----------------------------------------------------------------------
    // Authentication
    // -----------------------------------------------------------------------

    /**
     * Returns null on success, "401" (wrong password) or "404" (not found).
     */
    public synchronized String authenticate(String username, String password) {
        User u = users.get(username);
        if (u == null)                          return "404";
        if (!u.getPassword().equals(password))  return "401";
        return null;
    }

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    /**
     * Returns null on success, or an error message.
     */
    public synchronized String register(String name, String username, String password) {
        if (username == null || username.isBlank())
            return "Username cannot be empty.";
        if (users.containsKey(username))
            return "ERROR_USERNAME_TAKEN: '" + username + "' is already registered.";

        User u = new User(name, username, password);
        users.put(username, u);
        appendToFile(u);
        return null;
    }

    private void appendToFile(User u) {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, true))) {
            pw.println(u.toFileLine());
        } catch (IOException e) {
            System.err.println("[UserManager] Failed to write user: " + e.getMessage());
        }
    }

    // -----------------------------------------------------------------------
    // Lookup
    // -----------------------------------------------------------------------

    public synchronized User getUser(String username) {
        return users.get(username);
    }
}
