package dao;

import database.DatabaseConnection;
import models.ActivityLog;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * ActivityLog Data Access Object
 * Handles all database operations for ActivityLog entity
 */
public class ActivityLogDAO {
    
    /**
     * Create a new activity log entry
     * @param log ActivityLog object to create
     * @return true if successful, false otherwise
     */
    public boolean createLog(ActivityLog log) {
        String sql = "INSERT INTO activity_logs (user_id, machine_id, action, log_date) VALUES (?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, log.getUserId());
            pstmt.setInt(2, log.getMachineId());
            pstmt.setString(3, log.getAction());
            pstmt.setTimestamp(4, log.getLogDate());
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    log.setLogId(rs.getInt(1));
                }
                return true;
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating activity log: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Get all activity logs
     * @return List of all activity logs
     */
    public List<ActivityLog> getAllLogs() {
        List<ActivityLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM activity_logs ORDER BY log_date DESC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                logs.add(new ActivityLog(
                    rs.getInt("log_id"),
                    rs.getInt("user_id"),
                    rs.getInt("machine_id"),
                    rs.getString("action"),
                    rs.getTimestamp("log_date")
                ));
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting all logs: " + e.getMessage());
            e.printStackTrace();
        }
        return logs;
    }
    
    /**
     * Get activity logs by user ID
     * @param userId User ID
     * @return List of activity logs for the user
     */
    public List<ActivityLog> getLogsByUserId(int userId) {
        List<ActivityLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM activity_logs WHERE user_id = ? ORDER BY log_date DESC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                logs.add(new ActivityLog(
                    rs.getInt("log_id"),
                    rs.getInt("user_id"),
                    rs.getInt("machine_id"),
                    rs.getString("action"),
                    rs.getTimestamp("log_date")
                ));
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting logs by user: " + e.getMessage());
            e.printStackTrace();
        }
        return logs;
    }
    
    /**
     * Get activity logs by machine ID
     * @param machineId Machine ID
     * @return List of activity logs for the machine
     */
    public List<ActivityLog> getLogsByMachineId(int machineId) {
        List<ActivityLog> logs = new ArrayList<>();
        String sql = "SELECT * FROM activity_logs WHERE machine_id = ? ORDER BY log_date DESC";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, machineId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                logs.add(new ActivityLog(
                    rs.getInt("log_id"),
                    rs.getInt("user_id"),
                    rs.getInt("machine_id"),
                    rs.getString("action"),
                    rs.getTimestamp("log_date")
                ));
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting logs by machine: " + e.getMessage());
            e.printStackTrace();
        }
        return logs;
    }
    
    /**
     * Delete activity log
     * @param logId Log ID to delete
     * @return true if successful, false otherwise
     */
    public boolean deleteLog(int logId) {
        String sql = "DELETE FROM activity_logs WHERE log_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, logId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error deleting log: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
}
