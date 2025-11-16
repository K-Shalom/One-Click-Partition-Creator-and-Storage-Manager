package gui;

import dao.UserDAO;
import dao.MachineDAO;
import dao.PartitionDAO;
import database.DatabaseConnection;
import models.User;
import models.Machine;
import models.Partition;
import gui.PartitionStorage.PartitionInfo;
import utils.ActivityLogger;
import javax.swing.*;
import java.awt.*;
import java.sql.Date;
import java.util.List;

public class LoginForm extends JFrame {

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton loginButton, signupButton;
    private UserDAO userDAO;
    private MachineDAO machineDAO;
    private PartitionDAO partitionDAO;

    // Store logged in user
    public static User currentUser = null;

    public LoginForm() {
        userDAO = new UserDAO();
        machineDAO = new MachineDAO();
        partitionDAO = new PartitionDAO();
        setTitle("OneClick - Login");
        setSize(400, 320);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        // Title
        JLabel titleLabel = new JLabel("Welcome to OneClick", JLabel.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(33, 150, 243));
        add(titleLabel, BorderLayout.NORTH);

        // Center Panel
        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        formPanel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        formPanel.add(usernameField);

        formPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        formPanel.add(passwordField);

        add(formPanel, BorderLayout.CENTER);

        // Buttons
        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 60, 20, 60));

        loginButton = new JButton("Login");
        loginButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        loginButton.setBackground(new Color(33, 150, 243));
        loginButton.setForeground(Color.WHITE);
        loginButton.setFocusPainted(false);
        buttonPanel.add(loginButton);

        signupButton = new JButton("Create Account");
        signupButton.setFont(new Font("Segoe UI", Font.BOLD, 13));
        signupButton.setBackground(new Color(76, 175, 80));
        signupButton.setForeground(Color.WHITE);
        signupButton.setFocusPainted(false);
        buttonPanel.add(signupButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // Actions
        loginButton.addActionListener(e -> handleLogin());
        signupButton.addActionListener(e -> {
            dispose();
            new SignupForm().setVisible(true);
        });
    }

    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Show loading state
        loginButton.setEnabled(false);
        loginButton.setText("Logging in...");
        setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));

        try {
            // Test database connection first
            if (DatabaseConnection.getConnection() == null) {
                JOptionPane.showMessageDialog(this,
                        "Database Connection Failed!\n\n" +
                                "Possible causes  please check:\n" +
                                "1. MySQL JDBC Driver not found\n" +
                                "   - Download mysql-connector-java JAR\n" +
                                "   - Add to project classpath\n\n" +
                                "2. MySQL/MariaDB not running\n" +
                                "   - Start MySQL service\n\n" +
                                "3. Database 'onclick_db' doesn't exist\n" +
                                "   - Import onclick_db.sql\n\n" +
                                "Check console for detailed error messages.",
                        "Database Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Authenticate user from database
            User user = userDAO.authenticateUser(username, password);

            if (user != null) {
                currentUser = user;

                // Get or create current machine for logging
                Machine currentMachine = machineDAO.getOrCreateCurrentMachine(user.getUserId());

                // Log successful login activity
                if (currentMachine != null) {
                    ActivityLogger.logLogin(user.getUserId(), currentMachine.getMachineId());
                }

                // Close login form first
                this.dispose();

                // Show dashboard immediately (no blocking message)
                if (user.isAdmin()) {
                    AdminDashboard dashboard = new AdminDashboard(user);
                    dashboard.setVisible(true);
                    // Show welcome message after dashboard is visible (non-blocking)
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(dashboard,
                                "Welcome Admin: " + user.getUsername() + "!",
                                "Welcome",
                                JOptionPane.INFORMATION_MESSAGE);
                    });
                } else {
                    UserDashboard dashboard = new UserDashboard(user);
                    dashboard.setVisible(true);
                    // Show welcome message after dashboard is visible (non-blocking)
                    SwingUtilities.invokeLater(() -> {
                        JOptionPane.showMessageDialog(dashboard,
                                "Welcome User: " + user.getUsername() + "!",
                                "Welcome",
                                JOptionPane.INFORMATION_MESSAGE);
                    });
                }

                // Fetch and save partitions to database in background (non-blocking)
                new Thread(() -> saveSystemPartitionsToDatabase(user)).start();
            } else {
                // Reset button state on failure
                loginButton.setEnabled(true);
                loginButton.setText("Login");
                setCursor(Cursor.getDefaultCursor());

                JOptionPane.showMessageDialog(this,
                        "Invalid credentials!\n\n" +
                                "Please check your username and password.\n\n",
                        "Login Failed",
                        JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            // Reset button state on error
            loginButton.setEnabled(true);
            loginButton.setText("Login");
            setCursor(Cursor.getDefaultCursor());

            System.err.println("Login error: " + ex.getMessage());
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this,
                    "An error occurred during login!\n\n" +
                            "Error: " + ex.getMessage() + "\n\n" +
                            "Check console for details.",
                    "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * Fetch system partitions and save them to the database
     * @param user The logged-in user
     */
    private void saveSystemPartitionsToDatabase(User user) {
        try {
            System.out.println("\n=== Fetching and Saving System Partitions ===");

            // Get or create the current machine
            Machine currentMachine = machineDAO.getOrCreateCurrentMachine(user.getUserId());

            if (currentMachine == null) {
                System.err.println("Error: Could not get or create current machine");
                return;
            }

            System.out.println("Machine: " + currentMachine.getMachineName() + " (ID: " + currentMachine.getMachineId() + ")");

            // Get all system partitions
            List<PartitionInfo> systemPartitions = PartitionStorage.getSystemPartitions();

            if (systemPartitions.isEmpty()) {
                System.out.println("No partitions detected on this system");
                return;
            }

            System.out.println("Found " + systemPartitions.size() + " partition(s)");

            int savedCount = 0;
            int skippedCount = 0;

            // Save each partition to database
            for (PartitionInfo partInfo : systemPartitions) {
                String driveLetter = partInfo.getDriveLetter();
                long sizeGB = partInfo.getSizeGB();

                // Check if partition already exists
                Partition existingPartition = partitionDAO.getPartitionByDriveAndMachine(
                        driveLetter,
                        currentMachine.getMachineId(),
                        user.getUserId()
                );

                if (existingPartition != null) {
                    System.out.println("  - " + driveLetter + " (" + sizeGB + " GB) - Already exists, skipping");
                    skippedCount++;

                    // Update size if it has changed
                    if (existingPartition.getSizeGb() != (int) sizeGB) {
                        existingPartition.setSizeGb((int) sizeGB);
                        partitionDAO.updatePartition(existingPartition);
                        System.out.println("    Updated size to " + sizeGB + " GB");
                    }
                } else {
                    // Create new partition record
                    Partition newPartition = new Partition(
                            currentMachine.getMachineId(),
                            user.getUserId(),
                            driveLetter,
                            (int) sizeGB,
                            new Date(System.currentTimeMillis())
                    );

                    if (partitionDAO.createPartition(newPartition)) {
                        System.out.println("  ✓ " + driveLetter + " (" + sizeGB + " GB) - Saved successfully");
                        savedCount++;
                    } else {
                        System.err.println("  ✗ " + driveLetter + " - Failed to save");
                    }
                }
            }

            System.out.println("\nSummary: " + savedCount + " new partition(s) saved, " + skippedCount + " already existed");
            System.out.println("=== Partition Sync Complete ===\n");

            // Log partition synchronization activity
            if (savedCount > 0 || skippedCount > 0) {
                ActivityLogger.logSystemSync(user.getUserId(), currentMachine.getMachineId(),
                        savedCount + skippedCount);
            }

        } catch (Exception e) {
            System.err.println("Error saving partitions to database: " + e.getMessage());
            e.printStackTrace();

            // Log error activity
            try {
                Machine currentMachine = machineDAO.getOrCreateCurrentMachine(user.getUserId());
                if (currentMachine != null) {
                    ActivityLogger.logError(user.getUserId(), currentMachine.getMachineId(),
                            "Failed to sync partitions: " + e.getMessage());
                }
            } catch (Exception logEx) {
                System.err.println("Failed to log error: " + logEx.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new LoginForm().setVisible(true));
    }
}
