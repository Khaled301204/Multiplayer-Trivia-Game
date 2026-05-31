package model;

public class User {
    private String name;
    private String username;
    private String password;

    public User(String name, String username, String password) {
        this.name     = name;
        this.username = username;
        this.password = password;
    }

    public String getName()     { return name; }
    public String getUsername() { return username; }
    public String getPassword() { return password; }

    /** Serialise to one line in users.txt */
    public String toFileLine() {
        return name + "|" + username + "|" + password;
    }

    @Override
    public String toString() {
        return "User{username='" + username + "', name='" + name + "'}";
    }
}
