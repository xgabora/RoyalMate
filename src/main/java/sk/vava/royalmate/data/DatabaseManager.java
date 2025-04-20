package sk.vava.royalmate.data;

import sk.vava.royalmate.util.Config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.logging.Level;
import java.util.logging.Logger;

public class DatabaseManager {

    private static final Logger LOGGER = Logger.getLogger(DatabaseManager.class.getName());

    // Ensure the JDBC driver is loaded when the class is initialized.
    static {
        try {
            // For MySQL Connector/J 8.0+
            Class.forName("com.mysql.cj.jdbc.Driver");
            LOGGER.info("MySQL JDBC Driver registered successfully.");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "MySQL JDBC Driver not found! Ensure mysql-connector-j.jar is in the classpath.", e);
            // This is a critical failure, the application likely cannot proceed.
            throw new RuntimeException("MySQL JDBC Driver not found!", e);
        }
    }

    /**
     * Gets a connection to the database.
     *
     * @return A Connection object.
     * @throws SQLException if a database access error occurs.
     */
    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(Config.DB_URL, Config.DB_USER, Config.DB_PASSWORD);
    }

    /**
     * Tests the database connection.
     *
     * @return true if connection is successful, false otherwise.
     */
    public static boolean testConnection() {
        // Use try-with-resources to ensure the connection and statement are closed.
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            LOGGER.info("Attempting to connect to database: " + Config.DB_NAME + " on " + Config.DB_SERVER);
            // Execute a simple validation query. "SELECT 1" is standard and efficient.
            stmt.executeQuery("SELECT 1");
            LOGGER.info("Database connection successful.");
            return true;

        } catch (SQLException e) {
            // Log the specific SQL error
            LOGGER.log(Level.SEVERE, "Database connection failed! URL: " + Config.DB_URL + ", User: " + Config.DB_USER, e);
            return false;
        } catch (Exception e) {
            // Catch any other potential runtime exceptions during connection
            LOGGER.log(Level.SEVERE, "An unexpected error occurred during database connection test.", e);
            return false;
        }
    }

    // Optional: Add safe closing methods if not always using try-with-resources,
    // but try-with-resources is the preferred way for Connections, Statements, ResultSets.
    public static void closeConnection(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing database connection.", e);
            }
        }
    }

    public static void closeStatement(Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException e) {
                LOGGER.log(Level.WARNING, "Error closing statement.", e);
            }
        }
    }
    // Add similar for PreparedStatement and ResultSet if needed
}