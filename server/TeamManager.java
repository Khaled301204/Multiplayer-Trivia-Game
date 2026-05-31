package server;

import model.Team;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Creates, stores, and looks up teams.
 * Thread-safe; shared across all ClientHandler threads.
 */
public class TeamManager {

    private final Map<String, Team> teams = new ConcurrentHashMap<>();
    private final int maxPerTeam;

    public TeamManager(int maxPerTeam) {
        this.maxPerTeam = maxPerTeam;
    }

    // -----------------------------------------------------------------------
    // Create
    // -----------------------------------------------------------------------

    /**
     * Creates a new team and adds the creator as first member.
     * @return null on success, error message on failure
     */
    public synchronized String createTeam(String teamName, ClientHandler creator) {
        if (teamName == null || teamName.isBlank())
            return "Team name cannot be empty.";
        if (teams.containsKey(teamName))
            return "Team name '" + teamName + "' is already taken.";

        Team team = new Team(teamName, creator.getUsername());
        team.addMember(creator);
        teams.put(teamName, team);
        creator.setCurrentTeam(team);
        return null; // success
    }

    // -----------------------------------------------------------------------
    // Join
    // -----------------------------------------------------------------------

    /**
     * Adds a player to an existing team.
     * @return null on success, error message on failure
     */
    public synchronized String joinTeam(String teamName, ClientHandler player) {
        Team team = teams.get(teamName);
        if (team == null)
            return "Team '" + teamName + "' does not exist.";
        if (team.hasMember(player.getUsername()))
            return "You are already a member of team '" + teamName + "'.";
        if (team.getSize() >= maxPerTeam)
            return "Team '" + teamName + "' is full (" + maxPerTeam + " players max).";

        team.addMember(player);
        player.setCurrentTeam(team);
        return null;
    }

    // -----------------------------------------------------------------------
    // Remove / lookup
    // -----------------------------------------------------------------------

    public synchronized void removeTeam(String teamName) {
        teams.remove(teamName);
    }

    public synchronized Team getTeam(String name) {
        return teams.get(name);
    }

    public synchronized boolean exists(String name) {
        return teams.containsKey(name);
    }

    /** Returns a snapshot of all current teams for display. */
    public synchronized Collection<Team> getAllTeams() {
        return new ArrayList<>(teams.values());
    }
}
