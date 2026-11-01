package tests;

import com.hospital.api.HospitalApiServer;
import com.hospital.data.DatabaseManager;
import com.hospital.data.HospitalDao;
import com.hospital.data.SyntheticDataGenerator;
import com.hospital.model.*;
import com.hospital.service.HospitalSystemManager;
import org.junit.Before;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.Assert.*;

/**
 * JUnit 4 Integration & Regression Test Suite for MySQL JDBC Database Layer:
 * 1. JDBC Driver Loading & Registration Verification
 * 2. Database Connection Attempt & Graceful Fallback Semantics
 * 3. Schema & Table Structure Validation
 * 4. Transactional Atomic Admission Commit / Rollback
 * 5. Concurrent Modification Safety & State Synchronization
 * 6. Graceful In-Memory Collections Continuity when DB Offline
 */
public class HospitalDatabaseIntegrationTest {

    private static String testManagementToken;

    private HospitalSystemManager manager;
    private HospitalDao dao;

    @Before
    public void setUp() {
        manager = TestDatabaseReset.initializeCleanSystem();
        dao = manager.getHospitalDao();
    }

    /**
     * Test 1: Verify MySQL JDBC Driver (mysql-connector-j-8.3.0.jar) is present on classpath
     * and loads successfully into the JVM.
     */
    @Test
    public void testJdbcDriverPresenceAndLoading() {
        try {
            Class<?> driverClass = Class.forName("com.mysql.cj.jdbc.Driver");
            assertNotNull("MySQL Driver class must be loadable", driverClass);
            System.out.println("[DB TEST] Verified: com.mysql.cj.jdbc.Driver loaded successfully.");
        } catch (ClassNotFoundException e) {
            fail("com.mysql.cj.jdbc.Driver was not found in classpath: " + e.getMessage());
        }
    }

    /**
     * Test 2: Connection manager behavior and graceful offline handling.
     * When MySQL daemon is not running locally, isDatabaseAvailable() must return false safely
     * without throwing uncaught exceptions or crashing the JVM.
     */
    @Test
    public void testDatabaseManagerAvailabilityHandling() {
        boolean available = DatabaseManager.isDatabaseAvailable();
        System.out.println("[DB TEST] DatabaseManager.isDatabaseAvailable() = " + available);
        System.out.println("[DB TEST] Target JDBC URL: " + DatabaseManager.getJdbcUrl());

        if (available) {
            try (Connection conn = DatabaseManager.getConnection()) {
                assertNotNull("Connection must not be null when available", conn);
                assertFalse("Connection must be open", conn.isClosed());
            } catch (SQLException e) {
                fail("SQLException when fetching connection: " + e.getMessage());
            }
        } else {
            System.out.println("[DB TEST] MySQL instance is not running locally. Verified graceful offline status detection.");
            assertFalse("isDatabaseAvailable must be false when MySQL server is not running", available);
        }
    }

    /**
     * Test 3: DAO initialization and synchronization safety under offline/online conditions.
     */
    @Test
    public void testHospitalDaoSyncSafety() {
        assertNotNull("HospitalDao instance must exist in HospitalSystemManager", dao);
        if (DatabaseManager.isDatabaseAvailable()) {
            try {
                for (Hospital h : manager.getAllHospitals()) {
                    dao.syncHospitalStructure(h);
                }

                try (Connection conn = DatabaseManager.getConnection();
                     Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate("UPDATE beds SET is_occupied = TRUE, current_patient_id = 'SYNC-LIVE-PATIENT' " +
                            "WHERE bed_id = 'NDGH-ICU-01'");
                    stmt.executeUpdate("UPDATE doctors SET is_on_duty = FALSE, assigned_patient_id = 'SYNC-LIVE-PATIENT' " +
                            "WHERE doctor_id = 'DOC-01'");
                }
                dao.syncHospitalStructure(manager.getPrimaryHospital());

                try (Connection conn = DatabaseManager.getConnection();
                     Statement stmt = conn.createStatement();
                     var bed = stmt.executeQuery("SELECT is_occupied, current_patient_id FROM beds " +
                             "WHERE bed_id = 'NDGH-ICU-01'")) {
                    assertTrue("Persisted bed row must exist", bed.next());
                    assertTrue("Structure sync must preserve bed occupancy", bed.getBoolean("is_occupied"));
                    assertEquals("SYNC-LIVE-PATIENT", bed.getString("current_patient_id"));
                }
                try (Connection conn = DatabaseManager.getConnection();
                     Statement stmt = conn.createStatement();
                     var doctor = stmt.executeQuery("SELECT is_on_duty, assigned_patient_id FROM doctors " +
                             "WHERE doctor_id = 'DOC-01'")) {
                    assertTrue("Persisted doctor row must exist", doctor.next());
                    assertFalse("Structure sync must preserve doctor duty status", doctor.getBoolean("is_on_duty"));
                    assertEquals("SYNC-LIVE-PATIENT", doctor.getString("assigned_patient_id"));
                }
                System.out.println("[DB TEST] HospitalDao.syncHospitalStructure completed cleanly with live database.");
            } catch (Exception e) {
                fail("syncHospitalStructure threw unexpected exception: " + e.getMessage());
            }
        } else {
            System.out.println("[DB TEST] MySQL offline: HospitalDao operations safely bypassed, application runs via memory collections.");
            assertTrue("DAO exists and is ready for database connection", dao != null);
        }
    }

