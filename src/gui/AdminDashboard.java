package gui;

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
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class AdminDashboard extends JFrame {

    private JPanel diskPanel;
    private JTable userTable;
    private JTextArea logArea;
    private ScheduledExecutorService driveMonitor; // Background drive monitor
    private ArrayList<String> logs = new ArrayList<>();
    private User currentUser;
    private JTabbedPane tabs;
    private volatile int lastDriveCount = 0; // Track drive count for change detection

    // DAOs for database access
    private UserDAO userDAO;
    private ActivityLogDAO activityLogDAO;
    private MachineDAO machineDAO;

    // Cache for disk detection to avoid repeated expensive operations
    private File[] cachedRoots = null;
    private long lastDiskScan = 0;
    private static final long DISK_CACHE_MS = 5000; // Cache for 5 seconds (quick refresh)

    // Cache for unallocated space (very expensive PowerShell operation)
    private ArrayList<Long> cachedUnallocated = null;
    private long lastUnallocatedScan = 0;
    private static final long UNALLOCATED_CACHE_MS = 60000; // Cache for 60 seconds (expensive operation)

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
                stopDriveMonitor();
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
            addLog(currentUser.getUsername() + " used Remote Partition");
            ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), "Accessed Remote Partition feature");
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
            addLog(currentUser.getUsername() + " used Backup feature");
            ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), "Accessed Backup feature");
        });

        toolbar.add(remoteBtn);
        toolbar.add(backupBtn);

        // ---------- CENTER PANEL ----------
        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.add(toolbar, BorderLayout.NORTH); // toolbar on top
        centerPanel.add(tabs, BorderLayout.CENTER);    // tabs below
        add(centerPanel, BorderLayout.CENTER);

        // ---------- FOOTER ----------
        JLabel footer = new JLabel(" 2025 One Click Project | Rwanda Polytechnic", JLabel.CENTER);
        footer.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        footer.setForeground(Color.GRAY);
        footer.setBorder(new EmptyBorder(10, 0, 10, 0));
        add(footer, BorderLayout.SOUTH);

        // Lazy loading management (auto-refresh disabled by default for performance)
        final boolean[] usersLoaded = {false};
        final boolean[] disksLoaded = {false};
        final boolean[] logsLoaded = {false};

        tabs.addChangeListener(e -> {
            int selectedIndex = tabs.getSelectedIndex();

            // Users tab - lazy load on first access
            if (selectedIndex == 0 && !usersLoaded[0]) {
                loadUsersFromDatabase();
                usersLoaded[0] = true;
            }
            // Disk Monitor tab - lazy load on first access
            else if (selectedIndex == 1 && !disksLoaded[0]) {
                cachedRoots = null; // Clear cache for instant display
                detectDisks(true); // Full refresh with unallocated spaces on first load
                disksLoaded[0] = true;
                startDriveMonitor(); // Start drive monitor when switching to Disk Monitor
            } else if (selectedIndex == 1 && driveMonitor == null) {
                startDriveMonitor(); // Restart drive monitor if returning to Disk Monitor
            }
            // Logs tab - lazy load on first access
            else if (selectedIndex == 2 && !logsLoaded[0]) {
                loadActivityLogsFromDatabase();
                logsLoaded[0] = true;
            }

            // Stop drive monitor when leaving Disk Monitor tab
            if (selectedIndex != 1 && driveMonitor != null) {
                stopDriveMonitor();
            }
        });

        addLog(currentUser.getUsername() + " logged in");

        // Load initial tab data in background after UI is visible (non-blocking)
        SwingUtilities.invokeLater(() -> {
            // This runs after the window is shown, making dashboard appear instantly
            int selectedTab = tabs.getSelectedIndex();
            if (selectedTab == 0) {
                // Users tab is default - load users
                loadUsersFromDatabase();
                usersLoaded[0] = true;
            } else if (selectedTab == 1) {
                // Disk Monitor tab
                cachedRoots = null; // Clear cache for instant display
                detectDisks(true); // Full refresh with unallocated spaces on first load
                disksLoaded[0] = true;
                startDriveMonitor(); // Start drive monitor automatically
            }
        });
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

        // Button panel for refresh
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));

        JButton refreshBtn = new JButton("Refresh Users");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 14));
        refreshBtn.setBackground(new Color(52, 152, 219));
        refreshBtn.setForeground(Color.WHITE);
        refreshBtn.setFocusPainted(false);
        refreshBtn.addActionListener(e -> {
            loadUsersFromDatabase();
            addLog(currentUser.getUsername() + " refreshed user table");
            ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), "Refreshed user table");
        });

        buttonPanel.add(refreshBtn);

        panel.add(new JScrollPane(userTable), BorderLayout.CENTER);
        panel.add(buttonPanel, BorderLayout.SOUTH);
        return panel;
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
            System.err.println("Error loading users: " + e.getMessage());
            e.printStackTrace();
        }
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

            // Check if username already exists (if changed)
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
                loadUsersFromDatabase();
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
                loadUsersFromDatabase();
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

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.add(scroll, BorderLayout.CENTER);

        // Add manual refresh button for better performance
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton refreshBtn = new JButton("🔄 Refresh Disks");
        refreshBtn.setFont(new Font("Segoe UI", Font.BOLD, 13));
        refreshBtn.setToolTipText("Click to refresh disk information");
        refreshBtn.addActionListener(e -> {
            cachedRoots = null; // Clear disk cache
            cachedUnallocated = null; // Clear unallocated cache
            PartitionOperations.clearVolumeCache(); // Clear volume cache
            detectDisks(true); // Force full refresh including unallocated spaces
            addLog("Disk information refreshed");
        });

        buttonPanel.add(refreshBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        // Don't call detectDisks here - it will be called in background after UI loads
        // This makes dashboard appear instantly
        return panel;
    }

    @SuppressWarnings("unused") // Used as convenience wrapper for detectDisks(boolean)
    private void detectDisks() {
        detectDisks(false); // Default: skip unallocated space check for performance
    }

    private void detectDisks(boolean includeUnallocated) {
        // SwingWorker for background disk detection
        new SwingWorker<Void, Void>() {
            private File[] roots;
            private ArrayList<Long> unallocatedSpaces;

            @Override
            protected Void doInBackground() throws Exception {
                // Use cached roots if available and fresh
                long currentTime = System.currentTimeMillis();
                if (cachedRoots != null && (currentTime - lastDiskScan) < DISK_CACHE_MS) {
                    roots = cachedRoots;
                } else {
                    roots = File.listRoots();
                    cachedRoots = roots;
                    lastDiskScan = currentTime;
                }

                // Only check unallocated spaces if explicitly requested (expensive operation)
                if (includeUnallocated) {
                    long currentTimeUnalloc = System.currentTimeMillis();
                    if (cachedUnallocated != null && (currentTimeUnalloc - lastUnallocatedScan) < UNALLOCATED_CACHE_MS) {
                        unallocatedSpaces = cachedUnallocated;
                    } else {
                        unallocatedSpaces = getUnallocatedSpaces();
                        cachedUnallocated = unallocatedSpaces;
                        lastUnallocatedScan = currentTimeUnalloc;
                    }
                } else {
                    unallocatedSpaces = cachedUnallocated; // Use cached value or null
                }
                return null;
            }

            @Override
            protected void done() {
                // Always rebuild UI to show latest changes
                diskPanel.removeAll();
                diskPanel.setLayout(new BoxLayout(diskPanel, BoxLayout.Y_AXIS)); // Reset layout
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

                        // Get volume label
                        String driveLetter = root.getAbsolutePath().replace("\\", "").replace(":", "");
                        String volumeLabel = PartitionOperations.getVolumeLabel(driveLetter);
                        String displayName = volumeLabel.isEmpty() ? root.getAbsolutePath() : volumeLabel + " (" + root.getAbsolutePath() + ")";

                        JLabel label = new JLabel(displayName + " — Free: " + (free / (1024*1024*1024)) + "GB / Total: " + (total / (1024*1024*1024)) + "GB");
                        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));

                        JProgressBar progress = new JProgressBar(0, 100);
                        progress.setValue(usedPercent);
                        progress.setStringPainted(true);
                        progress.setPreferredSize(new Dimension(800, 25));
                        progress.setForeground(new Color(76, 175, 80));

                        card.add(label, BorderLayout.NORTH);
                        card.add(progress, BorderLayout.SOUTH);

                        // Right-click menu
                        JPopupMenu partitionMenu = new JPopupMenu();
                        String[] actions = {"Shrink Volume", "Format Volume", "Delete Volume", "Extend Volume", "Rename Volume", "Change Drive Letter"};
                        for (String action : actions) {
                            JMenuItem item = new JMenuItem(action);
                            item.addActionListener(e -> {
                                String actionDriveLetter = root.getAbsolutePath().replace("\\", "");
                                long freeBytes = root.getFreeSpace();
                                long totalBytes = root.getTotalSpace();

                                Runnable onComplete = () -> {
                                    cachedRoots = null; // Invalidate cache
                                    cachedUnallocated = null;
                                    detectDisks(true); // Full refresh after operation
                                };

                                switch (action) {
                                    case "Shrink Volume":
                                        PartitionOperations.executeShrinkVolume(AdminDashboard.this, actionDriveLetter, totalBytes, freeBytes, currentUser, machineDAO, onComplete);
                                        break;
                                    case "Format Volume":
                                        PartitionOperations.executeFormatVolume(AdminDashboard.this, actionDriveLetter, currentUser, machineDAO, onComplete);
                                        break;
                                    case "Delete Volume":
                                        PartitionOperations.executeDeleteVolume(AdminDashboard.this, actionDriveLetter, currentUser, machineDAO, onComplete);
                                        break;
                                    case "Extend Volume":
                                        PartitionOperations.executeExtendVolume(AdminDashboard.this, actionDriveLetter, totalBytes, freeBytes, currentUser, machineDAO, onComplete);
                                        break;
                                    case "Rename Volume":
                                        PartitionOperations.executeRenameVolume(AdminDashboard.this, actionDriveLetter, currentUser, machineDAO, onComplete);
                                        break;
                                    case "Change Drive Letter":
                                        PartitionOperations.executeChangeDriveLetter(AdminDashboard.this, actionDriveLetter, currentUser, machineDAO, onComplete);
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
                if (unallocatedSpaces != null) {
                    for (Long size : unallocatedSpaces) {
                        double sizeGB = size / 1_073_741_824.0;
                        if (sizeGB < 1) continue;
                        double percent = (totalDiskSize > 0) ? ((double) size / totalDiskSize) * 100 : 0;

                        JPanel unallocatedCard = new JPanel(new BorderLayout(10, 5));
                        unallocatedCard.setMaximumSize(new Dimension(Integer.MAX_VALUE, 60));
                        unallocatedCard.setBorder(BorderFactory.createLineBorder(Color.LIGHT_GRAY));
                        unallocatedCard.setBackground(new Color(245, 245, 245));

                        JLabel label = new JLabel("💿 Unallocated Space: " + String.format("%.2f", sizeGB) + " GB");
                        label.setFont(new Font("Segoe UI", Font.PLAIN, 14));
                        label.setForeground(Color.DARK_GRAY);

                        JProgressBar progress = new JProgressBar(0, 100);
                        progress.setValue((int) percent);
                        progress.setBackground(new Color(245, 245, 245));
                        progress.setForeground(new Color(200, 200, 200));

                        unallocatedCard.add(label, BorderLayout.NORTH);
                        unallocatedCard.add(progress, BorderLayout.SOUTH);

                        JPopupMenu unallocatedMenu = new JPopupMenu();
                        JMenuItem newVol = new JMenuItem("New Simple Volume");
                        newVol.addActionListener(e -> {
                            long diskNumber = 0; // Implement proper disk number detection if needed
                            Runnable onComplete = () -> {
                                cachedUnallocated = null; // Invalidate cache
                                detectDisks(true); // Full refresh after creating volume
                            };
                            PartitionOperations.executeNewSampleVolume(AdminDashboard.this, diskNumber, currentUser, machineDAO, onComplete);
                        });
                        unallocatedMenu.add(newVol);
                        unallocatedCard.setComponentPopupMenu(unallocatedMenu);

                        diskPanel.add(Box.createVerticalStrut(5));
                        diskPanel.add(unallocatedCard);
                    }
                }

                if ((roots == null || roots.length == 0) && (unallocatedSpaces == null || unallocatedSpaces.isEmpty())) {
                    JLabel noDiskLabel = new JLabel("No drives detected.");
                    diskPanel.add(noDiskLabel);
                }

                diskPanel.revalidate();
                diskPanel.repaint();
            }
        }.execute();
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
            ActivityLogger.logCustomAction(currentUser.getUserId(), PartitionOperations.getMachineId(currentUser, machineDAO), "Refreshed activity logs");
        });

        panel.add(new JScrollPane(logArea), BorderLayout.CENTER);
        panel.add(refreshLogsBtn, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Load activity logs from database and display
     * Uses optimized query with JOINs to avoid N+1 problem
     */
    private void loadActivityLogsFromDatabase() {
        try {
            if (activityLogDAO == null) {
                addLog("Error: ActivityLogDAO not initialized");
                return;
            }

            // Get formatted logs (optimized with JOINs)
            List<String> formattedLogs = activityLogDAO.getAllLogsFormatted();

            if (formattedLogs == null) {
                addLog("Warning: Could not load activity logs from database (connection may be unavailable)");
                return;
            }

            // Clear current logs and add database logs
            logs.clear();
            logs.add("=== Activity Logs from Database ===");

            logs.addAll(formattedLogs);

            logs.add("=== End of Database Logs ===");
            logs.add("");
            logs.add("=== Local Session Logs ===");

            updateLogArea();
            addLog("Loaded " + formattedLogs.size() + " activity logs from database");
        } catch (Exception e) {
            addLog("Error loading activity logs: " + e.getMessage());
            System.err.println("Error loading activity logs: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void startDriveMonitor() {
        if (driveMonitor != null) {
            stopDriveMonitor();
        }

        // Initialize drive count
        lastDriveCount = File.listRoots().length;

        // Create background thread that checks for drive changes every 500ms
        driveMonitor = Executors.newSingleThreadScheduledExecutor();
        driveMonitor.scheduleAtFixedRate(() -> {
            try {
                int currentDriveCount = File.listRoots().length;

                // Only refresh if drive count changed (insertion or removal)
                if (currentDriveCount != lastDriveCount) {
                    lastDriveCount = currentDriveCount;

                    // Update UI on Swing thread
                    SwingUtilities.invokeLater(() -> {
                        cachedRoots = null; // Invalidate cache
                        detectDisks(false); // Quick refresh without unallocated spaces
                        addLog("Drive change detected - refreshed automatically");
                    });
                }
            } catch (Exception e) {
                // Silently ignore errors in background thread
            }
        }, 0, 100, TimeUnit.MILLISECONDS); // Check every 100ms for faster detection
    }

    private void stopDriveMonitor() {
        if (driveMonitor != null) {
            driveMonitor.shutdownNow();
            driveMonitor = null;
        }
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









}
