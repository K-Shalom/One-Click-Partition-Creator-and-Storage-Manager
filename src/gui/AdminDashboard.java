package gui;

import dao.UserDAO;
import dao.PartitionDAO;
import dao.ActivityLogDAO;
import dao.MachineDAO;
import models.User;
import models.Partition;
import models.ActivityLog;
import models.Machine;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.ChangeListener;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;

public class AdminDashboard extends JFrame {

    private JPanel diskPanel;
    private JTable userTable;
    private JTextArea logArea;
    private Timer autoRefreshTimer; // javax.swing.Timer
    private ArrayList<String> logs = new ArrayList<>();
    private User currentUser;
    private JTabbedPane tabs;
    
    // DAOs for database access
    private UserDAO userDAO;
    private PartitionDAO partitionDAO;
    private ActivityLogDAO activityLogDAO;
    private MachineDAO machineDAO;

    public AdminDashboard(User user) {
        this.currentUser = user;
        
        // Initialize DAOs
        this.userDAO = new UserDAO();
        this.partitionDAO = new PartitionDAO();
        this.activityLogDAO = new ActivityLogDAO();
        this.machineDAO = new MachineDAO();

        setTitle("Admin Dashboard - " + user.getUsername());
        setSize(950, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // ---------- HEADER ----------
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(25, 118, 210));
        header.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel title = new JLabel("ONE CLICK | ADMIN DASHBOARD", JLabel.LEFT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logoutBtn.setBackground(new Color(231, 76, 60));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Logout?", "Logout", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                addLog(currentUser.getUsername() + " logged out");
                if (autoRefreshTimer != null) autoRefreshTimer.stop();
                dispose();
                new LoginForm().setVisible(true);
            }
        });
        header.add(logoutBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ---------- TABS ----------
        tabs = new JTabbedPane();
        tabs.addTab("👥 Users", createUserTab());
        tabs.addTab("💾 Disk Monitor", createDiskTab());
        tabs.addTab("📜 Logs", createLogTab());

        // ---------- TOOLBAR ABOVE TABS ----------
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        toolbar.setBackground(new Color(240, 240, 240));

        JButton remoteBtn = new JButton("🌐 Remote Partition");
        JButton backupBtn = new JButton("🗂️ Backup");

        Font btnFont = new Font("Segoe UI", Font.BOLD, 14);
        remoteBtn.setFont(btnFont);
        backupBtn.setFont(btnFont);

        remoteBtn.setBackground(new Color(52, 152, 219));
        backupBtn.setBackground(new Color(46, 204, 113));
        remoteBtn.setForeground(Color.WHITE);
        backupBtn.setForeground(Color.WHITE);
        remoteBtn.setFocusPainted(false);
        backupBtn.setFocusPainted(false);

        remoteBtn.addActionListener(e -> {
            int index = tabs.indexOfTab("🌐 Remote Partition");
            if (index == -1) {
                JPanel remotePanel = new JPanel(new BorderLayout());
                remotePanel.add(new JLabel("Remote Partition Feature Coming Soon!", JLabel.CENTER), BorderLayout.CENTER);
                tabs.addTab("🌐 Remote Partition", remotePanel);
                tabs.setSelectedComponent(remotePanel);
            } else {
                tabs.setSelectedIndex(index);
            }
            addLog(currentUser.getUsername() + " used Remote Partition");
        });

        backupBtn.addActionListener(e -> {
            int index = tabs.indexOfTab("🗂️ Backup");
            if (index == -1) {
                JPanel backupPanel = new JPanel(new BorderLayout());
                backupPanel.add(new JLabel("Backup Feature Coming Soon!", JLabel.CENTER), BorderLayout.CENTER);
                tabs.addTab("🗂️ Backup", backupPanel);
                tabs.setSelectedComponent(backupPanel);
            } else {
                tabs.setSelectedIndex(index);
            }
            addLog(currentUser.getUsername() + " used Backup feature");
        });

        toolbar.add(remoteBtn);
        toolbar.add(backupBtn);

        // ---------- CENTER PANEL ----------
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(toolbar, BorderLayout.NORTH); // toolbar on top
        centerPanel.add(tabs, BorderLayout.CENTER);    // tabs below
        add(centerPanel, BorderLayout.CENTER);

        // ---------- FOOTER ----------
        JLabel footer = new JLabel("© 2025 One Click Project | Rwanda Polytechnic", JLabel.CENTER);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        footer.setForeground(Color.GRAY);
        footer.setBorder(new EmptyBorder(10, 0, 10, 0));
        add(footer, BorderLayout.SOUTH);

        // Start auto-refresh for Disk Monitor tab
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) startAutoRefresh();
            else if (autoRefreshTimer != null) autoRefreshTimer.stop();
        });

        addLog(currentUser.getUsername() + " logged in");
        
        // Load initial data from database
        loadUsersFromDatabase();
        loadActivityLogsFromDatabase();
    }

    // ---------------- USERS TAB ----------------
    private JPanel createUserTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        String[] columns = {"User ID", "Username", "Role", "Status"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }
        };
        
        userTable = new JTable(model);
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userTable.setRowHeight(25);

        JButton refreshBtn = new JButton("Refresh Users");
        refreshBtn.addActionListener(e -> {
            loadUsersFromDatabase();
            addLog(currentUser.getUsername() + " refreshed user table");
        });

        panel.add(new JScrollPane(userTable), BorderLayout.CENTER);
        panel.add(refreshBtn, BorderLayout.SOUTH);
        return panel;
    }
    
    /**
     * Load all users from database and display in table
     */
    private void loadUsersFromDatabase() {
        try {
            if (userDAO == null) {
                addLog("Error: UserDAO not initialized");
                return;
            }
            
            List<User> users = userDAO.getAllUsers();
            
            if (users == null) {
                addLog("Warning: Could not load users from database (connection may be unavailable)");
                return;
            }
            
            DefaultTableModel model = (DefaultTableModel) userTable.getModel();
            model.setRowCount(0); // Clear existing rows
            
            for (User user : users) {
                Object[] row = {
                    user.getUserId(),
                    user.getUsername(),
                    user.getRole(),
                    "Active" // You can add a status field to User model if needed
                };
                model.addRow(row);
            }
            
            addLog("Loaded " + users.size() + " users from database");
        } catch (Exception e) {
            addLog("Error loading users: " + e.getMessage());
            System.err.println("Error loading users: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ---------------- DISK TAB ----------------
    private JPanel createDiskTab() {
        diskPanel = new JPanel();
        diskPanel.setLayout(new BoxLayout(diskPanel, BoxLayout.Y_AXIS));
        diskPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(diskPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Detected Drives"));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(scroll, BorderLayout.CENTER);

        detectDisks();
        return panel;
    }

    private void detectDisks() {
        diskPanel.removeAll();
        File[] roots = File.listRoots();
        long totalDiskSize = 0;

        if (roots != null) {
            for (File root : roots) {
                long free = root.getFreeSpace();
                long total = root.getTotalSpace();
                totalDiskSize += total;
                int usedPercent = (int) (((double)(total - free) / total) * 100);

                JPanel card = new JPanel(new BorderLayout(10, 5));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(200, 200, 200)),
                        new EmptyBorder(5, 10, 5, 10)
                ));
                card.setBackground(Color.WHITE);

                JLabel label = new JLabel(root.getAbsolutePath() + " — Free: " + (free / (1024*1024*1024)) + "GB / Total: " + (total / (1024*1024*1024)) + "GB");
                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));

                JProgressBar progress = new JProgressBar(0, 100);
                progress.setValue(usedPercent);
                progress.setStringPainted(true);
                progress.setPreferredSize(new Dimension(800, 25));
                progress.setForeground(new Color(76, 175, 80));

                card.add(label, BorderLayout.NORTH);
                card.add(progress, BorderLayout.SOUTH);

                // ------------------ Right-click menu ------------------
                JPopupMenu partitionMenu = new JPopupMenu();
                String[] actions = {"Shrink Volume", "Format Volume", "Delete Volume", "Extend Volume", "Rename Volume", "Change Drive Letter"};
                for (String action : actions) {
                    JMenuItem item = new JMenuItem(action);
                    item.addActionListener(e -> {
                        String driveLetter = root.getAbsolutePath().replace("\\", "");
                        long freeBytes = root.getFreeSpace();
                        long totalBytes = root.getTotalSpace();

                        switch (action) {
                            case "Shrink Volume": executeShrinkVolume(driveLetter, totalBytes, freeBytes); break;
                            case "Format Volume": executeFormatVolume(driveLetter); break;
                            case "Delete Volume": executeDeleteVolume(driveLetter); break;
                            case "Extend Volume": executeExtendVolume(driveLetter, totalBytes, freeBytes); break;
                            case "Rename Volume": executeRenameVolume(driveLetter); break;
                            case "Change Drive Letter": executeChangeDriveLetter(driveLetter); break;
                        }
                        detectDisks();
                    });
                    partitionMenu.add(item);
                }
                card.setComponentPopupMenu(partitionMenu);

                diskPanel.add(Box.createVerticalStrut(8));
                diskPanel.add(card);
            }
        }

        // ------------------ Unallocated spaces ------------------
        ArrayList<Long> unallocatedSpaces = getUnallocatedSpaces();
        for (Long size : unallocatedSpaces) {
            double sizeGB = size / 1_073_741_824.0;
            if (sizeGB < 1) continue;
            double percent = ((double) size / totalDiskSize) * 100;

            JPanel unallocatedCard = new JPanel(new BorderLayout(10, 5));
            unallocatedCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            unallocatedCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            unallocatedCard.setBackground(new Color(245, 245, 245));

            JLabel label = new JLabel("💿 Unallocated Space: " + String.format("%.2f", sizeGB) + " GB (" + String.format("%.1f", percent) + "% of total)");
            label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            label.setForeground(Color.DARK_GRAY);

            JProgressBar progress = new JProgressBar(0, 100);
            progress.setValue((int) percent);
            progress.setBackground(new Color(245, 245, 245));
            progress.setForeground(new Color(200, 200, 200));

            unallocatedCard.add(label, BorderLayout.NORTH);
            unallocatedCard.add(progress, BorderLayout.SOUTH);

            JPopupMenu unallocatedMenu = new JPopupMenu();
            JMenuItem newVol = new JMenuItem("New Sample Volume");
            newVol.addActionListener(e -> {
                long diskNumber = 0; // Implement proper disk number detection if needed
                executeNewSampleVolume(diskNumber);
                detectDisks();
            });
            unallocatedMenu.add(newVol);
            unallocatedCard.setComponentPopupMenu(unallocatedMenu);

            diskPanel.add(Box.createVerticalStrut(5));
            diskPanel.add(unallocatedCard);
        }

        if ((roots == null || roots.length == 0) && unallocatedSpaces.isEmpty()) {
            JLabel noDiskLabel = new JLabel("No drives detected.");
            diskPanel.add(noDiskLabel);
        }

        diskPanel.revalidate();
        diskPanel.repaint();
    }

    private ArrayList<Long> getUnallocatedSpaces() {
        ArrayList<Long> unallocatedList = new ArrayList<>();
        try {
            String command = "Get-Disk | ForEach-Object { $_.LargestFreeExtent }";
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) {
                    try {
                        long bytes = Long.parseLong(line);
                        if (bytes > 0) unallocatedList.add(bytes);
                    } catch (NumberFormatException ignored) {}
                }
            }
            reader.close();
        } catch (Exception e) {
            addLog("Error detecting unallocated space: " + e.getMessage());
        }
        return unallocatedList;
    }

    // ---------------- LOG TAB ----------------
    private JPanel createLogTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(10, 10, 10, 10));
        
        logArea = new JTextArea();
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setEditable(false);
        updateLogArea();
        
        JButton refreshLogsBtn = new JButton("Refresh Activity Logs");
        refreshLogsBtn.addActionListener(e -> {
            loadActivityLogsFromDatabase();
            addLog(currentUser.getUsername() + " refreshed activity logs");
        });
        
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        panel.add(refreshLogsBtn, BorderLayout.SOUTH);
        return panel;
    }
    
    /**
     * Load activity logs from database and display
     */
    private void loadActivityLogsFromDatabase() {
        try {
            if (activityLogDAO == null) {
                addLog("Error: ActivityLogDAO not initialized");
                return;
            }
            
            List<ActivityLog> activityLogs = activityLogDAO.getAllLogs();
            
            if (activityLogs == null) {
                addLog("Warning: Could not load activity logs from database (connection may be unavailable)");
                return;
            }
            
            // Clear current logs and add database logs
            logs.clear();
            logs.add("=== Activity Logs from Database ===");
            
            for (ActivityLog log : activityLogs) {
                // Get user and machine info
                User user = userDAO.getUserById(log.getUserId());
                Machine machine = machineDAO.getMachineById(log.getMachineId());
                
                String username = (user != null) ? user.getUsername() : "Unknown";
                String machineName = (machine != null) ? machine.getMachineName() : "Unknown";
                
                String logEntry = String.format("[%s] User: %s | Machine: %s | Action: %s",
                    log.getLogDate().toString(),
                    username,
                    machineName,
                    log.getAction());
                logs.add(logEntry);
            }
            
            logs.add("=== End of Database Logs ===");
            logs.add("");
            logs.add("=== Local Session Logs ===");
            
            updateLogArea();
            addLog("Loaded " + activityLogs.size() + " activity logs from database");
        } catch (Exception e) {
            addLog("Error loading activity logs: " + e.getMessage());
            System.err.println("Error loading activity logs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startAutoRefresh() {
        if (autoRefreshTimer != null) autoRefreshTimer.stop();
        autoRefreshTimer = new Timer(2000, e -> detectDisks());
        autoRefreshTimer.start();
    }

    private void addLog(String message) {
        String logEntry = "[" + java.time.LocalTime.now().withNano(0) + "] " + message;
        logs.add(logEntry);
        updateLogArea();
    }

    private void updateLogArea() {
        if (logArea != null) {
            StringBuilder sb = new StringBuilder();
            for (String log : logs) sb.append(log).append("\n");
            logArea.setText(sb.toString());
        }
    }

    // ------------------- POWERSHELL METHODS -------------------

    private void executeShrinkVolume(String drive, long totalBytes, long freeBytes) {
        double totalGB = totalBytes / 1_073_741_824.0;
        double freeGB = freeBytes / 1_073_741_824.0;

        String shrinkInput = JOptionPane.showInputDialog(this, "Enter amount to shrink (GB):", String.format("%.2f", freeGB));
        if (shrinkInput != null && !shrinkInput.trim().isEmpty()) {
            try {
                double shrinkBy = Double.parseDouble(shrinkInput.trim());
                double newSize = totalGB - shrinkBy;
                if (newSize <= 0) {
                    JOptionPane.showMessageDialog(this, "Shrink too large! Resulting size invalid.", "Error", JOptionPane.ERROR_MESSAGE);
                    return;
                }
                String cmd = "Resize-Partition -DriveLetter " + drive + " -Size " + newSize + "GB -Confirm:$false";
                addLog("Executing Shrink Volume on " + drive + " by " + shrinkBy + "GB...");
                runPowerShellAsync(cmd, "Shrink Volume on " + drive + " by " + shrinkBy + "GB");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number format. Enter numeric GB.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void executeExtendVolume(String drive, long totalBytes, long unallocatedBytes) {
        double totalGB = totalBytes / 1_073_741_824.0;
        double unallocatedGB = unallocatedBytes / 1_073_741_824.0;

        String extendInput = JOptionPane.showInputDialog(this, "Enter amount to extend (GB):", String.format("%.2f", unallocatedGB));
        if (extendInput != null && !extendInput.trim().isEmpty()) {
            try {
                double extendBy = Double.parseDouble(extendInput.trim());
                if (extendBy > unallocatedGB) extendBy = unallocatedGB; // clamp to actual unallocated
                double newSize = totalGB + extendBy;
                String cmd = "Resize-Partition -DriveLetter " + drive + " -Size " + newSize + "GB -Confirm:$false";
                addLog("Executing Extend Volume on " + drive + " by " + extendBy + "GB...");
                runPowerShellAsync(cmd, "Extend Volume on " + drive + " by " + extendBy + "GB");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(this, "Invalid number format. Enter numeric GB.", "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void executeFormatVolume(String drive) {
        String[] fileSystems = {"NTFS", "FAT32", "exFAT"};
        String filesystem = (String) JOptionPane.showInputDialog(this,
                "Select FileSystem:", "Format Volume",
                JOptionPane.QUESTION_MESSAGE, null, fileSystems, fileSystems[0]);
        if (filesystem != null) {
            String cmd = "Format-Volume -DriveLetter " + drive + " -FileSystem " + filesystem + " -Confirm:$false";
            addLog("Executing Format Volume on " + drive + "...");
            runPowerShellAsync(cmd, "Format Volume on " + drive);
        }
    }

    private void executeDeleteVolume(String drive) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete drive " + drive + "?",
                "Delete Volume", JOptionPane.YES_NO_OPTION);
        if (confirm == JOptionPane.YES_OPTION) {
            String cmd = "Remove-Partition -DriveLetter " + drive + " -Confirm:$false";
            addLog("Executing Delete Volume on " + drive + "...");
            runPowerShellAsync(cmd, "Delete Volume on " + drive);
        }
    }

    private void executeRenameVolume(String drive) {
        if (drive == null || drive.trim().isEmpty()) return;
        String newName = JOptionPane.showInputDialog(this, "Enter new volume name:");
        if (newName != null && !newName.trim().isEmpty()) {
            drive = drive.replace(":", "").trim().toUpperCase();
            newName = newName.trim();
            String cmd = "$vol = Get-Volume -DriveLetter " + drive + "; if ($vol -ne $null) { Set-Volume -DriveLetter " + drive + " -NewFileSystemLabel \"" + newName + "\"; Write-Output 'Volume renamed to " + newName + "' } else { Write-Error 'Volume not found.' }";
            addLog("Attempting Rename Volume on " + drive + " to " + newName + "...");
            runPowerShellAsync(cmd, "Rename Volume " + drive + " -> " + newName);
        }
    }

    private void executeChangeDriveLetter(String oldDrive) {
        if (oldDrive == null || oldDrive.trim().isEmpty()) return;
        String newDrive = JOptionPane.showInputDialog(this, "Enter new Drive Letter (e.g., D):");
        if (newDrive != null && !newDrive.trim().isEmpty()) {
            oldDrive = oldDrive.replace(":", "").trim().toUpperCase();
            newDrive = newDrive.replace(":", "").trim().toUpperCase();
            if (newDrive.length() != 1 || !Character.isLetter(newDrive.charAt(0))) {
                JOptionPane.showMessageDialog(this, "Invalid drive letter.", "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }
            String cmd = "$part = Get-Partition -DriveLetter " + oldDrive + "; if ($part) { Set-Partition -DriveLetter " + oldDrive + " -NewDriveLetter " + newDrive + " -Confirm:$false; Write-Output 'Success' } else { Write-Error 'Partition not found or in use.' }";
            addLog("Executing Change Drive Letter: " + oldDrive + " -> " + newDrive + "...");
            runPowerShellAsync(cmd, "Change Drive Letter " + oldDrive + " -> " + newDrive);
        }
    }

    private void executeNewSampleVolume(long diskNumber) {
        String[] fileSystems = {"NTFS", "FAT32", "exFAT"};
        String filesystem = (String) JOptionPane.showInputDialog(this,
                "Select FileSystem:", "New Sample Volume",
                JOptionPane.QUESTION_MESSAGE, null, fileSystems, fileSystems[0]);
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to format (e.g., E):");
        if (filesystem != null && drive != null) {
            String cmd = "New-Partition -DiskNumber " + diskNumber + " -UseMaximumSize -DriveLetter " + drive +
                    " | Format-Volume -FileSystem " + filesystem + " -NewFileSystemLabel 'New Volume' -Confirm:$false";
            addLog("Executing New Sample Volume on Disk " + diskNumber + "...");
            runPowerShellAsync(cmd, "New Sample Volume on Disk " + diskNumber);
        }
    }

    private void runPowerShellAsync(String command, String actionDescription) {
        JDialog loader = new JDialog(this, "OneClick Partition", true);
        JLabel lbl = new JLabel(actionDescription + "...");
        lbl.setBorder(new EmptyBorder(10, 20, 10, 20));
        loader.add(lbl);
        loader.pack();
        loader.setLocationRelativeTo(this);

        new Thread(() -> {
            try {
                ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", command);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String line;
                StringBuilder output = new StringBuilder();
                while ((line = reader.readLine()) != null) output.append(line).append("\n");
                reader.close();
                process.waitFor();

                if (output.toString().contains("No MSFT_Partition objects found")) {
                    addLog("[ERROR] " + actionDescription + " failed: Partition not found.");
                } else {
                    addLog("[SUCCESS] " + actionDescription);
                }

            } catch (Exception e) {
                addLog("[ERROR] " + actionDescription + ": " + e.getMessage());
            } finally {
                SwingUtilities.invokeLater(loader::dispose);
            }
        }).start();

        loader.setVisible(true);
    }
}
