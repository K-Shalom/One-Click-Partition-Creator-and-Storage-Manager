package gui;

import utils.BackupManager;
import dao.UserDAO;
import dao.ActivityLogDAO;
import dao.MachineDAO;
import models.User;
import models.ActivityLog;
import models.Machine;
import utils.ActivityLogger;
import utils.PartitionOperations;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableRowSorter;
import javax.swing.RowFilter;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import database.DatabaseConnection;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import lan.LanClient;
import lan.LanConfig;
import lan.http.HttpAgentServer;
import lan.http.HttpLanClient;

/**
 * AdminDashboard - optimized for non-blocking disk monitor and actions,
 * disk cards are created once and updated; heavy actions run on background executors.
 */
public class AdminDashboard extends JFrame {

    private JPanel diskPanel;
    private JTable userTable;
    private TableRowSorter<DefaultTableModel> userSorter;
    private JTextArea logArea;
    private Timer autoRefreshTimer; // only used for compatibility; we use ScheduledExecutorService for disk refresh
    private ArrayList<String> logs = new ArrayList<>();
    private User currentUser;
    private JTabbedPane tabs;
    private JPanel remoteTab;
    private DefaultTableModel remoteTableModel;
    private JLabel remoteStatusLabel;
    private JToggleButton remoteFreezeToggle;
    private JComboBox<String> userFilterCombo;
    private JTextField userSearchField;
    private DefaultTableModel userPreviewTableModel;
    private JTable remoteTable;
    private JPanel remoteSimpleTab;
    private DefaultTableModel remoteSimpleModel;
    private JTable remoteSimpleTable;
    private JTextField remoteSimpleSearchField;
    private JLabel remoteSimpleStatusLabel;
    private JTextField remoteHostField;
    private JTextField remotePortField;

    // DAOs for database access
    private UserDAO userDAO;
    private ActivityLogDAO activityLogDAO;
    private MachineDAO machineDAO;

    // Executors for background tasks
    private final ScheduledExecutorService diskScheduler = Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService actionExecutor = Executors.newFixedThreadPool(2); // for admin actions & heavy tasks

    // Disk UI caches
    private final Map<String, DiskCard> diskCardMap = new HashMap<>();
    private final Map<String, String> volumeLabelCache = new HashMap<>();
    private final JPanel unallocatedContainer = new JPanel();
    private volatile Set<String> lastRootKeys = new HashSet<>();
    private volatile boolean diskSchedulerStarted = false;

