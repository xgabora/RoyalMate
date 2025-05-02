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

    static {
        try {

            Class.forName("com.mysql.cj.jdbc.Driver");
            LOGGER.info("MySQL JDBC Driver registered successfully.");
        } catch (ClassNotFoundException e) {
            LOGGER.log(Level.SEVERE, "MySQL JDBC Driver not found! Ensure mysql-connector-j.jar is in the classpath.", e);

            throw new RuntimeException("MySQL JDBC Driver not found!", e);
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(Config.DB_URL, Config.DB_USER, Config.DB_PASSWORD);
    }

    public static boolean testConnection() {

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            LOGGER.info("Attempting to connect to database: " + Config.DB_NAME + " on " + Config.DB_SERVER);

            stmt.executeQuery("SELECT 1");
            LOGGER.info("Database connection successful.");
            return true;

        } catch (SQLException e) {

            LOGGER.log(Level.SEVERE, "Database connection failed! URL: " + Config.DB_URL + ", User: " + Config.DB_USER, e);
            return false;
        } catch (Exception e) {

            LOGGER.log(Level.SEVERE, "An unexpected error occurred during database connection test.", e);
            return false;
        }
    }

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

}