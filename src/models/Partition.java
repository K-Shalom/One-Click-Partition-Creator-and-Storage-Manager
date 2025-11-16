package models;

import java.sql.Date;

/**
 * Partition Model Class
 * Represents a disk partition
 */
public class Partition {
    private int partitionId;
    private int machineId;
    private int userId;
    private String driveLetter;
    private int sizeGb;
    private Date createdDate;
    
    // Constructors
    public Partition() {}
    
    public Partition(int machineId, int userId, String driveLetter, int sizeGb, Date createdDate) {
        this.machineId = machineId;
        this.userId = userId;
        this.driveLetter = driveLetter;
        this.sizeGb = sizeGb;
        this.createdDate = createdDate;
    }
    
    public Partition(int partitionId, int machineId, int userId, String driveLetter, int sizeGb, Date createdDate) {
        this.partitionId = partitionId;
        this.machineId = machineId;
        this.userId = userId;
        this.driveLetter = driveLetter;
        this.sizeGb = sizeGb;
        this.createdDate = createdDate;
    }
    
    // Getters and Setters
    public int getPartitionId() {
        return partitionId;
    }
    
    public void setPartitionId(int partitionId) {
        this.partitionId = partitionId;
    }
    
    public int getMachineId() {
        return machineId;
    }
    
    public void setMachineId(int machineId) {
        this.machineId = machineId;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public String getDriveLetter() {
        return driveLetter;
    }
    
    public void setDriveLetter(String driveLetter) {
        this.driveLetter = driveLetter;
    }
    
    public int getSizeGb() {
        return sizeGb;
    }
    
    public void setSizeGb(int sizeGb) {
        this.sizeGb = sizeGb;
    }
    
    public Date getCreatedDate() {
        return createdDate;
    }
    
    public void setCreatedDate(Date createdDate) {
        this.createdDate = createdDate;
    }
    
    @Override
    public String toString() {
        return "Partition{" +
                "partitionId=" + partitionId +
                ", machineId=" + machineId +
                ", userId=" + userId +
                ", driveLetter='" + driveLetter + '\'' +
                ", sizeGb=" + sizeGb +
                ", createdDate=" + createdDate +
                '}';
    }
}
