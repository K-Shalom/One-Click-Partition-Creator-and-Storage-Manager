package dao;

import database.DatabaseConnection;
import models.Partition;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Partition Data Access Object
 * Handles all database operations for Partition entity
 */
public class PartitionDAO {
    
    /**
     * Create a new partition in the database
     * @param partition Partition object to create
     * @return true if successful, false otherwise
     */
    public boolean createPartition(Partition partition) {
        String sql = "INSERT INTO partitions (machine_id, user_id, drive_letter, size_gb, created_date) VALUES (?, ?, ?, ?, ?)";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            
            pstmt.setInt(1, partition.getMachineId());
            pstmt.setInt(2, partition.getUserId());
            pstmt.setString(3, partition.getDriveLetter());
            pstmt.setInt(4, partition.getSizeGb());
            pstmt.setDate(5, partition.getCreatedDate());
            
            int rowsAffected = pstmt.executeUpdate();
            
            if (rowsAffected > 0) {
                ResultSet rs = pstmt.getGeneratedKeys();
                if (rs.next()) {
                    partition.setPartitionId(rs.getInt(1));
                }
                return true;
            }
            
        } catch (SQLException e) {
            System.err.println("Error creating partition: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Get partition by ID
     * @param partitionId Partition ID
     * @return Partition object if found, null otherwise
     */
    public Partition getPartitionById(int partitionId) {
        String sql = "SELECT * FROM partitions WHERE partition_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, partitionId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return new Partition(
                    rs.getInt("partition_id"),
                    rs.getInt("machine_id"),
                    rs.getInt("user_id"),
                    rs.getString("drive_letter"),
                    rs.getInt("size_gb"),
                    rs.getDate("created_date")
                );
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting partition: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Get all partitions for a specific machine
     * @param machineId Machine ID
     * @return List of partitions on the machine
     */
    public List<Partition> getPartitionsByMachineId(int machineId) {
        List<Partition> partitions = new ArrayList<>();
        String sql = "SELECT * FROM partitions WHERE machine_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, machineId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                partitions.add(new Partition(
                    rs.getInt("partition_id"),
                    rs.getInt("machine_id"),
                    rs.getInt("user_id"),
                    rs.getString("drive_letter"),
                    rs.getInt("size_gb"),
                    rs.getDate("created_date")
                ));
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting partitions by machine: " + e.getMessage());
            e.printStackTrace();
        }
        return partitions;
    }
    
    /**
     * Get all partitions for a specific user
     * @param userId User ID
     * @return List of partitions created by the user
     */
    public List<Partition> getPartitionsByUserId(int userId) {
        List<Partition> partitions = new ArrayList<>();
        String sql = "SELECT * FROM partitions WHERE user_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, userId);
            ResultSet rs = pstmt.executeQuery();
            
            while (rs.next()) {
                partitions.add(new Partition(
                    rs.getInt("partition_id"),
                    rs.getInt("machine_id"),
                    rs.getInt("user_id"),
                    rs.getString("drive_letter"),
                    rs.getInt("size_gb"),
                    rs.getDate("created_date")
                ));
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting partitions by user: " + e.getMessage());
            e.printStackTrace();
        }
        return partitions;
    }
    
    /**
     * Get all partitions
     * @return List of all partitions
     */
    public List<Partition> getAllPartitions() {
        List<Partition> partitions = new ArrayList<>();
        String sql = "SELECT * FROM partitions";
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            
            while (rs.next()) {
                partitions.add(new Partition(
                    rs.getInt("partition_id"),
                    rs.getInt("machine_id"),
                    rs.getInt("user_id"),
                    rs.getString("drive_letter"),
                    rs.getInt("size_gb"),
                    rs.getDate("created_date")
                ));
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting all partitions: " + e.getMessage());
            e.printStackTrace();
        }
        return partitions;
    }
    
    /**
     * Update partition information
     * @param partition Partition object with updated information
     * @return true if successful, false otherwise
     */
    public boolean updatePartition(Partition partition) {
        String sql = "UPDATE partitions SET machine_id = ?, user_id = ?, drive_letter = ?, size_gb = ?, created_date = ? WHERE partition_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, partition.getMachineId());
            pstmt.setInt(2, partition.getUserId());
            pstmt.setString(3, partition.getDriveLetter());
            pstmt.setInt(4, partition.getSizeGb());
            pstmt.setDate(5, partition.getCreatedDate());
            pstmt.setInt(6, partition.getPartitionId());
            
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error updating partition: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Delete partition
     * @param partitionId Partition ID to delete
     * @return true if successful, false otherwise
     */
    public boolean deletePartition(int partitionId) {
        String sql = "DELETE FROM partitions WHERE partition_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setInt(1, partitionId);
            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0;
            
        } catch (SQLException e) {
            System.err.println("Error deleting partition: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }
    
    /**
     * Get partition by drive letter, machine ID, and user ID
     * @param driveLetter Drive letter (e.g., "C:")
     * @param machineId Machine ID
     * @param userId User ID
     * @return Partition object if found, null otherwise
     */
    public Partition getPartitionByDriveAndMachine(String driveLetter, int machineId, int userId) {
        String sql = "SELECT * FROM partitions WHERE drive_letter = ? AND machine_id = ? AND user_id = ?";
        
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            pstmt.setString(1, driveLetter);
            pstmt.setInt(2, machineId);
            pstmt.setInt(3, userId);
            ResultSet rs = pstmt.executeQuery();
            
            if (rs.next()) {
                return new Partition(
                    rs.getInt("partition_id"),
                    rs.getInt("machine_id"),
                    rs.getInt("user_id"),
                    rs.getString("drive_letter"),
                    rs.getInt("size_gb"),
                    rs.getDate("created_date")
                );
            }
            
        } catch (SQLException e) {
            System.err.println("Error getting partition by drive and machine: " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }
}
