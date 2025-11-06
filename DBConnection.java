import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class DBConnection {
    // Use service name syntax for PDB
    private static final String URL = "jdbc:oracle:thin:@//rac1.localdomain:1521/onclick_db";
    private static final String USER = "onclick_user";           // exact username
    private static final String PASSWORD = "Welcome2025";  // exact password

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }
}