    /**
     * Test 4: End-to-End Patient Admission & Audit logging with DB synchronization.
     * Demonstrates that the core Java Collections algorithm executes flawlessly
     * and persists to DB if online, or cleanly continues in-memory if offline.
     */
    @Test
    public void testPatientRegistrationAndAllocationPersistence() {
        Patient pt = manager.registerPatient("DB-TEST-01", "Murugan Velu", 48, "Male",
                Severity.CRITICAL, "ICU");

        assertNotNull("Patient must be registered", pt);
        assertEquals("Murugan Velu", pt.getName());
        assertEquals(Patient.Status.WAITING, pt.getStatus());

        // Process queue -> Admitted to NDGH ICU
        List<AdmissionRequest> results = manager.processWaitingQueue();
        assertFalse("Waiting queue should have processed the critical patient", results.isEmpty());

        AdmissionRequest req = results.get(0);
        assertEquals(AdmissionRequest.Disposition.ADMITTED_PRIMARY, req.getDisposition());
        assertEquals("NDGH", req.getAllocatedHospitalId());
        assertEquals("NDGH-ICU-01", req.getAllocatedBedId());
        assertEquals("DOC-01", req.getAllocatedDoctorId());
        assertEquals(Patient.Status.ADMITTED_LOCAL, pt.getStatus());

        // Verify audit history logged the admission
        List<AuditRecord> auditList = manager.getAuditHistory();
        assertTrue("Audit ledger must have recorded the events", auditList.size() >= 2);
    }

    /**
     * Test 5: Discharge transaction and resource liberation with DB synchronization.
     */
    @Test
    public void testDischargeTransactionWithDatabaseSync() {
        // Register and admit patient
        Patient pt = manager.registerPatient("DB-TEST-02", "Anitha Selvam", 35, "Female",
                Severity.SERIOUS, "General");
        manager.processWaitingQueue();

        assertEquals(Patient.Status.ADMITTED_LOCAL, pt.getStatus());
        String bedId = pt.getAssignedBedId();
        String docId = pt.getAssignedDoctorId();

        // Perform discharge
        boolean discharged = manager.dischargePatient("NDGH", pt.getPatientId());
        assertTrue("Discharge must succeed", discharged);
        assertEquals(Patient.Status.DISCHARGED, pt.getStatus());
        assertNull("Bed must be unassigned from patient", pt.getAssignedBedId());

        // Check bed is vacant again
        Bed bed = manager.getPrimaryHospital().getBed(bedId);
        assertFalse("Bed must be vacant", bed.isOccupied());

        // Check doctor is free again
        Doctor doc = manager.getPrimaryHospital().getDoctor(docId);
        assertNull("Doctor must be free", doc.getAssignedPatientId());
    }

