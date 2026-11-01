package com.hospital.data;

import com.hospital.model.*;

import java.sql.*;
import java.util.*;

/**
 * Data Access Object (DAO) providing persistent JDBC operations for the Hospital Allocation system.
 * Uses PreparedStatements, handles Transactions, closes resources cleanly,
 * and maintains strict data consistency with the domain models.
 */
public class HospitalDao {

    /**
     * Initializes schema tables if they do not exist.
     */
    public void initializeSchema() throws SQLException {
        String[] ddl = {
            "CREATE TABLE IF NOT EXISTS hospitals (" +
            " hospital_id VARCHAR(32) PRIMARY KEY," +
            " name VARCHAR(255) NOT NULL," +
            " location_id VARCHAR(32) NOT NULL," +
            " is_primary BOOLEAN NOT NULL DEFAULT FALSE" +
            ") ENGINE=InnoDB",

            "CREATE TABLE IF NOT EXISTS beds (" +
            " bed_id VARCHAR(64) PRIMARY KEY," +
            " hospital_id VARCHAR(32) NOT NULL," +
            " bed_number VARCHAR(32) NOT NULL," +
            " bed_type VARCHAR(64) NOT NULL," +
            " is_occupied BOOLEAN NOT NULL DEFAULT FALSE," +
            " current_patient_id VARCHAR(64) NULL," +
            " FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id) ON DELETE CASCADE" +
            ") ENGINE=InnoDB",

            "CREATE TABLE IF NOT EXISTS doctors (" +
            " doctor_id VARCHAR(32) PRIMARY KEY," +
            " name VARCHAR(255) NOT NULL," +
            " specialization VARCHAR(128) NOT NULL," +
            " hospital_id VARCHAR(32) NOT NULL," +
            " is_on_duty BOOLEAN NOT NULL DEFAULT TRUE," +
            " assigned_patient_id VARCHAR(64) NULL," +
            " FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id) ON DELETE CASCADE" +
            ") ENGINE=InnoDB",

            "CREATE TABLE IF NOT EXISTS patients (" +
            " patient_id VARCHAR(64) PRIMARY KEY," +
            " name VARCHAR(255) NOT NULL," +
            " age INT NOT NULL," +
            " gender VARCHAR(32) NOT NULL," +
            " severity VARCHAR(32) NOT NULL," +
            " required_facility VARCHAR(64) NOT NULL," +
            " arrival_sequence BIGINT NOT NULL," +
            " status VARCHAR(32) NOT NULL," +
            " assigned_hospital_id VARCHAR(32) NULL," +
            " assigned_bed_id VARCHAR(64) NULL," +
            " assigned_doctor_id VARCHAR(32) NULL," +
            " referral_notes TEXT NULL" +
            ") ENGINE=InnoDB",

            "CREATE TABLE IF NOT EXISTS admissions (" +
            " admission_id VARCHAR(64) PRIMARY KEY," +
            " patient_id VARCHAR(64) NOT NULL," +
            " hospital_id VARCHAR(32) NOT NULL," +
            " bed_id VARCHAR(64) NOT NULL," +
            " doctor_id VARCHAR(32) NULL," +
            " disposition VARCHAR(64) NOT NULL," +
            " travel_time_minutes INT DEFAULT 0," +
            " remarks TEXT," +
            " admitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP," +
            " discharged_at TIMESTAMP NULL" +
            ") ENGINE=InnoDB",

            "CREATE TABLE IF NOT EXISTS audit_records (" +
            " record_id BIGINT AUTO_INCREMENT PRIMARY KEY," +
            " sequence_num BIGINT NOT NULL," +
            " event_type VARCHAR(64) NOT NULL," +
            " patient_id VARCHAR(64) NULL," +
            " hospital_id VARCHAR(32) NULL," +
            " bed_id VARCHAR(64) NULL," +
            " doctor_id VARCHAR(32) NULL," +
            " details TEXT," +
            " event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB",

            "CREATE TABLE IF NOT EXISTS portal_accounts (" +
            " email VARCHAR(254) PRIMARY KEY," +
            " full_name VARCHAR(255) NOT NULL," +
            " password_salt VARCHAR(64) NOT NULL," +
            " password_hash VARCHAR(128) NOT NULL," +
            " role VARCHAR(16) NOT NULL," +
            " patient_id VARCHAR(64) NULL UNIQUE," +
            " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
            ") ENGINE=InnoDB"
        };

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement()) {
            for (String sql : ddl) {
                stmt.execute(sql);
            }
        }
    }

    /**
     * Saves or updates a patient record.
     */
    public void savePatient(Patient patient) throws SQLException {
        String sql = "INSERT INTO patients (patient_id, name, age, gender, severity, required_facility, " +
                     "arrival_sequence, status, assigned_hospital_id, assigned_bed_id, assigned_doctor_id, referral_notes) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                     "ON DUPLICATE KEY UPDATE name = VALUES(name), age = VALUES(age), gender = VALUES(gender), " +
                     "severity = VALUES(severity), required_facility = VALUES(required_facility), " +
                     "status = VALUES(status), assigned_hospital_id = VALUES(assigned_hospital_id), " +
                     "assigned_bed_id = VALUES(assigned_bed_id), assigned_doctor_id = VALUES(assigned_doctor_id), " +
                     "referral_notes = VALUES(referral_notes)";

        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patient.getPatientId());
            ps.setString(2, patient.getName());
            ps.setInt(3, patient.getAge());
            ps.setString(4, patient.getGender());
            ps.setString(5, patient.getSeverity().name());
            ps.setString(6, patient.getRequiredFacility());
            ps.setLong(7, patient.getArrivalSequence());
            ps.setString(8, patient.getStatus().name());
            ps.setString(9, patient.getAssignedHospitalId());
            ps.setString(10, patient.getAssignedBedId());
            ps.setString(11, patient.getAssignedDoctorId());
            ps.setString(12, patient.getReferralNotes());
            ps.executeUpdate();
        }
    }

    /**
     * Loads a patient by ID from MySQL.
     */
    public Optional<Patient> findPatientById(String patientId) throws SQLException {
        String sql = "SELECT * FROM patients WHERE patient_id = ?";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    Patient p = mapRowToPatient(rs);
                    return Optional.of(p);
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Loads all patients from MySQL.
     */
    public List<Patient> findAllPatients() throws SQLException {
        List<Patient> list = new ArrayList<>();
        String sql = "SELECT * FROM patients ORDER BY arrival_sequence ASC";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(mapRowToPatient(rs));
            }
        }
        return list;
    }

    /**
     * Restores persisted bed occupancy and doctor assignments into the supplied hospitals.
     */
    public void restoreHospitalResourceState(Collection<Hospital> hospitals) throws SQLException {
        Map<String, Bed> bedsById = new HashMap<>();
        Map<String, Doctor> doctorsById = new HashMap<>();
        for (Hospital hospital : hospitals) {
            for (Bed bed : hospital.getAllBeds()) {
                bedsById.put(bed.getBedId(), bed);
            }
            for (Doctor doctor : hospital.getAllDoctors()) {
                doctorsById.put(doctor.getDoctorId(), doctor);
            }
        }

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT bed_id, is_occupied, current_patient_id FROM beds")) {
            while (rs.next()) {
                Bed bed = bedsById.get(rs.getString("bed_id"));
                String patientId = rs.getString("current_patient_id");
                if (bed != null && rs.getBoolean("is_occupied")) {
                    if (patientId == null) {
                        throw new SQLException("Occupied database bed " + bed.getBedId() + " has no assigned patient.");
                    }
                    if (!bed.isOccupied()) {
                        bed.admitPatient(patientId);
                    } else if (!patientId.equals(bed.getCurrentPatientId())) {
                        throw new SQLException("In-memory and database patient assignments differ for bed " + bed.getBedId() + ".");
                    }
                }
            }
        }

        try (Connection conn = DatabaseManager.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT doctor_id, is_on_duty, assigned_patient_id FROM doctors")) {
            while (rs.next()) {
                Doctor doctor = doctorsById.get(rs.getString("doctor_id"));
                if (doctor == null) {
                    continue;
                }
                boolean onDuty = rs.getBoolean("is_on_duty");
                String patientId = rs.getString("assigned_patient_id");
                if (doctor.getAssignedPatientId() == null) {
                    if (patientId != null && !onDuty) {
                        throw new SQLException("Off-duty database doctor " + doctor.getDoctorId() + " has an assigned patient.");
                    }
                    doctor.setDutyStatus(onDuty);
                    if (patientId != null) {
                        doctor.admitPatient(patientId);
                    }
                } else if (!Objects.equals(patientId, doctor.getAssignedPatientId()) ||
                        onDuty != doctor.isOnDuty()) {
                    throw new SQLException("In-memory and database assignment differs for doctor " + doctor.getDoctorId() + ".");
                }
            }
        }
    }

    public void updateDoctorDutyStatus(String doctorId, boolean onDuty) throws SQLException {
        String sql = "UPDATE doctors SET is_on_duty = ? WHERE doctor_id = ? AND assigned_patient_id IS NULL";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setBoolean(1, onDuty);
            ps.setString(2, doctorId);
            if (ps.executeUpdate() != 1) {
                throw new SQLException("Doctor " + doctorId + " could not be updated (unknown or currently assigned).");
            }
        }
    }

    public boolean hasPersistedResourceAssignment(String patientId) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM beds WHERE is_occupied = TRUE AND current_patient_id = ?) " +
                     "OR EXISTS (SELECT 1 FROM doctors WHERE assigned_patient_id = ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, patientId);
            ps.setString(2, patientId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean(1);
            }
        }
    }

    private Patient mapRowToPatient(ResultSet rs) throws SQLException {
        String pid = rs.getString("patient_id");
        String name = rs.getString("name");
        int age = rs.getInt("age");
        String gender = rs.getString("gender");
        Severity severity = Severity.valueOf(rs.getString("severity"));
        String reqFac = rs.getString("required_facility");
        long seq = rs.getLong("arrival_sequence");

        Patient p = new Patient(pid, name, age, gender, severity, reqFac, seq);
        p.setStatus(Patient.Status.valueOf(rs.getString("status")));
        p.setAssignedHospitalId(rs.getString("assigned_hospital_id"));
        p.setAssignedBedId(rs.getString("assigned_bed_id"));
        p.setAssignedDoctorId(rs.getString("assigned_doctor_id"));
        p.setReferralNotes(rs.getString("referral_notes"));
        return p;
    }

    /**
     * Synchronizes a hospital, its beds, and its doctors into the database.
     */
    public void syncHospitalStructure(Hospital hospital) throws SQLException {
        String sqlHosp = "INSERT INTO hospitals (hospital_id, name, location_id, is_primary) " +
                         "VALUES (?, ?, ?, ?) ON DUPLICATE KEY UPDATE name=VALUES(name), is_primary=VALUES(is_primary)";

        String sqlBed = "INSERT INTO beds (bed_id, hospital_id, bed_number, bed_type, is_occupied, current_patient_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE hospital_id=VALUES(hospital_id), " +
                        "bed_number=VALUES(bed_number), bed_type=VALUES(bed_type)";

        String sqlDoc = "INSERT INTO doctors (doctor_id, name, specialization, hospital_id, is_on_duty, assigned_patient_id) " +
                        "VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE name=VALUES(name), " +
                        "specialization=VALUES(specialization), hospital_id=VALUES(hospital_id)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psHosp = conn.prepareStatement(sqlHosp)) {
                    psHosp.setString(1, hospital.getHospitalId());
                    psHosp.setString(2, hospital.getName());
                    psHosp.setString(3, hospital.getLocationId());
                    psHosp.setBoolean(4, hospital.isPrimary());
                    psHosp.executeUpdate();
                }

                try (PreparedStatement psBed = conn.prepareStatement(sqlBed)) {
                    for (Bed bed : hospital.getAllBeds()) {
                        psBed.setString(1, bed.getBedId());
                        psBed.setString(2, hospital.getHospitalId());
                        psBed.setString(3, bed.getBedNumber());
                        psBed.setString(4, bed.getBedType());
                        psBed.setBoolean(5, bed.isOccupied());
                        psBed.setString(6, bed.getCurrentPatientId());
                        psBed.addBatch();
                    }
                    psBed.executeBatch();
                }

                try (PreparedStatement psDoc = conn.prepareStatement(sqlDoc)) {
                    for (Doctor doc : hospital.getAllDoctors()) {
                        psDoc.setString(1, doc.getDoctorId());
                        psDoc.setString(2, doc.getName());
                        psDoc.setString(3, doc.getSpecialization());
                        psDoc.setString(4, hospital.getHospitalId());
                        psDoc.setBoolean(5, doc.isOnDuty());
                        psDoc.setString(6, doc.getAssignedPatientId());
                        psDoc.addBatch();
                    }
                    psDoc.executeBatch();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Atomically records an admission or referral in MySQL using a database Transaction.
     * Updates:
     * 1. Patient status & assignments
     * 2. Bed occupancy & current_patient_id
     * 3. Doctor assignment
     * 4. Admission record
     */
    public void recordAdmissionTransaction(AdmissionRequest request, Patient patient, Bed bed, Doctor doctor) throws SQLException {
        String updatePatientSql = "UPDATE patients SET status = ?, assigned_hospital_id = ?, assigned_bed_id = ?, " +
                                  "assigned_doctor_id = ?, referral_notes = ? WHERE patient_id = ?";
        String updateBedSql = "UPDATE beds SET is_occupied = TRUE, current_patient_id = ? WHERE bed_id = ? AND is_occupied = FALSE";
        String updateDoctorSql = "UPDATE doctors SET assigned_patient_id = ? " +
                                 "WHERE doctor_id = ? AND is_on_duty = TRUE AND assigned_patient_id IS NULL";
        String insertAdmissionSql = "INSERT INTO admissions (admission_id, patient_id, hospital_id, bed_id, doctor_id, " +
                                    "disposition, travel_time_minutes, remarks, admitted_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Update Bed (atomic check: WHERE is_occupied = FALSE prevents double-allocation)
                try (PreparedStatement psBed = conn.prepareStatement(updateBedSql)) {
                    psBed.setString(1, patient.getPatientId());
                    psBed.setString(2, bed.getBedId());
                    int rows = psBed.executeUpdate();
                    if (rows == 0) {
                        throw new SQLException("Bed " + bed.getBedId() + " is already occupied in database. Allocation aborted.");
                    }
                }

                // 2. Update Doctor if provided
                if (doctor != null) {
                    try (PreparedStatement psDoc = conn.prepareStatement(updateDoctorSql)) {
                        psDoc.setString(1, patient.getPatientId());
                        psDoc.setString(2, doctor.getDoctorId());
                        if (psDoc.executeUpdate() != 1) {
                            throw new SQLException("Doctor " + doctor.getDoctorId() + " is no longer available. Allocation aborted.");
                        }
                    }
                }

                // 3. Update Patient
                try (PreparedStatement psPat = conn.prepareStatement(updatePatientSql)) {
                    psPat.setString(1, patient.getStatus().name());
                    psPat.setString(2, patient.getAssignedHospitalId());
                    psPat.setString(3, patient.getAssignedBedId());
                    psPat.setString(4, patient.getAssignedDoctorId());
                    psPat.setString(5, patient.getReferralNotes());
                    psPat.setString(6, patient.getPatientId());
                    if (psPat.executeUpdate() != 1) {
                        throw new SQLException("Patient " + patient.getPatientId() + " was not found for admission.");
                    }
                }

                // 4. Record Admission
                try (PreparedStatement psAdm = conn.prepareStatement(insertAdmissionSql)) {
                    psAdm.setString(1, request.getRequestId());
                    psAdm.setString(2, patient.getPatientId());
                    psAdm.setString(3, request.getAllocatedHospitalId());
                    psAdm.setString(4, bed.getBedId());
                    psAdm.setString(5, doctor != null ? doctor.getDoctorId() : null);
                    psAdm.setString(6, request.getDisposition().name());
                    psAdm.setInt(7, request.getTravelTimeMinutes());
                    psAdm.setString(8, request.getRemarks());
                    psAdm.setTimestamp(9, Timestamp.valueOf(request.getTimestamp()));
                    psAdm.executeUpdate();
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Atomically executes patient discharge in MySQL.
     * Updates:
     * 1. Patient status = DISCHARGED, clears assigned bed and doctor
     * 2. Bed occupancy = FALSE, clears current_patient_id
     * 3. Doctor assignment = NULL
     * 4. Updates admissions discharged_at timestamp
     */
    public void recordDischargeTransaction(String hospitalId, String patientId, String bedId, String doctorId) throws SQLException {
        String updatePatientSql = "UPDATE patients SET status = 'DISCHARGED', assigned_hospital_id = NULL, " +
                                  "assigned_bed_id = NULL, assigned_doctor_id = NULL WHERE patient_id = ? AND assigned_hospital_id = ? " +
                                  "AND assigned_bed_id = ? AND status IN ('ADMITTED_LOCAL', 'REFERRED')";
        String lockBedSql = "SELECT is_occupied, current_patient_id FROM beds WHERE bed_id = ? AND hospital_id = ? FOR UPDATE";
        String releaseBedSql = "UPDATE beds SET is_occupied = FALSE, current_patient_id = NULL WHERE bed_id = ? AND hospital_id = ?";
        String lockDoctorSql = "SELECT assigned_patient_id FROM doctors WHERE doctor_id = ? AND hospital_id = ? FOR UPDATE";
        String releaseDoctorSql = "UPDATE doctors SET assigned_patient_id = NULL " +
                                  "WHERE doctor_id = ? AND hospital_id = ? AND assigned_patient_id = ?";
        String updateAdmissionSql = "UPDATE admissions SET discharged_at = CURRENT_TIMESTAMP " +
                                    "WHERE patient_id = ? AND hospital_id = ? AND bed_id = ? AND discharged_at IS NULL";

        try (Connection conn = DatabaseManager.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement psPat = conn.prepareStatement(updatePatientSql)) {
                    psPat.setString(1, patientId);
                    psPat.setString(2, hospitalId);
                    psPat.setString(3, bedId);
                    if (psPat.executeUpdate() != 1) {
                        throw new SQLException("Patient " + patientId + " is not admitted to the specified hospital and bed.");
                    }
                }

                try (PreparedStatement psBed = conn.prepareStatement(lockBedSql)) {
                    psBed.setString(1, bedId);
                    psBed.setString(2, hospitalId);
                    try (ResultSet rs = psBed.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Bed " + bedId + " does not exist at hospital " + hospitalId + ".");
                        }
                        String currentPatientId = rs.getString("current_patient_id");
                        if (rs.getBoolean("is_occupied") && !patientId.equals(currentPatientId)) {
                            throw new SQLException("Bed " + bedId + " is assigned to another patient.");
                        }
                        if (!rs.getBoolean("is_occupied") && currentPatientId != null) {
                            throw new SQLException("Vacant bed " + bedId + " has a stale patient assignment.");
                        }
                    }
                }
                // A prior application version may have kept the patient assignment but lost bed occupancy.
                try (PreparedStatement psBed = conn.prepareStatement(releaseBedSql)) {
                    psBed.setString(1, bedId);
                    psBed.setString(2, hospitalId);
                    psBed.executeUpdate();
                }

                if (doctorId != null) {
                    // Never clear a doctor's assignment if it now belongs to a different patient.
                    try (PreparedStatement psDoc = conn.prepareStatement(lockDoctorSql)) {
                        psDoc.setString(1, doctorId);
                        psDoc.setString(2, hospitalId);
                        try (ResultSet rs = psDoc.executeQuery()) {
                            if (!rs.next()) {
                                throw new SQLException("Doctor " + doctorId + " does not belong to hospital " + hospitalId + ".");
                            }
                            if (patientId.equals(rs.getString("assigned_patient_id"))) {
                                try (PreparedStatement release = conn.prepareStatement(releaseDoctorSql)) {
                                    release.setString(1, doctorId);
                                    release.setString(2, hospitalId);
                                    release.setString(3, patientId);
                                    release.executeUpdate();
                                }
                            }
                        }
                    }
                }

                try (PreparedStatement psAdm = conn.prepareStatement(updateAdmissionSql)) {
                    psAdm.setString(1, patientId);
                    psAdm.setString(2, hospitalId);
                    psAdm.setString(3, bedId);
                    if (psAdm.executeUpdate() == 0) {
                        throw new SQLException("No active admission record matches patient " + patientId +
                                " at hospital " + hospitalId + " in bed " + bedId + ".");
                    }
                }

                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }

    /**
     * Records an audit record into MySQL.
     */
    public void saveAuditRecord(AuditRecord rec) throws SQLException {
        String sql = "INSERT INTO audit_records (sequence_num, event_type, patient_id, hospital_id, bed_id, doctor_id, details, event_time) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseManager.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setLong(1, rec.getSequence());
            ps.setString(2, rec.getEventType().name());
            ps.setString(3, rec.getPatientId());
            ps.setString(4, rec.getHospitalId());
            ps.setString(5, rec.getBedId());
            ps.setString(6, rec.getDoctorId());
            ps.setString(7, rec.getDetails());
            ps.setTimestamp(8, Timestamp.valueOf(rec.getTimestamp()));
            ps.executeUpdate();
        }
    }
}
