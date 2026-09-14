package com.hospital.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Audit history entry stored in an ArrayList to record all system state transitions:
 * admissions, discharges, referrals, and duty updates.
 */
public class AuditRecord {

    public enum EventType {
        ADMISSION,
        DISCHARGE,
        REFERRAL,
        QUEUE_ENQUEUE,
        QUEUE_REORDER,
        DOCTOR_DUTY_CHANGE,
        BED_RELEASED
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final long sequence;
    private final LocalDateTime timestamp;
    private final EventType eventType;
    private final String patientId;
    private final String hospitalId;
    private final String bedId;
    private final String doctorId;
    private final String details;

    public AuditRecord(long sequence, EventType eventType, String patientId,
                       String hospitalId, String bedId, String doctorId, String details) {
        this.sequence = sequence;
        this.timestamp = LocalDateTime.now();
        this.eventType = eventType;
        this.patientId = patientId;
        this.hospitalId = hospitalId;
        this.bedId = bedId;
        this.doctorId = doctorId;
        this.details = details;
    }

    public long getSequence() {
        return sequence;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public EventType getEventType() {
        return eventType;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public String getBedId() {
        return bedId;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public String getDetails() {
        return details;
    }

    @Override
    public String toString() {
        return String.format("#%03d [%s] %-18s | Patient: %-6s | Hospital: %-5s | Bed: %-6s | Doc: %-6s | %s",
                sequence, timestamp.format(TIME_FMT), eventType,
                patientId != null ? patientId : "-",
                hospitalId != null ? hospitalId : "-",
                bedId != null ? bedId : "-",
                doctorId != null ? doctorId : "-",
                details);
    }
}
