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
        this.patientId = Objects.requireNonNull(patientId, "patientId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.age = age;
        this.gender = gender;
        this.severity = Objects.requireNonNull(severity, "severity cannot be null");
        this.requiredFacility = Objects.requireNonNull(requiredFacility, "requiredFacility cannot be null");
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

    public Severity getSeverity() {
        return severity;
    }

    public void setSeverity(Severity severity) {
        this.severity = Objects.requireNonNull(severity, "severity cannot be null");
    }

    public String getRequiredFacility() {
        return requiredFacility;
    }

    public void setRequiredFacility(String requiredFacility) {
        this.requiredFacility = Objects.requireNonNull(requiredFacility, "requiredFacility cannot be null");
    }

    public long getArrivalSequence() {
        return arrivalSequence;
    }

    public LocalDateTime getArrivalTime() {
        return arrivalTime;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getAssignedHospitalId() {
        return assignedHospitalId;
    }

    public void setAssignedHospitalId(String assignedHospitalId) {
        this.assignedHospitalId = assignedHospitalId;
    }

    public String getAssignedBedId() {
        return assignedBedId;
    }

    public void setAssignedBedId(String assignedBedId) {
        this.assignedBedId = assignedBedId;
    }

    public String getAssignedDoctorId() {
        return assignedDoctorId;
    }

    public void setAssignedDoctorId(String assignedDoctorId) {
        this.assignedDoctorId = assignedDoctorId;
    }

    public String getReferralNotes() {
        return referralNotes;
    }

    public void setReferralNotes(String referralNotes) {
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
        int severityDiff = Integer.compare(this.severity.getRank(), other.severity.getRank());
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
    public String toString() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        return String.format("[%s] %s (Seq: %d, Age: %d, %s) - Urgency: %s, Facility: %s, Status: %s",
                patientId, name, arrivalSequence, age, gender, severity, requiredFacility, status);
    }
}
