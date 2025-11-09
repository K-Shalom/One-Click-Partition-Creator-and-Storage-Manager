package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Database Connection Utility Class
 * Manages database connections for the One Click Partition Manager
 */
public class DatabaseConnection {
    
    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;
    private static Connection connection = null;
    
    static {
        loadDatabaseConfig();
    }
    
    /**
     * Load database configuration from properties file
     */
    private static void loadDatabaseConfig() {
        Properties props = new Properties();
        try {
            // Try to load from config file
            FileInputStream fis = new FileInputStream("config/database.properties");
            props.load(fis);
            fis.close();
            
            DB_URL = props.getProperty("db.url", "jdbc:mysql://localhost:3306/onclick_db");
            DB_USER = props.getProperty("db.user", "root");
            DB_PASSWORD = props.getProperty("db.password", "");
        } catch (IOException e) {
            // Use default values if config file not found
            System.out.println("Config file not found, using default database settings");
            DB_URL = "jdbc:mysql://localhost:3306/onclick_db";
            DB_USER = "root";
            DB_PASSWORD = "";
        }
    }
    
    /**
     * Get database connection
     * @return Connection object
     */
    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                System.out.println("=== Connecting to Database ===");
                System.out.println("URL: " + DB_URL);
                System.out.println("User: " + DB_USER);
                System.out.println("Password: " + (DB_PASSWORD.isEmpty() ? "(empty)" : "***"));
                
                // Load MySQL JDBC Driver
                System.out.println("Loading MySQL JDBC Driver...");
                Class.forName("com.mysql.cj.jdbc.Driver");
                System.out.println("✓ Driver loaded successfully");
                
                System.out.println("Attempting connection...");
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                System.out.println("✓ Database connected successfully!");
                System.out.println("================================");
            }
            return connection;
        } catch (ClassNotFoundException e) {
            System.err.println("================================");
            System.err.println("✗ MySQL JDBC Driver not found!");
            System.err.println("================================");
            System.err.println("Please add mysql-connector-java to your project:");
            System.err.println("1. Download from: https://dev.mysql.com/downloads/connector/j/");
            System.err.println("2. Add JAR to project classpath");
            System.err.println("3. Rebuild project");
            System.err.println("================================");
            e.printStackTrace();
            return null;
        } catch (SQLException e) {
            System.err.println("================================");
            System.err.println("✗ Database connection failed!");
            System.err.println("================================");
            System.err.println("Configuration:");
            System.err.println("  URL: " + DB_URL);
            System.err.println("  User: " + DB_USER);
            System.err.println("  Password: " + (DB_PASSWORD.isEmpty() ? "(empty)" : "***"));
            System.err.println("");
            System.err.println("Error Details:");
            System.err.println("  Message: " + e.getMessage());
            System.err.println("  SQL State: " + e.getSQLState());
            System.err.println("  Error Code: " + e.getErrorCode());
            System.err.println("");
            System.err.println("Please check:");
            System.err.println("1. MySQL/MariaDB service is running");
            System.err.println("2. Database 'onclick_db' exists");
            System.err.println("3. Username and password are correct");
            System.err.println("4. config/database.properties is configured correctly");
            System.err.println("================================");
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Close database connection
     */
    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                System.out.println("Database connection closed");
            }
        } catch (SQLException e) {
            System.err.println("Error closing database connection");
            e.printStackTrace();
        }
    }
    
    /**
     * Test database connection
     * @return true if connection successful, false otherwise
     */
    public static boolean testConnection() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }
}
