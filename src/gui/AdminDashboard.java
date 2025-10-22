package gui;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
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
                new LoginForm().setVisible(true);
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
        File[] roots = File.listRoots();

        if (roots != null && roots.length > 0) {
            for (File root : roots) {
                long free = root.getFreeSpace();
                long total = root.getTotalSpace();
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
        }
    }
}
