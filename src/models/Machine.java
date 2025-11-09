package models;

/**
 * Machine Model Class
 * Represents a computer/machine in the network
 */
public class Machine {
    private int machineId;
    private int userId;
    private String machineName;
    private String ipAddress;
    
    // Constructors
    public Machine() {}
    
    public Machine(int userId, String machineName, String ipAddress) {
        this.userId = userId;
        this.machineName = machineName;
        this.ipAddress = ipAddress;
    }
    
    public Machine(int machineId, int userId, String machineName, String ipAddress) {
        this.machineId = machineId;
        this.userId = userId;
        this.machineName = machineName;
        this.ipAddress = ipAddress;
    }
    
    // Getters and Setters
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
    
    public String getMachineName() {
        return machineName;
    }
    
    public void setMachineName(String machineName) {
        this.machineName = machineName;
    }
    
    public String getIpAddress() {
        return ipAddress;
    }
    
    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }
    
    @Override
    public String toString() {
        return "Machine{" +
                "machineId=" + machineId +
                ", userId=" + userId +
                ", machineName='" + machineName + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                '}';
    }
}
