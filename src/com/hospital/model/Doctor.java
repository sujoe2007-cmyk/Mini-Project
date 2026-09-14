package com.hospital.model;

import java.util.Objects;

/**
 * Represents a medical officer / doctor on duty in a hospital.
 * Encapsulation:
 * Private duty status and patient assignment states change only through
 * admitPatient(), dischargePatient(), and setDutyStatus().
 */
public class Doctor {

    private final String doctorId;
    private final String name;
    private final String specialization; // e.g., "Critical Care", "General Medicine", "Trauma Care"
    private final String hospitalId;
    private boolean onDuty;
    private String assignedPatientId;

    public Doctor(String doctorId, String name, String specialization, String hospitalId, boolean onDuty) {
        this.doctorId = Objects.requireNonNull(doctorId, "doctorId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.specialization = Objects.requireNonNull(specialization, "specialization cannot be null");
        this.hospitalId = Objects.requireNonNull(hospitalId, "hospitalId cannot be null");
        this.onDuty = onDuty;
        this.assignedPatientId = null;
    }

    public String getDoctorId() {
        return doctorId;
    }

    public String getName() {
        return name;
    }

    public String getSpecialization() {
        return specialization;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public boolean isOnDuty() {
        return onDuty;
    }

    public boolean isAvailable() {
        return onDuty && assignedPatientId == null;
    }

    public String getAssignedPatientId() {
        return assignedPatientId;
    }

    /**
     * Encapsulated method to update duty status.
     * @param onDuty true if reporting for duty, false if signing off
     */
    public void setDutyStatus(boolean onDuty) {
        if (!onDuty && assignedPatientId != null) {
            throw new IllegalStateException("Doctor " + name + " (" + doctorId + ") cannot go off-duty while currently attending patient " + assignedPatientId);
        }
        this.onDuty = onDuty;
    }

    /**
     * Encapsulated method: Assigns a patient to this doctor upon admission.
     * @param patientId ID of the admitted patient
     */
    public void admitPatient(String patientId) {
        if (!this.onDuty) {
            throw new IllegalStateException("Doctor " + name + " is currently OFF-DUTY and cannot admit patients.");
        }
        if (this.assignedPatientId != null) {
            throw new IllegalStateException("Doctor " + name + " is already attending patient " + this.assignedPatientId);
        }
        this.assignedPatientId = Objects.requireNonNull(patientId, "patientId cannot be null");
    }

    /**
     * Encapsulated method: Releases the doctor when the patient is discharged.
     */
    public void dischargePatient() {
        if (this.assignedPatientId == null) {
            throw new IllegalStateException("Doctor " + name + " has no assigned patient to discharge.");
        }
        this.assignedPatientId = null;
    }

    /**
     * Checks if this doctor is clinically appropriate for the patient's severity and facility needs.
     */
    public boolean canTreat(Severity severity, String facilityRequirement) {
        if (!this.onDuty || this.assignedPatientId != null) {
            return false;
        }
        if (severity == Severity.CRITICAL || "ICU".equalsIgnoreCase(facilityRequirement)) {
            // Critical patients require Critical Care or Emergency/Trauma specialist
            return specialization.equalsIgnoreCase("Critical Care") ||
                   specialization.equalsIgnoreCase("Emergency Medicine") ||
                   specialization.equalsIgnoreCase("Trauma Surgery");
        }
        // Serious and Minor can be treated by any available on-duty doctor (General Medicine or Specialist)
        return true;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Doctor doctor)) return false;
        return Objects.equals(doctorId, doctor.doctorId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(doctorId);
    }

    @Override
    public String toString() {
        return String.format("[%s] Dr. %s (%s) @ %s - Duty: %s | Status: %s",
                doctorId, name, specialization, hospitalId,
                onDuty ? "ON-DUTY" : "OFF-DUTY",
                assignedPatientId != null ? "BUSY (Patient: " + assignedPatientId + ")" : "AVAILABLE");
    }
}
