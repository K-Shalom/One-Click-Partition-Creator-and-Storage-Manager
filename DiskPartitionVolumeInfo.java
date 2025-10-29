package javaexercises;

import java.io.BufferedReader;
import java.io.InputStreamReader;

public class DiskPartitionVolumeInfo {
    public static void main(String[] args) {
        showSection("=== DISK INFORMATION ===",
                "Get-Disk | Select Number,FriendlyName,Size,PartitionStyle");

        showSection("=== PARTITION INFORMATION ===",
                "Get-Partition | Select DiskNumber,PartitionNumber,DriveLetter,Size");

        showSection("=== VOLUME INFORMATION ===",
                "Get-Volume | Select DriveLetter,FileSystemLabel,FileSystem,SizeRemaining,Size");
    }

    private static void showSection(String title, String command) {
        try {
            System.out.println("\n" + title);
            System.out.println("-------------------------------------------------------");

            // Run PowerShell command
            ProcessBuilder pb = new ProcessBuilder(
                    "powershell.exe", "-Command", command);
            Process process = pb.start();

            // Read PowerShell output
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));

            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println(line);
            }

            process.waitFor();
            System.out.println("-------------------------------------------------------");

        } catch (Exception e) {
            System.out.println("❌ Error running command: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