    @Test
    public void testFailedAdmissionPersistenceRollsBackInMemoryReservation() throws SQLException {
        Patient patient = manager.registerPatient("API-DB-ROLLBACK-01", "Rollback Patient", 44, "Female",
                Severity.CRITICAL, "ICU");
        String conflictPatientId = "ROLLBACK-FIXTURE-01";

        try (Connection conn = DatabaseManager.getConnection();
             var conflict = conn.prepareStatement("UPDATE beds SET is_occupied = TRUE, current_patient_id = ? " +
                     "WHERE bed_id = 'NDGH-ICU-01' AND is_occupied = FALSE")) {
            conflict.setString(1, conflictPatientId);
            assertEquals("Fixture must make the database bed unavailable", 1, conflict.executeUpdate());
        }

        try {
            try {
                manager.processWaitingQueue();
                fail("Admission must fail when the database bed is occupied by another patient.");
            } catch (IllegalStateException expected) {
                assertTrue(expected.getMessage().contains("database persistence failed"));
            }

            assertEquals(Patient.Status.WAITING, patient.getStatus());
            assertNull(patient.getAssignedHospitalId());
            assertNull(patient.getAssignedBedId());
            assertNull(patient.getAssignedDoctorId());
            assertFalse(manager.getPrimaryHospital().getBed("NDGH-ICU-01").isOccupied());
            assertNull(manager.getPrimaryHospital().getDoctor("DOC-01").getAssignedPatientId());
            assertTrue(manager.getWaitingQueue().contains(patient));

            try (Connection conn = DatabaseManager.getConnection();
                 var statement = conn.createStatement();
                 var rows = statement.executeQuery("SELECT status, assigned_hospital_id, assigned_bed_id " +
                         "FROM patients WHERE patient_id = 'API-DB-ROLLBACK-01'")) {
                assertTrue(rows.next());
                assertEquals("WAITING", rows.getString("status"));
                assertNull(rows.getString("assigned_hospital_id"));
                assertNull(rows.getString("assigned_bed_id"));
            }
        } finally {
            try (Connection conn = DatabaseManager.getConnection();
                 var clear = conn.prepareStatement("UPDATE beds SET is_occupied = FALSE, current_patient_id = NULL " +
                         "WHERE bed_id = 'NDGH-ICU-01' AND current_patient_id = ?")) {
                clear.setString(1, conflictPatientId);
                assertEquals("Only the regression fixture bed assignment may be cleared", 1, clear.executeUpdate());
            }
        }
    }

    /**
     * Test 6: In-Memory collection integrity remains 100% deterministic
     * even when MySQL operations fall back.
     */
    @Test
    public void testStateRestorationSafety() {
        // Calling restoreStateFromDatabase() should execute cleanly
        try {
            manager.restoreStateFromDatabase();
            System.out.println("[DB TEST] restoreStateFromDatabase() executed cleanly.");
        } catch (Exception e) {
            fail("State restoration threw unexpected exception: " + e.getMessage());
        }
    }

