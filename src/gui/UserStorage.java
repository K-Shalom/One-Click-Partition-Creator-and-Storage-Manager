package gui;

import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * UserStorage - mock storage for demo purposes
 * Stores usernames, passwords, and roles.
 */
public class UserStorage {

    // username -> password
    private static final Map<String, String> passwords = new HashMap<>();
    // username -> role
    private static final Map<String, String> roles = new HashMap<>();

    static {
        // default users
        passwords.put("admin", "admin123");
        roles.put("admin", "Admin");

        passwords.put("user1", "user123");
        roles.put("user1", "User");

        passwords.put("user2", "user234");
        roles.put("user2", "User");
    }

    /** Check if username exists */
    public static boolean containsUser(String username) {
        return passwords.containsKey(username);
    }

    /** Verify password */
    public static boolean verifyPassword(String username, String password) {
        return passwords.containsKey(username) && passwords.get(username).equals(password);
    }

    /** Get role of a user */
    public static String getRole(String username) {
        return roles.getOrDefault(username, "User");
    }

    /** Get all usernames */
    public static List<String> getAllUsernames() {
        return new ArrayList<>(passwords.keySet());
    }

    /** Add a new user (for admin) */
    public static void addUser(String username, String password, String role) {
        passwords.put(username, password);
        roles.put(username, role);
    }

    /** Remove a user (for admin) */
    public static void removeUser(String username) {
        passwords.remove(username);
        roles.remove(username);
    }

    /** Update user password */
    public static void updatePassword(String username, String newPassword) {
        if (passwords.containsKey(username)) {
            passwords.put(username, newPassword);
        }
    }

    /** Update user role */
    public static void updateRole(String username, String newRole) {
        if (roles.containsKey(username)) {
            roles.put(username, newRole);
        }
    }
}
