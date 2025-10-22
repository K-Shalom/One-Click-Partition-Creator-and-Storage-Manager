package gui;

import javax.swing.*;
import java.awt.*;

public class SignupForm extends JFrame {

    private JTextField usernameField;
    private JPasswordField passwordField;
    private JButton signupButton, backButton;

    public SignupForm() {
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

        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Fill all fields!", "Warning", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (LoginForm.users.containsKey(username)) {
            JOptionPane.showMessageDialog(this, "Username already exists!", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        LoginForm.users.put(username, password);
        JOptionPane.showMessageDialog(this, "Account created! You can now login.");
        dispose();
        new LoginForm().setVisible(true);
    }
}
