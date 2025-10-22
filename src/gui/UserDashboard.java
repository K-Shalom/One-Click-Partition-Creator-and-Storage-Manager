package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;

public class UserDashboard extends JFrame {

    private JTextArea diskArea;
    private JTextArea logArea;
    private Timer autoRefreshTimer;
    private ArrayList<String> logs = new ArrayList<>();
    private String username;

    public UserDashboard(String username) {
        this.username = username;

        setTitle("User Dashboard - " + username);
        setSize(900, 580);
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
                new LoginForm().setVisible(true);
            }
        });
        header.add(logoutBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ---------------- MAIN TABS ----------------
        JTabbedPane tabs = new JTabbedPane();
        tabs.addTab("💾 Disk Monitor", createDiskTab());
        tabs.addTab("📜 Activity Logs", createLogTab());
        add(tabs, BorderLayout.CENTER);

        // ---------------- FOOTER ----------------
        JLabel footer = new JLabel("© 2025 One Click Project | Rwanda Polytechnic", JLabel.CENTER);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        footer.setForeground(Color.GRAY);
        footer.setBorder(new EmptyBorder(10, 0, 10, 0));
        add(footer, BorderLayout.SOUTH);

        // Auto refresh only for Disk tab
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 0) {
                startAutoRefresh();
            } else if (autoRefreshTimer != null) {
                autoRefreshTimer.cancel();
            }
        });

        addLog(username + " logged in");
    }

    // ---------------- DISK TAB ----------------
    private JPanel createDiskTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        diskArea = new JTextArea();
        diskArea.setFont(new Font("Consolas", Font.PLAIN, 14));
        diskArea.setEditable(false);
        diskArea.setText("Loading drives...\n");

        JScrollPane scroll = new JScrollPane(diskArea);
        scroll.setBorder(BorderFactory.createTitledBorder("Available Drives"));

        JButton refreshBtn = new JButton("🔄 Refresh");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        refreshBtn.addActionListener(e -> {
            detectDisks();
            addLog(username + " manually refreshed disk info");
        });

        panel.add(scroll, BorderLayout.CENTER);
        panel.add(refreshBtn, BorderLayout.SOUTH);

        return panel;
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

    // ---------------- DISK DETECTION ----------------
    private void detectDisks() {
        diskArea.setText("Scanning available drives...\n\n");
        File[] roots = File.listRoots();

        if (roots != null && roots.length > 0) {
            for (File root : roots) {
                long free = root.getFreeSpace() / (1024 * 1024 * 1024);
                long total = root.getTotalSpace() / (1024 * 1024 * 1024);
                int usedPercent = (int) ((total - free) * 100 / (total == 0 ? 1 : total));

                // Show like Windows style
                diskArea.append(String.format("• %s — %d GB free / %d GB total (%d%% used)\n", root.getAbsolutePath(), free, total, usedPercent));
            }
        } else {
            diskArea.append("⚠️ No drives detected.\n");
        }
    }

    // ---------------- AUTO REFRESH ----------------
    private void startAutoRefresh() {
        if (autoRefreshTimer != null) autoRefreshTimer.cancel();
        autoRefreshTimer = new Timer();
        autoRefreshTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> detectDisks());
            }
        }, 0, 2500); // Refresh every 2.5 seconds secretly
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
            for (String log : logs) {
                sb.append(log).append("\n");
            }
            logArea.setText(sb.toString());
        }
    }

    // ---------------- MAIN ----------------
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new UserDashboard("demoUser").setVisible(true));
    }
}
