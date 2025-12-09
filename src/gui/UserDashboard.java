package gui;

import dao.ActivityLogDAO;
import dao.MachineDAO;
import models.User;
import models.ActivityLog;
import models.Machine;
import utils.ActivityLogger;
import utils.PartitionOperations;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.*;
import lan.http.HttpAgentServer;

public class UserDashboard extends JFrame {

    private JPanel diskPanel;
    private JTextArea logArea;
    private Timer autoRefreshTimer;
    private ArrayList<String> logs = new ArrayList<>();
    private User currentUser;
    private JTabbedPane tabs;
    
    // DAOs for database access
    private ActivityLogDAO activityLogDAO;
    private MachineDAO machineDAO;
    private volatile boolean diskRefreshInProgress = false;
    private final ScheduledExecutorService diskScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService actionExecutor = Executors.newFixedThreadPool(2);
    private final Map<String, DiskCard> diskCardMap = new HashMap<>();
    private final Map<String, String> volumeLabelCache = new HashMap<>();
    private final JPanel unallocatedContainer = new JPanel();
    private volatile Set<String> lastRootKeys = new HashSet<>();

    public UserDashboard(User user) {
        this.currentUser = user;
        
        // Initialize DAOs
        this.activityLogDAO = new ActivityLogDAO();
        this.machineDAO = new MachineDAO();

        setTitle("User Dashboard - " + user.getUsername());
        setSize(950, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        // Start lightweight HTTP LAN agent so admin can connect to this machine
        try { HttpAgentServer.ensureStarted(); } catch (Throwable ignored) {}

        // ---------- HEADER ----------
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(new Color(25, 118, 210));
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
                addLog(currentUser.getUsername() + " logged out");
                ActivityLogger.logLogout(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO));
                if (autoRefreshTimer != null) autoRefreshTimer.stop();
                dispose();
                new LoginForm().setVisible(true);
            }
        });
        header.add(logoutBtn, BorderLayout.EAST);
        add(header, BorderLayout.NORTH);

        // ---------- TABS ----------
        tabs = new JTabbedPane();
        tabs.addTab("💾 Disk Monitor", createDiskTab());
        tabs.addTab("📜 Activity Logs", createLogTab());

        add(tabs, BorderLayout.CENTER);

        // ---------- FOOTER ----------
        JLabel footer = new JLabel("© 2025 One Click Project | Rwanda Polytechnic", JLabel.CENTER);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        footer.setForeground(Color.GRAY);
        footer.setBorder(new EmptyBorder(10, 0, 10, 0));
        add(footer, BorderLayout.SOUTH);

        // Start disk build/refresh when Disk Monitor tab is selected
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 0) {
                if (diskCardMap.isEmpty()) {
                    buildDiskCards();
                    startDiskScheduledRefresh();
                }
            }
        });

        // If Disk Monitor is the initially selected tab, build immediately
        if (tabs.getSelectedIndex() == 0 && diskCardMap.isEmpty()) {
            buildDiskCards();
            startDiskScheduledRefresh();
        }

        addLog(currentUser.getUsername() + " logged in");
        
        // Load initial data from database
        loadActivityLogsFromDatabaseAsync();
    }

    // ---------------- DISK TAB ----------------
    private JPanel createDiskTab() {
        diskPanel = new JPanel();
        diskPanel.setLayout(new BoxLayout(diskPanel, BoxLayout.Y_AXIS));
        diskPanel.setBorder(new EmptyBorder(10, 10, 10, 10));

        JScrollPane scroll = new JScrollPane(diskPanel);
        scroll.setBorder(BorderFactory.createTitledBorder("Detected Drives"));

        JPanel header = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 5));
        header.setBorder(new EmptyBorder(0, 5, 0, 5));
        JButton refreshBtn = new JButton("Refresh Now");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        refreshBtn.setBackground(new Color(46, 204, 113));
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> {
            addLog(currentUser.getUsername() + " manually refreshed disk monitor");
            SwingUtilities.invokeLater(this::buildDiskCards);
        });
        header.add(refreshBtn);

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(header, BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
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
                int usedPercent = total > 0 ? (int)(((double)(total - free)/total)*100) : 0;

                JPanel card = new JPanel(new BorderLayout(10, 5));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(200,200,200)),
                        new EmptyBorder(5,10,5,10)
                ));
                card.setBackground(Color.WHITE);

                // Get volume label
                String driveLetter = root.getAbsolutePath().replace("\\", "").replace(":", "");
                String volumeLabel = PartitionOperations.getVolumeLabel(driveLetter);
                String displayName = volumeLabel.isEmpty() ? root.getAbsolutePath() : volumeLabel + " (" + root.getAbsolutePath() + ")";
                
                JLabel label = new JLabel(displayName + " — Free: " + (free/(1024*1024*1024)) + "GB / Total: " + (total/(1024*1024*1024)) + "GB");
                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));

                JProgressBar progress = new JProgressBar(0,100);
                progress.setValue(usedPercent);
                progress.setStringPainted(true);
                progress.setPreferredSize(new Dimension(800,25));
                progress.setForeground(new Color(76,175,80));

                card.add(label, BorderLayout.NORTH);
                card.add(progress, BorderLayout.SOUTH);

                // Right-click menu
                JPopupMenu partitionMenu = new JPopupMenu();
                String[] actions = {"Shrink Volume", "Format Volume", "Delete Volume", "Extend Volume", "Rename Volume", "Change Drive Letter"};
                for(String action : actions){
                    JMenuItem item = new JMenuItem(action);
                    item.addActionListener(e -> {
                        String actionDriveLetter = root.getAbsolutePath().replace("\\", "");
                        long freeBytes = root.getFreeSpace();
                        long totalBytes = root.getTotalSpace();

                        switch (action){
                            case "Shrink Volume": 
                                PartitionOperations.executeShrinkVolume(UserDashboard.this, actionDriveLetter, totalBytes, freeBytes, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                                break;
                            case "Format Volume": 
                                PartitionOperations.executeFormatVolume(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                                break;
                            case "Delete Volume": 
                                PartitionOperations.executeDeleteVolume(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                                break;
                            case "Extend Volume": 
                                PartitionOperations.executeExtendVolume(UserDashboard.this, actionDriveLetter, totalBytes, freeBytes, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                                break;
                            case "Rename Volume": 
                                PartitionOperations.executeRenameVolume(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                                break;
                            case "Change Drive Letter": 
                                PartitionOperations.executeChangeDriveLetter(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                                break;
                        }
                    });
                    partitionMenu.add(item);
                }
                partitionMenu.addSeparator();
                JMenuItem supItem = new JMenuItem("Show Supported Size");
                supItem.addActionListener(e -> PartitionOperations.showSupportedSize(UserDashboard.this, root.getAbsolutePath().replace("\\", "")));
                partitionMenu.add(supItem);
                card.setComponentPopupMenu(partitionMenu);

                diskPanel.add(Box.createVerticalStrut(8));
                diskPanel.add(card);
            }
        }

        // Unallocated spaces (disk-number aware)
        ArrayList<UnallocExtent> unallocatedExts = getUnallocatedExtentsBackground();
        for(UnallocExtent ext : unallocatedExts){
            double sizeGB = ext.bytes / 1_073_741_824.0;
            if(sizeGB < 1) continue;
            double percent = (totalDiskSize > 0) ? ((double) ext.bytes / totalDiskSize) * 100 : 0;

            JPanel unallocatedCard = new JPanel(new BorderLayout(10,5));
            unallocatedCard.setMaximumSize(new Dimension(Integer.MAX_VALUE,60));
            unallocatedCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            unallocatedCard.setBackground(new Color(245,245,245));

            JLabel label = new JLabel("💿 Unallocated Space on Disk " + ext.diskNumber + ": " + String.format("%.2f",sizeGB) + " GB (" + String.format("%.1f", percent) + "% of total)");
            label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            label.setForeground(Color.DARK_GRAY);

            JProgressBar progress = new JProgressBar(0,100);
            progress.setValue((int)percent);
            progress.setBackground(new Color(245,245,245));
            progress.setForeground(new Color(200,200,200));

            unallocatedCard.add(label, BorderLayout.NORTH);
            unallocatedCard.add(progress, BorderLayout.SOUTH);

            JPopupMenu unallocatedMenu = new JPopupMenu();
            JMenuItem createVol = new JMenuItem("Create New Volume");
            createVol.addActionListener(e -> PartitionOperations.executeCreateVolume(UserDashboard.this, ext.diskNumber, currentUser, machineDAO, () -> SwingUtilities.invokeLater(this::buildDiskCards)));
            unallocatedMenu.add(createVol);
            unallocatedCard.setComponentPopupMenu(unallocatedMenu);

            diskPanel.add(Box.createVerticalStrut(5));
            diskPanel.add(unallocatedCard);
        }

        if((roots==null || roots.length==0) && (unallocatedExts==null || unallocatedExts.isEmpty())){
            JLabel noDiskLabel = new JLabel("No drives detected.");
            diskPanel.add(noDiskLabel);
        }

        diskPanel.revalidate();
        diskPanel.repaint();
    }

    private class DiskCard extends JPanel {
        private final File root;
        private final JLabel titleLabel;
        private final JProgressBar progress;
        private final JLabel statusLabel;
        private volatile boolean busy = false;

        public DiskCard(File root) {
            this.root = root;
            setLayout(new BorderLayout(6, 4));
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 90));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(new Color(200, 200, 200)),
                    new EmptyBorder(6, 10, 6, 10)
            ));
            setBackground(Color.WHITE);

            titleLabel = new JLabel("", JLabel.LEFT);
            titleLabel.setFont(new Font("Segoe UI", Font.PLAIN, 14));
            progress = new JProgressBar(0, 100);
            progress.setStringPainted(true);
            progress.setPreferredSize(new Dimension(800, 25));
            statusLabel = new JLabel("", JLabel.LEFT);
            statusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));

            add(titleLabel, BorderLayout.NORTH);
            add(progress, BorderLayout.CENTER);
            add(statusLabel, BorderLayout.SOUTH);

            JPopupMenu menu = new JPopupMenu();
            String[] actions = {"Shrink Volume", "Format Volume", "Delete Volume", "Extend Volume", "Rename Volume", "Change Drive Letter"};
            for (String action : actions) {
                JMenuItem item = new JMenuItem(action);
                item.addActionListener(evt -> triggerDiskAction(action));
                menu.add(item);
            }
            menu.addSeparator();
            JMenuItem supItem = new JMenuItem("Show Supported Size");
            supItem.addActionListener(evt -> PartitionOperations.showSupportedSize(UserDashboard.this, getDriveKey()));
            menu.add(supItem);
            setComponentPopupMenu(menu);
        }

        public File getRootFile() { return root; }
        public String getDriveKey() { return root.getAbsolutePath().replace("\\", "").replace(":", ""); }

        public void updateStats(long freeBytes, long totalBytes) {
            SwingUtilities.invokeLater(() -> {
                int usedPercent = (totalBytes == 0) ? 0 : (int) (((double) (totalBytes - freeBytes) / totalBytes) * 100);
                progress.setValue(Math.max(0, Math.min(100, usedPercent)));
                String drivePath = root.getAbsolutePath();
                String driveKey = getDriveKey();
                String label = volumeLabelCache.getOrDefault(driveKey, drivePath);
                titleLabel.setText(label + " — Free: " + (freeBytes / (1024L * 1024 * 1024)) + "GB / Total: " + (totalBytes / (1024L * 1024 * 1024)) + "GB");
                statusLabel.setText(busy ? "Working..." : "Idle");
            });
        }

        public void setBusy(boolean b) {
            this.busy = b;
            SwingUtilities.invokeLater(() -> statusLabel.setText(b ? "Working..." : "Idle"));
        }

        private void triggerDiskAction(String action) {
            setBusy(true);
            addLog(currentUser.getUsername() + " requested " + action + " on " + root.getAbsolutePath());
            actionExecutor.submit(() -> {
                try {
                    String drive = getDriveKey();
                    long freeBytes = root.getFreeSpace();
                    long totalBytes = root.getTotalSpace();
                    switch (action) {
                        case "Shrink Volume":
                            PartitionOperations.executeShrinkVolume(UserDashboard.this, drive, totalBytes, freeBytes, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                            break;
                        case "Format Volume":
                            PartitionOperations.executeFormatVolume(UserDashboard.this, drive, currentUser, machineDAO, () -> updateStats(root.getFreeSpace(), root.getTotalSpace()));
                            break;
                        case "Delete Volume":
                            PartitionOperations.executeDeleteVolume(UserDashboard.this, drive, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                            break;
                        case "Extend Volume":
                            PartitionOperations.executeExtendVolume(UserDashboard.this, drive, totalBytes, freeBytes, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                            break;
                        case "Rename Volume":
                            PartitionOperations.executeRenameVolume(UserDashboard.this, drive, currentUser, machineDAO, () -> {
                                volumeLabelCache.remove(drive);
                                updateStats(root.getFreeSpace(), root.getTotalSpace());
                            });
                            break;
                        case "Change Drive Letter":
                            PartitionOperations.executeChangeDriveLetter(UserDashboard.this, drive, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                            break;
                    }
                    ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), action + " executed on " + getDriveKey());
                } catch (Exception ex) {
                    addLog("Error running action " + action + ": " + ex.getMessage());
                } finally {
                    long finalFree = root.getFreeSpace();
                    long finalTotal = root.getTotalSpace();
                    updateStats(finalFree, finalTotal);
                    setBusy(false);
                }
            });
        }
    }

    private void buildDiskCards() {
        diskPanel.removeAll();
        diskCardMap.clear();
        File[] roots = File.listRoots();
        Set<String> newKeys = new HashSet<>();
        if (roots != null && roots.length > 0) {
            for (File root : roots) {
                long free = root.getFreeSpace();
                long total = root.getTotalSpace();
                String driveKey = root.getAbsolutePath().replace("\\", "").replace(":", "");
                newKeys.add(driveKey);
                DiskCard card = new DiskCard(root);
                diskCardMap.put(driveKey, card);
                diskPanel.add(Box.createVerticalStrut(8));
                diskPanel.add(card);
                card.updateStats(free, total);
                actionExecutor.submit(() -> {
                    try {
                        String label = PartitionOperations.getVolumeLabel(driveKey);
                        if (label == null) label = "";
                        String display = label.isEmpty() ? root.getAbsolutePath() : label + " (" + root.getAbsolutePath() + ")";
                        volumeLabelCache.put(driveKey, display);
                        card.updateStats(root.getFreeSpace(), root.getTotalSpace());
                    } catch (Exception ignored) {}
                });
            }
        }

        // Prepare and place the unallocated container
        diskPanel.add(Box.createVerticalStrut(5));
        unallocatedContainer.removeAll();
        unallocatedContainer.setLayout(new BoxLayout(unallocatedContainer, BoxLayout.Y_AXIS));
        unallocatedContainer.setOpaque(false);
        diskPanel.add(unallocatedContainer);

        // Load unallocated spaces in background and render into container
        actionExecutor.submit(() -> {
            ArrayList<UnallocExtent> unallocated = getUnallocatedExtentsBackground();
            if (!unallocated.isEmpty()) {
                SwingUtilities.invokeLater(() -> {
                    unallocatedContainer.removeAll();
                    for (UnallocExtent ext : unallocated) {
                        double sizeGB = ext.bytes / 1_073_741_824.0;
                        if (sizeGB < 1) continue;
                        JPanel unallocatedCard = new JPanel(new BorderLayout(10, 5));
                        unallocatedCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
                        unallocatedCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
                        unallocatedCard.setBackground(new Color(245, 245, 245));
                        JLabel label = new JLabel("Unallocated Space on Disk " + ext.diskNumber + ": " + String.format("%.2f", sizeGB) + " GB");
                        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                        unallocatedCard.add(label, BorderLayout.NORTH);

                        JPopupMenu menu = new JPopupMenu();
                        JMenuItem createItem = new JMenuItem("Create New Volume");
                        createItem.addActionListener(e -> PartitionOperations.executeCreateVolume(UserDashboard.this, ext.diskNumber, currentUser, machineDAO, () -> SwingUtilities.invokeLater(this::buildDiskCards)));
                        menu.add(createItem);
                        unallocatedCard.setComponentPopupMenu(menu);

                        unallocatedContainer.add(Box.createVerticalStrut(5));
                        unallocatedContainer.add(unallocatedCard);
                    }
                    unallocatedContainer.revalidate();
                    unallocatedContainer.repaint();
                });
            }
        });

        diskPanel.revalidate();
        diskPanel.repaint();
        lastRootKeys = newKeys;
    }

    private void startDiskScheduledRefresh() {
        diskScheduler.scheduleWithFixedDelay(() -> {
            try {
                // Rescan roots and rebuild if topology changed (drives added/removed)
                File[] roots = File.listRoots();
                Set<String> newKeys = new HashSet<>();
                if (roots != null) {
                    for (File r : roots) {
                        newKeys.add(r.getAbsolutePath().replace("\\", "").replace(":", ""));
                    }
                }
                if (!newKeys.equals(lastRootKeys)) {
                    lastRootKeys = newKeys;
                    SwingUtilities.invokeLater(this::buildDiskCards);
                    return;
                }

                // Update stats for each card
                for (Map.Entry<String, DiskCard> entry : diskCardMap.entrySet()) {
                    DiskCard card = entry.getValue();
                    File root = card.getRootFile();
                    long free = root.getFreeSpace();
                    long total = root.getTotalSpace();
                    card.updateStats(free, total);
                }

                // Refresh unallocated spaces
                actionExecutor.submit(() -> {
                    ArrayList<UnallocExtent> unalloc = getUnallocatedExtentsBackground();
                    SwingUtilities.invokeLater(() -> {
                        unallocatedContainer.removeAll();
                        if (unalloc != null && !unalloc.isEmpty()) {
                            for (UnallocExtent ext : unalloc) {
                                double sizeGB = ext.bytes / 1_073_741_824.0;
                                if (sizeGB < 1) continue;
                                JPanel unallocatedCard = new JPanel(new BorderLayout(10, 5));
                                unallocatedCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
                                unallocatedCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
                                unallocatedCard.setBackground(new Color(245, 245, 245));
                                JLabel label = new JLabel("Unallocated Space on Disk " + ext.diskNumber + ": " + String.format("%.2f", sizeGB) + " GB");
                                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                                unallocatedCard.add(label, BorderLayout.NORTH);

                                JPopupMenu menu = new JPopupMenu();
                                JMenuItem createItem = new JMenuItem("Create New Volume");
                                createItem.addActionListener(e -> PartitionOperations.executeCreateVolume(UserDashboard.this, ext.diskNumber, currentUser, machineDAO, () -> SwingUtilities.invokeLater(this::buildDiskCards)));
                                menu.add(createItem);
                                unallocatedCard.setComponentPopupMenu(menu);

                                unallocatedContainer.add(Box.createVerticalStrut(5));
                                unallocatedContainer.add(unallocatedCard);
                            }
                        }
                        unallocatedContainer.revalidate();
                        unallocatedContainer.repaint();
                    });
                });
            } catch (Throwable t) {
                t.printStackTrace();
            }
        }, 5, 8, TimeUnit.SECONDS);
    }

    private ArrayList<Long> getUnallocatedSpacesBackground() {
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
            addLog("Error detecting unallocated space (background): " + e.getMessage());
        }
        return unallocatedList;
    }

    private static class UnallocExtent {
        int diskNumber;
        long bytes;
        UnallocExtent(int diskNumber, long bytes) { this.diskNumber = diskNumber; this.bytes = bytes; }
    }

    private ArrayList<UnallocExtent> getUnallocatedExtentsBackground() {
        ArrayList<UnallocExtent> list = new ArrayList<>();
        try {
            String command = "Get-Disk | ForEach-Object { Write-Output ($_.Number.ToString() + '|' + $_.LargestFreeExtent) }";
            ProcessBuilder pb = new ProcessBuilder("powershell.exe", "-Command", command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty() && line.contains("|")) {
                    String[] parts = line.split("\\|");
                    if (parts.length == 2) {
                        try {
                            int diskNum = Integer.parseInt(parts[0].trim());
                            long bytes = Long.parseLong(parts[1].trim());
                            if (bytes > 0) list.add(new UnallocExtent(diskNum, bytes));
                        } catch (NumberFormatException ignored) {}
                    }
                }
            }
            reader.close();
        } catch (Exception e) {
            addLog("Error detecting unallocated extents: " + e.getMessage());
        }
        return list;
    }

    private void shutdownExecutors() {
        try {
            diskScheduler.shutdownNow();
            actionExecutor.shutdownNow();
        } catch (Exception ignored) {}
    }

    @Override
    public void dispose() {
        shutdownExecutors();
        super.dispose();
    }
    

    private static class DiskInfo {
        File root;
        long free;
        long total;
        String volumeLabel;
        DiskInfo(File root, long free, long total, String volumeLabel) {
            this.root = root;
            this.free = free;
            this.total = total;
            this.volumeLabel = volumeLabel;
        }
    }

    private void detectDisksAsync() {
        if (diskRefreshInProgress) return;
        diskRefreshInProgress = true;
        SwingWorker<Object, Void> worker = new SwingWorker<>() {
            private List<DiskInfo> diskInfos;
            private ArrayList<Long> unallocatedSpaces;
            private long totalDiskSize;

            @Override
            protected Object doInBackground() {
                diskInfos = new ArrayList<>();
                unallocatedSpaces = new ArrayList<>();
                totalDiskSize = 0;
                try {
                    File[] roots = File.listRoots();
                    if (roots != null) {
                        for (File root : roots) {
                            long free = root.getFreeSpace();
                            long total = root.getTotalSpace();
                            totalDiskSize += total;
                            String driveLetter = root.getAbsolutePath().replace("\\", "").replace(":", "");
                            String volumeLabel = PartitionOperations.getVolumeLabel(driveLetter);
                            diskInfos.add(new DiskInfo(root, free, total, volumeLabel));
                        }
                    }

                    try {
                        ProcessBuilder pb = new ProcessBuilder("powershell.exe","-Command","Get-Disk | ForEach-Object { $_.LargestFreeExtent }");
                        pb.redirectErrorStream(true);
                        Process process = pb.start();
                        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                        String line;
                        while((line=reader.readLine())!=null){
                            line = line.trim();
                            if(!line.isEmpty()){
                                try{
                                    long bytes = Long.parseLong(line);
                                    if(bytes>0) unallocatedSpaces.add(bytes);
                                }catch(NumberFormatException ignored){}
                            }
                        }
                        reader.close();
                    } catch (Exception ignored) {}
                } catch (Exception ex) {
                }
                return null;
            }

            @Override
            protected void done() {
                try {
                    renderDiskUI(diskInfos, unallocatedSpaces, totalDiskSize);
                } finally {
                    diskRefreshInProgress = false;
                }
            }
        };
        worker.execute();
    }

    private void renderDiskUI(List<DiskInfo> infos, ArrayList<Long> unallocatedSpaces, long totalDiskSize) {
        diskPanel.removeAll();
        if (infos != null) {
            for (DiskInfo info : infos) {
                long free = info.free;
                long total = info.total;
                int usedPercent = total > 0 ? (int)(((double)(total - free)/total)*100) : 0;

                JPanel card = new JPanel(new BorderLayout(10, 5));
                card.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
                card.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(new Color(200,200,200)),
                        new EmptyBorder(5,10,5,10)
                ));
                card.setBackground(Color.WHITE);

                String displayName = (info.volumeLabel == null || info.volumeLabel.isEmpty())
                        ? info.root.getAbsolutePath()
                        : info.volumeLabel + " (" + info.root.getAbsolutePath() + ")";

                JLabel label = new JLabel(displayName + " — Free: " + (free/(1024*1024*1024)) + "GB / Total: " + (total/(1024*1024*1024)) + "GB");
                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));

                JProgressBar progress = new JProgressBar(0,100);
                progress.setValue(usedPercent);
                progress.setStringPainted(true);
                progress.setPreferredSize(new Dimension(800,25));
                progress.setForeground(new Color(76,175,80));

                card.add(label, BorderLayout.NORTH);
                card.add(progress, BorderLayout.SOUTH);

                JPopupMenu partitionMenu = new JPopupMenu();
                String[] actions = {"Shrink Volume", "Format Volume", "Delete Volume", "Extend Volume", "Rename Volume", "Change Drive Letter"};
                for(String action : actions){
                    JMenuItem item = new JMenuItem(action);
                    item.addActionListener(e -> {
                        String actionDriveLetter = info.root.getAbsolutePath().replace("\\", "");
                        long freeBytes = free;
                        long totalBytes = total;

                        switch (action){
                            case "Shrink Volume": 
                                PartitionOperations.executeShrinkVolume(UserDashboard.this, actionDriveLetter, totalBytes, freeBytes, currentUser, machineDAO, () -> detectDisksAsync());
                                break;
                            case "Format Volume": 
                                PartitionOperations.executeFormatVolume(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> detectDisksAsync());
                                break;
                            case "Delete Volume": 
                                PartitionOperations.executeDeleteVolume(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> detectDisksAsync());
                                break;
                            case "Extend Volume": 
                                PartitionOperations.executeExtendVolume(UserDashboard.this, actionDriveLetter, totalBytes, freeBytes, currentUser, machineDAO, () -> detectDisksAsync());
                                break;
                            case "Rename Volume": 
                                PartitionOperations.executeRenameVolume(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> detectDisksAsync());
                                break;
                            case "Change Drive Letter": 
                                PartitionOperations.executeChangeDriveLetter(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> detectDisksAsync());
                                break;
                        }
                    });
                    partitionMenu.add(item);
                }
                card.setComponentPopupMenu(partitionMenu);

                diskPanel.add(Box.createVerticalStrut(8));
                diskPanel.add(card);
            }
        }

        if (unallocatedSpaces != null) {
            for(Long size : unallocatedSpaces){
                double sizeGB = size / 1_073_741_824.0;
                if(sizeGB < 1) continue;
                double percent = (totalDiskSize > 0) ? ((double) size / totalDiskSize) * 100 : 0;

                JPanel unallocatedCard = new JPanel(new BorderLayout(10,5));
                unallocatedCard.setMaximumSize(new Dimension(Integer.MAX_VALUE,60));
                unallocatedCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
                unallocatedCard.setBackground(new Color(245,245,245));

                JLabel label = new JLabel("Unallocated Space: " + String.format("%.2f",sizeGB) + " GB (" + String.format("%.1f", percent) + "% of total)");
                label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                label.setForeground(Color.DARK_GRAY);

                JProgressBar progress = new JProgressBar(0,100);
                progress.setValue((int)percent);
                progress.setBackground(new Color(245,245,245));
                progress.setForeground(new Color(200,200,200));

                unallocatedCard.add(label, BorderLayout.NORTH);
                unallocatedCard.add(progress, BorderLayout.SOUTH);

                JPopupMenu unallocatedMenu = new JPopupMenu();
                JMenuItem newVol = new JMenuItem("New Sample Volume");
                newVol.addActionListener(e -> {
                    long diskNumber = 0; 
                    PartitionOperations.executeNewSampleVolume(UserDashboard.this, diskNumber, currentUser, machineDAO, () -> detectDisksAsync());
                });
                unallocatedMenu.add(newVol);
                unallocatedCard.setComponentPopupMenu(unallocatedMenu);

                diskPanel.add(Box.createVerticalStrut(5));
                diskPanel.add(unallocatedCard);
            }
        }

        if ((infos == null || infos.isEmpty()) && (unallocatedSpaces == null || unallocatedSpaces.isEmpty())){
            JLabel noDiskLabel = new JLabel("No drives detected.");
            diskPanel.add(noDiskLabel);
        }

        diskPanel.revalidate();
        diskPanel.repaint();
    }

    private static class LogLoadResult {
        List<String> lines;
        int count;
        LogLoadResult(List<String> lines, int count) {
            this.lines = lines;
            this.count = count;
        }
    }

    private void loadActivityLogsFromDatabaseAsync() {
        loadActivityLogsFromDatabaseAsync(null);
    }

    private void loadActivityLogsFromDatabaseAsync(Runnable onComplete) {
        SwingWorker<LogLoadResult, Void> worker = new SwingWorker<>() {
            @Override
            protected LogLoadResult doInBackground() {
                try {
                    if (activityLogDAO == null) {
                        return new LogLoadResult(null, 0);
                    }
                    List<ActivityLog> activityLogs = activityLogDAO.getLogsByUserId(currentUser.getUserId());
                    if (activityLogs == null) {
                        return new LogLoadResult(null, 0);
                    }
                    List<String> lines = new ArrayList<>();
                    for (ActivityLog log : activityLogs) {
                        Machine machine = machineDAO.getMachineById(log.getMachineId());
                        String machineName = (machine != null) ? machine.getMachineName() : "Unknown";
                        String logEntry = String.format("[%s] Machine: %s | Action: %s",
                                log.getLogDate().toString(),
                                machineName,
                                log.getAction());
                        lines.add(logEntry);
                    }
                    return new LogLoadResult(lines, activityLogs.size());
                } catch (Exception ex) {
                    System.err.println("Error loading activity logs: " + ex.getMessage());
                    ex.printStackTrace();
                    return new LogLoadResult(null, 0);
                }
            }

            @Override
            protected void done() {
                try {
                    LogLoadResult result = get();
                    if (result == null || result.lines == null) {
                        addLog("Warning: Could not load activity logs from database (connection may be unavailable)");
                        return;
                    }
                    logs.clear();
                    logs.add("=== My Activity Logs from Database ===");
                    logs.add("User: " + currentUser.getUsername() + " (ID: " + currentUser.getUserId() + ")");
                    logs.add("");
                    for (String line : result.lines) {
                        logs.add(line);
                    }
                    logs.add("");
                    logs.add("=== End of Database Logs ===");
                    logs.add("");
                    logs.add("=== Local Session Logs ===");
                    updateLogArea();
                } catch (Exception e) {
                    addLog("Error loading activity logs: " + e.getMessage());
                    System.err.println("Error loading activity logs: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    if (onComplete != null) onComplete.run();
                }
            }
        };
        worker.execute();
    }
    private ArrayList<Long> getUnallocatedSpaces(){
        ArrayList<Long> unallocatedList = new ArrayList<>();
        try{
            String command = "Get-Disk | ForEach-Object { $_.LargestFreeExtent }";
            ProcessBuilder pb = new ProcessBuilder("powershell.exe","-Command",command);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while((line=reader.readLine())!=null){
                line = line.trim();
                if(!line.isEmpty()){
                    try{
                        long bytes = Long.parseLong(line);
                        if(bytes>0) unallocatedList.add(bytes);
                    }catch(NumberFormatException ignored){}
                }
            }
            reader.close();
        }catch(Exception e){
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
        
        JButton refreshLogsBtn = new JButton("Refresh My Activity Logs");
        refreshLogsBtn.addActionListener(e -> {
            loadActivityLogsFromDatabaseAsync(() -> {
                addLog(currentUser.getUsername() + " refreshed activity logs");
                ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), "Refreshed activity logs");
            });
        });
        
        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        panel.add(refreshLogsBtn, BorderLayout.SOUTH);
        return panel;
    }
    
    /**
     * Load activity logs from database for current user and display
     */
    private void loadActivityLogsFromDatabase() {
        try {
            if (activityLogDAO == null) {
                addLog("Error: ActivityLogDAO not initialized");
                return;
            }
            
            // Get logs for current user only
            List<ActivityLog> activityLogs = activityLogDAO.getLogsByUserId(currentUser.getUserId());
            
            if (activityLogs == null) {
                addLog("Warning: Could not load activity logs from database (connection may be unavailable)");
                return;
            }
            
            // Clear current logs and add database logs
            logs.clear();
            logs.add("=== My Activity Logs from Database ===");
            logs.add("User: " + currentUser.getUsername() + " (ID: " + currentUser.getUserId() + ")");
            logs.add("");
            
            for (ActivityLog log : activityLogs) {
                // Get machine info
                Machine machine = machineDAO.getMachineById(log.getMachineId());
                String machineName = (machine != null) ? machine.getMachineName() : "Unknown";
                
                String logEntry = String.format("[%s] Machine: %s | Action: %s",
                    log.getLogDate().toString(),
                    machineName,
                    log.getAction());
                logs.add(logEntry);
            }
            
            logs.add("");
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

    private void startAutoRefresh(){
        if(autoRefreshTimer!=null) autoRefreshTimer.stop();
        autoRefreshTimer = new Timer(2000, e -> {
            if (!diskRefreshInProgress) {
                detectDisksAsync();
            }
        });
        autoRefreshTimer.start();
    }

    private void addLog(String message){
        String logEntry = "[" + java.time.LocalTime.now().withNano(0) + "] " + message;
        logs.add(logEntry);
        updateLogArea();
    }

    private void updateLogArea(){
        if(logArea!=null){
            StringBuilder sb = new StringBuilder();
            for(String log:logs) sb.append(log).append("\n");
            logArea.setText(sb.toString());
        }
    }

    // ------------------- POWERSHELL METHODS -------------------







    

    public static void main(String[] args){
        // For testing purposes - create a demo user
        User demoUser = new User(1, "demoUser", "password", "USER");
        SwingUtilities.invokeLater(() -> new UserDashboard(demoUser).setVisible(true));
    }
}
