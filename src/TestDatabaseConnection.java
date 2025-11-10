import database.DatabaseConnection;
import dao.UserDAO;
import models.User;

import java.sql.Connection;

/**
 * Test Database Connection
 * Run this to verify your database setup
 */
public class TestDatabaseConnection {
    
    public static void main(String[] args) {
        System.out.println("========================================");
        System.out.println("   DATABASE CONNECTION TEST");
        System.out.println("========================================");
        System.out.println();
        
        // Test 1: Database Connection
        System.out.println("TEST 1: Testing database connection...");
        System.out.println("----------------------------------------");
        Connection conn = DatabaseConnection.getConnection();
        
        if (conn != null) {
            System.out.println("✓ SUCCESS: Database connected!");
            System.out.println();
            
            // Test 2: Create Test User
            System.out.println("TEST 2: Testing user creation...");
            System.out.println("----------------------------------------");
            
            UserDAO userDAO = new UserDAO();
            User testUser = new User("testuser" + System.currentTimeMillis(), "test123", "USER");
            
            boolean created = userDAO.createUser(testUser);
            
            if (created) {
                System.out.println("✓ SUCCESS: User created in database!");
                System.out.println("   User ID: " + testUser.getUserId());
                System.out.println("   Username: " + testUser.getUsername());
                System.out.println("   Role: " + testUser.getRole());
            } else {
                System.out.println("✗ FAILED: Could not create user");
            }
            
            System.out.println();
            System.out.println("========================================");
            System.out.println("   TEST COMPLETE");
            System.out.println("========================================");
            
        } else {
            System.out.println("✗ FAILED: Could not connect to database!");
            System.out.println();
            System.out.println("TROUBLESHOOTING STEPS:");
            System.out.println("1. Make sure MySQL/MariaDB is running");
            System.out.println("2. Check if database 'onclick_db' exists:");
            System.out.println("   mysql -u root -p");
            System.out.println("   SHOW DATABASES;");
            System.out.println();
            System.out.println("3. Import the database schema:");
            System.out.println("   mysql -u root -p < onclick_db.sql");
            System.out.println();
            System.out.println("4. Check config/database.properties");
            System.out.println();
            System.out.println("5. Make sure mysql-connector-java JAR is in classpath");
            System.out.println();
            System.out.println("========================================");
        }
    }
}
