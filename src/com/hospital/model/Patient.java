package com.hospital.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents a patient arriving at the emergency department.
 * Implements Comparable to maintain deterministic triage ordering:
 * 1. Urgency/Severity (CRITICAL > SERIOUS > MINOR)
 * 2. Arrival Sequence (earlier arrival first)
 * 3. Patient ID (lexicographical tie-break)
 */
public class Patient implements Comparable<Patient> {

    public enum Status {
        WAITING,
        ADMITTED_LOCAL,
        REFERRED,
        DISCHARGED
    }

    private final String patientId;
    private final String name;
    private final int age;
    private final String gender;
    private Severity severity;
    private String requiredFacility; // e.g., "General", "ICU"
    private final long arrivalSequence;
    private final LocalDateTime arrivalTime;

    private Status status;
    private String assignedHospitalId;
    private String assignedBedId;
    private String assignedDoctorId;
    private String referralNotes;

    public Patient(String patientId, String name, int age, String gender,
                   Severity severity, String requiredFacility, long arrivalSequence) {
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient name cannot be null or empty.");
        }
        if (age < 0 || age > 130) {
            throw new IllegalArgumentException("Age must be between 0 and 130 years: " + age);
        }
        if (requiredFacility == null || requiredFacility.trim().isEmpty()) {
            throw new IllegalArgumentException("Required facility cannot be null or empty.");
        }
        this.patientId = patientId.trim();
        this.name = name.trim();
        this.age = age;
        this.gender = (gender != null && !gender.trim().isEmpty()) ? gender.trim() : "Unspecified";
        this.severity = Objects.requireNonNull(severity, "severity cannot be null");
        this.requiredFacility = requiredFacility.trim();
        this.arrivalSequence = arrivalSequence;
        this.arrivalTime = LocalDateTime.now();
        this.status = Status.WAITING;
    }

    // Getters and Setters
    public String getPatientId() {
        return patientId;
    }

    public String getName() {
        return name;
    }

    public int getAge() {
        return age;
    }

    public String getGender() {
        return gender;
    }

    public synchronized Severity getSeverity() {
        return severity;
    }

    public synchronized void setSeverity(Severity severity) {
        this.severity = Objects.requireNonNull(severity, "severity cannot be null");
    }

    public synchronized String getRequiredFacility() {
        return requiredFacility;
    }

    public synchronized void setRequiredFacility(String requiredFacility) {
        if (requiredFacility == null || requiredFacility.trim().isEmpty()) {
            throw new IllegalArgumentException("Required facility cannot be null or empty.");
        }
        this.requiredFacility = requiredFacility.trim();
    }

    public long getArrivalSequence() {
        return arrivalSequence;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public synchronized Status getStatus() {
        return status;
    }

    public synchronized void setStatus(Status status) {
        this.status = Objects.requireNonNull(status, "status cannot be null");
    }

    public synchronized String getAssignedHospitalId() {
        return assignedHospitalId;
    }

    public synchronized void setAssignedHospitalId(String assignedHospitalId) {
        this.assignedHospitalId = assignedHospitalId;
    }

    public synchronized String getAssignedBedId() {
        return assignedBedId;
    }

    public synchronized void setAssignedBedId(String assignedBedId) {
        this.assignedBedId = assignedBedId;
    }

    public synchronized String getAssignedDoctorId() {
        return assignedDoctorId;
    }

    public synchronized void setAssignedDoctorId(String assignedDoctorId) {
        this.assignedDoctorId = assignedDoctorId;
    }

    public synchronized String getReferralNotes() {
        return referralNotes;
    }

    public synchronized void setReferralNotes(String referralNotes) {
        this.referralNotes = referralNotes;
    }

    /**
     * Deterministic comparison:
     * 1. Severity rank ascending (1 = Critical, 2 = Serious, 3 = Minor)
     * 2. Arrival sequence ascending (earlier arrival first)
     * 3. Patient ID ascending (strictly deterministic tie-breaker)
     */
    @Override
    public int compareTo(Patient other) {
        if (this == other) return 0;
        if (other == null) return -1;
        int severityDiff = Integer.compare(this.getSeverity().getRank(), other.getSeverity().getRank());
        if (severityDiff != 0) {
            return severityDiff;
        }
        int seqDiff = Long.compare(this.arrivalSequence, other.arrivalSequence);
        if (seqDiff != 0) {
            return seqDiff;
        }
        return this.patientId.compareTo(other.patientId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Patient patient)) return false;
        return Objects.equals(patientId, patient.patientId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(patientId);
    }

    @Override
    public synchronized String toString() {
        return String.format("[%s] %s (Seq: %d, Age: %d, %s) - Urgency: %s, Facility: %s, Status: %s",
                patientId, name, arrivalSequence, age, gender, severity, requiredFacility, status);
    }
}
