package gui;

import java.io.*;
import java.util.*;

public class PartitionStorage {
    
    /**
     * Represents a partition with its details
     */
    public static class PartitionInfo {
        private String driveLetter;
        private long sizeGB;
        
        public PartitionInfo(String driveLetter, long sizeGB) {
            this.driveLetter = driveLetter;
            this.sizeGB = sizeGB;
        }
        
        public String getDriveLetter() {
            return driveLetter;
        }
        
        public long getSizeGB() {
            return sizeGB;
        }
        
        @Override
        public String toString() {
            return driveLetter + " (" + sizeGB + " GB)";
        }
    }
    
    /**
     * Get all volumes/partitions with basic info
     * @return List of drive letters
     */
    public static List<String> getVolumes() {
        List<String> volumes = new ArrayList<>();
        try {
            String command = "powershell.exe Get-Volume | ForEach-Object {$_.DriveLetter + ':\\'}";
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) volumes.add(line.trim());
            }
            reader.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return volumes;
    }
    
    /**
     * Get all system partitions with detailed information
     * @return List of PartitionInfo objects containing drive letter and size
     */
    public static List<PartitionInfo> getSystemPartitions() {
        List<PartitionInfo> partitions = new ArrayList<>();
        
        try {
            // PowerShell command to get all fixed drives with their size
            String command = "powershell.exe \"Get-PSDrive -PSProvider FileSystem | Where-Object {$_.Used -ne $null} | ForEach-Object {Write-Output ($_.Name + '|' + [Math]::Round(($_.Used + $_.Free) / 1GB, 2))}\"";
            
            Process process = Runtime.getRuntime().exec(command);
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.contains("|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2) {
                        String driveLetter = parts[0].trim();
                        try {
                            double sizeGB = Double.parseDouble(parts[1].trim());
                            // Only add valid drive letters (single character)
                            if (driveLetter.length() == 1 && Character.isLetter(driveLetter.charAt(0))) {
                                partitions.add(new PartitionInfo(driveLetter + ":", (long) sizeGB));
                            }
                        } catch (NumberFormatException e) {
                            System.err.println("Error parsing size for drive " + driveLetter + ": " + parts[1]);
                        }
                    }
                }
            }
            
            // Read any errors
            String error;
            while ((error = errorReader.readLine()) != null) {
                System.err.println("PowerShell Error: " + error);
            }
            
            reader.close();
            errorReader.close();
            process.waitFor();
            
        } catch (Exception e) {
            System.err.println("Error getting system partitions: " + e.getMessage());
            e.printStackTrace();
        }
        
        return partitions;
    }
}
