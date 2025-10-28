package gui;

import java.io.*;
import java.util.*;

public class PartitionStorage {
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
}
