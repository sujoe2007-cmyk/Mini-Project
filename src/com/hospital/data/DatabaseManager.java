package com.hospital.data;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Manages JDBC connections to MySQL database with graceful fallback.
 * Reads configuration from db.properties or environment variables.
 * Never hardcodes sensitive credentials.
 */
public class DatabaseManager {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 3306;
    private static final String DEFAULT_DB = "hospital_allocation_db";
    private static final String DEFAULT_USER = "root";
    private static final String DEFAULT_PASS = "";

    private static String host;
    private static int port;
    private static String dbName;
    private static String user;
    private static String password;
    private static boolean initialized = false;
    private static Boolean connectionAvailable = null;

    static {
        loadConfiguration();
    }

    private static synchronized void loadConfiguration() {
        if (initialized) return;

        Properties props = new Properties();
        File propFile = new File("db.properties");
        if (propFile.exists()) {
            try (FileInputStream fis = new FileInputStream(propFile)) {
                props.load(fis);
            } catch (IOException e) {
                System.err.println("[DatabaseManager] Notice: Could not read db.properties: " + e.getMessage());
            }
        }

        
        host = getEnvOrDefault("MYSQL_HOST", props.getProperty("db.host", DEFAULT_HOST));
        String portStr = getEnvOrDefault("MYSQL_PORT", props.getProperty("db.port", String.valueOf(DEFAULT_PORT)));
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException e) {
            port = DEFAULT_PORT;
        }
        String testDatabase = System.getProperty("hospital.test.database");
        dbName = (testDatabase != null && !testDatabase.trim().isEmpty())
                ? testDatabase.trim()
                : getEnvOrDefault("MYSQL_DB", props.getProperty("db.name", DEFAULT_DB));
        user = getEnvOrDefault("MYSQL_USER", props.getProperty("db.user", DEFAULT_USER));
        password = getEnvOrDefault("MYSQL_PASSWORD", props.getProperty("db.password", DEFAULT_PASS));

        initialized = true;
    }

    private static String getEnvOrDefault(String envVar, String defaultValue) {
        String val = System.getenv(envVar);
        return (val != null && !val.trim().isEmpty()) ? val.trim() : defaultValue;
    }

    public static String getJdbcUrl() {
        return String.format("jdbc:mysql://%s:%d/%s?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC&connectTimeout=2000",
                host, port, dbName);
    }

    public static String getDatabaseName() {
        return dbName;
    }

    /**
     * Checks if MySQL database connection is currently available without throwing exception.
     */
    public static synchronized boolean isDatabaseAvailable() {
        if (Boolean.TRUE.equals(connectionAvailable)) {
            return true;
        }
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            try (Connection conn = DriverManager.getConnection(getJdbcUrl(), user, password)) {
                connectionAvailable = (conn != null && !conn.isClosed());
            }
        } catch (ClassNotFoundException e) {
            connectionAvailable = null;
            System.err.println("[DatabaseManager] MySQL JDBC driver is unavailable: " + e.getMessage());
        } catch (SQLException e) {
            connectionAvailable = null;
            String message = e.getMessage();
            if (message != null) {
                if (password != null && !password.isEmpty()) {
                    message = message.replace(password, "<redacted>");
                }
                message = message.replaceAll("(?i)(password|passwd|pwd)(\\s*[=:]\\s*)[^\\s,;]+",
                        "$1$2<redacted>");
            }
            System.err.println("[DatabaseManager] MySQL connection failed (SQLState="
                    + e.getSQLState() + ", vendorCode=" + e.getErrorCode() + "): " + message);
        }
        return Boolean.TRUE.equals(connectionAvailable);
    }

    /**
     * Obtains a connection to MySQL.
     * @return Connection instance or throws SQLException
     */
    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC Driver (com.mysql.cj.jdbc.Driver) not found in classpath.", e);
        }
        return DriverManager.getConnection(getJdbcUrl(), user, password);
    }

    public static void resetStatusCheck() {
        connectionAvailable = null;
    }
}
