package model;

import server.ClientHandler;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class Team {
    private final String name;
    private final String creatorUsername;
    private final List<ClientHandler>   members;
    private final Map<String, Integer>  memberScores; // username -> score
    private int teamScore;

    public Team(String name, String creatorUsername) {
        this.name            = name;
        this.creatorUsername = creatorUsername;
        this.members         = new ArrayList<>();
        this.memberScores    = new ConcurrentHashMap<>();
    }

    // -----------------------------------------------------------------------
    // Membership
    // -----------------------------------------------------------------------

    public synchronized void addMember(ClientHandler h) {
        members.add(h);
        memberScores.put(h.getUsername(), 0);
    }

    public synchronized void removeMember(ClientHandler h) {
        if (h == null) return;
        members.removeIf(m -> m.getUsername().equals(h.getUsername()));
        memberScores.remove(h.getUsername());
    }

    public synchronized boolean hasMember(String username) {
        return memberScores.containsKey(username);
    }

    public synchronized int getSize() { return members.size(); }

    public synchronized List<ClientHandler> getMembers() {
        return new ArrayList<>(members);
    }

    // -----------------------------------------------------------------------
    // Scoring
    // -----------------------------------------------------------------------

    public synchronized void addScore(String username, int pts) {
        memberScores.merge(username, pts, Integer::sum);
        teamScore += pts;
    }

    public synchronized int getMemberScore(String username) {
        return memberScores.getOrDefault(username, 0);
    }

    public int getTeamScore() { return teamScore; }

    // -----------------------------------------------------------------------
    // Broadcast
    // -----------------------------------------------------------------------

    public synchronized void broadcast(String msg) {
        for (ClientHandler h : new ArrayList<>(members)) {
            h.sendMessage(msg);
        }
    }

    // -----------------------------------------------------------------------
    // Getters
    // -----------------------------------------------------------------------

    public String getName()            { return name; }
    public String getCreatorUsername() { return creatorUsername; }
}
