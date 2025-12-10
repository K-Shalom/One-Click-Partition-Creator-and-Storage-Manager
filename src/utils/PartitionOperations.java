package utils;

import models.User;
import models.Machine;
import dao.MachineDAO;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * Utility class for common partition operations
 * Reduces code duplication between AdminDashboard and UserDashboard
 */
public class PartitionOperations {
    
    /**
     * Execute a PowerShell command asynchronously with a loading dialog
     * @param parent Parent frame for the dialog
     * @param command PowerShell command to execute
     * @param actionDescription Description of the action
     * @param onComplete Callback to run after completion (can be null)
     */
    public static void runPowerShellAsync(JFrame parent, String command, String actionDescription, Runnable onComplete) {
        JDialog loader = new JDialog(parent, "OneClick Partition", true);
        JLabel lbl = new JLabel(actionDescription + "...");
        lbl.setBorder(new EmptyBorder(10, 20, 10, 20));
        loader.add(lbl);
        loader.pack();
        loader.setLocationRelativeTo(parent);

        new Thread(() -> {
            try {
                String wrapped = "$ErrorActionPreference='Stop'; " + command + "; try { Start-Sleep -Milliseconds 500; Update-HostStorageCache -ErrorAction SilentlyContinue } catch {}";
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", wrapped);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
                reader.close();
                int exitCode = process.waitFor();

                String result = output.toString();
                boolean likelyError = exitCode != 0
                        || result.toLowerCase().contains("error")
                        || result.toLowerCase().contains("denied")
                        || result.toLowerCase().contains("unauthorized")
                        || result.contains("No MSFT_Partition objects found");
                if (likelyError) {
                    System.err.println("[ERROR] " + actionDescription + ":\n" + result);
                    final String details = summarizeOutput(result);
                    SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(parent,
                            actionDescription + " failed.\nIf you are not running as Administrator, please run the app as Admin.\n\nDetails:\n" + details,
                            "Operation Failed",
                            JOptionPane.ERROR_MESSAGE));
                } else {
                    System.out.println("[SUCCESS] " + actionDescription + (result.isEmpty() ? "" : ("\n" + result)));
                }

            } catch (Exception e) {
                System.err.println("[ERROR] " + actionDescription + ": " + e.getMessage());
            } finally {
                SwingUtilities.invokeLater(() -> {
                    loader.dispose();
                    if (onComplete != null) {
                        onComplete.run();
                    }
                });
            }
        }).start();

