package gui;

import dao.UserDAO;
import models.User;

import javax.swing.*;
import java.awt.*;

public class SignupForm extends JFrame {

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton signupButton, backButton;
    private UserDAO userDAO;

    public SignupForm() {
        userDAO = new UserDAO();
        setTitle("One Click - Create Account");
        setSize(400, 320);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout(10, 10));

        JLabel titleLabel = new JLabel("Create New Account", JLabel.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 22));
        titleLabel.setForeground(new Color(76, 175, 80));
        add(titleLabel, BorderLayout.NORTH);

        JPanel formPanel = new JPanel(new GridLayout(3, 2, 10, 10));
        formPanel.setBorder(BorderFactory.createEmptyBorder(20, 40, 20, 40));

        formPanel.add(new JLabel("Username:"));
        usernameField = new JTextField();
        formPanel.add(usernameField);

        formPanel.add(new JLabel("Password:"));
        passwordField = new JPasswordField();
        formPanel.add(passwordField);

        add(formPanel, BorderLayout.CENTER);

        JPanel buttonPanel = new JPanel(new GridLayout(2, 1, 10, 10));
        buttonPanel.setBorder(BorderFactory.createEmptyBorder(0, 60, 20, 60));

        signupButton = new JButton("Sign Up");
        signupButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        signupButton.setBackground(new Color(76, 175, 80));
        signupButton.setForeground(Color.WHITE);
        buttonPanel.add(signupButton);

        backButton = new JButton("Back to Login");
        backButton.setFont(new Font("Segoe UI", Font.BOLD, 14));
        backButton.setBackground(new Color(25, 118, 210));
        backButton.setForeground(Color.WHITE);
        buttonPanel.add(backButton);

        add(buttonPanel, BorderLayout.SOUTH);

        // Actions
        signupButton.addActionListener(e -> handleSignup());
        backButton.addActionListener(e -> {
            dispose();
            new LoginForm().setVisible(true);
        });
    }

    private void handleSignup() {
        String username = usernameField.getText().trim();
        String password = new String(passwordField.getPassword());

        // Validation
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Please fill in all fields!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (username.length() < 3) {
            JOptionPane.showMessageDialog(this, "Username must be at least 3 characters long!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (password.length() < 4) {
            JOptionPane.showMessageDialog(this, "Password must be at least 4 characters long!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        try {
            // Check if username already exists
            System.out.println("Checking if username exists: " + username);
            if (userDAO.usernameExists(username)) {
                JOptionPane.showMessageDialog(this, 
                    "Username '" + username + "' already exists!\nPlease choose a different username.", 
                    "Username Taken", 
                    JOptionPane.ERROR_MESSAGE);
                return;
            }

            // Create new user (default role is USER)
            System.out.println("Creating new user: " + username);
            User newUser = new User(username, password, "USER");
            
            boolean success = userDAO.createUser(newUser);
            System.out.println("User creation result: " + success);
            
            if (success) {
                JOptionPane.showMessageDialog(this, 
                    "Account created successfully!\n\nUsername: " + username + "\n\nYou can now login.", 
                    "Success", 
                    JOptionPane.INFORMATION_MESSAGE);
                
                // Clear fields
                usernameField.setText("");
                passwordField.setText("");
                
                // Go back to login
                dispose();
                new LoginForm().setVisible(true);
            } else {
                JOptionPane.showMessageDialog(this, 
                    "Error creating account!\n\nPossible causes:\n- Database connection failed\n- Database error\n\nCheck console for details.", 
                    "Error", 
                    JOptionPane.ERROR_MESSAGE);
            }
        } catch (Exception ex) {
            System.err.println("Exception during signup: " + ex.getMessage());
            ex.printStackTrace();
            JOptionPane.showMessageDialog(this, 
                "An error occurred!\n\nError: " + ex.getMessage() + "\n\nCheck console for details.", 
                "Error", 
                JOptionPane.ERROR_MESSAGE);
        }
    }
}
