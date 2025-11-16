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

        // ---------- HEADER ----------
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

        // Start auto-refresh for Disk Monitor tab
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 0) startAutoRefresh();
            else if (autoRefreshTimer != null) autoRefreshTimer.stop();
        });

        addLog(currentUser.getUsername() + " logged in");
        
        // Load initial data from database
        loadActivityLogsFromDatabase();
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
                                PartitionOperations.executeShrinkVolume(UserDashboard.this, actionDriveLetter, totalBytes, freeBytes, currentUser, machineDAO, () -> detectDisks());
                                break;
                            case "Format Volume": 
                                PartitionOperations.executeFormatVolume(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> detectDisks());
                                break;
                            case "Delete Volume": 
                                PartitionOperations.executeDeleteVolume(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> detectDisks());
                                break;
                            case "Extend Volume": 
                                PartitionOperations.executeExtendVolume(UserDashboard.this, actionDriveLetter, totalBytes, freeBytes, currentUser, machineDAO, () -> detectDisks());
                                break;
                            case "Rename Volume": 
                                PartitionOperations.executeRenameVolume(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> detectDisks());
                                break;
                            case "Change Drive Letter": 
                                PartitionOperations.executeChangeDriveLetter(UserDashboard.this, actionDriveLetter, currentUser, machineDAO, () -> detectDisks());
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

        // Unallocated spaces
        ArrayList<Long> unallocatedSpaces = getUnallocatedSpaces();
        for(Long size : unallocatedSpaces){
            double sizeGB = size / 1_073_741_824.0;
            if(sizeGB < 1) continue;
            double percent = ((double) size / totalDiskSize) * 100;

            JPanel unallocatedCard = new JPanel(new BorderLayout(10,5));
            unallocatedCard.setMaximumSize(new Dimension(Integer.MAX_VALUE,60));
            unallocatedCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
            unallocatedCard.setBackground(new Color(245,245,245));

            JLabel label = new JLabel("💿 Unallocated Space: " + String.format("%.2f",sizeGB) + " GB (" + String.format("%.1f", percent) + "% of total)");
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
                long diskNumber = 0; // can implement automatic detection as in Admin
                PartitionOperations.executeNewSampleVolume(UserDashboard.this, diskNumber, currentUser, machineDAO, () -> detectDisks());
            });
            unallocatedMenu.add(newVol);
            unallocatedCard.setComponentPopupMenu(unallocatedMenu);

            diskPanel.add(Box.createVerticalStrut(5));
            diskPanel.add(unallocatedCard);
        }

        if((roots==null || roots.length==0) && unallocatedSpaces.isEmpty()){
            JLabel noDiskLabel = new JLabel("No drives detected.");
            diskPanel.add(noDiskLabel);
        }

        diskPanel.revalidate();
        diskPanel.repaint();
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
            loadActivityLogsFromDatabase();
            addLog(currentUser.getUsername() + " refreshed activity logs");
            ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), "Refreshed activity logs");
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
        autoRefreshTimer = new Timer(2000,e -> detectDisks());
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
