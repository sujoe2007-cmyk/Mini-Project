-- =============================================================================
-- Government Hospital Resource Allocation System (Nalam District)
-- Database Schema for MySQL 8.x / 5.7+
-- =============================================================================

CREATE DATABASE IF NOT EXISTS hospital_allocation_db
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE hospital_allocation_db;

-- 1. Hospitals Table
CREATE TABLE IF NOT EXISTS hospitals (
    hospital_id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    location_id VARCHAR(32) NOT NULL,
    is_primary BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 2. Beds Table
CREATE TABLE IF NOT EXISTS beds (
    bed_id VARCHAR(64) PRIMARY KEY,
    hospital_id VARCHAR(32) NOT NULL,
    bed_number VARCHAR(32) NOT NULL,
    bed_type VARCHAR(64) NOT NULL,
    is_occupied BOOLEAN NOT NULL DEFAULT FALSE,
    current_patient_id VARCHAR(64) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 3. Doctors Table
CREATE TABLE IF NOT EXISTS doctors (
    doctor_id VARCHAR(32) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    specialization VARCHAR(128) NOT NULL,
    hospital_id VARCHAR(32) NOT NULL,
    is_on_duty BOOLEAN NOT NULL DEFAULT TRUE,
    assigned_patient_id VARCHAR(64) NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 4. Patients Table
CREATE TABLE IF NOT EXISTS patients (
    patient_id VARCHAR(64) PRIMARY KEY,
    name VARCHAR(255) NOT NULL,
    age INT NOT NULL,
    gender VARCHAR(32) NOT NULL,
    severity VARCHAR(32) NOT NULL,
    required_facility VARCHAR(64) NOT NULL,
    arrival_sequence BIGINT NOT NULL,
    status VARCHAR(32) NOT NULL,
    assigned_hospital_id VARCHAR(32) NULL,
    assigned_bed_id VARCHAR(64) NULL,
    assigned_doctor_id VARCHAR(32) NULL,
    referral_notes TEXT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 5. Admissions and Allocations History / Audit Log
CREATE TABLE IF NOT EXISTS admissions (
    admission_id VARCHAR(64) PRIMARY KEY,
    patient_id VARCHAR(64) NOT NULL,
    hospital_id VARCHAR(32) NOT NULL,
    bed_id VARCHAR(64) NOT NULL,
    doctor_id VARCHAR(32) NULL,
    disposition VARCHAR(64) NOT NULL,
    travel_time_minutes INT DEFAULT 0,
    remarks TEXT,
    admitted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    discharged_at TIMESTAMP NULL,
    FOREIGN KEY (hospital_id) REFERENCES hospitals(hospital_id) ON DELETE CASCADE,
    FOREIGN KEY (bed_id) REFERENCES beds(bed_id) ON DELETE CASCADE
) ENGINE=InnoDB;

-- 6. Audit Records Table
CREATE TABLE IF NOT EXISTS audit_records (
    record_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sequence_num BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    patient_id VARCHAR(64) NULL,
    hospital_id VARCHAR(32) NULL,
    bed_id VARCHAR(64) NULL,
    doctor_id VARCHAR(32) NULL,
    details TEXT,
    event_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- 7. Portal accounts. Passwords are stored as salted PBKDF2 hashes by the Java API.
CREATE TABLE IF NOT EXISTS portal_accounts (
    email VARCHAR(254) PRIMARY KEY,
    full_name VARCHAR(255) NOT NULL,
    password_salt VARCHAR(64) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    role VARCHAR(16) NOT NULL,
    patient_id VARCHAR(64) NULL UNIQUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB;

-- -----------------------------------------------------------------------------
-- Initial District Seed Data (Matches SyntheticDataGenerator baseline)
-- -----------------------------------------------------------------------------

INSERT IGNORE INTO hospitals (hospital_id, name, location_id, is_primary) VALUES
('NDGH', 'Nalam District Government Hospital', 'L1', TRUE),
('VSDH', 'Vallam Sub-District Hospital', 'L3', FALSE),
('TCHC', 'Thoppur Community Health Centre', 'L5', FALSE),
('KTH',  'Kadur Taluk Hospital', 'L8', FALSE);

INSERT IGNORE INTO beds (bed_id, hospital_id, bed_number, bed_type, is_occupied) VALUES
('NDGH-ICU-01', 'NDGH', 'ICU-101', 'ICU+VENT', FALSE),
('NDGH-ICU-02', 'NDGH', 'ICU-102', 'ICU+VENT', FALSE),
('NDGH-GEN-01', 'NDGH', 'GEN-201', 'GENERAL+O2', FALSE),
('NDGH-GEN-02', 'NDGH', 'GEN-202', 'GENERAL+O2', FALSE),
('NDGH-GEN-03', 'NDGH', 'GEN-203', 'GENERAL', FALSE),
('NDGH-GEN-04', 'NDGH', 'GEN-204', 'GENERAL', FALSE),
('VSDH-ICU-01', 'VSDH', 'ICU-301', 'ICU+VENT', FALSE),
('VSDH-ICU-02', 'VSDH', 'ICU-302', 'ICU', FALSE),
('VSDH-GEN-01', 'VSDH', 'GEN-301', 'GENERAL+O2', FALSE),
('VSDH-GEN-02', 'VSDH', 'GEN-302', 'GENERAL+O2', FALSE),
('VSDH-GEN-03', 'VSDH', 'GEN-303', 'GENERAL', FALSE),
('TCHC-GEN-01', 'TCHC', 'GEN-501', 'GENERAL+O2', FALSE),
('TCHC-GEN-02', 'TCHC', 'GEN-502', 'GENERAL', FALSE),
('TCHC-GEN-03', 'TCHC', 'GEN-503', 'GENERAL', FALSE),
('KTH-ICU-01',  'KTH',  'ICU-801', 'ICU+VENT', FALSE),
('KTH-ICU-02',  'KTH',  'ICU-802', 'ICU+VENT', FALSE),
('KTH-GEN-01',  'KTH',  'GEN-801', 'GENERAL+O2', FALSE),
('KTH-GEN-02',  'KTH',  'GEN-802', 'GENERAL', FALSE);

INSERT IGNORE INTO doctors (doctor_id, name, specialization, hospital_id, is_on_duty) VALUES
('DOC-01', 'Ramesh Kumar', 'Critical Care', 'NDGH', TRUE),
('DOC-02', 'Priya Sundaram', 'Emergency Medicine', 'NDGH', TRUE),
('DOC-03', 'Anand Varma', 'General Medicine', 'NDGH', TRUE),
('DOC-04', 'Kavitha Nair', 'General Medicine', 'NDGH', TRUE),
('DOC-05', 'Karthik Rajan', 'Critical Care', 'VSDH', TRUE),
('DOC-06', 'Meena Krishnan', 'General Medicine', 'VSDH', TRUE),
('DOC-07', 'Vignesh Balan', 'General Medicine', 'TCHC', TRUE),
('DOC-08', 'Dr. S. Radhakrishnan', 'Critical Care', 'KTH', TRUE);
