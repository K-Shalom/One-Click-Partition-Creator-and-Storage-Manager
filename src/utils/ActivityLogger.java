package utils;

import dao.ActivityLogDAO;
import dao.MachineDAO;
import models.ActivityLog;
import models.Machine;

/**
 * Utility class for logging user activities
 * Provides convenient methods to log various user actions
 */
public class ActivityLogger {
    
    private static ActivityLogDAO activityLogDAO = new ActivityLogDAO();
    private static MachineDAO machineDAO = new MachineDAO();
    
    /**
     * Log a user activity
     * @param userId User ID
     * @param machineId Machine ID
     * @param action Description of the action
     * @return true if logged successfully, false otherwise
     */
    public static boolean log(int userId, int machineId, String action) {
        try {
            ActivityLog log = new ActivityLog(userId, machineId, action);
            boolean success = activityLogDAO.createLog(log);
            if (success) {
                System.out.println("[Activity Log] User " + userId + " - " + action);
            }
            return success;
        } catch (Exception e) {
            System.err.println("Error logging activity: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Log a user activity with automatic machine detection
     * @param userId User ID
     * @param action Description of the action
     * @return true if logged successfully, false otherwise
     */
    public static boolean log(int userId, String action) {
        try {
            Machine currentMachine = machineDAO.getOrCreateCurrentMachine(userId);
            if (currentMachine != null) {
                return log(userId, currentMachine.getMachineId(), action);
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error logging activity: " + e.getMessage());
            return false;
        }
    }
    
    // Predefined activity types for consistency
    
    public static boolean logLogin(int userId, int machineId) {
        return log(userId, machineId, "User logged in successfully");
    }
    
    public static boolean logLogout(int userId, int machineId) {
        return log(userId, machineId, "User logged out");
    }
    
    public static boolean logPartitionCreated(int userId, int machineId, String driveLetter, int sizeGB) {
        return log(userId, machineId, "Created partition " + driveLetter + " (" + sizeGB + " GB)");
    }
    
    public static boolean logPartitionDeleted(int userId, int machineId, String driveLetter) {
        return log(userId, machineId, "Deleted partition " + driveLetter);
    }
    
    public static boolean logPartitionModified(int userId, int machineId, String driveLetter, int newSizeGB) {
        return log(userId, machineId, "Modified partition " + driveLetter + " to " + newSizeGB + " GB");
    }
    
    public static boolean logMachineRegistered(int userId, int machineId, String machineName) {
        return log(userId, machineId, "Registered machine: " + machineName);
    }
    
    public static boolean logUserCreated(int adminUserId, int machineId, String newUsername) {
        return log(adminUserId, machineId, "Created new user: " + newUsername);
    }
    
    public static boolean logUserDeleted(int adminUserId, int machineId, String deletedUsername) {
        return log(adminUserId, machineId, "Deleted user: " + deletedUsername);
    }
    
    public static boolean logUserModified(int adminUserId, int machineId, String modifiedUsername) {
        return log(adminUserId, machineId, "Modified user: " + modifiedUsername);
    }
    
    public static boolean logPasswordChanged(int userId, int machineId) {
        return log(userId, machineId, "Changed password");
    }
    
    public static boolean logSystemSync(int userId, int machineId, int partitionCount) {
        return log(userId, machineId, "Synchronized " + partitionCount + " partition(s) with database");
    }
    
    public static boolean logError(int userId, int machineId, String errorDescription) {
        return log(userId, machineId, "Error: " + errorDescription);
    }
    
    public static boolean logCustomAction(int userId, int machineId, String customAction) {
        return log(userId, machineId, customAction);
    }
}
