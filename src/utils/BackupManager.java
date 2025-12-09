package utils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public class BackupManager {

    // Change this if your MySQL is installed elsewhere
    private static final String MYSQLDUMP_PATH = "C:\\xampp\\mysql\\bin\\mysqldump.exe";
    private static final String DB_NAME = "onclick_db";
    private static final String DB_USER = "root"; // using root
    private static final String DB_PASSWORD = ""; // empty password

    private static final String[] BACKUP_PATHS = {
            "X:\\backups\\onclick_backup.sql",
            "G:\\My Drive\\OneclickPartitionBackup\\onclick_backup.sql"
    };

    public static String backupDatabase() {
        try {
            File mysqldump = new File(MYSQLDUMP_PATH);
            if (!mysqldump.exists()) {
                JOptionPane.showMessageDialog(null,
                        "Backup Error\nCannot find mysqldump.exe. Please check path:\n" + MYSQLDUMP_PATH,
                        "Backup Error", JOptionPane.ERROR_MESSAGE);
                return null;
            }

            for (String path : BACKUP_PATHS) {
                File backupFile = new File(path);
                backupFile.getParentFile().mkdirs(); // create parent directories if not exist

                // Build command
                String command;
                if (DB_PASSWORD.isEmpty()) {
                    command = String.format("\"%s\" -u%s --databases %s -r \"%s\"",
                            MYSQLDUMP_PATH, DB_USER, DB_NAME, path);
                } else {
                    command = String.format("\"%s\" -u%s -p%s --databases %s -r \"%s\"",
                            MYSQLDUMP_PATH, DB_USER, DB_PASSWORD, DB_NAME, path);
                }

                // Execute backup
                Process process = Runtime.getRuntime().exec(command);
                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    JOptionPane.showMessageDialog(null,
                            "Backup failed at path:\n" + path,
                            "Backup Error", JOptionPane.ERROR_MESSAGE);
                    return null;
                }
            }

            // Success
            return "Backup successful!\nSaved to:\n" + String.join("\n", BACKUP_PATHS);

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Backup Error\n" + e.getMessage(),
                    "Backup Error", JOptionPane.ERROR_MESSAGE);
            return null;
        }
    }

    // Optional: show backup result
    public static void showBackupResult(String message) {
        if (message != null) {
            JOptionPane.showMessageDialog(null, " " + message, "Backup Complete", JOptionPane.INFORMATION_MESSAGE);
        }
    }
}
