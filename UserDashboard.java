package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.*;

public class UserDashboard extends JFrame {

    private JTextArea logArea;
    private java.util.Timer autoRefreshTimer;
    private ArrayList<String> logs = new ArrayList<>();
    private String username;
    private JPanel diskPanel; // Advanced Disk Monitor panel

    public UserDashboard(String username) {
        this.username = username;

        setTitle("User Dashboard - " + username);
        setSize(950, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // ---------------- HEADER ----------------
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(33, 150, 243));
        header.setBorder(new EmptyBorder(10, 15, 10, 15));

        JLabel title = new JLabel("ONE CLICK | USER DASHBOARD", JLabel.LEFT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(Color.WHITE);
        header.add(title, BorderLayout.WEST);

        JButton logoutBtn = new JButton("Logout");
        logoutBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        logoutBtn.setBackground(new Color(231, 76, 60));
        logoutBtn.setForeground(Color.WHITE);
        logoutBtn.setFocusPainted(false);
        logoutBtn.addActionListener(e -> {
            int confirm = JOptionPane.showConfirmDialog(this, "Do you want to logout?", "Logout", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                addLog(username + " logged out");
                if (autoRefreshTimer != null) autoRefreshTimer.cancel();
                dispose();
                new LoginForm().setVisible(true); // assume LoginForm exists
            }
        });
        header.add(logoutBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ---------------- TABS ----------------
        JTabbedPane tabs = new JTabbedPane();

        // Advanced Disk Monitor tab
        tabs.addTab("💾 Disk Monitor (Advanced)", createDiskTabAdvanced());

        // Activity Logs tab
        tabs.addTab("📜 Activity Logs", createLogTab());

        add(tabs, BorderLayout.CENTER);

        // ---------------- FOOTER ----------------
        JLabel footer = new JLabel("© 2025 One Click Project | Rwanda Polytechnic", JLabel.CENTER);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        footer.setForeground(Color.GRAY);
        footer.setBorder(new EmptyBorder(10, 0, 10, 0));
        add(footer, BorderLayout.SOUTH);

        // Auto-refresh on tab selection
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 0) startAutoRefresh();
            else if (autoRefreshTimer != null) autoRefreshTimer.cancel();
        });

