package tests;

import com.hospital.data.DatabaseManager;
import com.hospital.data.HospitalDao;
import com.hospital.data.SyntheticDataGenerator;
import com.hospital.service.HospitalSystemManager;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

final class TestDatabaseReset {

    private static final String TEST_DATABASE = "hospital_allocation_test_db";
    private static final String DATABASE_PROPERTY = "hospital.test.database";

    private TestDatabaseReset() {
    }

    static HospitalSystemManager initializeCleanSystem() {
        resetBeforeManagerInitialization();
        return SyntheticDataGenerator.initializeSystem();
    }

    static void resetBeforeManagerInitialization() {
        System.setProperty(DATABASE_PROPERTY, TEST_DATABASE);
        if (!TEST_DATABASE.equals(DatabaseManager.getDatabaseName())) {
            throw new IllegalStateException("Refusing test cleanup: configured database is not " + TEST_DATABASE);
        }

        try (Connection connection = DatabaseManager.getConnection()) {
            verifyActiveDatabase(connection);
        } catch (SQLException e) {
            throw new IllegalStateException("Cannot verify the configured test database before cleanup.", e);
        }

        try {
            new HospitalDao().initializeSchema();
        } catch (SQLException e) {
            throw new IllegalStateException("Could not initialize the test database schema.", e);
        }

        try (Connection connection = DatabaseManager.getConnection()) {
            verifyActiveDatabase(connection);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("DELETE FROM admissions");
                statement.executeUpdate("DELETE FROM audit_records");
                statement.executeUpdate("DELETE FROM patients");
                statement.executeUpdate("UPDATE beds SET is_occupied = FALSE, current_patient_id = NULL");
                statement.executeUpdate("UPDATE doctors SET is_on_duty = TRUE, assigned_patient_id = NULL");
                connection.commit();
            } catch (SQLException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(true);
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Could not safely reset test records and resource assignments.", e);
        }
    }

    private static void verifyActiveDatabase(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             var resultSet = statement.executeQuery("SELECT DATABASE()")) {
            if (!resultSet.next() || !TEST_DATABASE.equals(resultSet.getString(1))) {
                throw new IllegalStateException("Refusing test cleanup: active database is not " + TEST_DATABASE);
            }
        }
    }
}
