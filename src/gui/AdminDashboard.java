package gui;

import dao.ActivityLogDAO;
import dao.UserDAO;
import models.ActivityLog;
import models.User;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;
import java.util.List;

public class AdminDashboard extends JFrame {

    private JPanel diskPanel;
    private JTable userTable;
    private JTextArea logArea;
    private Timer autoRefreshTimer;
    private ArrayList<String> logs = new ArrayList<>();
    private User currentUser;
    private String adminUsername;
    private JTabbedPane tabs;
    private ActivityLogDAO activityLogDAO;
    private UserDAO userDAO;

    // Constructor accepting User object
    public AdminDashboard(User user) {
        this.currentUser = user;
        this.adminUsername = user.getUsername();
        this.activityLogDAO = new ActivityLogDAO();
        this.userDAO = new UserDAO();

        setTitle("Admin Dashboard - " + adminUsername);
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
                addLog(adminUsername + " logged out");
                if (autoRefreshTimer != null) autoRefreshTimer.cancel();
                dispose();
                new LoginForm().setVisible(true); // Assume LoginForm exists
            }
        });
        header.add(logoutBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ---------- TABS ----------
        tabs = new JTabbedPane();
        tabs.addTab("Users", createUserTab());
        tabs.addTab("Disk Monitor", createDiskTab());
        tabs.addTab("Logs", createLogTab());

        // ---------- TOOLBAR ABOVE TABS ----------
        JPanel toolbar = new JPanel(new FlowLayout(FlowLayout.LEFT, 15, 10));
        toolbar.setBackground(new Color(240, 240, 240));

        JButton remoteBtn = new JButton("Remote Partition");
        JButton backupBtn = new JButton("Backup");

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
            int index = tabs.indexOfTab("Remote Partition");
            if (index == -1) {
                JPanel remotePanel = new JPanel(new BorderLayout());
                remotePanel.add(new JLabel("Remote Partition Feature Coming Soon!", JLabel.CENTER), BorderLayout.CENTER);
                tabs.addTab("Remote Partition", remotePanel);
                tabs.setSelectedComponent(remotePanel);
            } else {
                tabs.setSelectedIndex(index);
            }
            addLog(adminUsername + " used Remote Partition");
        });

        backupBtn.addActionListener(e -> {
            int index = tabs.indexOfTab("Backup");
            if (index == -1) {
                JPanel backupPanel = new JPanel(new BorderLayout());
                backupPanel.add(new JLabel("Backup Feature Coming Soon!", JLabel.CENTER), BorderLayout.CENTER);
                tabs.addTab("Backup", backupPanel);
                tabs.setSelectedComponent(backupPanel);
            } else {
                tabs.setSelectedIndex(index);
            }
            addLog(adminUsername + " used Backup feature");
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

        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) startAutoRefresh();
            else if (autoRefreshTimer != null) autoRefreshTimer.cancel();
        });

        addLog(adminUsername + " logged in");
    }

    // ---------------- USERS TAB ----------------
    private JPanel createUserTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        String[] columns = {"User ID", "Username", "Role", "Status"};
        
        // Load users from database
        List<User> users = userDAO.getAllUsers();
        Object[][] data = new Object[users.size()][4];
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            data[i][0] = u.getUserId();
            data[i][1] = u.getUsername();
            data[i][2] = u.getRole();
            data[i][3] = "Active";
        }
        
        userTable = new JTable(data, columns);
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userTable.setRowHeight(25);

        JButton refreshBtn = new JButton("Refresh Users");
        refreshBtn.addActionListener(e -> {
            refreshUserTable();
            addLog(adminUsername + " refreshed user table");
        });

        panel.add(new JScrollPane(userTable), BorderLayout.CENTER);
        panel.add(refreshBtn, BorderLayout.SOUTH);
        return panel;
    }
    
    private void refreshUserTable() {
        List<User> users = userDAO.getAllUsers();
        Object[][] data = new Object[users.size()][4];
        for (int i = 0; i < users.size(); i++) {
            User u = users.get(i);
            data[i][0] = u.getUserId();
            data[i][1] = u.getUsername();
            data[i][2] = u.getRole();
            data[i][3] = "Active";
        }
        userTable.setModel(new javax.swing.table.DefaultTableModel(data, new String[]{"User ID", "Username", "Role", "Status"}));
    }

    // ---------------- DISK TAB ----------------
    private JPanel createDiskTab() {
        diskPanel = new JPanel();
        diskPanel.setLayout(new BoxLayout(diskPanel, BoxLayout.Y_AXIS));
        diskPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(diskPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Detected Drives"));

        // --- Dropdown Menu for Disk Actions ---
        JButton menuButton = new JButton("Disk Actions ▼");
        menuButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        menuButton.setBackground(new Color(52, 152, 219));
        menuButton.setForeground(Color.WHITE);
        menuButton.setFocusPainted(false);

        JPopupMenu popupMenu = new JPopupMenu();
        String[] actions = {
                "Shrink Volume",
                "New Sample Volume",
                "Format Volume",
                "Delete Volume",
                "Extend Volume",
                "Rename Volume",
                "Change Drive Letter"
        };

        for (String action : actions) {
            JMenuItem item = new JMenuItem(action);
            item.addActionListener(e -> {
                switch (action) {
                    case "Shrink Volume": executeShrinkVolume(); break;
                    case "New Sample Volume": executeNewSampleVolume(); break;
                    case "Format Volume": executeFormatVolume(); break;
                    case "Delete Volume": executeDeleteVolume(); break;
                    case "Extend Volume": executeExtendVolume(); break;
                    case "Rename Volume": executeRenameVolume(); break;
                    case "Change Drive Letter": executeChangeDriveLetter(); break;
                }
                detectDisks(); // Refresh after any operation
            });
            popupMenu.add(item);
        }

        menuButton.addActionListener(e -> popupMenu.show(menuButton, 0, menuButton.getHeight()));

        // --- Refresh Button ---
        JButton refreshBtn = new JButton("Refresh Now");
        refreshBtn.addActionListener(e -> {
            detectDisks();
            addLog(adminUsername + " refreshed disk info");
        });

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.add(menuButton);
        topPanel.add(refreshBtn);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        detectDisks();
        return panel;
    }

    private void detectDisks() {
        diskPanel.removeAll();

        // --- Existing Partitions ---
        File[] roots = File.listRoots();
        long totalDiskSize = 0;

        if (roots != null && roots.length > 0) {
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

                JLabel label = new JLabel(root.getAbsolutePath() + " — Free: " + (free / (1024 * 1024 * 1024)) + "GB / Total: " + (total / (1024 * 1024 * 1024)) + "GB");
                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));

                JProgressBar progress = new JProgressBar(0, 100);
                progress.setValue(usedPercent);
                progress.setStringPainted(true);
                progress.setPreferredSize(new Dimension(800, 25));
                progress.setForeground(new Color(76, 175, 80));

                card.add(label, BorderLayout.NORTH);
                card.add(progress, BorderLayout.SOUTH);

                diskPanel.add(Box.createVerticalStrut(8));
                diskPanel.add(card);
            }
        }

        // --- Unallocated Spaces ---
        ArrayList<Long> unallocatedSpaces = getUnallocatedSpaces();
        for (Long size : unallocatedSpaces) {
            long sizeGB = size / (1024L * 1024L * 1024L);
            if (sizeGB < 1) continue; // Only show if >= 1GB

            double percent = ((double) size / totalDiskSize) * 100;

            JPanel unallocatedCard = new JPanel(new BorderLayout(10, 5));
            unallocatedCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
            unallocatedCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            unallocatedCard.setBackground(new Color(245, 245, 245)); // light gray

            JLabel label = new JLabel("Unallocated Space: " + sizeGB + " GB (" + String.format("%.1f", percent) + "% of total)");
            label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            label.setForeground(Color.DARK_GRAY);

            JProgressBar progress = new JProgressBar(0, 100);
            progress.setValue((int) percent);
            progress.setBackground(new Color(245, 245, 245));
            progress.setForeground(new Color(200, 200, 200));

            unallocatedCard.add(label, BorderLayout.NORTH);
            unallocatedCard.add(progress, BorderLayout.SOUTH);

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
        JPanel panel = new JPanel(new BorderLayout());
        logArea = new JTextArea();
        logArea.setFont(new Font("Consolas", Font.PLAIN, 13));
        logArea.setEditable(false);
        updateLogArea();
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        return panel;
    }

    private void startAutoRefresh() {
        if (autoRefreshTimer != null) autoRefreshTimer.cancel();
        autoRefreshTimer = new Timer();
        autoRefreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> detectDisks());
            }
        }, 0, 2000);
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
    private void executeShrinkVolume() {
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to shrink (e.g., C):");
        String size = JOptionPane.showInputDialog(this, "Enter size to shrink in GB (e.g., 50):");
        if (drive != null && size != null) {
            String command = "Resize-Partition -DriveLetter " + drive + " -Size " + size + "GB -Confirm:$false";
            runPowerShell(command);
            addLog("Shrink Volume executed on " + drive + " by " + adminUsername);
        }
    }

    private void executeNewSampleVolume() {
        String diskNumber = JOptionPane.showInputDialog(this, "Enter Disk Number (e.g., 0):");
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to format (e.g., E):");
        String filesystem = JOptionPane.showInputDialog(this, "Enter FileSystem (NTFS/FAT32/exFAT):");
        if (diskNumber != null && drive != null && filesystem != null) {
            String command = "New-Partition -DiskNumber " + diskNumber + " -UseMaximumSize -AssignDriveLetter | Format-Volume -DriveLetter " + drive + " -FileSystem " + filesystem + " -NewFileSystemLabel 'New Volume' -Confirm:$false";
            runPowerShell(command);
            addLog("New Sample Volume executed on Disk " + diskNumber);
        }
    }

    private void executeFormatVolume() {
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to format (e.g., E):");
        String filesystem = JOptionPane.showInputDialog(this, "Enter FileSystem (NTFS/FAT32/exFAT):");
        if (drive != null && filesystem != null) {
            String command = "Format-Volume -DriveLetter " + drive + " -FileSystem " + filesystem + " -Confirm:$false";
            runPowerShell(command);
            addLog("Format Volume executed on " + drive);
        }
    }

    private void executeDeleteVolume() {
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to delete (e.g., E):");
        if (drive != null) {
            String command = "Remove-Partition -DriveLetter " + drive + " -Confirm:$false";
            runPowerShell(command);
            addLog("Delete Volume executed on " + drive);
        }
    }

    private void executeExtendVolume() {
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to extend (e.g., C):");
        String newSize = JOptionPane.showInputDialog(this, "Enter new total size in GB (e.g., 400):");
        if (drive != null && newSize != null) {
            String command = "Resize-Partition -DriveLetter " + drive + " -Size " + newSize + "GB -Confirm:$false";
            runPowerShell(command);
            addLog("Extend Volume executed on " + drive);
        }
    }

    private void executeRenameVolume() {
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter (e.g., E):");
        String newName = JOptionPane.showInputDialog(this, "Enter new volume name:");
        if (drive != null && newName != null) {
            String command = "Set-Volume -DriveLetter " + drive + " -NewFileSystemLabel '" + newName + "'";
            runPowerShell(command);
            addLog("Rename Volume executed on " + drive + " as " + newName);
        }
    }

    private void executeChangeDriveLetter() {
        String oldDrive = JOptionPane.showInputDialog(this, "Enter current Drive Letter (e.g., E):");
        String newDrive = JOptionPane.showInputDialog(this, "Enter new Drive Letter (e.g., D):");
        if (oldDrive != null && newDrive != null) {
            String command = "Set-Partition -DriveLetter " + oldDrive + " -NewDriveLetter " + newDrive;
            runPowerShell(command);
            addLog("Change Drive Letter executed: " + oldDrive + " -> " + newDrive);
        }
    }

    private void runPowerShell(String command) {
        try {
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            StringBuilder output = new StringBuilder();
            while ((line = reader.readLine()) != null) output.append(line).append("\n");
            reader.close();

            JOptionPane.showMessageDialog(this, output.toString(), "PowerShell Output", JOptionPane.INFORMATION_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Error executing command:\n" + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
