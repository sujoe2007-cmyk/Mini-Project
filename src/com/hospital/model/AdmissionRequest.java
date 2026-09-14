package com.hospital.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

/**
 * Represents an admission or referral request transaction.
 */
public class AdmissionRequest {

    public enum Disposition {
        PENDING,
        ADMITTED_PRIMARY,
        REFERRED_AND_RESERVED,
        QUEUED_NO_BED,
        QUEUED_NO_DOCTOR,
        QUEUED_NO_CAPACITY,
        DISCHARGED
    }

    private final String requestId;
    private final LocalDateTime timestamp;
    private final Patient patient;
    private final String requestedHospitalId;
    private Disposition disposition;
    private String allocatedHospitalId;
    private String allocatedBedId;
    private String allocatedDoctorId;
    private int travelTimeMinutes;
    private String remarks;

    public AdmissionRequest(String requestId, Patient patient, String requestedHospitalId) {
        this.requestId = Objects.requireNonNull(requestId, "requestId cannot be null");
        this.timestamp = LocalDateTime.now();
        this.patient = Objects.requireNonNull(patient, "patient cannot be null");
        this.requestedHospitalId = Objects.requireNonNull(requestedHospitalId, "requestedHospitalId cannot be null");
        this.disposition = Disposition.PENDING;
        this.travelTimeMinutes = 0;
    }

    public String getRequestId() {
        return requestId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Patient getPatient() {
        return patient;
    }

    public String getRequestedHospitalId() {
        return requestedHospitalId;
    }

    public Disposition getDisposition() {
        return disposition;
    }

    public void setDisposition(Disposition disposition) {
        this.disposition = disposition;
    }

    public String getAllocatedHospitalId() {
        return allocatedHospitalId;
    }

    public void setAllocatedHospitalId(String allocatedHospitalId) {
        this.allocatedHospitalId = allocatedHospitalId;
    }

    public String getAllocatedBedId() {
        return allocatedBedId;
    }

    public void setAllocatedBedId(String allocatedBedId) {
        this.allocatedBedId = allocatedBedId;
    }

    public String getAllocatedDoctorId() {
        return allocatedDoctorId;
    }

    public void setAllocatedDoctorId(String allocatedDoctorId) {
        this.allocatedDoctorId = allocatedDoctorId;
    }

    public int getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public void setTravelTimeMinutes(int travelTimeMinutes) {
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public String toString() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        return String.format("[%s @ %s] Patient %s (%s, Req: %s) -> %s | Remarks: %s",
                requestId, timestamp.format(dtf), patient.getName(), patient.getSeverity(),
                patient.getRequiredFacility(), disposition, remarks);
    }
}