        addLog(username + " logged in");
    }

    // ---------------- ADVANCED DISK TAB WITH DROPDOWN ----------------
    private JPanel createDiskTabAdvanced() {
        diskPanel = new JPanel();
        diskPanel.setLayout(new BoxLayout(diskPanel, BoxLayout.Y_AXIS));
        diskPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(diskPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Detected Drives"));

        // Dropdown menu for all disk actions
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
            });
            popupMenu.add(item);
        }

        menuButton.addActionListener(e -> popupMenu.show(menuButton, 0, menuButton.getHeight()));

        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        topPanel.add(menuButton);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(topPanel, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);

        detectDisksAdvanced();
        startAutoRefresh();

        return panel;
    }

    private void detectDisksAdvanced() {
        diskPanel.removeAll();
        File[] roots = File.listRoots();

        if (roots != null && roots.length > 0) {
            for (File root : roots) {
                long free = root.getFreeSpace();
                long total = root.getTotalSpace();
                int usedPercent = total > 0 ? (int) (((double) (total - free) / total) * 100) : 0;

                JPanel card = new JPanel(new BorderLayout(10, 5));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(200, 200, 200)),
                        new EmptyBorder(5, 10, 5, 10)
                ));
                card.setBackground(Color.WHITE);

                JLabel label = new JLabel(root.getAbsolutePath() + " — Free: " + (free / (1024L * 1024L * 1024L)) + "GB / Total: " + (total / (1024L * 1024L * 1024L)) + "GB");
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
        } else {
            JLabel noDiskLabel = new JLabel("No drives detected.");
            diskPanel.add(noDiskLabel);
        }

        diskPanel.revalidate();
        diskPanel.repaint();
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

    // ---------------- AUTO REFRESH ----------------
    private void startAutoRefresh() {
        if (autoRefreshTimer != null) autoRefreshTimer.cancel();
        autoRefreshTimer = new java.util.Timer();
        autoRefreshTimer.schedule(new java.util.TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> detectDisksAdvanced());
            }
        }, 0, 2500); // Refresh every 2.5 seconds
    }

    // ---------------- LOG MANAGEMENT ----------------
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
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to shrink drive " + drive + " by " + size + "GB?",
                    "Confirm Shrink", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                String command = "Resize-Partition -DriveLetter " + drive + " -Size " + size + "GB -Confirm:$false";
                runPowerShell(command);
                addLog("Shrink Volume executed on " + drive + " by " + username);
            }
        }
    }

    private void executeNewSampleVolume() {
        String diskNumber = JOptionPane.showInputDialog(this, "Enter Disk Number (e.g., 0):");
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to format (e.g., E):");
        String filesystem = JOptionPane.showInputDialog(this, "Enter FileSystem (NTFS/FAT32/exFAT):");
        if (diskNumber != null && filesystem != null && drive != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to create a new volume on Disk " + diskNumber + " as drive " + drive + "?",
                    "Confirm New Volume", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                String command = "New-Partition -DiskNumber " + diskNumber + " -UseMaximumSize -AssignDriveLetter | " +
                        "Format-Volume -DriveLetter " + drive + " -FileSystem " + filesystem + " -NewFileSystemLabel 'New Volume' -Confirm:$false";
                runPowerShell(command);
                addLog("New Sample Volume executed on Disk " + diskNumber);
            }
        }
    }

    private void executeFormatVolume() {
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to format (e.g., E):");
        String filesystem = JOptionPane.showInputDialog(this, "Enter FileSystem (NTFS/FAT32/exFAT):");
        if (drive != null && filesystem != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to format drive " + drive + " as " + filesystem + "?",
                    "Confirm Format", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                String command = "Format-Volume -DriveLetter " + drive + " -FileSystem " + filesystem + " -Confirm:$false";
                runPowerShell(command);
                addLog("Format Volume executed on " + drive);
            }
        }
    }

    private void executeDeleteVolume() {
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to delete (e.g., E):");
        if (drive != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to DELETE drive " + drive + "? This action cannot be undone!",
                    "Confirm Delete", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                String command = "Remove-Partition -DriveLetter " + drive + " -Confirm:$false";
                runPowerShell(command);
                addLog("Delete Volume executed on " + drive);
            }
        }
    }

    private void executeExtendVolume() {
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter to extend (e.g., C):");
        String newSize = JOptionPane.showInputDialog(this, "Enter new total size in GB (e.g., 400):");
        if (drive != null && newSize != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to extend drive " + drive + " to " + newSize + "GB?",
                    "Confirm Extend", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                String command = "Resize-Partition -DriveLetter " + drive + " -Size " + newSize + "GB -Confirm:$false";
                runPowerShell(command);
                addLog("Extend Volume executed on " + drive);
            }
        }
    }

    private void executeRenameVolume() {
        String drive = JOptionPane.showInputDialog(this, "Enter Drive Letter (e.g., E):");
        String newName = JOptionPane.showInputDialog(this, "Enter new volume name:");
        if (drive != null && newName != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to rename drive " + drive + " to '" + newName + "'?",
                    "Confirm Rename", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                String command = "Set-Volume -DriveLetter " + drive + " -NewFileSystemLabel '" + newName + "' -Confirm:$false";
                runPowerShell(command);
                addLog("Rename Volume executed on " + drive + " as " + newName);
            }
        }
    }

    private void executeChangeDriveLetter() {
        String oldDrive = JOptionPane.showInputDialog(this, "Enter current Drive Letter (e.g., E):");
        String newDrive = JOptionPane.showInputDialog(this, "Enter new Drive Letter (e.g., D):");
        if (oldDrive != null && newDrive != null) {
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Are you sure you want to change drive letter from " + oldDrive + " to " + newDrive + "?",
                    "Confirm Change Letter", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                String command = "Set-Partition -DriveLetter " + oldDrive + " -NewDriveLetter " + newDrive + " -Confirm:$false";
                runPowerShell(command);
                addLog("Change Drive Letter executed: " + oldDrive + " -> " + newDrive);
            }
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

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UserDashboard("demoUser").setVisible(true));
    }
}
