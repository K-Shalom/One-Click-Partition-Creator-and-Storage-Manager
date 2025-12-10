package database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class DatabaseConnection {

    private static String DB_URL;
    private static String DB_USER;
    private static String DB_PASSWORD;
    private static Connection connection = null;

    static {
        loadDatabaseConfig();
    }

    private static void loadDatabaseConfig() {
        Properties props = new Properties();
        try {
            FileInputStream fis = new FileInputStream("config/database.properties");
            props.load(fis);
            fis.close();

            DB_URL = props.getProperty("db.url", "jdbc:mysql://localhost:3306/onclick_db?useSSL=false&serverTimezone=UTC");
            DB_USER = props.getProperty("db.user", "root");
            DB_PASSWORD = props.getProperty("db.password", "");
        } catch (IOException e) {
            System.out.println("Config file not found, using default database settings");
            DB_URL = "jdbc:mysql://localhost:3306/onclick_db";
            DB_USER = "root";
            DB_PASSWORD = "";
        }
    }

    public static Connection getConnection() {
        try {
            if (connection == null || connection.isClosed()) {
                System.out.println("Connecting to Database...");
                Class.forName("com.mysql.cj.jdbc.Driver");
                connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
            }
            return connection;
        } catch (ClassNotFoundException | SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public static boolean testConnection() {
        try {
            Connection conn = getConnection();
            return conn != null && !conn.isClosed();
        } catch (SQLException e) {
            return false;
        }
    }

    // ===== NEW GETTER METHODS FOR BACKUP =====
    public static String getUrl() {
        return DB_URL;
    }

    public static String getUser() {
        return DB_USER;
    }

    public static String getPassword() {
        return DB_PASSWORD;
    }
}
