package models;

import java.sql.Timestamp;

/**
 * ActivityLog Model Class
 * Represents an activity log entry
 */
public class ActivityLog {
    private int logId;
    private int userId;
    private int machineId;
    private String action;
    private Timestamp logDate;
    
    // Constructors
    public ActivityLog() {}
    
    public ActivityLog(int userId, int machineId, String action) {
        this.userId = userId;
        this.machineId = machineId;
        this.action = action;
        this.logDate = new Timestamp(System.currentTimeMillis());
    }
    
    public ActivityLog(int logId, int userId, int machineId, String action, Timestamp logDate) {
        this.logId = logId;
        this.userId = userId;
        this.machineId = machineId;
        this.action = action;
        this.logDate = logDate;
    }
    
    // Getters and Setters
    public int getLogId() {
        return logId;
    }
    
    public void setLogId(int logId) {
        this.logId = logId;
    }
    
    public int getUserId() {
        return userId;
    }
    
    public void setUserId(int userId) {
        this.userId = userId;
    }
    
    public int getMachineId() {
        return machineId;
    }
    
    public void setMachineId(int machineId) {
        this.machineId = machineId;
    }
    
    public String getAction() {
        return action;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public Timestamp getLogDate() {
        return logDate;
    }
    
    public void setLogDate(Timestamp logDate) {
        this.logDate = logDate;
    }
    
    @Override
    public String toString() {
        return "ActivityLog{" +
                "logId=" + logId +
                ", userId=" + userId +
                ", machineId=" + machineId +
                ", action='" + action + '\'' +
                ", logDate=" + logDate +
                '}';
    }
}
