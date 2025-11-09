package dao;

import database.DatabaseConnection;
import models.Machine;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Machine Data Access Object
 * Handles all database operations for Machine entity
 */
public class MachineDAO {
    
    /**
     * Create a new machine in the database
     * @param machine Machine object to create
     * @return true if successful, false otherwise
     */
    public boolean createMachine(Machine machine) {
        String sql = "INSERT INTO machines (user_id, machine_name, ip_address) VALUES (?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, machine.getUserId());
            pstmt.setString(2, machine.getMachineName());
            pstmt.setString(3, machine.getIpAddress());
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    machine.setMachineId(rs.getInt(1));
                }
                return true;
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating machine: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Get machine by ID
     * @param machineId Machine ID
     * @return Machine object if found, null otherwise
     */
    public Machine getMachineById(int machineId) {
        String sql = "SELECT * FROM machines WHERE machine_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, machineId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return new Machine(
                    rs.getInt("machine_id"),
                    rs.getInt("user_id"),
                    rs.getString("machine_name"),
                    rs.getString("ip_address")
                );
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting machine: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Get all machines for a specific user
     * @param userId User ID
     * @return List of machines belonging to the user
     */
    public List<Machine> getMachinesByUserId(int userId) {
        List<Machine> machines = new ArrayList<>();
        String sql = "SELECT * FROM machines WHERE user_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                machines.add(new Machine(
                    rs.getInt("machine_id"),
                    rs.getInt("user_id"),
                    rs.getString("machine_name"),
                    rs.getString("ip_address")
                ));
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting machines by user: " + e.getMessage());
            e.printStackTrace();
        }
        return machines;
    }
    
    /**
     * Get all machines
     * @return List of all machines
     */
    public List<Machine> getAllMachines() {
        List<Machine> machines = new ArrayList<>();
        String sql = "SELECT * FROM machines";
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                machines.add(new Machine(
                    rs.getInt("machine_id"),
                    rs.getInt("user_id"),
                    rs.getString("machine_name"),
                    rs.getString("ip_address")
                ));
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting all machines: " + e.getMessage());
            e.printStackTrace();
        }
        return machines;
    }
    
    /**
     * Update machine information
     * @param machine Machine object with updated information
     * @return true if successful, false otherwise
     */
    public boolean updateMachine(Machine machine) {
        String sql = "UPDATE machines SET user_id = ?, machine_name = ?, ip_address = ? WHERE machine_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, machine.getUserId());
            pstmt.setString(2, machine.getMachineName());
            pstmt.setString(3, machine.getIpAddress());
            pstmt.setInt(4, machine.getMachineId());
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating machine: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Delete machine
     * @param machineId Machine ID to delete
     * @return true if successful, false otherwise
     */
    public boolean deleteMachine(int machineId) {
        String sql = "DELETE FROM machines WHERE machine_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, machineId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error deleting machine: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Get machine by machine name and user ID
     * @param machineName Machine name
     * @param userId User ID
     * @return Machine object if found, null otherwise
     */
    public Machine getMachineByNameAndUserId(String machineName, int userId) {
        String sql = "SELECT * FROM machines WHERE machine_name = ? AND user_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, machineName);
            pstmt.setInt(2, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return new Machine(
                    rs.getInt("machine_id"),
                    rs.getInt("user_id"),
                    rs.getString("machine_name"),
                    rs.getString("ip_address")
                );
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting machine by name and user: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Get or create the current machine for a user
     * This method gets the current computer name and IP, then either retrieves
     * the existing machine record or creates a new one
     * @param userId User ID
     * @return Machine object representing the current machine
     */
    public Machine getOrCreateCurrentMachine(int userId) {
        try {
            // Get current machine name
            String machineName = System.getenv("COMPUTERNAME");
            if (machineName == null || machineName.isEmpty()) {
                machineName = java.net.InetAddress.getLocalHost().getHostName();
            }
            
            // Get current IP address
            String ipAddress = java.net.InetAddress.getLocalHost().getHostAddress();
            
            // Check if machine already exists for this user
            Machine existingMachine = getMachineByNameAndUserId(machineName, userId);
            
            if (existingMachine != null) {
                // Update IP address if it has changed
                if (!existingMachine.getIpAddress().equals(ipAddress)) {
                    existingMachine.setIpAddress(ipAddress);
                    updateMachine(existingMachine);
                }
                return existingMachine;
            } else {
                // Create new machine record
                Machine newMachine = new Machine(userId, machineName, ipAddress);
                if (createMachine(newMachine)) {
                    return newMachine;
                }
            }
            
        } catch (Exception e) {
            System.err.println("Error getting or creating current machine: " + e.getMessage());
            e.printStackTrace();
        }
        
        return null;
    }
}