        loader.setVisible(true);
    }

    private static String summarizeOutput(String output) {
        if (output == null) return "";
        String[] lines = output.split("\n");
        int start = Math.max(0, lines.length - 6);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < lines.length; i++) sb.append(lines[i]).append("\n");
        return sb.toString().trim();
    }
    
    /**
     * Get volume label for a drive using PowerShell
     * @param driveLetter Drive letter (e.g., "C")
     * @return Volume label or empty string if not found
     */
    public static String getVolumeLabel(String driveLetter) {
        try {
            String command = "(Get-Volume -DriveLetter " + driveLetter + ").FileSystemLabel";
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String label = reader.readLine();
            reader.close();
            process.waitFor();
            
            if (label != null && !label.trim().isEmpty() && !label.contains("error")) {
                return label.trim();
            }
        } catch (Exception e) {
            // Silently fail and return empty string
        }
        return "";
    }
    
    /**
     * Get machine ID for activity logging
     * @param user Current user
     * @param machineDAO Machine DAO instance
     * @return machine ID or 0 if not found
     */
    public static int getMachineId(User user, MachineDAO machineDAO) {
        try {
            Machine currentMachine = machineDAO.getOrCreateCurrentMachine(user.getUserId());
            return (currentMachine != null) ? currentMachine.getMachineId() : 0;
        } catch (Exception e) {
            System.err.println("Error getting machine ID: " + e.getMessage());
            return 0;
        }
    }
    
    /**
     * Execute shrink volume operation
     */
    public static void executeShrinkVolume(JFrame parent, String drive, long totalBytes, long freeBytes, 
                                          User user, MachineDAO machineDAO, Runnable refreshCallback) {
        if (drive == null || drive.trim().isEmpty()) return;
        drive = drive.replace(":", "").trim().toUpperCase();
        double totalGB = totalBytes / 1_073_741_824.0;
        long sizeMinBytes = totalBytes;
        try {
            String cmdMin = "(Get-PartitionSupportedSize -DriveLetter " + drive + ").SizeMin";
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", cmdMin);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String s = r.readLine();
            r.close();
            if (s != null && !s.trim().isEmpty()) {
                sizeMinBytes = Long.parseLong(s.trim());
            }
        } catch (Exception ignored) {}
        double maxShrinkGB = Math.max(0, (totalBytes - sizeMinBytes) / 1_073_741_824.0);
        String shrinkInput = JOptionPane.showInputDialog(parent, "Enter amount to shrink (GB):", 
                                                         String.format("%.2f", maxShrinkGB));
        if (shrinkInput != null && !shrinkInput.trim().isEmpty()) {
            try {
                double shrinkBy = Double.parseDouble(shrinkInput.trim());
                if (shrinkBy > maxShrinkGB) shrinkBy = maxShrinkGB;
                long shrinkBytes = (long) Math.round(shrinkBy * 1_073_741_824.0);
                long newSizeBytes = totalBytes - shrinkBytes;
                if (newSizeBytes < sizeMinBytes) newSizeBytes = sizeMinBytes;
                if (newSizeBytes <= 0) {
                    JOptionPane.showMessageDialog(parent, "Shrink too large! Resulting size invalid.", 
                                                 "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                long shrinkMB = (long) Math.max(1, Math.round(shrinkBytes / 1_048_576.0));
                String cmd = "$drive='" + drive + "'; $size=" + newSizeBytes + "; $shrinkMB=" + shrinkMB + "; " +
                        "try { Resize-Partition -DriveLetter $drive -Size $size -Confirm:$false } " +
                        "catch { $script = \"select volume $drive`r`nshrink desired=$shrinkMB\"; " +
                        "$path = [IO.Path]::GetTempFileName()+'.txt'; Set-Content -Path $path -Value $script; " +
                        "diskpart /s $path; Remove-Item $path -Force }";
                String action = "Shrink Volume on " + drive + " by " + String.format("%.2f", shrinkBy) + "GB";
                ActivityLogger.logCustomAction(user.getUserId(), getMachineId(user, machineDAO), action);
                runPowerShellAsync(parent, cmd, action, refreshCallback);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(parent, "Invalid number format. Enter numeric GB.", 
                                             "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Execute extend volume operation
     */
    public static void executeExtendVolume(JFrame parent, String drive, long totalBytes, long unallocatedBytes,
                                          User user, MachineDAO machineDAO, Runnable refreshCallback) {
        if (drive == null || drive.trim().isEmpty()) return;
        drive = drive.replace(":", "").trim().toUpperCase();
        double totalGB = totalBytes / 1_073_741_824.0;
        long sizeMaxBytes = totalBytes;
        try {
            String cmdMax = "(Get-PartitionSupportedSize -DriveLetter " + drive + ").SizeMax";
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", cmdMax);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String s = r.readLine();
            r.close();
            if (s != null && !s.trim().isEmpty()) {
                sizeMaxBytes = Long.parseLong(s.trim());
            }
        } catch (Exception ignored) {}
        double maxExtendGB = Math.max(0, (sizeMaxBytes - totalBytes) / 1_073_741_824.0);
        String extendInput = JOptionPane.showInputDialog(parent, "Enter amount to extend (GB):", 
                                                         String.format("%.2f", maxExtendGB));
        if (extendInput != null && !extendInput.trim().isEmpty()) {
            try {
                double extendBy = Double.parseDouble(extendInput.trim());
                if (extendBy > maxExtendGB) extendBy = maxExtendGB;
                long extendBytes = (long) Math.round(extendBy * 1_073_741_824.0);
                long newSizeBytes = totalBytes + extendBytes;
                if (newSizeBytes > sizeMaxBytes) newSizeBytes = sizeMaxBytes;
                String cmd = "Resize-Partition -DriveLetter " + drive + " -Size " + newSizeBytes + " -Confirm:$false";
                String action = "Extend Volume on " + drive + " by " + String.format("%.2f", extendBy) + "GB";
                ActivityLogger.logCustomAction(user.getUserId(), getMachineId(user, machineDAO), action);
                runPowerShellAsync(parent, cmd, action, refreshCallback);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(parent, "Invalid number format. Enter numeric GB.", 
                                             "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }
    
    /**
     * Execute format volume operation
     */
    public static void executeFormatVolume(JFrame parent, String drive, User user, MachineDAO machineDAO, 
                                          Runnable refreshCallback) {
        String[] fileSystems = {"NTFS", "FAT32", "exFAT"};
        String filesystem = (String) JOptionPane.showInputDialog(parent, "Select FileSystem:", "Format Volume",
                JOptionPane.QUESTION_MESSAGE, null, fileSystems, fileSystems[0]);
        if (filesystem != null) {
            String cmd = "Format-Volume -DriveLetter " + drive + " -FileSystem " + filesystem + " -Confirm:$false";
            String action = "Format Volume on " + drive;
            
            ActivityLogger.logCustomAction(user.getUserId(), getMachineId(user, machineDAO), 
                                          "Format Volume on " + drive + " with " + filesystem);
            runPowerShellAsync(parent, cmd, action, refreshCallback);
        }
    }
    
    /**
     * Execute delete volume operation
     */
    public static void executeDeleteVolume(JFrame parent, String drive, User user, MachineDAO machineDAO, 
                                          Runnable refreshCallback) {
        int confirm = JOptionPane.showConfirmDialog(parent,
                "Are you sure you want to delete drive " + drive + "?",
                "Delete Volume", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            String cmd = "Remove-Partition -DriveLetter " + drive + " -Confirm:$false";
            String action = "Delete Volume on " + drive;
            
            ActivityLogger.logPartitionDeleted(user.getUserId(), getMachineId(user, machineDAO), drive);
            runPowerShellAsync(parent, cmd, action, refreshCallback);
        }
    }
    
    /**
     * Execute rename volume operation
     */
    public static void executeRenameVolume(JFrame parent, String drive, User user, MachineDAO machineDAO, 
                                          Runnable refreshCallback) {
        if (drive == null || drive.trim().isEmpty()) return;
        String newName = JOptionPane.showInputDialog(parent, "Enter new volume name:");
        if (newName != null && !newName.trim().isEmpty()) {
            drive = drive.replace(":", "").trim().toUpperCase();
            newName = newName.trim();
            String cmd = "$vol = Get-Volume -DriveLetter " + drive + "; if ($vol -ne $null) { Set-Volume -DriveLetter " 
                       + drive + " -NewFileSystemLabel \"" + newName + "\"; Write-Output 'Volume renamed to " + newName 
                       + "' } else { Write-Error 'Volume not found.' }";
            String action = "Rename Volume " + drive + " -> " + newName;
            
            ActivityLogger.logCustomAction(user.getUserId(), getMachineId(user, machineDAO), 
                                          "Rename Volume " + drive + " to " + newName);
            runPowerShellAsync(parent, cmd, action, refreshCallback);
        }
    }
    
    /**
     * Execute change drive letter operation
     */
    public static void executeChangeDriveLetter(JFrame parent, String oldDrive, User user, MachineDAO machineDAO, 
                                               Runnable refreshCallback) {
        if (oldDrive == null || oldDrive.trim().isEmpty()) return;
        String newDrive = JOptionPane.showInputDialog(parent, "Enter new Drive Letter (e.g., D):");
        if (newDrive != null && !newDrive.trim().isEmpty()) {
            oldDrive = oldDrive.replace(":", "").trim().toUpperCase();
            newDrive = newDrive.replace(":", "").trim().toUpperCase();
            if (newDrive.length() != 1 || !Character.isLetter(newDrive.charAt(0))) {
                JOptionPane.showMessageDialog(parent, "Invalid drive letter.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String cmd = "$part = Get-Partition -DriveLetter " + oldDrive + "; if ($part) { Set-Partition -DriveLetter " 
                       + oldDrive + " -NewDriveLetter " + newDrive + " -Confirm:$false; Write-Output 'Success' } else { "
                       + "Write-Error 'Partition not found or in use.' }";
            String action = "Change Drive Letter " + oldDrive + " -> " + newDrive;
            
            ActivityLogger.logCustomAction(user.getUserId(), getMachineId(user, machineDAO), 
                                          "Change Drive Letter " + oldDrive + " to " + newDrive);
            runPowerShellAsync(parent, cmd, action, refreshCallback);
        }
    }
    
    /**
     * Execute new sample volume operation
     */
    public static void executeNewSampleVolume(JFrame parent, long diskNumber, User user, MachineDAO machineDAO, 
                                             Runnable refreshCallback) {
        String[] fileSystems = {"NTFS", "FAT32", "exFAT"};
        String filesystem = (String) JOptionPane.showInputDialog(parent, "Select FileSystem:", "New Sample Volume",
                JOptionPane.QUESTION_MESSAGE, null, fileSystems, fileSystems[0]);
        String drive = JOptionPane.showInputDialog(parent, "Enter Drive Letter to format (e.g., E):");
        if (filesystem != null && drive != null) {
            String cmd = "New-Partition -DiskNumber " + diskNumber + " -UseMaximumSize -DriveLetter " + drive
                       + " | Format-Volume -FileSystem " + filesystem + " -NewFileSystemLabel 'New Volume' -Confirm:$false";
            String action = "New Sample Volume on Disk " + diskNumber;
            
            ActivityLogger.logCustomAction(user.getUserId(), getMachineId(user, machineDAO), 
                                          "Created New Volume " + drive + " on Disk " + diskNumber + " with " + filesystem);
            runPowerShellAsync(parent, cmd, action, refreshCallback);
        }
    }

    public static void executeCreateVolume(JFrame parent, long diskNumber, User user, MachineDAO machineDAO,
                                           Runnable refreshCallback) {
        String sizeInput = JOptionPane.showInputDialog(parent, "Enter size (GB):", "1");
        if (sizeInput == null || sizeInput.trim().isEmpty()) return;
        double sizeGB;
        try { sizeGB = Double.parseDouble(sizeInput.trim()); } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Invalid size.", "Error", JOptionPane.ERROR_MESSAGE); return; }
        String[] fileSystems = {"NTFS", "FAT32", "exFAT"};
        String filesystem = (String) JOptionPane.showInputDialog(parent, "Select FileSystem:", "Create Volume",
                JOptionPane.QUESTION_MESSAGE, null, fileSystems, fileSystems[0]);
        if (filesystem == null) return;
        String label = JOptionPane.showInputDialog(parent, "Enter Volume Label:", "New Volume");
        if (label == null) label = "New Volume";
        String drive = JOptionPane.showInputDialog(parent, "Enter Drive Letter (e.g., E):");
        if (drive == null || drive.trim().isEmpty()) return;
        drive = drive.replace(":", "").trim().toUpperCase();
        long sizeBytes = (long) Math.round(sizeGB * 1_073_741_824.0);
        String cmd = "New-Partition -DiskNumber " + diskNumber + " -Size " + sizeBytes + " -DriveLetter " + drive
                   + " | Format-Volume -FileSystem " + filesystem + " -NewFileSystemLabel '" + label + "' -Confirm:$false";
        String action = "Create Volume " + drive + " on Disk " + diskNumber + " (" + sizeGB + "GB)";
        ActivityLogger.logCustomAction(user.getUserId(), getMachineId(user, machineDAO), action);
        runPowerShellAsync(parent, cmd, action, refreshCallback);
    }

    public static void showSupportedSize(JFrame parent, String drive) {
        if (drive == null || drive.trim().isEmpty()) return;
        drive = drive.replace(":", "").trim().toUpperCase();
        try {
            String cmd = "$s=Get-PartitionSupportedSize -DriveLetter " + drive + "; Write-Output $s.SizeMin; Write-Output $s.SizeMax";
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", cmd);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String minStr = r.readLine();
            String maxStr = r.readLine();
            r.close();
            long min = (minStr != null && !minStr.trim().isEmpty()) ? Long.parseLong(minStr.trim()) : -1;
            long max = (maxStr != null && !maxStr.trim().isEmpty()) ? Long.parseLong(maxStr.trim()) : -1;
            double minGB = (min >= 0) ? (min / 1_073_741_824.0) : -1;
            double maxGB = (max >= 0) ? (max / 1_073_741_824.0) : -1;
            JOptionPane.showMessageDialog(parent,
                    "Supported size for " + drive + ":\n" +
                            "Min: " + (minGB >= 0 ? String.format("%.2f GB", minGB) : "n/a") + "\n" +
                            "Max: " + (maxGB >= 0 ? String.format("%.2f GB", maxGB) : "n/a"),
                    "Partition Supported Size",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(parent, "Failed to query supported size: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