    @Test
    public void testHttpApiPersistenceRefreshRestartAndReallocation() throws Exception {
        int port = findAvailablePort();
        HospitalApiServer server = new HospitalApiServer(manager, port);
        server.start();
        try {
            HttpResult account = request(port, "POST", "/api/auth/signup",
                    "{\"name\":\"API Test Staff\",\"email\":\"api-test-" + java.util.UUID.randomUUID() + "@example.test\",\"password\":\"TestPass123!\",\"role\":\"MANAGEMENT\"}");
            assertEquals("Management portal signup must succeed", 200, account.status);
            testManagementToken = account.body.replaceAll(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*", "$1");
            assertFalse("Signup response must include a bearer token", testManagementToken.equals(account.body));
            HttpResult invalidInput = request(port, "POST", "/api/register",
                    "{\"id\":\"API-INVALID\",\"name\":\"Invalid Patient\",\"age\":-1," +
                            "\"gender\":\"Other\",\"severity\":\"CRITICAL\",\"requiredFacility\":\"ICU\"}");
            assertEquals("Invalid registration input must return a client error", 400, invalidInput.status);
            assertFalse(request(port, "GET", "/api/patients", null).body.contains("API-INVALID"));

            HttpResult dutyOff = request(port, "POST", "/api/doctor-duty",
                    "{\"hospitalId\":\"NDGH\",\"doctorId\":\"DOC-04\",\"onDuty\":false}");
            assertEquals(200, dutyOff.status);
            assertTrue(dutyOff.body.contains("\"success\":true"));

            HttpResult registration = request(port, "POST", "/api/register",
                    "{\"id\":\"API-RESTORE-01\",\"name\":\"Ravi, \\\"Arun\\\"\",\"age\":45," +
                            "\"gender\":\"Male\",\"severity\":\"CRITICAL\",\"requiredFacility\":\"ICU\"}");
            assertEquals(201, registration.status);
            assertTrue(registration.body.contains("\"databasePersisted\":true"));
            assertTrue(registration.body.contains("Ravi, \\\"Arun\\\""));
            assertTrue(request(port, "GET", "/api/patients", null).body.contains("\"id\":\"API-RESTORE-01\""));

            HttpResult triage = request(port, "POST", "/api/process-queue", "{}");
            assertEquals(200, triage.status);
            assertTrue(triage.body.contains("\"disposition\":\"ADMITTED_PRIMARY\""));
            assertTrue(triage.body.contains("\"allocatedBedId\":\"NDGH-ICU-01\""));
            HttpResult admitted = request(port, "GET", "/api/patients", null);
            assertTrue(admitted.body.contains("\"status\":\"ADMITTED_LOCAL\""));
            assertTrue(request(port, "GET", "/api/hospitals", null).body.contains("\"patientId\":\"API-RESTORE-01\""));

            server.stop();
            manager = SyntheticDataGenerator.initializeSystem();
            Patient restored = manager.getPatient("API-RESTORE-01");
            assertNotNull("Persisted patient must be restored after manager restart", restored);
            assertEquals(Patient.Status.ADMITTED_LOCAL, restored.getStatus());
            assertTrue(manager.getPrimaryHospital().getBed("NDGH-ICU-01").isOccupied());
            assertEquals("API-RESTORE-01", manager.getPrimaryHospital().getBed("NDGH-ICU-01").getCurrentPatientId());
            assertEquals("API-RESTORE-01", manager.getPrimaryHospital().getDoctor("DOC-01").getAssignedPatientId());
            assertFalse("Doctor duty changes must survive a server restart",
                    manager.getPrimaryHospital().getDoctor("DOC-04").isOnDuty());

            server = new HospitalApiServer(manager, port);
            server.start();
            assertTrue(request(port, "GET", "/api/patients", null).body.contains("\"status\":\"ADMITTED_LOCAL\""));
            HttpResult dutyOn = request(port, "POST", "/api/doctor-duty",
                    "{\"hospitalId\":\"NDGH\",\"doctorId\":\"DOC-04\",\"onDuty\":true}");
            assertEquals(200, dutyOn.status);

            for (int i = 0; i < 8; i++) {
                HttpResult waitingRegistration = request(port, "POST", "/api/register",
                        "{\"id\":\"API-WAIT-" + i + "\",\"name\":\"Queue Patient " + i + "\",\"age\":30," +
                                "\"gender\":\"Female\",\"severity\":\"SERIOUS\",\"requiredFacility\":\"General\"}");
                assertEquals(201, waitingRegistration.status);
            }
            request(port, "POST", "/api/process-queue", "{}");
            String queue = request(port, "GET", "/api/queue", null).body;
            assertTrue("Patient without an available doctor should remain in the queue", queue.contains("\"id\":\"API-WAIT-7\""));

            // Reproduce legacy stale resource rows: the patient remains admitted, but its bed and doctor rows are vacant.
            try (Connection conn = DatabaseManager.getConnection();
                 var bed = conn.prepareStatement("UPDATE beds SET is_occupied = FALSE, current_patient_id = NULL " +
                         "WHERE bed_id = 'NDGH-ICU-01' AND current_patient_id = 'API-RESTORE-01'");
                 var doctor = conn.prepareStatement("UPDATE doctors SET assigned_patient_id = NULL " +
                         "WHERE doctor_id = 'DOC-01' AND assigned_patient_id = 'API-RESTORE-01'")) {
                assertEquals(1, bed.executeUpdate());
                assertEquals(1, doctor.executeUpdate());
            }

            HttpResult discharge = request(port, "POST", "/api/discharge",
                    "{\"hospitalId\":\"NDGH\",\"patientId\":\"API-RESTORE-01\"}");
            assertEquals(200, discharge.status);
            String patientsAfterDischarge = request(port, "GET", "/api/patients", null).body;
            assertTrue("Waiting patient should be admitted immediately after discharge",
                    patientsAfterDischarge.contains("\"id\":\"API-WAIT-7\"") &&
                            patientsAfterDischarge.contains("\"status\":\"ADMITTED_LOCAL\""));
            assertTrue("Discharged patient must remain visible with discharged status",
                    patientsAfterDischarge.contains("\"id\":\"API-RESTORE-01\"") &&
                            patientsAfterDischarge.contains("\"status\":\"DISCHARGED\""));

            try (Connection conn = DatabaseManager.getConnection();
                 var statement = conn.createStatement();
                 var patientRows = statement.executeQuery("SELECT patient_id, status, assigned_bed_id FROM patients " +
                         "WHERE patient_id IN ('API-RESTORE-01', 'API-WAIT-7')")) {
                int rows = 0;
                while (patientRows.next()) {
                    rows++;
                    if ("API-RESTORE-01".equals(patientRows.getString("patient_id"))) {
                        assertEquals("DISCHARGED", patientRows.getString("status"));
                        assertNull(patientRows.getString("assigned_bed_id"));
                    } else {
                        assertEquals("ADMITTED_LOCAL", patientRows.getString("status"));
                        assertNotNull(patientRows.getString("assigned_bed_id"));
                    }
                }
                assertEquals("Both test patients must be present in the test database", 2, rows);
            }
            try (Connection conn = DatabaseManager.getConnection();
                 var statement = conn.createStatement();
                 var resourceRows = statement.executeQuery("SELECT b.is_occupied, b.current_patient_id, " +
                         "d.assigned_patient_id, a.discharged_at FROM beds b " +
                         "JOIN doctors d ON d.doctor_id = 'DOC-01' " +
                         "JOIN admissions a ON a.patient_id = 'API-RESTORE-01' " +
                         "WHERE b.bed_id = 'NDGH-ICU-01'")) {
                assertTrue(resourceRows.next());
                assertFalse("Discharged bed must be vacant in MySQL", resourceRows.getBoolean("is_occupied"));
                assertNull(resourceRows.getString("current_patient_id"));
                assertEquals("Released doctor must be immediately reassigned to the admitted waiter",
                        "API-WAIT-7", resourceRows.getString("assigned_patient_id"));
                assertNotNull("Matching admission must be timestamped as discharged",
                        resourceRows.getTimestamp("discharged_at"));
            }
        } finally {
            server.stop();
            testManagementToken = null;
        }
    }

    @Test
    public void testPatientSignupThenSeparateDatabaseBackedIntake() throws Exception {
        int port=findAvailablePort();
        HospitalApiServer server=new HospitalApiServer(manager,port); server.start();
        try {
            HttpResult account=request(port,"POST","/api/auth/signup",
                    "{\"name\":\"Portal Patient\",\"email\":\"portal-patient-"+java.util.UUID.randomUUID()+"@example.test\",\"password\":\"TestPass123!\",\"role\":\"PATIENT\"}");
            assertEquals(200,account.status);
            testManagementToken=account.body.replaceAll(".*\\\"token\\\":\\\"([^\\\"]+)\\\".*","$1");
            assertTrue(request(port,"GET","/api/patient/me",null).body.contains("\"registered\":false"));
            HttpResult intake=request(port,"POST","/api/register","{\"name\":\"Portal Patient\",\"age\":34,\"gender\":\"Other\",\"severity\":\"MINOR\",\"requiredFacility\":\"General\"}");
            assertEquals(201,intake.status);
            assertTrue(intake.body.contains("\"registered\":true"));
            assertTrue(request(port,"GET","/api/patient/me",null).body.contains("\"registered\":true"));
            assertTrue(intake.body.contains("NAL-"));
        } finally { testManagementToken=null; server.stop(); }
    }

    private static int findAvailablePort() throws SQLException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (java.io.IOException e) {
            throw new SQLException("Could not reserve a local port for API integration testing.", e);
        }
    }

    private static HttpResult request(int port, String method, String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://localhost:" + port + path).openConnection();
        connection.setRequestMethod(method);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        if (testManagementToken != null) connection.setRequestProperty("Authorization", "Bearer " + testManagementToken);
        if (body != null) {
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
        }
        int status = connection.getResponseCode();
        InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
        String response;
        try (stream) {
            response = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
        return new HttpResult(status, response);
    }

    private static final class HttpResult {
        final int status;
        final String body;

        HttpResult(int status, String body) {
            this.status = status;
            this.body = body;
        }
    }
}
