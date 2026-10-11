package com.hospital.model;

import java.util.Objects;

/**
 * Abstract base class representing a hospital bed.
 * Demonstrates:
 * - Abstraction: abstract method supportsRequirement()
 * - Encapsulation: private bed state accessible only through admitPatient() and dischargePatient()
 */
public abstract class Bed {

    private final String bedId;
    private final String hospitalId;
    private final String bedNumber;
    private boolean occupied;
    private String currentPatientId;

    public Bed(String bedId, String hospitalId, String bedNumber) {
        this.bedId = Objects.requireNonNull(bedId, "bedId cannot be null");
        this.hospitalId = Objects.requireNonNull(hospitalId, "hospitalId cannot be null");
        this.bedNumber = Objects.requireNonNull(bedNumber, "bedNumber cannot be null");
        this.occupied = false;
        this.currentPatientId = null;
    }

    public String getBedId() {
        return bedId;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public String getBedNumber() {
        return bedNumber;
    }

    /**
     * Polymorphic requirement check to be overridden by subclasses.
     * @param facilityRequirement the facility requested by patient (e.g., "General", "ICU")
     * @return true if this bed type satisfies the requirement
     */
    public abstract boolean supportsRequirement(String facilityRequirement);

    public abstract String getBedType();

    /**
     * Encapsulated method to allocate this bed to a patient.
     * Throws IllegalStateException if the bed is already occupied.
     */
    public synchronized void admitPatient(String patientId) {
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty.");
        }
        if (this.occupied) {
            throw new IllegalStateException("Bed " + bedId + " is already occupied by patient " + currentPatientId);
        }
        this.occupied = true;
        this.currentPatientId = patientId.trim();
    }

    /**
     * Encapsulated method to discharge the patient and release the bed.
     * Throws IllegalStateException if the bed is not currently occupied.
     */
    public synchronized void dischargePatient() {
        if (!this.occupied) {
            throw new IllegalStateException("Bed " + bedId + " is already vacant.");
        }
        this.occupied = false;
        this.currentPatientId = null;
    }

    public synchronized boolean isOccupied() {
        return occupied;
    }

    public synchronized String getCurrentPatientId() {
        return currentPatientId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Bed bed)) return false;
        return Objects.equals(bedId, bed.bedId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(bedId);
    }

    @Override
    public String toString() {
        return String.format("[%s] Bed %s (%s) @ %s - %s",
                bedId, bedNumber, getBedType(), hospitalId, occupied ? "OCCUPIED (Patient: " + currentPatientId + ")" : "VACANT");
    }
}
