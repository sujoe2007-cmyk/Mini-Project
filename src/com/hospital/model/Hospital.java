package com.hospital.model;

import java.util.*;

/**
 * Hospital entity demonstrating Composition:
 * Contains collections of Bed and Doctor objects stored in HashMaps by ID.
 */
public class Hospital {

    private final String hospitalId;
    private final String name;
    private final String locationId;
    private final boolean isPrimary;

    // Composition: Hospital contains Beds and Doctors indexed by ID (HashMap)
    private final Map<String, Bed> beds;
    private final Map<String, Doctor> doctors;

    public Hospital(String hospitalId, String name, String locationId, boolean isPrimary) {
        this.hospitalId = Objects.requireNonNull(hospitalId, "hospitalId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.locationId = Objects.requireNonNull(locationId, "locationId cannot be null");
        this.isPrimary = isPrimary;
        this.beds = new LinkedHashMap<>();
        this.doctors = new LinkedHashMap<>();
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public String getName() {
        return name;
    }

    public String getLocationId() {
        return locationId;
    }

    public boolean isPrimary() {
        return isPrimary;
    }

    public void addBed(Bed bed) {
        beds.put(bed.getBedId(), bed);
    }

    public void addDoctor(Doctor doctor) {
        doctors.put(doctor.getDoctorId(), doctor);
    }

    public Bed getBed(String bedId) {
        return beds.get(bedId);
    }

    public Doctor getDoctor(String doctorId) {
        return doctors.get(doctorId);
    }

    public Collection<Bed> getAllBeds() {
        return Collections.unmodifiableCollection(beds.values());
    }

    public Collection<Doctor> getAllDoctors() {
        return Collections.unmodifiableCollection(doctors.values());
    }

    /**
     * Finds the first available bed satisfying the given facility requirement
     * using polymorphic supportsRequirement().
     */
    /**
     * Finds the first available bed satisfying the given facility requirement
     * using polymorphic supportsRequirement().
     */
    public synchronized Optional<Bed> findAvailableBed(String facilityRequirement) {
        for (Bed bed : beds.values()) {
            if (!bed.isOccupied() && bed.supportsRequirement(facilityRequirement)) {
                return Optional.of(bed);
            }
        }
        return Optional.empty();
    }

    /**
     * Finds an available on-duty doctor who can treat the specified severity and facility requirement.
     */
    public synchronized Optional<Doctor> findAvailableDoctor(Severity severity, String facilityRequirement) {
        for (Doctor doctor : doctors.values()) {
            if (doctor.canTreat(severity, facilityRequirement)) {
                return Optional.of(doctor);
            }
        }
        return Optional.empty();
    }

    /**
     * Reserves bed and doctor together in an atomic admission operation.
     */
    public synchronized void admitPatient(Patient patient, Bed bed, Doctor doctor) {
        Objects.requireNonNull(patient, "patient cannot be null");
        Objects.requireNonNull(bed, "bed cannot be null");

        // Reserve Bed
        bed.admitPatient(patient.getPatientId());

        // Reserve Doctor (if provided/required)
        if (doctor != null) {
            doctor.admitPatient(patient.getPatientId());
            patient.setAssignedDoctorId(doctor.getDoctorId());
        }

        patient.setAssignedHospitalId(this.hospitalId);
        patient.setAssignedBedId(bed.getBedId());
        patient.setStatus(this.isPrimary ? Patient.Status.ADMITTED_LOCAL : Patient.Status.REFERRED);
    }

    /**
     * Discharges a patient, atomically freeing both the bed and the assigned doctor.
     * @return true if patient was found and discharged
     */
    public synchronized boolean dischargePatient(String patientId) {
        if (patientId == null || patientId.trim().isEmpty()) {
            return false;
        }
        Bed targetBed = null;
        for (Bed bed : beds.values()) {
            if (patientId.equals(bed.getCurrentPatientId())) {
                targetBed = bed;
                break;
            }
        }

        if (targetBed == null) {
            return false;
        }

        // Release bed
        targetBed.dischargePatient();

        // Release assigned doctor
        for (Doctor doctor : doctors.values()) {
            if (patientId.equals(doctor.getAssignedPatientId())) {
                doctor.dischargePatient();
                break;
            }
        }
        return true;
    }

    // Capacity Metrics
    public int getTotalBedsCount() {
        return beds.size();
    }

    public long getOccupiedBedsCount() {
        return beds.values().stream().filter(Bed::isOccupied).count();
    }

    public long getAvailableBedsCount() {
        return beds.values().stream().filter(b -> !b.isOccupied()).count();
    }

    public long getAvailableBedsCount(Class<? extends Bed> bedType) {
        return beds.values().stream()
                .filter(b -> !b.isOccupied() && bedType.isInstance(b))
                .count();
    }

    public long getAvailableDoctorCount() {
        return doctors.values().stream().filter(Doctor::isAvailable).count();
    }

    public long getOnDutyDoctorCount() {
        return doctors.values().stream().filter(Doctor::isOnDuty).count();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Hospital hospital)) return false;
        return Objects.equals(hospitalId, hospital.hospitalId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(hospitalId);
    }

    @Override
    public String toString() {
        return String.format("%s (%s) @ %s [%s] - Beds: %d/%d Avail (ICU: %d, Gen: %d) | Doctors: %d/%d Avail",
                name, hospitalId, locationId, isPrimary ? "PRIMARY" : "REFERRAL",
                getAvailableBedsCount(), getTotalBedsCount(),
                getAvailableBedsCount(ICUBed.class), getAvailableBedsCount(GeneralBed.class),
                getAvailableDoctorCount(), getOnDutyDoctorCount());
    }
}