    public AdminDashboard(User user) {
        this.currentUser = user;

        // Initialize DAOs
        this.userDAO = new UserDAO();
        this.activityLogDAO = new ActivityLogDAO();
        this.machineDAO = new MachineDAO();

        setTitle("Admin Dashboard - " + user.getUsername());
        setSize(950, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));
        // Start lightweight HTTP LAN agent on this machine
        try { HttpAgentServer.ensureStarted(); } catch (Throwable ignored) {}

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
                ActivityLogger.logLogout(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO));
                shutdownExecutors();
                dispose();
                new LoginForm().setVisible(true);
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

        JButton remoteSimpleBtn = new JButton("Remote Partition");
        JButton backupBtn = new JButton("Backup");

        Font btnFont = new Font("Segoe UI", Font.BOLD, 14);
        remoteSimpleBtn.setFont(btnFont);
        backupBtn.setFont(btnFont);

        remoteSimpleBtn.setBackground(new Color(52, 152, 219));
        backupBtn.setBackground(new Color(46, 204, 113));

        remoteSimpleBtn.setForeground(Color.WHITE);
        backupBtn.setForeground(Color.WHITE);

        remoteSimpleBtn.setFocusPainted(false);
        backupBtn.setFocusPainted(false);

        remoteSimpleBtn.addActionListener(e -> {
            int index = tabs.indexOfTab("Remote Partition");
            if (index == -1) {
                remoteSimpleTab = createRemoteSimpleTab();
                tabs.addTab("Remote Partition", remoteSimpleTab);
                tabs.setSelectedComponent(remoteSimpleTab);
            } else {
                tabs.setSelectedIndex(index);
                refreshRemoteSimple();
            }
            addLog(currentUser.getUsername() + " used Remote Partition");
            ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), "Accessed Remote Partition feature");
        });

        backupBtn.addActionListener(e -> {
            new Thread(() -> {
                addLog(currentUser.getUsername() + " started database backup...");
                String result = BackupManager.backupDatabase();
                if (result != null) {
                    addLog(currentUser.getUsername() + " backup completed successfully.");
                } else {
                    addLog(currentUser.getUsername() + " backup failed!");
                }

                SwingUtilities.invokeLater(() -> BackupManager.showBackupResult(result));
            }).start();
        });

        toolbar.add(remoteSimpleBtn);
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

        // Start auto-refresh for Disk Monitor tab (we start when tab selected)
        tabs.addChangeListener(e -> {
            if (tabs.getSelectedIndex() == 1) {
                ensureDiskMonitorRunning();
            }
        });

        addLog(currentUser.getUsername() + " logged in");

        // Load initial data from database in background
        loadUsersAsync();
        loadActivityLogsAsync();

        // Start disk monitor auto-refresh immediately so it behaves like Windows' auto update
        SwingUtilities.invokeLater(this::ensureDiskMonitorRunning);
    }

    // ---------------- USERS TAB ----------------
    private JPanel createUserTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        // Columns: Auto-increment ID, Username, Role, Status, Edit, Delete, Hidden DB ID
        String[] columns = {"#", "Username", "Role", "Status", "Edit", "Delete", "DB_ID"};
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false; // Make table read-only
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 4 || columnIndex == 5) {
                    return JButton.class;
                }
                return Object.class;
            }
        };

        userTable = new JTable(model);
        userSorter = new TableRowSorter<>(model);
        userTable.setRowSorter(userSorter);
        userTable.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        userTable.setRowHeight(35);
        userTable.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

        // Hide the DB_ID column (last column)
        userTable.getColumnModel().getColumn(6).setMinWidth(0);
        userTable.getColumnModel().getColumn(6).setMaxWidth(0);
        userTable.getColumnModel().getColumn(6).setWidth(0);

        // Set column widths
        userTable.getColumnModel().getColumn(0).setPreferredWidth(50);  // #
        userTable.getColumnModel().getColumn(1).setPreferredWidth(200); // Username
        userTable.getColumnModel().getColumn(2).setPreferredWidth(100); // Role
        userTable.getColumnModel().getColumn(3).setPreferredWidth(100); // Status
        userTable.getColumnModel().getColumn(4).setPreferredWidth(80);  // Edit
        userTable.getColumnModel().getColumn(5).setPreferredWidth(80);  // Delete

        // Custom renderer for Edit and Delete buttons
        userTable.getColumnModel().getColumn(4).setCellRenderer(new ButtonRenderer("Edit", new Color(46, 204, 113)));
        userTable.getColumnModel().getColumn(5).setCellRenderer(new ButtonRenderer("Delete", new Color(231, 76, 60)));

        // Add mouse listener for button clicks
        userTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = userTable.rowAtPoint(e.getPoint());
                int col = userTable.columnAtPoint(e.getPoint());

                if (row >= 0) {
                    if (col == 4) { // Edit button
                        handleEditUserFromTable(row);
                    } else if (col == 5) { // Delete button
                        handleDeleteUserFromTable(row);
                    }
                }
            }
        });

        JPanel searchPanel = new JPanel(new BorderLayout(8, 8));
        searchPanel.add(new JLabel("Search (username / role):"), BorderLayout.WEST);
        userSearchField = new JTextField();
        searchPanel.add(userSearchField, BorderLayout.CENTER);
        userSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { applyUserFilter(); }
            @Override public void removeUpdate(DocumentEvent e) { applyUserFilter(); }
            @Override public void changedUpdate(DocumentEvent e) { applyUserFilter(); }
        });

        // Button panel for refresh
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton refreshBtn = new JButton("Refresh Users");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        refreshBtn.setBackground(new Color(52, 152, 219));
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> {
            loadUsersAsync();
            addLog(currentUser.getUsername() + " refreshed user table");
            ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), "Refreshed user table");
        });

        buttonPanel.add(refreshBtn);

        panel.add(searchPanel, BorderLayout.NORTH);
        panel.add(new JScrollPane(userTable), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
    }

    private void applyUserFilter() {
        if (userSorter == null) return;
        String text = userSearchField == null ? "" : userSearchField.getText().trim();
        if (text.isEmpty()) {
            userSorter.setRowFilter(null);
        } else {
            String regex = ".*" + Pattern.quote(text) + ".*";
            userSorter.setRowFilter(RowFilter.regexFilter("(?i)" + regex));
        }
    }

    private void doPing() {
        String host = remoteHostField.getText().trim();
        int port = parsePort(remotePortField.getText().trim(), LanConfig.getPort());
        boolean ok = HttpLanClient.ping(host, port);
        JOptionPane.showMessageDialog(this,
                ok ? ("Agent reachable at " + host + ":" + port) : ("No response from " + host + ":" + port),
                ok ? "Ping Success" : "Ping Failed",
                ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
    }

    private void doListVolumes() {
        String host = remoteHostField.getText().trim();
        int port = parsePort(remotePortField.getText().trim(), LanConfig.getPort());
        try {
            if (!HttpLanClient.ping(host, port)) {
                JOptionPane.showMessageDialog(this,
                        "Agent not reachable at " + host + ":" + port + "\nCheck that the app is running on the remote machine and firewall allows port " + port + ".",
                        "Ping Failed",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
            java.util.List<LanClient.RemoteVolume> vols = HttpLanClient.listVolumes(host, port);
            showVolumesDialog(host, port, vols);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to list remote volumes: " + ex.getMessage() + "\nIf this persists, try Ping first and verify Windows Firewall inbound rule on port " + port + ".",
                    "Remote Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doRenameVolume() {
        String host = remoteHostField.getText().trim();
        int port = parsePort(remotePortField.getText().trim(), LanConfig.getPort());
        String drive = JOptionPane.showInputDialog(this,
                "Drive letter to rename (e.g. C):",
                "Rename Volume",
                JOptionPane.QUESTION_MESSAGE);
        if (drive == null || drive.trim().isEmpty()) return;
        String newLabel = JOptionPane.showInputDialog(this,
                "New volume label (max 32 chars):",
                "Rename Volume",
                JOptionPane.QUESTION_MESSAGE);
        if (newLabel == null) return;
        newLabel = newLabel.trim();
        if (newLabel.length() > 32) newLabel = newLabel.substring(0, 32);
        int confirm = JOptionPane.showConfirmDialog(this,
                "Rename " + drive + ": to \"" + newLabel + "\" on " + host + ":" + port + "?",
                "Confirm Rename",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            boolean ok = HttpLanClient.renameVolume(host, port, drive, newLabel);
            JOptionPane.showMessageDialog(this,
                    ok ? "Rename successful." : "Rename failed.",
                    ok ? "Success" : "Failed",
                    ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to rename: " + ex.getMessage(),
                    "Remote Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doChangeLetter() {
        String host = remoteHostField.getText().trim();
        int port = parsePort(remotePortField.getText().trim(), LanConfig.getPort());
        String from = JOptionPane.showInputDialog(this,
                "Current drive letter (e.g. D):",
                "Change Drive Letter",
                JOptionPane.QUESTION_MESSAGE);
        if (from == null || from.trim().isEmpty()) return;
        String to = JOptionPane.showInputDialog(this,
                "New drive letter (single letter, e.g. E):",
                "Change Drive Letter",
                JOptionPane.QUESTION_MESSAGE);
        if (to == null || to.trim().isEmpty()) return;

        from = from.trim();
        to = to.trim();
        if (from.endsWith(":")) from = from.substring(0, from.length()-1);
        if (from.endsWith("\\")) from = from.substring(0, from.length()-1);
        if (from.length() == 2 && from.charAt(1) == ':') from = String.valueOf(from.charAt(0));
        if (to.endsWith(":")) to = to.substring(0, to.length()-1);
        if (to.endsWith("\\")) to = to.substring(0, to.length()-1);
        if (to.length() == 2 && to.charAt(1) == ':') to = String.valueOf(to.charAt(0));
        if (from.length() != 1 || to.length() != 1 || !Character.isLetter(from.charAt(0)) || !Character.isLetter(to.charAt(0))) {
            JOptionPane.showMessageDialog(this, "Invalid letters. Use a single letter like D or E.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
        from = ("" + Character.toUpperCase(from.charAt(0)));
        to = ("" + Character.toUpperCase(to.charAt(0)));
        if (from.equalsIgnoreCase("C")) {
            JOptionPane.showMessageDialog(this, "Changing system drive C is blocked for safety.", "Blocked", JOptionPane.ERROR_MESSAGE);
            return;
        }

        int confirm = JOptionPane.showConfirmDialog(this,
                "Change drive letter " + from + ": to " + to + ": on " + host + ":" + port + "?\n" +
                        "Ensure no applications are using the drive.",
                "Confirm Change Letter",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            boolean ok = HttpLanClient.changeDriveLetter(host, port, from, to);
            JOptionPane.showMessageDialog(this,
                    ok ? "Drive letter changed." : "Change letter failed.",
                    ok ? "Success" : "Failed",
                    ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to change letter: " + ex.getMessage(),
                    "Remote Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private int parsePort(String s, int def) {
        try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; }
    }

    private void doFormatVolume() {
        String host = remoteHostField.getText().trim();
        int port = parsePort(remotePortField.getText().trim(), LanConfig.getPort());
        String drive = JOptionPane.showInputDialog(this,
                "Drive letter to format (e.g. D):",
                "Format Volume",
                JOptionPane.QUESTION_MESSAGE);
        if (drive == null || drive.trim().isEmpty()) return;
        drive = drive.trim();
        if (drive.endsWith(":")) drive = drive.substring(0, drive.length()-1);
        if (drive.endsWith("\\")) drive = drive.substring(0, drive.length()-1);
        if (drive.length() == 2 && drive.charAt(1) == ':') drive = String.valueOf(drive.charAt(0));
        drive = ("" + Character.toUpperCase(drive.charAt(0)));
        if ("C".equalsIgnoreCase(drive)) {
            JOptionPane.showMessageDialog(this, "Formatting system drive C is blocked.", "Blocked", JOptionPane.ERROR_MESSAGE);
            return;
        }
        String[] fileSystems = {"NTFS", "FAT32", "exFAT"};
        String fs = (String) JOptionPane.showInputDialog(this, "Select File System:", "Format Volume",
                JOptionPane.QUESTION_MESSAGE, null, fileSystems, fileSystems[0]);
        if (fs == null) return;
        String label = JOptionPane.showInputDialog(this, "New Volume Label (optional):", "");
        if (label == null) label = "";
        int confirm1 = JOptionPane.showConfirmDialog(this,
                "This will ERASE all data on " + drive + ":. Proceed?",
                "Confirm Format",
                JOptionPane.YES_NO_OPTION);
        if (confirm1 != JOptionPane.YES_OPTION) return;
        int confirm2 = JOptionPane.showConfirmDialog(this,
                "Are you absolutely sure to format " + drive + ": as " + fs + (label.isEmpty()? "" : (" (\"" + label + "\")")) + "?",
                "Final Confirmation",
                JOptionPane.YES_NO_OPTION);
        if (confirm2 != JOptionPane.YES_OPTION) return;
        try {
            boolean ok = HttpLanClient.formatVolume(host, port, drive, fs, label);
            JOptionPane.showMessageDialog(this,
                    ok ? "Format completed." : "Format failed.",
                    ok ? "Success" : "Failed",
                    ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to format: " + ex.getMessage(),
                    "Remote Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doShrinkVolume() {
        String host = remoteHostField.getText().trim();
        int port = parsePort(remotePortField.getText().trim(), LanConfig.getPort());
        String drive = JOptionPane.showInputDialog(this,
                "Drive letter to shrink (e.g. D):",
                "Shrink Volume",
                JOptionPane.QUESTION_MESSAGE);
        if (drive == null || drive.trim().isEmpty()) return;
        String amt = JOptionPane.showInputDialog(this,
                "Amount to shrink (GB):",
                "1");
        if (amt == null || amt.trim().isEmpty()) return;
        double gb;
        try { gb = Double.parseDouble(amt.trim()); } catch (Exception e) { JOptionPane.showMessageDialog(this, "Invalid number."); return; }
        if (gb <= 0) { JOptionPane.showMessageDialog(this, "Amount must be > 0."); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Shrink " + drive + ": by " + String.format("%.2f", gb) + " GB on " + host + ":" + port + "?",
                "Confirm Shrink",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            boolean ok = HttpLanClient.shrinkVolume(host, port, drive, gb);
            JOptionPane.showMessageDialog(this,
                    ok ? "Shrink requested." : "Shrink failed.",
                    ok ? "Success" : "Failed",
                    ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to shrink: " + ex.getMessage(),
                    "Remote Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doExtendVolume() {
        String host = remoteHostField.getText().trim();
        int port = parsePort(remotePortField.getText().trim(), LanConfig.getPort());
        String drive = JOptionPane.showInputDialog(this,
                "Drive letter to extend (e.g. D):",
                "Extend Volume",
                JOptionPane.QUESTION_MESSAGE);
        if (drive == null || drive.trim().isEmpty()) return;
        String amt = JOptionPane.showInputDialog(this,
                "Amount to extend (GB):",
                "1");
        if (amt == null || amt.trim().isEmpty()) return;
        double gb;
        try { gb = Double.parseDouble(amt.trim()); } catch (Exception e) { JOptionPane.showMessageDialog(this, "Invalid number."); return; }
        if (gb <= 0) { JOptionPane.showMessageDialog(this, "Amount must be > 0."); return; }
        int confirm = JOptionPane.showConfirmDialog(this,
                "Extend " + drive + ": by " + String.format("%.2f", gb) + " GB on " + host + ":" + port + "?",
                "Confirm Extend",
                JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) return;
        try {
            boolean ok = HttpLanClient.extendVolume(host, port, drive, gb);
            JOptionPane.showMessageDialog(this,
                    ok ? "Extend requested." : "Extend failed.",
                    ok ? "Success" : "Failed",
                    ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to extend: " + ex.getMessage(),
                    "Remote Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void doDeleteVolume() {
        String host = remoteHostField.getText().trim();
        int port = parsePort(remotePortField.getText().trim(), LanConfig.getPort());
        String drive = JOptionPane.showInputDialog(this,
                "Drive letter to delete (e.g. E):",
                "Delete Volume",
                JOptionPane.QUESTION_MESSAGE);
        if (drive == null || drive.trim().isEmpty()) return;
        String d = drive.trim();
        if (d.endsWith(":")) d = d.substring(0, d.length()-1);
        if (d.endsWith("\\")) d = d.substring(0, d.length()-1);
        if (d.length() == 2 && d.charAt(1) == ':') d = String.valueOf(d.charAt(0));
        d = ("" + Character.toUpperCase(d.charAt(0)));
        if ("C".equalsIgnoreCase(d)) {
            JOptionPane.showMessageDialog(this, "Deleting system drive C is blocked.", "Blocked", JOptionPane.ERROR_MESSAGE);
            return;
        }
        int c1 = JOptionPane.showConfirmDialog(this,
                "This will REMOVE the partition " + d + ": on " + host + ":" + port + ".\nData will be lost. Proceed?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION);
        if (c1 != JOptionPane.YES_OPTION) return;
        int c2 = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete " + d + ": ?",
                "Final Confirmation",
                JOptionPane.YES_NO_OPTION);
        if (c2 != JOptionPane.YES_OPTION) return;
        try {
            boolean ok = HttpLanClient.deleteVolume(host, port, d);
            JOptionPane.showMessageDialog(this,
                    ok ? "Delete requested." : "Delete failed.",
                    ok ? "Success" : "Failed",
                    ok ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.ERROR_MESSAGE);
        } catch (Exception ex) {
            JOptionPane.showMessageDialog(this,
                    "Failed to delete: " + ex.getMessage(),
                    "Remote Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void showVolumesDialog(String host, int port, java.util.List<LanClient.RemoteVolume> vols) {
        JDialog dlg = new JDialog(this, "Volumes on " + host + ":" + port, true);
        String[] cols = {"Drive", "Label", "Free (GB)", "Total (GB)"};
        Object[][] data = new Object[vols.size()][4];
        for (int i = 0; i < vols.size(); i++) {
            LanClient.RemoteVolume v = vols.get(i);
            data[i][0] = v.drive;
            data[i][1] = v.label;
            data[i][2] = String.format("%.2f", v.freeBytes / 1_073_741_824.0);
            data[i][3] = String.format("%.2f", v.totalBytes / 1_073_741_824.0);
        }
        JTable t = new JTable(new javax.swing.table.DefaultTableModel(data, cols) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        });
        t.setRowHeight(24);
        dlg.add(new JScrollPane(t));
        dlg.setSize(520, 360);
        dlg.setLocationRelativeTo(this);
        dlg.setVisible(true);
    }

    

    private JPanel createRemotePartitionTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JPanel hero = new JPanel(new BorderLayout());
        JLabel heroTitle = new JLabel("Remote Partition Preview", JLabel.LEFT);
        heroTitle.setFont(new Font("Segoe UI", Font.BOLD, 18));
        JTextArea heroDesc = new JTextArea(
                "Pick a user on the left to view their machines. Use search or show all remote nodes. " +
                "Once the LAN agent is live, this list will reflect real-time status.");
        heroDesc.setLineWrap(true);
        heroDesc.setWrapStyleWord(true);
        heroDesc.setEditable(false);
        heroDesc.setOpaque(false);
        hero.add(heroTitle, BorderLayout.NORTH);
        hero.add(heroDesc, BorderLayout.CENTER);
        panel.add(hero, BorderLayout.NORTH);

        JPanel selectorPanel = new JPanel(new BorderLayout(10, 5));
        selectorPanel.setBorder(BorderFactory.createTitledBorder("People Online"));
        JPanel searchPanel = new JPanel(new BorderLayout(5, 5));
        userSearchField = new JTextField();
        searchPanel.add(new JLabel("Search user:"), BorderLayout.WEST);
        searchPanel.add(userSearchField, BorderLayout.CENTER);
        selectorPanel.add(searchPanel, BorderLayout.NORTH);

        userFilterCombo = new JComboBox<>();
        selectorPanel.add(userFilterCombo, BorderLayout.CENTER);

        userPreviewTableModel = new DefaultTableModel(new Object[]{"Username", "Role"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable userPreviewTable = new JTable(userPreviewTableModel);
        userPreviewTable.setRowHeight(24);
        userPreviewTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        selectorPanel.add(new JScrollPane(userPreviewTable), BorderLayout.SOUTH);

        panel.add(selectorPanel, BorderLayout.WEST);

        remoteTableModel = new DefaultTableModel(new Object[]{"Machine", "User", "IP", "Last Seen", "Status"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        remoteTable = new JTable(remoteTableModel);
        remoteTable.setFillsViewportHeight(true);
        remoteTable.setRowHeight(28);
        remoteTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.add(new JScrollPane(remoteTable), BorderLayout.CENTER);

        remoteStatusLabel = new JLabel("Preparing preview...", JLabel.LEFT);
        remoteStatusLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));

        remoteFreezeToggle = new JToggleButton("Freeze Preview");
        JButton refreshBtn = new JButton("Refresh Sample");
        JButton staticBtn = new JButton("Show Static Mock");

        refreshBtn.addActionListener(e -> refreshRemotePreview());
        remoteFreezeToggle.addActionListener(e -> {
            if (remoteFreezeToggle.isSelected()) {
                remoteStatusLabel.setText("Preview frozen. Showing pinned snapshot.");
            } else {
                refreshRemotePreview();
            }
        });
        staticBtn.addActionListener(e -> loadStaticRemoteSample());

        JPanel controls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 5));
        controls.add(remoteFreezeToggle);
        controls.add(staticBtn);
        controls.add(refreshBtn);

        JPanel footer = new JPanel(new BorderLayout());
        footer.add(remoteStatusLabel, BorderLayout.CENTER);
        footer.add(controls, BorderLayout.SOUTH);
        panel.add(footer, BorderLayout.SOUTH);

        wireUserFilterInteractions(userPreviewTable);
        refreshRemotePreview();
        loadUserFilterOptions();
        return panel;
    }

    private JPanel createRemoteSimpleTab() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(new EmptyBorder(15, 15, 15, 15));

        JLabel title = new JLabel("Remote Partition", JLabel.LEFT);
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        panel.add(title, BorderLayout.NORTH);

        remoteSimpleModel = new DefaultTableModel(new Object[]{"User", "Machine", "IP"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) { return false; }
        };
        remoteSimpleTable = new JTable(remoteSimpleModel);
        remoteSimpleTable.setRowHeight(26);
        remoteSimpleTable.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        panel.add(new JScrollPane(remoteSimpleTable), BorderLayout.CENTER);

        JPanel controls = new JPanel(new BorderLayout(8, 8));
        JPanel left = new JPanel(new BorderLayout(6, 6));
        left.add(new JLabel("Search:"), BorderLayout.WEST);
        remoteSimpleSearchField = new JTextField();
        left.add(remoteSimpleSearchField, BorderLayout.CENTER);
        JButton refreshBtn = new JButton("Refresh");
        controls.add(left, BorderLayout.CENTER);
        controls.add(refreshBtn, BorderLayout.EAST);

        // Right-side LAN quick connect: host / port / Ping / List Volumes
        JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        right.add(new JLabel("Host:"));
        remoteHostField = new JTextField("127.0.0.1", 12);
        right.add(remoteHostField);
        right.add(new JLabel("Port:"));
        remotePortField = new JTextField(String.valueOf(LanConfig.getPort()), 5);
        right.add(remotePortField);
        JButton pingBtn = new JButton("Ping");
        JButton listBtn = new JButton("List Volumes");
        JButton renameBtn = new JButton("Rename...");
        JButton changeLetterBtn = new JButton("Change Letter...");
        JButton formatBtn = new JButton("Format...");
        JButton shrinkBtn = new JButton("Shrink...");
        JButton extendBtn = new JButton("Extend...");
        JButton deleteBtn = new JButton("Delete...");
        right.add(pingBtn);
        right.add(listBtn);
        right.add(renameBtn);
        right.add(changeLetterBtn);
        right.add(formatBtn);
        right.add(shrinkBtn);
        right.add(extendBtn);
        right.add(deleteBtn);

        remoteSimpleStatusLabel = new JLabel("Ready", JLabel.LEFT);
        JPanel south = new JPanel(new BorderLayout());
        south.add(remoteSimpleStatusLabel, BorderLayout.WEST);
        JPanel southRight = new JPanel(new BorderLayout());
        southRight.add(controls, BorderLayout.CENTER);
        southRight.add(right, BorderLayout.SOUTH);
        south.add(southRight, BorderLayout.EAST);
        panel.add(south, BorderLayout.SOUTH);

        // interactions
        refreshBtn.addActionListener(e -> refreshRemoteSimple());
        remoteSimpleSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refreshRemoteSimple(); }
            @Override public void removeUpdate(DocumentEvent e) { refreshRemoteSimple(); }
            @Override public void changedUpdate(DocumentEvent e) { refreshRemoteSimple(); }
        });

        pingBtn.addActionListener(e -> doPing());
        listBtn.addActionListener(e -> doListVolumes());
        renameBtn.addActionListener(e -> doRenameVolume());
        changeLetterBtn.addActionListener(e -> doChangeLetter());
        formatBtn.addActionListener(e -> doFormatVolume());
        shrinkBtn.addActionListener(e -> doShrinkVolume());
        extendBtn.addActionListener(e -> doExtendVolume());
        deleteBtn.addActionListener(e -> doDeleteVolume());

        remoteSimpleTable.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && remoteSimpleTable.getSelectedRow() >= 0) {
                    int row = remoteSimpleTable.getSelectedRow();
                    String user = (String) remoteSimpleModel.getValueAt(row, 0);
                    String machine = (String) remoteSimpleModel.getValueAt(row, 1);
                    String ip = (String) remoteSimpleModel.getValueAt(row, 2);
                    JOptionPane.showMessageDialog(AdminDashboard.this,
                            "Selected: " + user + " — " + machine + " (" + ip + ")\n" +
                            "Remote actions will be enabled when the LAN agent is connected.",
                            "Remote Info",
                            JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        refreshRemoteSimple();
        return panel;
    }

    private void refreshRemoteSimple() {
        if (remoteSimpleModel == null) return;
        remoteSimpleModel.setRowCount(0);
        String q = remoteSimpleSearchField != null ? remoteSimpleSearchField.getText().trim().toLowerCase() : "";
        int count = 0;
        try {
            List<User> users = userDAO.getAllUsers();
            if (users != null) {
                for (User u : users) {
                    List<Machine> ms = machineDAO.getMachinesByUserId(u.getUserId());
                    if (ms == null || ms.isEmpty()) {
                        if (q.isEmpty() || u.getUsername().toLowerCase().contains(q)) {
                            remoteSimpleModel.addRow(new Object[]{u.getUsername(), "-", "-"});
                            count++;
                        }
                        continue;
                    }
                    for (Machine m : ms) {
                        if (!q.isEmpty()) {
                            String key = (u.getUsername() + " " + m.getMachineName() + " " + m.getIpAddress()).toLowerCase();
                            if (!key.contains(q)) continue;
                        }
                        remoteSimpleModel.addRow(new Object[]{u.getUsername(), m.getMachineName(), m.getIpAddress()});
                        count++;
                    }
                }
            }
            remoteSimpleStatusLabel.setText("Showing " + count + " item(s)");
        } catch (Exception ex) {
            remoteSimpleStatusLabel.setText("Failed to load: " + ex.getMessage());
        }
    }

    private void wireUserFilterInteractions(JTable userPreviewTable) {
        userSearchField.getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) { loadUserFilterOptions(); }

            @Override
            public void removeUpdate(DocumentEvent e) { loadUserFilterOptions(); }

            @Override
            public void changedUpdate(DocumentEvent e) { loadUserFilterOptions(); }
        });

        userFilterCombo.addActionListener(e -> refreshRemotePreview());

        userPreviewTable.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                    int row = userPreviewTable.getSelectedRow();
                    if (row >= 0) {
                        String userName = (String) userPreviewTableModel.getValueAt(row, 0);
                        userFilterCombo.setSelectedItem(userName);
                        refreshRemotePreview();
                    }
                }
            }
        });
    }

    private void loadUserFilterOptions() {
        if (userFilterCombo == null) return;
        userFilterCombo.removeAllItems();
        userFilterCombo.addItem("All Users");
        userPreviewTableModel.setRowCount(0);
        String search = userSearchField != null ? userSearchField.getText().trim().toLowerCase() : "";

        List<User> users = userDAO.getAllUsers();
        if (users == null) return;
        for (User user : users) {
            if (!search.isEmpty() && !user.getUsername().toLowerCase().contains(search)) continue;
            userFilterCombo.addItem(user.getUsername());
            userPreviewTableModel.addRow(new Object[]{user.getUsername(), user.getRole()});
        }
    }

    private void refreshRemotePreview() {
        if (remoteTableModel == null) return;
        if (remoteFreezeToggle != null && remoteFreezeToggle.isSelected()) {
            remoteStatusLabel.setText("Preview frozen. Unfreeze to refresh dynamically.");
            return;
        }

        remoteStatusLabel.setText("Scanning LAN nodes...");
        final String selectedUser = (userFilterCombo != null && userFilterCombo.getSelectedItem() != null
                && !"All Users".equals(userFilterCombo.getSelectedItem()))
                ? userFilterCombo.getSelectedItem().toString()
                : null;
        SwingWorker<List<RemoteMachineInfo>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<RemoteMachineInfo> doInBackground() {
                return fetchRemoteMachines(selectedUser);
            }

            @Override
            protected void done() {
                try {
                    List<RemoteMachineInfo> data = get();
                    if (data == null || data.isEmpty()) {
                        applyRemoteRows(fallbackRemoteSamples());
                        remoteStatusLabel.setText("No registered machines yet. Showing static mock data.");
                    } else {
                        applyRemoteRows(data);
                        remoteStatusLabel.setText("Showing " + data.size() + " remote node(s). Ready to bind real sockets.");
                    }
                } catch (Exception ex) {
                    remoteStatusLabel.setText("Failed to update preview: " + ex.getMessage());
                }
            }
        };
        worker.execute();
    }

    private void loadStaticRemoteSample() {
        if (remoteTableModel == null) return;
        remoteFreezeToggle.setSelected(true);
        applyRemoteRows(fallbackRemoteSamples());
        remoteStatusLabel.setText("Static mock snapshot pinned. Unfreeze to resume dynamic preview.");
    }

    private void applyRemoteRows(List<RemoteMachineInfo> rows) {
        if (remoteTableModel == null) return;
        remoteTableModel.setRowCount(0);
        if (rows == null) return;
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd MMM HH:mm");
        for (RemoteMachineInfo info : rows) {
            remoteTableModel.addRow(new Object[]{
                    info.machineName,
                    info.ownerName,
                    info.ipAddress,
                    info.lastSeen.format(fmt),
                    info.status
            });
        }
    }

    private List<RemoteMachineInfo> fetchRemoteMachines(String filterUser) {
        List<RemoteMachineInfo> list = new ArrayList<>();
        try {
            List<Machine> machines = machineDAO.getAllMachines();
            if (machines == null) return list;
            int idx = 0;
            for (Machine machine : machines) {
                String ownerName = "User-" + machine.getUserId();
                try {
                    User owner = userDAO.getUserById(machine.getUserId());
                    if (owner != null) {
                        ownerName = owner.getUsername();
                        if (filterUser != null && !filterUser.equalsIgnoreCase(ownerName)) {
                            continue;
                        }
                    }
                } catch (Exception ignored) {}

                LocalDateTime lastSeen = LocalDateTime.now().minusMinutes(Math.min(idx * 3L, 30));
                String status = (idx % 3 == 0) ? "Online" : (idx % 3 == 1 ? "Busy" : "Idle");
                list.add(new RemoteMachineInfo(
                        machine.getMachineName(),
                        ownerName,
                        machine.getIpAddress(),
                        lastSeen,
                        status
                ));
                idx++;
            }
        } catch (Exception ex) {
            System.err.println("Failed to fetch remote machines: " + ex.getMessage());
        }
        return list;
    }

    private List<RemoteMachineInfo> fallbackRemoteSamples() {
        List<RemoteMachineInfo> samples = new ArrayList<>();
        samples.add(new RemoteMachineInfo("Lab-Admin", "Shalom", "192.168.1.10", LocalDateTime.now().minusMinutes(2), "Online"));
        samples.add(new RemoteMachineInfo("Finance-PC", "Gilbert", "192.168.1.33", LocalDateTime.now().minusMinutes(5), "Busy"));
        samples.add(new RemoteMachineInfo("Store-01", "Annonciatha", "192.168.1.58", LocalDateTime.now().minusMinutes(11), "Idle"));
        samples.add(new RemoteMachineInfo("Laptop-Guest", "Ines", "192.168.1.77", LocalDateTime.now().minusMinutes(15), "Offline"));
        return samples;
    }

    private static class RemoteMachineInfo {
        private final String machineName;
        private final String ownerName;
        private final String ipAddress;
        private final LocalDateTime lastSeen;
        private final String status;

        private RemoteMachineInfo(String machineName, String ownerName, String ipAddress, LocalDateTime lastSeen, String status) {
            this.machineName = machineName;
            this.ownerName = ownerName;
            this.ipAddress = ipAddress;
            this.lastSeen = lastSeen;
            this.status = status;
        }
    }

    /**
     * Custom button renderer for table cells
     */
    private class ButtonRenderer extends JButton implements TableCellRenderer {
        private String text;
        private Color bgColor;

        public ButtonRenderer(String text, Color bgColor) {
            this.text = text;
            this.bgColor = bgColor;
            setOpaque(true);
            setFont(new Font("Segoe UI", Font.BOLD, 12));
            setForeground(Color.WHITE);
            setBackground(bgColor);
            setFocusPainted(false);
        }

        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {
            setText(text);
            setBackground(bgColor);
            return this;
        }
    }

    /**
     * Load all users from database and display in table (background)
     */
    private void loadUsersAsync() {
        SwingWorker<List<User>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<User> doInBackground() {
                if (userDAO == null) return null;
                return userDAO.getAllUsers();
            }

            @Override
            protected void done() {
                try {
                    List<User> users = get();
                    if (users == null) {
                        addLog("Warning: Could not load users from database (connection may be unavailable)");
                        return;
                    }
                    DefaultTableModel model = (DefaultTableModel) userTable.getModel();
                    model.setRowCount(0); // Clear existing rows

                    int rowNumber = 1;
                    for (User user : users) {
                        Object[] row = {
                                rowNumber++,              // Auto-increment display ID
                                user.getUsername(),
                                user.getRole(),
                                "Active",                 // Status
                                "Edit",                   // Edit button placeholder
                                "Delete",                 // Delete button placeholder
                                user.getUserId()          // Hidden database ID
                        };
                        model.addRow(row);
                    }
                    addLog("Loaded " + users.size() + " users from database");
                } catch (Exception e) {
                    addLog("Error loading users: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    /**
     * Handle editing a user from table row
     */
    private void handleEditUserFromTable(int row) {
        // Get user data from selected row (DB_ID is in column 6)
        int userId = (int) userTable.getValueAt(row, 6);
        String currentUsername = (String) userTable.getValueAt(row, 1);
        String currentRole = (String) userTable.getValueAt(row, 2);

        handleEditUser(userId, currentUsername, currentRole);
    }

    /**
     * Handle editing a selected user
     */
    private void handleEditUser(int userId, String currentUsername, String currentRole) {

        // Prevent editing own account
        if (userId == currentUser.getUserId()) {
            JOptionPane.showMessageDialog(this,
                    "You cannot edit your own account!",
                    "Invalid Action",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Create edit dialog
        JDialog editDialog = new JDialog(this, "Edit User", true);
        editDialog.setLayout(new BorderLayout(10, 10));
        editDialog.setSize(400, 250);
        editDialog.setLocationRelativeTo(this);

        // Form panel
        JPanel formPanel = new JPanel(new GridLayout(4, 2, 10, 10));
        formPanel.setBorder(new EmptyBorder(20, 20, 20, 20));

        formPanel.add(new JLabel("Username:"));
        JTextField usernameField = new JTextField(currentUsername);
        formPanel.add(usernameField);

        formPanel.add(new JLabel("New Password:"));
        JPasswordField passwordField = new JPasswordField();
        formPanel.add(passwordField);

        formPanel.add(new JLabel("Role:"));
        String[] roles = {"USER", "ADMIN"};
        JComboBox<String> roleCombo = new JComboBox<>(roles);
        roleCombo.setSelectedItem(currentRole);
        formPanel.add(roleCombo);

        formPanel.add(new JLabel("(Leave password empty to keep current)"));
        formPanel.add(new JLabel(""));

        // Button panel
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));

        JButton saveBtn = new JButton("Save Changes");
        saveBtn.setBackground(new Color(46, 204, 113));
        saveBtn.setForeground(Color.WHITE);
        saveBtn.setFocusPainted(false);
        saveBtn.addActionListener(e -> {
            String newUsername = usernameField.getText().trim();
            String newPassword = new String(passwordField.getPassword()).trim();
            String newRole = (String) roleCombo.getSelectedItem();

            if (newUsername.isEmpty()) {
                JOptionPane.showMessageDialog(editDialog,
                        "Username cannot be empty!",
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Check if username already exists (if changed) - runs on EDT, but quick DAO call
            if (!newUsername.equals(currentUsername) && userDAO.usernameExists(newUsername)) {
                JOptionPane.showMessageDialog(editDialog,
                        "Username already exists!",
                        "Validation Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Get current user data
            User userToUpdate = userDAO.getUserById(userId);
            if (userToUpdate == null) {
                JOptionPane.showMessageDialog(editDialog,
                        "User not found!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Update user data
            userToUpdate.setUsername(newUsername);
            userToUpdate.setRole(newRole);

            // Only update password if a new one was provided
            if (!newPassword.isEmpty()) {
                userToUpdate.setPassword(newPassword);
            }

            // Save to database
            if (userDAO.updateUser(userToUpdate)) {
                JOptionPane.showMessageDialog(editDialog,
                        "User updated successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                addLog(currentUser.getUsername() + " updated user: " + newUsername);
                ActivityLogger.logUserModified(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), newUsername);
                loadUsersAsync();
                editDialog.dispose();
            } else {
                JOptionPane.showMessageDialog(editDialog,
                        "Failed to update user!",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        JButton cancelBtn = new JButton("Cancel");
        cancelBtn.setBackground(new Color(149, 165, 166));
        cancelBtn.setForeground(Color.WHITE);
        cancelBtn.setFocusPainted(false);
        cancelBtn.addActionListener(e -> editDialog.dispose());

        buttonPanel.add(saveBtn);
        buttonPanel.add(cancelBtn);

        editDialog.add(formPanel, BorderLayout.CENTER);
        editDialog.add(buttonPanel, BorderLayout.SOUTH);
        editDialog.setVisible(true);
    }

    /**
     * Handle deleting a user from table row
     */
    private void handleDeleteUserFromTable(int row) {
        // Get user data from selected row (DB_ID is in column 6)
        int userId = (int) userTable.getValueAt(row, 6);
        String username = (String) userTable.getValueAt(row, 1);

        handleDeleteUser(userId, username);
    }

    /**
     * Handle deleting a selected user
     */
    private void handleDeleteUser(int userId, String username) {

        // Prevent deleting own account
        if (userId == currentUser.getUserId()) {
            JOptionPane.showMessageDialog(this,
                    "You cannot delete your own account!",
                    "Invalid Action",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Confirm deletion
        int confirm = JOptionPane.showConfirmDialog(this,
                "Are you sure you want to delete user '" + username + "'?\n\n" +
                        "This action cannot be undone!",
                "Confirm Deletion",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            if (userDAO.deleteUser(userId)) {
                JOptionPane.showMessageDialog(this,
                        "User '" + username + "' deleted successfully!",
                        "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                addLog(currentUser.getUsername() + " deleted user: " + username);
                ActivityLogger.logUserDeleted(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), username);
                loadUsersAsync();
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to delete user!\n\nThe user may have associated data.",
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
                addLog("Failed to delete user: " + username);
            }
        }
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

    /**
     * Build DiskCard components once (creates cards + caches labels asynchronously)
     */
    private void buildDiskCards() {
        diskPanel.removeAll();
        diskCardMap.clear();

        File[] roots = File.listRoots();
        long totalDiskSize = 0;
        Set<String> newKeys = new HashSet<>();

        if (roots != null && roots.length > 0) {
            for (File root : roots) {
                long free = root.getFreeSpace();
                long total = root.getTotalSpace();
                totalDiskSize += total;

                String driveKey = root.getAbsolutePath().replace("\\", "").replace(":", "");
                newKeys.add(driveKey);
                DiskCard card = new DiskCard(root);
                diskCardMap.put(driveKey, card);

                // Add to panel
                diskPanel.add(Box.createVerticalStrut(8));
                diskPanel.add(card);

                // initial stats
                card.updateStats(free, total);

                // asynchronously fetch & cache volume label (avoid blocking)
                actionExecutor.submit(() -> {
                    try {
                        String label = PartitionOperations.getVolumeLabel(driveKey);
                        if (label == null) label = "";
                        String display = label.isEmpty() ? root.getAbsolutePath() : label + " (" + root.getAbsolutePath() + ")";
                        volumeLabelCache.put(driveKey, display);
                        // update card title once label is known
                        card.updateStats(root.getFreeSpace(), root.getTotalSpace());
                    } catch (Exception ex) {
                        // ignore label fetch failures
                    }
                });
            }
        }

        // Prepare and place the unallocated container
        diskPanel.add(Box.createVerticalStrut(5));
        unallocatedContainer.removeAll();
        unallocatedContainer.setLayout(new BoxLayout(unallocatedContainer, BoxLayout.Y_AXIS));
        unallocatedContainer.setOpaque(false);
        diskPanel.add(unallocatedContainer);

        // get unallocated spaces in background and show lightweight cards in container
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
                        createItem.addActionListener(e -> PartitionOperations.executeCreateVolume(AdminDashboard.this, ext.diskNumber, currentUser, machineDAO, () -> SwingUtilities.invokeLater(this::buildDiskCards)));
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

    /**
     * Start scheduled periodic refresh of disk stats (non-blocking)
     */
    private void startDiskScheduledRefresh() {
        // schedule periodic updates - initial delay 5s, then every 8s
        if (diskSchedulerStarted) return;
        diskSchedulerStarted = true;
        diskScheduler.scheduleWithFixedDelay(() -> {
            try {
                // Rescan roots and rebuild if topology changed
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

                // update stats for each card
                for (Map.Entry<String, DiskCard> entry : diskCardMap.entrySet()) {
                    DiskCard card = entry.getValue();
                    File root = card.getRootFile();
                    long free = root.getFreeSpace();
                    long total = root.getTotalSpace();
                    card.updateStats(free, total);
                }

                // Refresh unallocated spaces in background and update container
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
                                createItem.addActionListener(e -> PartitionOperations.executeCreateVolume(AdminDashboard.this, ext.diskNumber, currentUser, machineDAO, () -> SwingUtilities.invokeLater(this::buildDiskCards)));
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
        }, 3, 2, TimeUnit.SECONDS);
    }

    private void ensureDiskMonitorRunning() {
        if (diskPanel == null) return;
        if (diskCardMap.isEmpty()) {
            buildDiskCards();
        }
        startDiskScheduledRefresh();
    }

    /**
     * DiskCard component representing a single drive; actions run in actionExecutor.
     */
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

            // build context menu once and attach
            JPopupMenu menu = new JPopupMenu();
            String[] actions = {"Shrink Volume", "Format Volume", "Delete Volume", "Extend Volume", "Rename Volume", "Change Drive Letter"};
            for (String action : actions) {
                JMenuItem item = new JMenuItem(action);
                item.addActionListener(evt -> triggerDiskAction(action));
                menu.add(item);
            }
            setComponentPopupMenu(menu);
        }

        public File getRootFile() {
            return root;
        }

        public String getDriveKey() {
            return root.getAbsolutePath().replace("\\", "").replace(":", "");
        }

        /**
         * Update UI stats on EDT (safe)
         */
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

        /**
         * Trigger disk action in background. Updates card state optimistically then finalizes.
         */
        private void triggerDiskAction(String action) {
            setBusy(true);
            addLog(currentUser.getUsername() + " requested " + action + " on " + root.getAbsolutePath());

            // Run action in actionExecutor to avoid blocking EDT
            actionExecutor.submit(() -> {
                boolean success = false;
                try {
                    String drive = getDriveKey();
                    long freeBytes = root.getFreeSpace();
                    long totalBytes = root.getTotalSpace();

                    // Call existing PartitionOperations - these calls may show confirmations/dialogs.
                    // If PartitionOperations shows Swing dialogs, they may require EDT; adapt PartitionOperations to use
                    // SwingUtilities.invokeAndWait for confirmations and keep heavy work off EDT.
                    switch (action) {
                        case "Shrink Volume":
                            PartitionOperations.executeShrinkVolume(AdminDashboard.this, drive, totalBytes, freeBytes, currentUser, machineDAO, () -> SwingUtilities.invokeLater(() -> buildDiskCards()));
                            success = true; // assume PartitionOperations handles success/failure internally and uses callback
                            break;
                        case "Format Volume":
                            PartitionOperations.executeFormatVolume(AdminDashboard.this, drive, currentUser, machineDAO, () -> {
                                long newFree = root.getFreeSpace();
                                long newTotal = root.getTotalSpace();
                                updateStats(newFree, newTotal);
                                addLog(action + " finished for " + drive);
                            });
                            success = true;
                            break;
                        case "Delete Volume":
                            PartitionOperations.executeDeleteVolume(AdminDashboard.this, drive, currentUser, machineDAO, () -> {
                                SwingUtilities.invokeLater(() -> buildDiskCards());
                                addLog(action + " finished for " + drive);
                            });
                            success = true;
                            break;
                        case "Extend Volume":
                            PartitionOperations.executeExtendVolume(AdminDashboard.this, drive, totalBytes, freeBytes, currentUser, machineDAO, () -> {
                                SwingUtilities.invokeLater(() -> buildDiskCards());
                                addLog(action + " finished for " + drive);
                            });
                            success = true;
                            break;
                        case "Rename Volume":
                            PartitionOperations.executeRenameVolume(AdminDashboard.this, drive, currentUser, machineDAO, () -> {
                                // label might change; clear cache and refresh
                                volumeLabelCache.remove(drive);
                                updateStats(root.getFreeSpace(), root.getTotalSpace());
                                addLog(action + " finished for " + drive);
                            });
                            success = true;
                            break;
                        case "Change Drive Letter":
                            PartitionOperations.executeChangeDriveLetter(AdminDashboard.this, drive, currentUser, machineDAO, () -> {
                                // drive key may change after drive letter change -> rebuild disks
                                SwingUtilities.invokeLater(() -> {
                                    buildDiskCards();
                                });
                                addLog(action + " finished for " + drive);
                            });
                            success = true;
                            break;
                    }
                } catch (Exception ex) {
                    addLog("Error running action " + action + ": " + ex.getMessage());
                    ex.printStackTrace();
                } finally {
                    // final update: requery stats
                    long finalFree = root.getFreeSpace();
                    long finalTotal = root.getTotalSpace();
                    updateStats(finalFree, finalTotal);
                    setBusy(false);

                    if (success) {
                        ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), action + " executed on " + getDriveKey());
                    } else {
                        addLog("Action " + action + " may have failed on " + getDriveKey());
                    }
                }
            });
        }
    }

    /**
     * Background version of getUnallocatedSpaces (calls powershell) - safe to run off EDT
     */
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
            loadActivityLogsAsync();
            addLog(currentUser.getUsername() + " refreshed activity logs");
            ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), "Refreshed activity logs");
        });

        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        panel.add(refreshLogsBtn, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Load activity logs from database in background and display
     */
    private void loadActivityLogsAsync() {
        SwingWorker<List<ActivityLog>, Void> worker = new SwingWorker<>() {
            @Override
            protected List<ActivityLog> doInBackground() {
                if (activityLogDAO == null) return null;
                return activityLogDAO.getAllLogs();
            }

            @Override
            protected void done() {
                try {
                    List<ActivityLog> activityLogs = get();
                    if (activityLogs == null) {
                        addLog("Warning: Could not load activity logs from database (connection may be unavailable)");
                        return;
                    }

                    logs.clear();
                    logs.add("=== Activity Logs from Database ===");

                    // Cache user/machine lookups to reduce DB calls
                    Map<Integer, String> userCache = new HashMap<>();
                    Map<Integer, String> machineCache = new HashMap<>();

                    for (ActivityLog log : activityLogs) {
                        String username = userCache.computeIfAbsent(log.getUserId(), id -> {
                            User u = userDAO.getUserById(id);
                            return (u != null) ? u.getUsername() : "Unknown";
                        });
                        String machineName = machineCache.computeIfAbsent(log.getMachineId(), id -> {
                            Machine m = machineDAO.getMachineById(id);
                            return (m != null) ? m.getMachineName() : "Unknown";
                        });

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
                    e.printStackTrace();
                }
            }
        };
        worker.execute();
    }

    /**
     * Safely shutdown executors when application closes
     */
    private void shutdownExecutors() {
        try {
            diskScheduler.shutdownNow();
            actionExecutor.shutdownNow();
        } catch (Exception ignored) {
        }
    }

    private void addLog(String message) {
        String logEntry = "[" + java.time.LocalTime.now().withNano(0) + "] " + message;
        logs.add(logEntry);
        // keep logs bounded to avoid huge memory growth
        if (logs.size() > 1000) {
            // remove oldest 200
            for (int i = 0; i < 200; i++) logs.remove(0);
        }
        updateLogArea();
    }

    private void updateLogArea() {
        if (logArea != null) {
            StringBuilder sb = new StringBuilder();
            for (String log : logs) sb.append(log).append("\n");
            logArea.setText(sb.toString());
            // keep caret at end
            logArea.setCaretPosition(logArea.getDocument().getLength());
        }
    }
 
    // ------------------- POWERSHELL METHODS -------------------
    // Left intact - getUnallocatedSpacesBackground used above instead of blocking calls on EDT

    // Make sure to call shutdownExecutors() before exit (done in logout and dispose)

    @Override
    public void dispose() {
        shutdownExecutors();
        super.dispose();
    }
}
