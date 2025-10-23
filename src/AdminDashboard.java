package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class AdminDashboard extends JFrame {

    private JPanel diskPanel;
    private JTable userTable;
    private JTextArea logArea;
    private Timer autoRefreshTimer;
    private ArrayList<String> logs = new ArrayList<>();
    private String adminUsername;
    private ArrayList<DriveInfo> detectedDrives = new ArrayList<>();

    // Inner class to store drive information
    class DriveInfo {
        String driveLetter;
        String path;
        long totalSpace;
        long freeSpace;
        long usedSpace;
        double usedPercentage;

        public DriveInfo(String path, long total, long free) {
            this.path = path;
            this.driveLetter = path.substring(0, 1);
            this.totalSpace = total;
            this.freeSpace = free;
            this.usedSpace = total - free;
            this.usedPercentage = total > 0 ? ((double) usedSpace / total) * 100 : 0;
        }
    }

    public AdminDashboard(String adminUsername) {
        this.adminUsername = adminUsername;

        setTitle("Admin Dashboard - " + adminUsername);
        setSize(950, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // HEADER
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
                // This should reference your actual LoginForm class
                // new LoginForm().setVisible(true);
            }
        });
        header.add(logoutBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // TABS
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("👥 Users", createUserTab());
        tabs.addTab("💾 Disk Monitor", createDiskTab());
        tabs.addTab("📜 Logs", createLogTab());
        add(tabs, BorderLayout.CENTER);

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

        String[] columns = {"Username", "Role", "Status"};
        Object[][] data = {{"admin", "Administrator", "Active"}};
        userTable = new JTable(data, columns);
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userTable.setRowHeight(25);

        JButton refreshBtn = new JButton("Refresh Users");
        refreshBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Coming soon!");
            addLog(adminUsername + " refreshed user table");
        });

        panel.add(new JScrollPane(userTable), BorderLayout.CENTER);
        panel.add(refreshBtn, BorderLayout.SOUTH);
        return panel;
    }

    // ---------------- DISK TAB ----------------
    private JPanel createDiskTab() {
        diskPanel = new JPanel();
        diskPanel.setLayout(new BoxLayout(diskPanel, BoxLayout.Y_AXIS));
        diskPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(diskPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Detected Drives"));

        // --- Buttons for partition management ---
        JButton createBtn = new JButton("Create Partition");
        JButton deleteBtn = new JButton("Delete Partition");
        JButton backupBtn = new JButton("Backup");

        createBtn.setBackground(new Color(46, 204, 113));
        deleteBtn.setBackground(new Color(231, 76, 60));
        backupBtn.setBackground(new Color(52, 152, 219));

        createBtn.setForeground(Color.WHITE);
        deleteBtn.setForeground(Color.WHITE);
        backupBtn.setForeground(Color.WHITE);

        createBtn.setFocusPainted(false);
        deleteBtn.setFocusPainted(false);
        backupBtn.setFocusPainted(false);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 15, 10));
        buttonPanel.add(createBtn);
        buttonPanel.add(deleteBtn);
        buttonPanel.add(backupBtn);

        createBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Create Partition feature coming soon!");
            addLog(adminUsername + " clicked Create Partition");
        });
        deleteBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Delete Partition feature coming soon!");
            addLog(adminUsername + " clicked Delete Partition");
        });
        backupBtn.addActionListener(e -> {
            JOptionPane.showMessageDialog(this, "Backup feature coming soon!");
            addLog(adminUsername + " initiated Backup");
        });

        JButton refreshBtn = new JButton("Refresh Now");
        refreshBtn.addActionListener(e -> {
            detectDisks();
            addLog(adminUsername + " refreshed disk info");
        });

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(scroll, BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.NORTH);
        panel.add(refreshBtn, BorderLayout.SOUTH);

        detectDisks();
        return panel;
    }

    private void detectDisks() {
        diskPanel.removeAll();
        detectedDrives.clear();
        File[] roots = File.listRoots();

        if (roots != null && roots.length > 0) {
            for (File root : roots) {
                long free = root.getFreeSpace();
                long total = root.getTotalSpace();
                int usedPercent = (int) (((double)(total - free) / total) * 100);

                // Store drive info
                DriveInfo driveInfo = new DriveInfo(root.getAbsolutePath(), total, free);
                detectedDrives.add(driveInfo);

                JPanel card = new JPanel(new BorderLayout(10, 5));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(200, 200, 200)),
                        new EmptyBorder(5, 10, 5, 10)
                ));
                card.setBackground(Color.WHITE);

                // Create drive info label - FIXED: Use %d for integers instead of %f
                String driveInfoText = String.format("%s — Free: %dGB / Total: %dGB (%d%% used)",
                        root.getAbsolutePath(),
                        free / (1024*1024*1024),
                        total / (1024*1024*1024),
                        usedPercent);
                JLabel label = new JLabel(driveInfoText);
                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));

                JProgressBar progress = new JProgressBar(0, 100);
                progress.setValue(usedPercent);
                progress.setStringPainted(true);
                progress.setPreferredSize(new Dimension(800, 25));

                // Set color based on usage
                if (usedPercent > 90) {
                    progress.setForeground(new Color(231, 76, 60)); // Red
                } else if (usedPercent > 70) {
                    progress.setForeground(new Color(241, 196, 15)); // Yellow
                } else {
                    progress.setForeground(new Color(76, 175, 80)); // Green
                }

                card.add(label, BorderLayout.NORTH);
                card.add(progress, BorderLayout.SOUTH);

                // Add right-click context menu
                card.setComponentPopupMenu(createDriveContextMenu(driveInfo));

                // Add mouse listener for hover effects
                card.addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        card.setBackground(new Color(240, 240, 240));
                        card.setCursor(new Cursor(Cursor.HAND_CURSOR));
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        card.setBackground(Color.WHITE);
                        card.setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
                    }

                    @Override
                    public void mouseClicked(MouseEvent e) {
                        if (SwingUtilities.isRightMouseButton(e)) {
                            card.getComponentPopupMenu().show(card, e.getX(), e.getY());
                        }
                    }
                });

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

    private JPopupMenu createDriveContextMenu(DriveInfo driveInfo) {
        JPopupMenu contextMenu = new JPopupMenu();

        // Drive information item
        JMenuItem infoItem = new JMenuItem("Drive Information");
        infoItem.addActionListener(e -> showDriveInfo(driveInfo));

        // Shrink drive item
        JMenuItem shrinkItem = new JMenuItem("Shrink Drive");
        shrinkItem.addActionListener(e -> shrinkDrive(driveInfo));

        // Format drive item
        JMenuItem formatItem = new JMenuItem("Format Drive");
        formatItem.addActionListener(e -> formatDrive(driveInfo));

        // Open in explorer item
        JMenuItem openItem = new JMenuItem("Open in Explorer");
        openItem.addActionListener(e -> openDriveInExplorer(driveInfo));

        // Properties item
        JMenuItem propertiesItem = new JMenuItem("Properties");
        propertiesItem.addActionListener(e -> showDriveProperties(driveInfo));

        contextMenu.add(infoItem);
        contextMenu.addSeparator();
        contextMenu.add(shrinkItem);
        contextMenu.add(formatItem);
        contextMenu.addSeparator();
        contextMenu.add(openItem);
        contextMenu.add(propertiesItem);

        return contextMenu;
    }

    private void showDriveInfo(DriveInfo driveInfo) {
        String info = String.format(
                "Drive Information:\n\n" +
                        "Drive Letter: %s\n" +
                        "Path: %s\n" +
                        "Total Space: %.2f GB\n" +
                        "Used Space: %.2f GB\n" +
                        "Free Space: %.2f GB\n" +
                        "Usage: %.1f%%",
                driveInfo.driveLetter,
                driveInfo.path,
                driveInfo.totalSpace / (1024.0 * 1024 * 1024),
                driveInfo.usedSpace / (1024.0 * 1024 * 1024),
                driveInfo.freeSpace / (1024.0 * 1024 * 1024),
                driveInfo.usedPercentage
        );

        JOptionPane.showMessageDialog(this, info, "Drive Information - " + driveInfo.path,
                JOptionPane.INFORMATION_MESSAGE);
        addLog(adminUsername + " viewed info for drive " + driveInfo.path);
    }

    private void shrinkDrive(DriveInfo driveInfo) {
        // Simulate shrink drive functionality
        JSpinner shrinkSize = new JSpinner(new SpinnerNumberModel(1000, 100,
                (int)(driveInfo.freeSpace / (1024 * 1024)), 100));

        JPanel panel = new JPanel(new GridLayout(0, 1));
        panel.add(new JLabel("Enter amount to shrink (MB):"));
        panel.add(shrinkSize);

        int result = JOptionPane.showConfirmDialog(this, panel, "Shrink Drive - " + driveInfo.path,
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            int shrinkMB = (Integer) shrinkSize.getValue();
            addLog(adminUsername + " attempted to shrink drive " + driveInfo.path + " by " + shrinkMB + "MB");
            JOptionPane.showMessageDialog(this,
                    "Shrink operation scheduled for " + shrinkMB + "MB on drive " + driveInfo.path + "\n" +
                            "This is a simulation - actual shrink would require administrative privileges.",
                    "Shrink Scheduled", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void formatDrive(DriveInfo driveInfo) {
        int confirm = JOptionPane.showConfirmDialog(this,
                "WARNING: Formatting will erase ALL data on drive " + driveInfo.path + "!\n\n" +
                        "Are you sure you want to continue?",
                "Format Drive Warning", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            addLog(adminUsername + " attempted to format drive " + driveInfo.path);
            JOptionPane.showMessageDialog(this,
                    "Format operation scheduled for drive " + driveInfo.path + "\n" +
                            "This is a simulation - actual format would require administrative privileges.",
                    "Format Scheduled", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void openDriveInExplorer(DriveInfo driveInfo) {
        try {
            Desktop.getDesktop().open(new File(driveInfo.path));
            addLog(adminUsername + " opened drive " + driveInfo.path + " in explorer");
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Cannot open drive: " + ex.getMessage(),
                    "Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showDriveProperties(DriveInfo driveInfo) {
        // Create a detailed properties dialog
        JDialog propertiesDialog = new JDialog(this, "Properties - " + driveInfo.path, true);
        propertiesDialog.setLayout(new BorderLayout());
        propertiesDialog.setSize(400, 300);
        propertiesDialog.setLocationRelativeTo(this);

        JTextArea propertiesArea = new JTextArea();
        propertiesArea.setEditable(false);
        propertiesArea.setFont(new Font("Consolas", Font.PLAIN, 12));

        String properties = String.format(
                "Drive Properties:\n" +
                        "=================\n\n" +
                        "Drive Letter: %s\n" +
                        "Path: %s\n\n" +
                        "Storage Information:\n" +
                        "  Total Space: %,.2f GB\n" +
                        "  Used Space:  %,.2f GB\n" +
                        "  Free Space:  %,.2f GB\n" +
                        "  Usage:       %.1f%%\n\n" +
                        "Raw Values:\n" +
                        "  Total Bytes: %,d\n" +
                        "  Free Bytes:  %,d\n" +
                        "  Used Bytes:  %,d",
                driveInfo.driveLetter,
                driveInfo.path,
                driveInfo.totalSpace / (1024.0 * 1024 * 1024),
                driveInfo.usedSpace / (1024.0 * 1024 * 1024),
                driveInfo.freeSpace / (1024.0 * 1024 * 1024),
                driveInfo.usedPercentage,
                driveInfo.totalSpace,
                driveInfo.freeSpace,
                driveInfo.usedSpace
        );

        propertiesArea.setText(properties);

        JButton closeBtn = new JButton("Close");
        closeBtn.addActionListener(e -> propertiesDialog.dispose());

        propertiesDialog.add(new JScrollPane(propertiesArea), BorderLayout.CENTER);
        propertiesDialog.add(closeBtn, BorderLayout.SOUTH);
        propertiesDialog.setVisible(true);

        addLog(adminUsername + " viewed properties for drive " + driveInfo.path);
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
        }, 0, 2000); // refresh every 2 seconds (hidden)
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
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }

    // Main method for testing
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new AdminDashboard("admin").setVisible(true);
        });
    }
}