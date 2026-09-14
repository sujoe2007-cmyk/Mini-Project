import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

// =============================================================================
// 1. ENUMS & DOMAIN MODELS
// =============================================================================

/**
 * Severity level assigned by the emergency triage operator.
 * Ordered by clinical urgency: CRITICAL > SERIOUS > MINOR.
 */
enum Severity {
    CRITICAL(1, "Critical", "Immediate life threat; requires urgent resuscitation or ICU care"),
    SERIOUS(2, "Serious", "Severe condition; requires timely acute intervention and monitoring"),
    MINOR(3, "Minor", "Non-life-threatening condition; stable and can wait for standard general bed");

    private final int rank;
    private final String displayName;
    private final String description;

    Severity(int rank, String displayName, String description) {
        this.rank = rank;
        this.displayName = displayName;
        this.description = description;
    }

    public int getRank() {
        return rank;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}

/**
 * Represents a geographical node/location in Nalam District.
 */
class Location {
    private final String locationId;
    private final String name;
    private final String description;

    public Location(String locationId, String name, String description) {
        this.locationId = Objects.requireNonNull(locationId, "locationId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = description;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location location)) return false;
        return Objects.equals(locationId, location.locationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", locationId, name, description != null ? description : "");
    }
}

/**
 * Represents a weighted edge in the road network graph.
 * Weight is road travel time in minutes.
 */
class RoadEdge {
    private final String fromLocationId;
    private final String toLocationId;
    private int travelTimeMinutes;

    public RoadEdge(String fromLocationId, String toLocationId, int travelTimeMinutes) {
        this.fromLocationId = Objects.requireNonNull(fromLocationId, "fromLocationId cannot be null");
        this.toLocationId = Objects.requireNonNull(toLocationId, "toLocationId cannot be null");
        if (travelTimeMinutes < 0) {
            throw new IllegalArgumentException("Travel time cannot be negative: " + travelTimeMinutes);
        }
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public String getFromLocationId() {
        return fromLocationId;
    }

    public String getToLocationId() {
        return toLocationId;
    }

    public int getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public void setTravelTimeMinutes(int travelTimeMinutes) {
        if (travelTimeMinutes < 0) {
            throw new IllegalArgumentException("Travel time cannot be negative: " + travelTimeMinutes);
        }
        this.travelTimeMinutes = travelTimeMinutes;
    }

    @Override
    public String toString() {
        return String.format("%s -> %s (%d min)", fromLocationId, toLocationId, travelTimeMinutes);
    }
}

/**
 * Encapsulates the output of Dijkstra's algorithm:
 * shortest distances and reconstructed shortest paths from the source location.
 */
class DijkstraResult {
    private final String sourceLocationId;
    private final Map<String, Integer> distances;
    private final Map<String, String> predecessors;

    public DijkstraResult(String sourceLocationId, Map<String, Integer> distances, Map<String, String> predecessors) {
        this.sourceLocationId = sourceLocationId;
        this.distances = new HashMap<>(distances);
        this.predecessors = new HashMap<>(predecessors);
    }

    public String getSourceLocationId() {
        return sourceLocationId;
    }

    public boolean hasPathTo(String destinationLocationId) {
        Integer dist = distances.get(destinationLocationId);
        return dist != null && dist < Integer.MAX_VALUE;
    }

    public int getDistanceTo(String destinationLocationId) {
        return distances.getOrDefault(destinationLocationId, Integer.MAX_VALUE);
    }

    public List<String> getPathTo(String destinationLocationId) {
        if (!hasPathTo(destinationLocationId)) {
            return Collections.emptyList();
        }
        LinkedList<String> path = new LinkedList<>();
        String curr = destinationLocationId;
        while (curr != null) {
            path.addFirst(curr);
            if (curr.equals(sourceLocationId)) {
                break;
            }
            curr = predecessors.get(curr);
        }
        return path;
    }

    public Map<String, Integer> getAllDistances() {
        return Collections.unmodifiableMap(distances);
    }
}

/**
 * Abstract base class representing a hospital bed.
 * Demonstrates:
 * - Abstraction: abstract method supportsRequirement()
 * - Encapsulation: private bed state accessible only through admitPatient() and dischargePatient()
 */
abstract class Bed {
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

    public boolean isOccupied() {
        return occupied;
    }

    public String getCurrentPatientId() {
        return currentPatientId;
    }

    public abstract boolean supportsRequirement(String facilityRequirement);

    public abstract String getBedType();

    public void admitPatient(String patientId) {
        if (this.occupied) {
            throw new IllegalStateException("Bed " + bedId + " is already occupied by patient " + currentPatientId);
        }
        this.occupied = true;
        this.currentPatientId = Objects.requireNonNull(patientId, "patientId cannot be null");
    }

    public void dischargePatient() {
        if (!this.occupied) {
            throw new IllegalStateException("Bed " + bedId + " is already vacant.");
        }
        this.occupied = false;
        this.currentPatientId = null;
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

/**
 * General Ward Bed subclass.
 */
class GeneralBed extends Bed {
    private final boolean hasOxygenSupport;

    public GeneralBed(String bedId, String hospitalId, String bedNumber, boolean hasOxygenSupport) {
        super(bedId, hospitalId, bedNumber);
        this.hasOxygenSupport = hasOxygenSupport;
    }

    public boolean isHasOxygenSupport() {
        return hasOxygenSupport;
    }

    @Override
    public String getBedType() {
        return "GENERAL" + (hasOxygenSupport ? "+O2" : "");
    }

    @Override
    public boolean supportsRequirement(String facilityRequirement) {
        if (facilityRequirement == null) return false;
        String req = facilityRequirement.trim().toLowerCase();
        return req.equals("general") || req.equals("ward") || req.equals("observation");
    }
}

/**
 * Intensive Care Unit (ICU) Bed subclass.
 */
class ICUBed extends Bed {
    private final boolean hasVentilator;
    private final boolean hasAdvancedTelemetry;

    public ICUBed(String bedId, String hospitalId, String bedNumber, boolean hasVentilator, boolean hasAdvancedTelemetry) {
        super(bedId, hospitalId, bedNumber);
        this.hasVentilator = hasVentilator;
        this.hasAdvancedTelemetry = hasAdvancedTelemetry;
    }

    public boolean isHasVentilator() {
        return hasVentilator;
    }

    public boolean isHasAdvancedTelemetry() {
        return hasAdvancedTelemetry;
    }

    @Override
    public String getBedType() {
        return "ICU" + (hasVentilator ? "+VENT" : "");
    }

    @Override
    public boolean supportsRequirement(String facilityRequirement) {
        if (facilityRequirement == null) return false;
        String req = facilityRequirement.trim().toLowerCase();
        return req.equals("icu") || req.equals("ventilator") || req.equals("critical care") || req.equals("critical");
    }
}

/**
 * Represents a patient arriving at the emergency department.
 * Implements Comparable to maintain deterministic triage ordering:
 * 1. Urgency/Severity (CRITICAL > SERIOUS > MINOR)
 * 2. Arrival Sequence (earlier arrival first)
 * 3. Patient ID (lexicographical tie-break)
 */
class Patient implements Comparable<Patient> {
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
    private String requiredFacility;
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
        return String.format("[%s] %s (Seq: %d, Age: %d, %s) - Urgency: %s, Facility: %s, Status: %s",
                patientId, name, arrivalSequence, age, gender, severity, requiredFacility, status);
    }
}

/**
 * Represents a medical officer / doctor on duty in a hospital.
 */
class Doctor {
    private final String doctorId;
    private final String name;
    private final String specialization;
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

    public void setDutyStatus(boolean onDuty) {
        if (!onDuty && assignedPatientId != null) {
            throw new IllegalStateException("Doctor " + name + " (" + doctorId + ") cannot go off-duty while currently attending patient " + assignedPatientId);
        }
        this.onDuty = onDuty;
    }

    public void admitPatient(String patientId) {
        if (!this.onDuty) {
            throw new IllegalStateException("Doctor " + name + " is currently OFF-DUTY and cannot admit patients.");
        }
        if (this.assignedPatientId != null) {
            throw new IllegalStateException("Doctor " + name + " is already attending patient " + this.assignedPatientId);
        }
        this.assignedPatientId = Objects.requireNonNull(patientId, "patientId cannot be null");
    }

    public void dischargePatient() {
        if (this.assignedPatientId == null) {
            throw new IllegalStateException("Doctor " + name + " has no assigned patient to discharge.");
        }
        this.assignedPatientId = null;
    }

    public boolean canTreat(Severity severity, String facilityRequirement) {
        if (!this.onDuty || this.assignedPatientId != null) {
            return false;
        }
        if (severity == Severity.CRITICAL || "ICU".equalsIgnoreCase(facilityRequirement)) {
            return specialization.equalsIgnoreCase("Critical Care") ||
                   specialization.equalsIgnoreCase("Emergency Medicine") ||
                   specialization.equalsIgnoreCase("Trauma Surgery");
        }
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

/**
 * Represents an admission or referral request transaction.
 */
class AdmissionRequest {
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

/**
 * Audit history entry stored in an ArrayList to record all system state transitions.
 */
class AuditRecord {
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

/**
 * Hospital entity demonstrating Composition:
 * Contains collections of Bed and Doctor objects stored in HashMaps by ID.
 */
class Hospital {
    private final String hospitalId;
    private final String name;
    private final String locationId;
    private final boolean isPrimary;

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

    public Optional<Bed> findAvailableBed(String facilityRequirement) {
        for (Bed bed : beds.values()) {
            if (!bed.isOccupied() && bed.supportsRequirement(facilityRequirement)) {
                return Optional.of(bed);
            }
        }
        return Optional.empty();
    }

    public Optional<Doctor> findAvailableDoctor(Severity severity, String facilityRequirement) {
        for (Doctor doctor : doctors.values()) {
            if (doctor.canTreat(severity, facilityRequirement)) {
                return Optional.of(doctor);
            }
        }
        return Optional.empty();
    }

    public void admitPatient(Patient patient, Bed bed, Doctor doctor) {
        Objects.requireNonNull(patient, "patient cannot be null");
        Objects.requireNonNull(bed, "bed cannot be null");

        bed.admitPatient(patient.getPatientId());

        if (doctor != null) {
            doctor.admitPatient(patient.getPatientId());
            patient.setAssignedDoctorId(doctor.getDoctorId());
        }

        patient.setAssignedHospitalId(this.hospitalId);
        patient.setAssignedBedId(bed.getBedId());
        patient.setStatus(this.isPrimary ? Patient.Status.ADMITTED_LOCAL : Patient.Status.REFERRED);
    }

    public boolean dischargePatient(String patientId) {
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

        targetBed.dischargePatient();

        for (Doctor doctor : doctors.values()) {
            if (patientId.equals(doctor.getAssignedPatientId())) {
                doctor.dischargePatient();
                break;
            }
        }
        return true;
    }

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

// =============================================================================
// 2. POLICY ABSTRACTION
// =============================================================================

interface AdmissionPolicy {
    class PolicyResult {
        private final boolean eligible;
        private final Bed availableBed;
        private final Doctor availableDoctor;
        private final String reason;

        public PolicyResult(boolean eligible, Bed availableBed, Doctor availableDoctor, String reason) {
            this.eligible = eligible;
            this.availableBed = availableBed;
            this.availableDoctor = availableDoctor;
            this.reason = reason;
        }

        public static PolicyResult approved(Bed bed, Doctor doctor) {
            return new PolicyResult(true, bed, doctor, "Eligible: Bed and Doctor are both available.");
        }

        public static PolicyResult rejectedNoBed(String requiredFacility) {
            return new PolicyResult(false, null, null, "Ineligible: No available bed supporting required facility '" + requiredFacility + "'.");
        }

        public static PolicyResult rejectedNoDoctor(String message) {
            return new PolicyResult(false, null, null, "Ineligible: " + message);
        }

        public boolean isEligible() {
            return eligible;
        }

        public Optional<Bed> getAvailableBed() {
            return Optional.ofNullable(availableBed);
        }

        public Optional<Doctor> getAvailableDoctor() {
            return Optional.ofNullable(availableDoctor);
        }

        public String getReason() {
            return reason;
        }

        @Override
        public String toString() {
            return eligible ? "APPROVED (" + availableBed.getBedId() + ", " + availableDoctor.getDoctorId() + ")" : "REJECTED: " + reason;
        }
    }

    PolicyResult evaluate(Hospital hospital, Patient patient);
}

class StandardAdmissionPolicy implements AdmissionPolicy {
    @Override
    public PolicyResult evaluate(Hospital hospital, Patient patient) {
        if (hospital == null || patient == null) {
            return new PolicyResult(false, null, null, "Hospital or Patient is null");
        }

        Optional<Bed> availableBedOpt = hospital.findAvailableBed(patient.getRequiredFacility());
        if (availableBedOpt.isEmpty()) {
            return PolicyResult.rejectedNoBed(patient.getRequiredFacility());
        }

        Optional<Doctor> availableDoctorOpt = hospital.findAvailableDoctor(patient.getSeverity(), patient.getRequiredFacility());
        if (availableDoctorOpt.isEmpty()) {
            return PolicyResult.rejectedNoDoctor("No on-duty doctor available matching clinical requirements (" +
                    patient.getSeverity() + " / " + patient.getRequiredFacility() + ").");
        }

        return PolicyResult.approved(availableBedOpt.get(), availableDoctorOpt.get());
    }
}

// =============================================================================
// 3. GRAPH ROUTING (DIJKSTRA + MIN-HEAP)
// =============================================================================

class RoadGraph {
    private final Map<String, Location> locations;
    private final Map<String, List<RoadEdge>> adjacencyList;

    public RoadGraph() {
        this.locations = new LinkedHashMap<>();
        this.adjacencyList = new LinkedHashMap<>();
    }

    public void addLocation(Location location) {
        locations.put(location.getLocationId(), location);
        adjacencyList.putIfAbsent(location.getLocationId(), new ArrayList<>());
    }

    public void addRoad(String locationA, String locationB, int travelTimeMinutes) {
        if (!locations.containsKey(locationA) || !locations.containsKey(locationB)) {
            throw new IllegalArgumentException("Both locations must exist in graph: " + locationA + ", " + locationB);
        }
        adjacencyList.get(locationA).add(new RoadEdge(locationA, locationB, travelTimeMinutes));
        adjacencyList.get(locationB).add(new RoadEdge(locationB, locationA, travelTimeMinutes));
    }

    public boolean updateTravelTime(String locationA, String locationB, int newTravelTimeMinutes) {
        boolean updated = false;
        List<RoadEdge> edgesA = adjacencyList.get(locationA);
        if (edgesA != null) {
            for (RoadEdge edge : edgesA) {
                if (edge.getToLocationId().equals(locationB)) {
                    edge.setTravelTimeMinutes(newTravelTimeMinutes);
                    updated = true;
                }
            }
        }

        List<RoadEdge> edgesB = adjacencyList.get(locationB);
        if (edgesB != null) {
            for (RoadEdge edge : edgesB) {
                if (edge.getToLocationId().equals(locationA)) {
                    edge.setTravelTimeMinutes(newTravelTimeMinutes);
                    updated = true;
                }
            }
        }
        return updated;
    }

    public Location getLocation(String locationId) {
        return locations.get(locationId);
    }

    public Collection<Location> getAllLocations() {
        return Collections.unmodifiableCollection(locations.values());
    }

    public List<RoadEdge> getEdgesFrom(String locationId) {
        return adjacencyList.getOrDefault(locationId, Collections.emptyList());
    }

    public Map<String, List<RoadEdge>> getAdjacencyList() {
        return Collections.unmodifiableMap(adjacencyList);
    }

    public boolean containsLocation(String locationId) {
        return locations.containsKey(locationId);
    }
}

class RoutePlanner {
    private static class HeapEntry implements Comparable<HeapEntry> {
        private final String locationId;
        private final int distance;

        public HeapEntry(String locationId, int distance) {
            this.locationId = locationId;
            this.distance = distance;
        }

        public String getLocationId() {
            return locationId;
        }

        public int getDistance() {
            return distance;
        }

        @Override
        public int compareTo(HeapEntry other) {
            int cmp = Integer.compare(this.distance, other.distance);
            if (cmp != 0) {
                return cmp;
            }
            return this.locationId.compareTo(other.locationId);
        }
    }

    public DijkstraResult calculateShortestPaths(RoadGraph graph, String sourceLocationId) {
        Objects.requireNonNull(graph, "graph cannot be null");
        Objects.requireNonNull(sourceLocationId, "sourceLocationId cannot be null");

        if (!graph.containsLocation(sourceLocationId)) {
            throw new IllegalArgumentException("Source location does not exist in graph: " + sourceLocationId);
        }

        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();

        for (Location loc : graph.getAllLocations()) {
            distances.put(loc.getLocationId(), Integer.MAX_VALUE);
        }
        distances.put(sourceLocationId, 0);

        PriorityQueue<HeapEntry> minHeap = new PriorityQueue<>();
        minHeap.offer(new HeapEntry(sourceLocationId, 0));

        Set<String> visited = new HashSet<>();

        while (!minHeap.isEmpty()) {
            HeapEntry current = minHeap.poll();
            String u = current.getLocationId();
            int currentDist = current.getDistance();

            if (currentDist > distances.get(u)) {
                continue;
            }

            visited.add(u);

            for (RoadEdge edge : graph.getEdgesFrom(u)) {
                String v = edge.getToLocationId();
                int weight = edge.getTravelTimeMinutes();

                if (distances.get(u) != Integer.MAX_VALUE) {
                    int newDist = distances.get(u) + weight;
                    if (newDist < distances.get(v)) {
                        distances.put(v, newDist);
                        predecessors.put(v, u);
                        minHeap.offer(new HeapEntry(v, newDist));
                    }
                }
            }
        }

        return new DijkstraResult(sourceLocationId, distances, predecessors);
    }
}

// =============================================================================
// 4. COORDINATION SERVICES & SYSTEM MANAGER
// =============================================================================

class ReferralService {
    public static class ReferralResult {
        private final boolean successful;
        private final Hospital referralHospital;
        private final Bed reservedBed;
        private final Doctor assignedDoctor;
        private final int travelTimeMinutes;
        private final List<String> routePath;
        private final String message;

        public ReferralResult(boolean successful, Hospital referralHospital, Bed reservedBed,
                              Doctor assignedDoctor, int travelTimeMinutes, List<String> routePath, String message) {
            this.successful = successful;
            this.referralHospital = referralHospital;
            this.reservedBed = reservedBed;
            this.assignedDoctor = assignedDoctor;
            this.travelTimeMinutes = travelTimeMinutes;
            this.routePath = routePath != null ? routePath : Collections.emptyList();
            this.message = message;
        }

        public static ReferralResult failure(String reason) {
            return new ReferralResult(false, null, null, null, -1, null, reason);
        }

        public boolean isSuccessful() {
            return successful;
        }

        public Hospital getReferralHospital() {
            return referralHospital;
        }

        public Bed getReservedBed() {
            return reservedBed;
        }

        public Doctor getAssignedDoctor() {
            return assignedDoctor;
        }

        public int getTravelTimeMinutes() {
            return travelTimeMinutes;
        }

        public List<String> getRoutePath() {
            return routePath;
        }

        public String getMessage() {
            return message;
        }

        @Override
        public String toString() {
            if (!successful) {
                return "REFERRAL FAILED: " + message;
            }
            return String.format("REFERRAL SUCCESS: Referred to %s (%s) | Reserved Bed: %s | Travel Time: %d mins | Route: %s",
                    referralHospital.getName(), referralHospital.getHospitalId(),
                    reservedBed.getBedId(), travelTimeMinutes, String.join(" -> ", routePath));
        }
    }

    private final RoutePlanner routePlanner;
    private final RoadGraph roadGraph;

    public ReferralService(RoutePlanner routePlanner, RoadGraph roadGraph) {
        this.routePlanner = Objects.requireNonNull(routePlanner, "routePlanner cannot be null");
        this.roadGraph = Objects.requireNonNull(roadGraph, "roadGraph cannot be null");
    }

    public ReferralResult referPatient(Hospital primaryHospital,
                                       Collection<Hospital> allHospitals,
                                       Patient patient) {
        Objects.requireNonNull(primaryHospital, "primaryHospital cannot be null");
        Objects.requireNonNull(patient, "patient cannot be null");

        List<Hospital> eligibleCandidates = new ArrayList<>();
        Map<String, Bed> candidateBeds = new HashMap<>();
        Map<String, Doctor> candidateDoctors = new HashMap<>();

        for (Hospital h : allHospitals) {
            if (h.getHospitalId().equals(primaryHospital.getHospitalId())) {
                continue;
            }
            Optional<Bed> bedOpt = h.findAvailableBed(patient.getRequiredFacility());
            if (bedOpt.isPresent()) {
                Optional<Doctor> docOpt = h.findAvailableDoctor(patient.getSeverity(), patient.getRequiredFacility());
                if (docOpt.isPresent()) {
                    eligibleCandidates.add(h);
                    candidateBeds.put(h.getHospitalId(), bedOpt.get());
                    candidateDoctors.put(h.getHospitalId(), docOpt.get());
                }
            }
        }

        if (eligibleCandidates.isEmpty()) {
            return ReferralResult.failure("No referral hospital has both an available " +
                    patient.getRequiredFacility() + " bed and an available doctor.");
        }

        DijkstraResult dijkstraResult = routePlanner.calculateShortestPaths(roadGraph, primaryHospital.getLocationId());

        Hospital fastestHospital = null;
        int minTravelTime = Integer.MAX_VALUE;

        for (Hospital candidate : eligibleCandidates) {
            int travelTime = dijkstraResult.getDistanceTo(candidate.getLocationId());
            if (travelTime < minTravelTime) {
                minTravelTime = travelTime;
                fastestHospital = candidate;
            } else if (travelTime == minTravelTime && fastestHospital != null) {
                if (candidate.getHospitalId().compareTo(fastestHospital.getHospitalId()) < 0) {
                    fastestHospital = candidate;
                }
            }
        }

        if (fastestHospital == null || minTravelTime == Integer.MAX_VALUE) {
            return ReferralResult.failure("No reachable route found to any eligible referral hospital.");
        }

        Bed chosenBed = candidateBeds.get(fastestHospital.getHospitalId());
        Doctor chosenDoctor = candidateDoctors.get(fastestHospital.getHospitalId());

        fastestHospital.admitPatient(patient, chosenBed, chosenDoctor);

        List<String> route = dijkstraResult.getPathTo(fastestHospital.getLocationId());
        String notes = String.format("Referred from %s to %s via %s (%d mins). Bed: %s",
                primaryHospital.getHospitalId(), fastestHospital.getHospitalId(),
                String.join("->", route), minTravelTime, chosenBed.getBedId());
        patient.setReferralNotes(notes);

        return new ReferralResult(true, fastestHospital, chosenBed, chosenDoctor, minTravelTime, route,
                "Successfully referred and reserved bed at " + fastestHospital.getName());
    }
}

class HospitalSystemManager {
    private final PriorityQueue<Patient> waitingQueue;
    private final Map<String, Patient> allPatients;
    private final Map<String, Hospital> hospitals;
    private final List<AuditRecord> auditHistory;

    private final AdmissionPolicy admissionPolicy;
    private final ReferralService referralService;
    private final RoadGraph roadGraph;
    private final Hospital primaryHospital;

    private final AtomicLong patientSeqCounter = new AtomicLong(1);
    private final AtomicLong auditSeqCounter = new AtomicLong(1);

    public HospitalSystemManager(Hospital primaryHospital,
                                 List<Hospital> referralHospitals,
                                 RoadGraph roadGraph) {
        this.waitingQueue = new PriorityQueue<>();
        this.allPatients = new LinkedHashMap<>();
        this.hospitals = new LinkedHashMap<>();
        this.auditHistory = new ArrayList<>();

        this.roadGraph = Objects.requireNonNull(roadGraph, "roadGraph cannot be null");
        this.primaryHospital = Objects.requireNonNull(primaryHospital, "primaryHospital cannot be null");

        this.hospitals.put(primaryHospital.getHospitalId(), primaryHospital);

        if (referralHospitals != null) {
            for (Hospital h : referralHospitals) {
                this.hospitals.put(h.getHospitalId(), h);
            }
        }

        this.admissionPolicy = new StandardAdmissionPolicy();
        RoutePlanner routePlanner = new RoutePlanner();
        this.referralService = new ReferralService(routePlanner, this.roadGraph);
    }

    public Patient registerPatient(String patientId, String name, int age, String gender,
                                   Severity severity, String requiredFacility) {
        long seq = patientSeqCounter.getAndIncrement();
        Patient patient = new Patient(patientId, name, age, gender, severity, requiredFacility, seq);
        allPatients.put(patientId, patient);
        waitingQueue.offer(patient);

        logAudit(AuditRecord.EventType.QUEUE_ENQUEUE, patientId, primaryHospital.getHospitalId(),
                null, null, String.format("Registered and enqueued: Urgency=%s, ReqFacility=%s (Seq #%d)",
                        severity, requiredFacility, seq));
        return patient;
    }

    public boolean updatePatientSeverity(String patientId, Severity newSeverity) {
        Patient patient = allPatients.get(patientId);
        if (patient == null) {
            return false;
        }

        if (waitingQueue.contains(patient)) {
            waitingQueue.remove(patient);
            Severity old = patient.getSeverity();
            patient.setSeverity(newSeverity);
            waitingQueue.offer(patient);

            logAudit(AuditRecord.EventType.QUEUE_REORDER, patientId, primaryHospital.getHospitalId(),
                    null, null, String.format("Severity updated from %s to %s; re-queued with updated priority",
                            old, newSeverity));
            return true;
        } else {
            patient.setSeverity(newSeverity);
            return true;
        }
    }

    public List<AdmissionRequest> processWaitingQueue() {
        List<AdmissionRequest> results = new ArrayList<>();
        List<Patient> unservedPatients = new ArrayList<>();
        int reqIdCounter = 1;

        while (!waitingQueue.isEmpty()) {
            Patient patient = waitingQueue.poll();
            String reqId = "REQ-" + System.currentTimeMillis() + "-" + (reqIdCounter++);
            AdmissionRequest request = new AdmissionRequest(reqId, patient, primaryHospital.getHospitalId());

            AdmissionPolicy.PolicyResult policyResult = admissionPolicy.evaluate(primaryHospital, patient);

            if (policyResult.isEligible()) {
                Bed bed = policyResult.getAvailableBed().orElseThrow();
                Doctor doc = policyResult.getAvailableDoctor().orElseThrow();

                primaryHospital.admitPatient(patient, bed, doc);

                request.setDisposition(AdmissionRequest.Disposition.ADMITTED_PRIMARY);
                request.setAllocatedHospitalId(primaryHospital.getHospitalId());
                request.setAllocatedBedId(bed.getBedId());
                request.setAllocatedDoctorId(doc.getDoctorId());
                request.setRemarks("Admitted locally at " + primaryHospital.getName() + " -> Bed: " + bed.getBedId() + ", Doctor: Dr. " + doc.getName());

                logAudit(AuditRecord.EventType.ADMISSION, patient.getPatientId(), primaryHospital.getHospitalId(),
                        bed.getBedId(), doc.getDoctorId(), "Admitted locally to " + bed.getBedType() + " bed");
                results.add(request);
            } else {
                ReferralService.ReferralResult referralResult = referralService.referPatient(
                        primaryHospital, hospitals.values(), patient);

                if (referralResult.isSuccessful()) {
                    request.setDisposition(AdmissionRequest.Disposition.REFERRED_AND_RESERVED);
                    request.setAllocatedHospitalId(referralResult.getReferralHospital().getHospitalId());
                    request.setAllocatedBedId(referralResult.getReservedBed().getBedId());
                    if (referralResult.getAssignedDoctor() != null) {
                        request.setAllocatedDoctorId(referralResult.getAssignedDoctor().getDoctorId());
                    }
                    request.setTravelTimeMinutes(referralResult.getTravelTimeMinutes());
                    request.setRemarks(String.format("Referred to %s (%d mins travel via Dijkstra). Bed reserved: %s",
                            referralResult.getReferralHospital().getName(),
                            referralResult.getTravelTimeMinutes(),
                            referralResult.getReservedBed().getBedId()));

                    logAudit(AuditRecord.EventType.REFERRAL, patient.getPatientId(),
                            referralResult.getReferralHospital().getHospitalId(),
                            referralResult.getReservedBed().getBedId(),
                            referralResult.getAssignedDoctor() != null ? referralResult.getAssignedDoctor().getDoctorId() : null,
                            String.format("Referred via Dijkstra (%d min travel time). Path: %s",
                                    referralResult.getTravelTimeMinutes(),
                                    String.join("->", referralResult.getRoutePath())));
                    results.add(request);
                } else {
                    request.setDisposition(policyResult.getReason().contains("bed")
                            ? AdmissionRequest.Disposition.QUEUED_NO_BED
                            : AdmissionRequest.Disposition.QUEUED_NO_DOCTOR);
                    request.setRemarks("Admission/Referral deferred: " + policyResult.getReason() + " | Referral error: " + referralResult.getMessage());
                    unservedPatients.add(patient);
                    results.add(request);
                }
            }
        }

        for (Patient unserved : unservedPatients) {
            waitingQueue.offer(unserved);
        }

        return results;
    }

    public boolean dischargePatient(String hospitalId, String patientId) {
        Hospital hospital = hospitals.get(hospitalId);
        if (hospital == null) {
            return false;
        }

        Patient patient = allPatients.get(patientId);
        String bedId = (patient != null) ? patient.getAssignedBedId() : null;
        String docId = (patient != null) ? patient.getAssignedDoctorId() : null;

        boolean discharged = hospital.dischargePatient(patientId);
        if (discharged) {
            if (patient != null) {
                patient.setStatus(Patient.Status.DISCHARGED);
                patient.setAssignedBedId(null);
                patient.setAssignedDoctorId(null);
            }

            logAudit(AuditRecord.EventType.DISCHARGE, patientId, hospitalId, bedId, docId,
                    "Patient " + patientId + " discharged from " + hospital.getName() + "; Bed and Doctor released.");

            processWaitingQueue();
            return true;
        }
        return false;
    }

    public boolean setDoctorDutyStatus(String hospitalId, String doctorId, boolean onDuty) {
        Hospital hospital = hospitals.get(hospitalId);
        if (hospital == null) return false;
        Doctor doctor = hospital.getDoctor(doctorId);
        if (doctor == null) return false;

        doctor.setDutyStatus(onDuty);
        logAudit(AuditRecord.EventType.DOCTOR_DUTY_CHANGE, null, hospitalId, null, doctorId,
                "Dr. " + doctor.getName() + " duty status set to " + (onDuty ? "ON-DUTY" : "OFF-DUTY"));

        if (onDuty) {
            processWaitingQueue();
        }
        return true;
    }

    public boolean updateTravelTime(String locationA, String locationB, int newTravelTimeMinutes) {
        return roadGraph.updateTravelTime(locationA, locationB, newTravelTimeMinutes);
    }

    private void logAudit(AuditRecord.EventType type, String patientId, String hospitalId,
                          String bedId, String doctorId, String details) {
        auditHistory.add(new AuditRecord(auditSeqCounter.getAndIncrement(), type,
                patientId, hospitalId, bedId, doctorId, details));
    }

    public PriorityQueue<Patient> getWaitingQueue() {
        return waitingQueue;
    }

    public List<Patient> getWaitingListSnapshot() {
        List<Patient> snapshot = new ArrayList<>(waitingQueue);
        Collections.sort(snapshot);
        return snapshot;
    }

    public Map<String, Patient> getAllPatients() {
        return Collections.unmodifiableMap(allPatients);
    }

    public Patient getPatient(String patientId) {
        return allPatients.get(patientId);
    }

    public Hospital getHospital(String hospitalId) {
        return hospitals.get(hospitalId);
    }

    public Collection<Hospital> getAllHospitals() {
        return Collections.unmodifiableCollection(hospitals.values());
    }

    public Hospital getPrimaryHospital() {
        return primaryHospital;
    }

    public List<AuditRecord> getAuditHistory() {
        return Collections.unmodifiableList(auditHistory);
    }

    public RoadGraph getRoadGraph() {
        return roadGraph;
    }
}

// =============================================================================
// 5. SYNTHETIC DATA GENERATOR
// =============================================================================

class SyntheticDataGenerator {
    public static RoadGraph buildNalamRoadNetwork() {
        RoadGraph graph = new RoadGraph();

        graph.addLocation(new Location("L1", "Nalam Central", "District HQ & NDGH Location"));
        graph.addLocation(new Location("L2", "North Nalam Junction", "Transit Hub"));
        graph.addLocation(new Location("L3", "Vallam Cross", "Sub-district & VSDH Location"));
        graph.addLocation(new Location("L4", "East Highway Circle", "State Highway Connector"));
        graph.addLocation(new Location("L5", "Thoppur Rural", "Agricultural Belt & TCHC Location"));
        graph.addLocation(new Location("L6", "South Bridge", "River Crossing Point"));
        graph.addLocation(new Location("L7", "South Industrial Area", "Industrial Zone Corridor"));
        graph.addLocation(new Location("L8", "Kadur Hills", "Hill Station & KTH Location"));
        graph.addLocation(new Location("L9", "West Bypass", "Ring Road Highway"));

        graph.addRoad("L1", "L2", 8);
        graph.addRoad("L1", "L4", 12);
        graph.addRoad("L1", "L6", 10);
        graph.addRoad("L1", "L9", 15);

        graph.addRoad("L2", "L3", 6);
        graph.addRoad("L2", "L9", 11);

        graph.addRoad("L4", "L5", 7);
        graph.addRoad("L4", "L3", 14);

        graph.addRoad("L6", "L7", 9);
        graph.addRoad("L7", "L8", 11);
        graph.addRoad("L9", "L8", 22);

        return graph;
    }

    public static Hospital createPrimaryHospital() {
        Hospital ndgh = new Hospital("NDGH", "Nalam District Government Hospital", "L1", true);

        ndgh.addBed(new ICUBed("NDGH-ICU-01", "NDGH", "ICU-101", true, true));
        ndgh.addBed(new ICUBed("NDGH-ICU-02", "NDGH", "ICU-102", true, true));
        ndgh.addBed(new GeneralBed("NDGH-GEN-01", "NDGH", "GEN-201", true));
        ndgh.addBed(new GeneralBed("NDGH-GEN-02", "NDGH", "GEN-202", true));
        ndgh.addBed(new GeneralBed("NDGH-GEN-03", "NDGH", "GEN-203", false));
        ndgh.addBed(new GeneralBed("NDGH-GEN-04", "NDGH", "GEN-204", false));

        ndgh.addDoctor(new Doctor("DOC-01", "Ramesh Kumar", "Critical Care", "NDGH", true));
        ndgh.addDoctor(new Doctor("DOC-02", "Priya Sundaram", "Emergency Medicine", "NDGH", true));
        ndgh.addDoctor(new Doctor("DOC-03", "Anand Varma", "General Medicine", "NDGH", true));
        ndgh.addDoctor(new Doctor("DOC-04", "Kavitha Nair", "General Medicine", "NDGH", true));

        return ndgh;
    }

    public static List<Hospital> createReferralHospitals() {
        List<Hospital> referrals = new ArrayList<>();

        Hospital vsdh = new Hospital("VSDH", "Vallam Sub-District Hospital", "L3", false);
        vsdh.addBed(new ICUBed("VSDH-ICU-01", "VSDH", "ICU-301", true, true));
        vsdh.addBed(new ICUBed("VSDH-ICU-02", "VSDH", "ICU-302", false, true));
        vsdh.addBed(new GeneralBed("VSDH-GEN-01", "VSDH", "GEN-301", true));
        vsdh.addBed(new GeneralBed("VSDH-GEN-02", "VSDH", "GEN-302", true));
        vsdh.addBed(new GeneralBed("VSDH-GEN-03", "VSDH", "GEN-303", false));
        vsdh.addDoctor(new Doctor("DOC-05", "Karthik Rajan", "Critical Care", "VSDH", true));
        vsdh.addDoctor(new Doctor("DOC-06", "Meena Krishnan", "General Medicine", "VSDH", true));
        referrals.add(vsdh);

        Hospital tchc = new Hospital("TCHC", "Thoppur Community Health Centre", "L5", false);
        tchc.addBed(new GeneralBed("TCHC-GEN-01", "TCHC", "GEN-501", true));
        tchc.addBed(new GeneralBed("TCHC-GEN-02", "TCHC", "GEN-502", false));
        tchc.addBed(new GeneralBed("TCHC-GEN-03", "TCHC", "GEN-503", false));
        tchc.addDoctor(new Doctor("DOC-07", "Vignesh Balan", "General Medicine", "TCHC", true));
        referrals.add(tchc);

        Hospital kth = new Hospital("KTH", "Kadur Taluk Hospital", "L8", false);
        kth.addBed(new ICUBed("KTH-ICU-01", "KTH", "ICU-801", true, true));
        kth.addBed(new ICUBed("KTH-ICU-02", "KTH", "ICU-802", true, false));
        kth.addBed(new GeneralBed("KTH-GEN-01", "KTH", "GEN-801", true));
        kth.addBed(new GeneralBed("KTH-GEN-02", "KTH", "GEN-802", false));
        kth.addDoctor(new Doctor("DOC-08", "Shalini Devi", "Emergency Medicine", "KTH", true));
        referrals.add(kth);

        return referrals;
    }

    public static HospitalSystemManager initializeSystem() {
        RoadGraph graph = buildNalamRoadNetwork();
        Hospital primary = createPrimaryHospital();
        List<Hospital> referrals = createReferralHospitals();
        return new HospitalSystemManager(primary, referrals, graph);
    }

    public static List<SyntheticPatientRecord> getSyntheticPatientBatch() {
        List<SyntheticPatientRecord> list = new ArrayList<>();
        list.add(new SyntheticPatientRecord("P-101", "Arun Kumar", 58, "Male", Severity.CRITICAL, "ICU", "Acute Myocardial Infarction / Cardiogenic Shock"));
        list.add(new SyntheticPatientRecord("P-102", "Deepa Mohan", 34, "Female", Severity.SERIOUS, "General", "Post-partum hemorrhage / Severe Anemia"));
        list.add(new SyntheticPatientRecord("P-103", "Murugan Vel", 42, "Male", Severity.MINOR, "General", "Laceration forearm / Suturing required"));
        list.add(new SyntheticPatientRecord("P-104", "Fatima Beevi", 66, "Female", Severity.CRITICAL, "ICU", "Acute Respiratory Failure / ARDS"));
        list.add(new SyntheticPatientRecord("P-105", "Suresh Gopinath", 29, "Male", Severity.SERIOUS, "General", "Road accident / Closed tibia fracture"));
        list.add(new SyntheticPatientRecord("P-106", "Lakshmi Ammal", 71, "Female", Severity.CRITICAL, "ICU", "Severe Sepsis / Septic Shock"));
        list.add(new SyntheticPatientRecord("P-107", "Kavita Sethi", 25, "Female", Severity.MINOR, "General", "Dehydration due to acute gastroenteritis"));
        list.add(new SyntheticPatientRecord("P-108", "Rajesh Khanna", 50, "Male", Severity.SERIOUS, "General", "High grade fever with thrombocytopenia (Dengue)"));
        list.add(new SyntheticPatientRecord("P-109", "Selvamani P.", 63, "Male", Severity.CRITICAL, "ICU", "Cerebrovascular Stroke with coma"));
        list.add(new SyntheticPatientRecord("P-110", "Amina Khatun", 45, "Female", Severity.MINOR, "General", "Mild asthma bronchospasm"));
        list.add(new SyntheticPatientRecord("P-111", "Venkatesh Rao", 52, "Male", Severity.SERIOUS, "General", "Acute pancreatitis"));
        list.add(new SyntheticPatientRecord("P-112", "Chitra Devi", 38, "Female", Severity.CRITICAL, "ICU", "Status epilepticus"));
        list.add(new SyntheticPatientRecord("P-113", "Mani Kandan", 19, "Male", Severity.MINOR, "General", "Second degree minor scald burn"));
        list.add(new SyntheticPatientRecord("P-114", "Sundar Raj", 61, "Male", Severity.SERIOUS, "General", "Bilateral pneumonia requiring high-flow O2"));
        list.add(new SyntheticPatientRecord("P-115", "Shanti Ramasamy", 55, "Female", Severity.CRITICAL, "ICU", "Severe polytrauma with tension pneumothorax"));
        list.add(new SyntheticPatientRecord("P-116", "Praveen Joshua", 31, "Male", Severity.MINOR, "General", "Soft tissue contusion right ankle"));
        list.add(new SyntheticPatientRecord("P-117", "Banumathi K.", 48, "Female", Severity.SERIOUS, "General", "Severe diabetic ketoacidosis"));
        list.add(new SyntheticPatientRecord("P-118", "Dinesh Babu", 36, "Male", Severity.MINOR, "General", "Superficial laceration forehead"));
        list.add(new SyntheticPatientRecord("P-119", "Radhika Narayanan", 67, "Female", Severity.CRITICAL, "ICU", "Pulmonary embolism"));
        list.add(new SyntheticPatientRecord("P-120", "Abdul Kareem", 53, "Male", Severity.SERIOUS, "General", "Acute appendicitis with localized peritonitis"));
        list.add(new SyntheticPatientRecord("P-121", "Nalini Sekar", 27, "Female", Severity.MINOR, "General", "Renal colic / ureteric calculus"));
        list.add(new SyntheticPatientRecord("P-122", "Ravi Shankar", 44, "Male", Severity.SERIOUS, "General", "Blunt abdominal trauma under observation"));
        return list;
    }

    public static class SyntheticPatientRecord {
        public final String id;
        public final String name;
        public final int age;
        public final String gender;
        public final Severity severity;
        public final String requiredFacility;
        public final String complaint;

        public SyntheticPatientRecord(String id, String name, int age, String gender,
                                      Severity severity, String requiredFacility, String complaint) {
            this.id = id;
            this.name = name;
            this.age = age;
            this.gender = gender;
            this.severity = severity;
            this.requiredFacility = requiredFacility;
            this.complaint = complaint;
        }
    }
}

// =============================================================================
// 6. AUTOMATED TEST SUITE RUNNER
// =============================================================================

class TestRunner {
    private static int passedTests = 0;
    private static int totalTests = 0;

    public static void main(String[] args) {
        passedTests = 0;
        totalTests = 0;

        System.out.println("================================================================================");
        System.out.println("   GOVERNMENT HOSPITAL RESOURCE ALLOCATION - AUTOMATED TEST SUITE   ");
        System.out.println("================================================================================");

        runTestCase1_NoICUBed_TriggersReferral();
        runTestCase2_NoMatchingDoctor_BlocksAdmission();
        runTestCase3_SimultaneousDemand_PrioritizesSeverityFirst();
        runTestCase4_DischargeEnablesWaitingAdmission();
        runTestCase5_PriorityTiesAndDynamicReranking();

        System.out.println("\n================================================================================");
        System.out.printf("   TEST SUITE SUMMARY: %d / %d TESTS PASSED (%.1f%% Success Rate)\n",
                passedTests, totalTests, (passedTests * 100.0 / totalTests));
        System.out.println("================================================================================");

        if (passedTests != totalTests) {
            System.exit(1);
        }
    }

    private static void assertTrue(String testName, boolean condition, String details) {
        totalTests++;
        if (condition) {
            passedTests++;
            System.out.printf("  [PASS] %s: %s\n", testName, details);
        } else {
            System.err.printf("  [FAIL] %s: %s\n", testName, details);
        }
    }

    private static void runTestCase1_NoICUBed_TriggersReferral() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 1: No ICU Bed at Primary Hospital -> Dijkstra Shortest-Path Referral");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();
        Hospital primary = manager.getPrimaryHospital();

        Bed icu1 = primary.getBed("NDGH-ICU-01");
        Bed icu2 = primary.getBed("NDGH-ICU-02");
        Doctor doc1 = primary.getDoctor("DOC-01");
        Doctor doc2 = primary.getDoctor("DOC-02");

        Patient existing1 = new Patient("EXT-01", "Existing ICU Pt 1", 60, "M", Severity.CRITICAL, "ICU", 1);
        Patient existing2 = new Patient("EXT-02", "Existing ICU Pt 2", 65, "F", Severity.CRITICAL, "ICU", 2);
        primary.admitPatient(existing1, icu1, doc1);
        primary.admitPatient(existing2, icu2, doc2);

        assertTrue("TC1.1", primary.getAvailableBedsCount(ICUBed.class) == 0,
                "Primary hospital NDGH has 0 available ICU beds.");

        Patient criticalPt = manager.registerPatient("TC1-P01", "Karthik Subramanian", 54, "M",
                Severity.CRITICAL, "ICU");

        List<AdmissionRequest> results = manager.processWaitingQueue();
        AdmissionRequest req = results.get(0);

        assertTrue("TC1.2", req.getDisposition() == AdmissionRequest.Disposition.REFERRED_AND_RESERVED,
                "Patient disposition is REFERRED_AND_RESERVED.");
        assertTrue("TC1.3", "VSDH".equals(req.getAllocatedHospitalId()),
                "Fastest reachable hospital selected is VSDH (Vallam Sub-District Hospital).");
        assertTrue("TC1.4", req.getTravelTimeMinutes() == 14,
                "Shortest travel time calculated by Dijkstra is 14 minutes.");
        assertTrue("TC1.5", criticalPt.getStatus() == Patient.Status.REFERRED,
                "Patient status transitioned to REFERRED.");

        Hospital vsdh = manager.getHospital("VSDH");
        Bed reservedBed = vsdh.getBed(req.getAllocatedBedId());
        assertTrue("TC1.6", reservedBed.isOccupied() && criticalPt.getPatientId().equals(reservedBed.getCurrentPatientId()),
                "Referral bed " + reservedBed.getBedId() + " was reserved immediately before confirmation.");
    }

    private static void runTestCase2_NoMatchingDoctor_BlocksAdmission() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 2: No Matching Doctor -> Admission Blocked & Queued; Restores on Duty");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();
        Hospital primary = manager.getPrimaryHospital();

        manager.setDoctorDutyStatus("NDGH", "DOC-01", false);
        manager.setDoctorDutyStatus("NDGH", "DOC-02", false);

        for (Hospital h : manager.getAllHospitals()) {
            if (!h.isPrimary()) {
                for (Bed b : h.getAllBeds()) {
                    if (b instanceof ICUBed && !b.isOccupied()) {
                        b.admitPatient("DUMMY-" + b.getBedId());
                    }
                }
            }
        }

        assertTrue("TC2.1", primary.getAvailableBedsCount(ICUBed.class) > 0,
                "Primary hospital has free ICU bed.");

        Patient patient = manager.registerPatient("TC2-P01", "Meenakshi Sundaram", 62, "F",
                Severity.CRITICAL, "ICU");

        List<AdmissionRequest> results = manager.processWaitingQueue();
        AdmissionRequest req = results.get(0);

        assertTrue("TC2.2", req.getDisposition() == AdmissionRequest.Disposition.QUEUED_NO_DOCTOR,
                "Admission rejected due to doctor unavailability: " + req.getRemarks());
        assertTrue("TC2.3", patient.getStatus() == Patient.Status.WAITING,
                "Patient remains safely queued in WAITING status.");

        System.out.println("  [ACTION] Dr. Ramesh Kumar (DOC-01) reports on duty...");
        manager.setDoctorDutyStatus("NDGH", "DOC-01", true);

        assertTrue("TC2.4", patient.getStatus() == Patient.Status.ADMITTED_LOCAL,
                "Patient automatically admitted upon doctor duty resumption!");
        assertTrue("TC2.5", "DOC-01".equals(patient.getAssignedDoctorId()),
                "Doctor DOC-01 assigned to patient.");
    }

    private static void runTestCase3_SimultaneousDemand_PrioritizesSeverityFirst() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 3: Simultaneous Demand for 1 Bed -> Process Severity First, Arrival Second");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();
        Hospital primary = manager.getPrimaryHospital();

        primary.getBed("NDGH-GEN-01").admitPatient("OCC-01");
        primary.getBed("NDGH-GEN-02").admitPatient("OCC-02");
        primary.getBed("NDGH-GEN-03").admitPatient("OCC-03");

        for (Hospital h : manager.getAllHospitals()) {
            if (!h.isPrimary()) {
                for (Bed b : h.getAllBeds()) {
                    if (b instanceof GeneralBed && !b.isOccupied()) {
                        b.admitPatient("DUMMY-" + b.getBedId());
                    }
                }
            }
        }

        assertTrue("TC3.1", primary.getAvailableBedsCount(GeneralBed.class) == 1,
                "Exactly 1 General Bed remaining at primary hospital.");

        Patient ptA = manager.registerPatient("TC3-PT-A", "Patient A (Arrived 1st)", 22, "M",
                Severity.MINOR, "General");
        Patient ptB = manager.registerPatient("TC3-PT-B", "Patient B (Arrived 2nd)", 48, "F",
                Severity.SERIOUS, "General");

        assertTrue("TC3.2", ptA.getArrivalSequence() < ptB.getArrivalSequence(),
                "Pt A arrived earlier (Seq " + ptA.getArrivalSequence() + " < Seq " + ptB.getArrivalSequence() + ").");

        manager.processWaitingQueue();

        assertTrue("TC3.3", ptB.getStatus() == Patient.Status.ADMITTED_LOCAL,
                "Patient B (SERIOUS) was admitted first due to higher clinical severity!");
        assertTrue("TC3.4", ptA.getStatus() == Patient.Status.WAITING,
                "Patient A (MINOR) kept waiting as capacity exhausted.");
    }

    private static void runTestCase4_DischargeEnablesWaitingAdmission() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 4: Discharge Enabling Admission -> Immediate Waiting List Reflection");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();
        Hospital primary = manager.getPrimaryHospital();

        for (Bed b : primary.getAllBeds()) {
            if (!b.isOccupied()) {
                b.admitPatient("OCC-" + b.getBedId());
            }
        }

        for (Hospital h : manager.getAllHospitals()) {
            if (!h.isPrimary()) {
                for (Bed b : h.getAllBeds()) {
                    if (!b.isOccupied()) {
                        b.admitPatient("DUMMY-" + b.getBedId());
                    }
                }
            }
        }

        Patient waitingPt = manager.registerPatient("TC4-WAIT", "Waiting Patient Mani", 39, "M",
                Severity.SERIOUS, "General");
        manager.processWaitingQueue();

        assertTrue("TC4.1", waitingPt.getStatus() == Patient.Status.WAITING,
                "Patient is waiting due to zero available beds.");

        System.out.println("  [ACTION] Discharging occupant OCC-NDGH-GEN-01 from NDGH-GEN-01...");
        boolean discharged = manager.dischargePatient("NDGH", "OCC-NDGH-GEN-01");
        assertTrue("TC4.2", discharged, "Occupant discharged successfully.");

        assertTrue("TC4.3", waitingPt.getStatus() == Patient.Status.ADMITTED_LOCAL,
                "Waiting patient immediately admitted upon bed discharge!");
        assertTrue("TC4.4", "NDGH-GEN-01".equals(waitingPt.getAssignedBedId()),
                "Assigned to the newly vacated bed NDGH-GEN-01.");
    }

    private static void runTestCase5_PriorityTiesAndDynamicReranking() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 5: Priority Ties & Dynamic Re-ranking (Remove & Reinsert Heap)");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();

        Patient pt1 = manager.registerPatient("TC5-P01", "Tie Pt 1", 30, "M", Severity.SERIOUS, "General");
        Patient pt2 = manager.registerPatient("TC5-P02", "Tie Pt 2", 32, "F", Severity.SERIOUS, "General");

        List<Patient> snapshot = manager.getWaitingListSnapshot();
        assertTrue("TC5.1", snapshot.get(0).getPatientId().equals("TC5-P01"),
                "Priority tie broken deterministically by arrival sequence (Pt 1 before Pt 2).");

        System.out.println("  [ACTION] Clinically escalating Pt 2 from SERIOUS to CRITICAL...");
        boolean updated = manager.updatePatientSeverity("TC5-P02", Severity.CRITICAL);
        assertTrue("TC5.2", updated, "Severity successfully updated with heap re-insertion.");

        List<Patient> updatedSnapshot = manager.getWaitingListSnapshot();
        assertTrue("TC5.3", updatedSnapshot.get(0).getPatientId().equals("TC5-P02"),
                "Pt 2 is now at head of PriorityQueue after dynamic re-ranking!");
    }
}

// =============================================================================
// 7. PUBLIC ENTRY POINT & INTERACTIVE CONSOLE APP
// =============================================================================

public class HospitalApp {
    private final HospitalSystemManager manager;
    private final Scanner scanner;

    public HospitalApp() {
        this.manager = SyntheticDataGenerator.initializeSystem();
        this.scanner = new Scanner(System.in);
    }

    public static void main(String[] args) {
        if (args.length > 0 && ("--test".equalsIgnoreCase(args[0]) || "-t".equalsIgnoreCase(args[0]))) {
            TestRunner.main(args);
            return;
        }

        HospitalApp app = new HospitalApp();
        app.start();
    }

    public void start() {
        printHeader();
        boolean running = true;
        while (running) {
            printMainMenu();
            String choice = prompt("Select an option [0-11]: ");
            switch (choice.trim()) {
                case "1" -> viewHospitalStatus();
                case "2" -> viewRoadNetworkAndRoutes();
                case "3" -> registerNewPatient();
                case "4" -> processWaitingQueue();
                case "5" -> dischargePatient();
                case "6" -> updateDoctorDutyStatus();
                case "7" -> updateRoadTravelTime();
                case "8" -> updatePatientSeverity();
                case "9" -> loadAndSimulateSyntheticBatch();
                case "10" -> runAutomatedVerificationTests();
                case "11" -> viewAuditHistory();
                case "0" -> {
                    System.out.println("\nExiting Hospital Emergency Resource Allocation System. Stay safe!");
                    running = false;
                }
                default -> System.out.println("Invalid option! Please enter a number between 0 and 11.");
            }
            if (running) {
                System.out.println("\nPress ENTER to continue...");
                scanner.nextLine();
            }
        }
    }

    private void printHeader() {
        System.out.println("================================================================================");
        System.out.println("     GOVERNMENT HOSPITAL EMERGENCY RESOURCE ALLOCATION SYSTEM (NALAM DIST)      ");
        System.out.println("================================================================================");
        System.out.println("  SDG Connections: SDG 3 (Health), SDG 9 (Infrastructure), SDG 10 (Inequalities)");
        System.out.println("  Core Tech: Pure Java SE | OOP Principles | Custom Graphs & Heaps");
        System.out.println("================================================================================");
    }

    private void printMainMenu() {
        System.out.println("\n---------------------------------- MAIN MENU -----------------------------------");
        System.out.println(" 1. View District Hospital Status & Capacities (Beds, Doctors)");
        System.out.println(" 2. View Road Network & Dijkstra Shortest Travel Times from NDGH");
        System.out.println(" 3. Register New Emergency Patient (Manual Intake)");
        System.out.println(" 4. Process Waiting Queue (Severity-First Admission & Referrals)");
        System.out.println(" 5. Discharge Patient / Complete Treatment (Releases Bed & Doctor)");
        System.out.println(" 6. Update Doctor Duty Status (Toggle On-Duty / Off-Duty)");
        System.out.println(" 7. Update Road Travel Time (Manual Road Condition Update)");
        System.out.println(" 8. Update Waiting Patient Urgency (Dynamic Re-Ranking in Heap)");
        System.out.println(" 9. Load & Simulate 22 Synthetic Patient Requests Batch");
        System.out.println(" 10. Run Mandatory Verification Test Suite (4 Failure & Contention Cases)");
        System.out.println(" 11. View Event Audit History (ArrayList Event Log)");
        System.out.println("  0. Exit System");
        System.out.println("--------------------------------------------------------------------------------");
    }

    private void viewHospitalStatus() {
        System.out.println("\n=========================== DISTRICT HOSPITAL STATUS ===========================");
        for (Hospital h : manager.getAllHospitals()) {
            System.out.println("\n" + (h.isPrimary() ? ">>> PRIMARY HOSPITAL <<<" : "--- REFERRAL HOSPITAL ---"));
            System.out.printf("%s (%s) @ Location [%s]\n", h.getName(), h.getHospitalId(), h.getLocationId());
            System.out.printf("  Total Beds: %d | Available: %d | Occupied: %d\n",
                    h.getTotalBedsCount(), h.getAvailableBedsCount(), h.getOccupiedBedsCount());
            System.out.printf("  Available Breakdown: [ICU: %d]  [General: %d]\n",
                    h.getAvailableBedsCount(ICUBed.class), h.getAvailableBedsCount(GeneralBed.class));
            System.out.printf("  Doctors On Duty: %d | Available for Admission: %d\n",
                    h.getOnDutyDoctorCount(), h.getAvailableDoctorCount());

            System.out.println("  Beds Registry:");
            for (Bed b : h.getAllBeds()) {
                System.out.printf("    - Bed %-12s Type: %-14s Status: %s\n",
                        b.getBedId(), b.getBedType(),
                        b.isOccupied() ? "OCCUPIED (Pt: " + b.getCurrentPatientId() + ")" : "VACANT");
            }

            System.out.println("  Doctors Registry:");
            for (Doctor d : h.getAllDoctors()) {
                System.out.printf("    - [%s] Dr. %-18s (%-18s) Duty: %-8s Status: %s\n",
                        d.getDoctorId(), d.getName(), d.getSpecialization(),
                        d.isOnDuty() ? "ON-DUTY" : "OFF-DUTY",
                        d.getAssignedPatientId() != null ? "BUSY (Pt: " + d.getAssignedPatientId() + ")" : "FREE");
            }
        }

        List<Patient> waiting = manager.getWaitingListSnapshot();
        System.out.println("\n---------------------------- WAITING QUEUE SUMMARY -----------------------------");
        System.out.printf("Patients currently waiting in PriorityQueue: %d\n", waiting.size());
        if (waiting.isEmpty()) {
            System.out.println("  (No patients currently waiting)");
        } else {
            int rank = 1;
            for (Patient p : waiting) {
                System.out.printf("  #%d [%s] %s | Urgency: %-8s | Facility: %-7s | Arrival Seq: #%d\n",
                        rank++, p.getPatientId(), p.getName(), p.getSeverity(), p.getRequiredFacility(), p.getArrivalSequence());
            }
        }
    }

    private void viewRoadNetworkAndRoutes() {
        System.out.println("\n===================== NALAM DISTRICT ROAD NETWORK TOPOLOGY =====================");
        RoadGraph graph = manager.getRoadGraph();
        for (Location loc : graph.getAllLocations()) {
            System.out.printf("%-4s: %-22s - %s\n", loc.getLocationId(), loc.getName(), loc.getDescription());
            for (RoadEdge edge : graph.getEdgesFrom(loc.getLocationId())) {
                System.out.printf("      -> %-4s (%2d min travel time)\n", edge.getToLocationId(), edge.getTravelTimeMinutes());
            }
        }

        System.out.println("\n----------------- DIJKSTRA SHORTEST TRAVEL TIMES FROM NDGH (L1) ----------------");
        RoutePlanner planner = new RoutePlanner();
        DijkstraResult result = planner.calculateShortestPaths(graph, "L1");
        for (Hospital h : manager.getAllHospitals()) {
            if (h.isPrimary()) continue;
            int time = result.getDistanceTo(h.getLocationId());
            List<String> path = result.getPathTo(h.getLocationId());
            System.out.printf("  %s (%s) @ %s: Travel Time = %d mins | Route: %s\n",
                    h.getName(), h.getHospitalId(), h.getLocationId(), time, String.join(" -> ", path));
        }
    }

    private void registerNewPatient() {
        System.out.println("\n======================== REGISTER EMERGENCY PATIENT ========================");
        String id = "P-" + (manager.getAllPatients().size() + 101);
        System.out.println("Generated Patient ID: " + id);

        String name = prompt("Enter Patient Name: ");
        int age = promptInt("Enter Age: ", 0, 120);
        String gender = prompt("Enter Gender (Male/Female/Other): ");

        System.out.println("\nSelect Severity:");
        System.out.println("  1. CRITICAL (Immediate resuscitation / ventilator / ICU required)");
        System.out.println("  2. SERIOUS  (Acute intervention / close monitoring required)");
        System.out.println("  3. MINOR    (Non-life threatening / standard ward observation)");
        int sevChoice = promptInt("Choice [1-3]: ", 1, 3);
        Severity severity = switch (sevChoice) {
            case 1 -> Severity.CRITICAL;
            case 2 -> Severity.SERIOUS;
            default -> Severity.MINOR;
        };

        System.out.println("\nSelect Required Facility:");
        System.out.println("  1. ICU (Intensive Care Unit / Ventilator Bed)");
        System.out.println("  2. General (Standard Inpatient Ward Bed)");
        int facChoice = promptInt("Choice [1-2]: ", 1, 2);
        String requiredFacility = (facChoice == 1) ? "ICU" : "General";

        Patient patient = manager.registerPatient(id, name, age, gender, severity, requiredFacility);
        System.out.printf("\n[SUCCESS] Patient %s registered and enqueued into PriorityQueue at arrival sequence #%d.\n",
                patient.getName(), patient.getArrivalSequence());

        String autoProcess = prompt("Attempt admission immediately? (Y/N): ");
        if ("Y".equalsIgnoreCase(autoProcess.trim())) {
            processWaitingQueue();
        }
    }

    private void processWaitingQueue() {
        System.out.println("\n======================== PROCESSING WAITING QUEUE ========================");
        if (manager.getWaitingQueue().isEmpty()) {
            System.out.println("Waiting queue is currently empty. No patients to process.");
            return;
        }

        System.out.printf("Evaluating %d queued patient(s) in order of clinical severity and arrival...\n",
                manager.getWaitingQueue().size());

        List<AdmissionRequest> results = manager.processWaitingQueue();
        for (AdmissionRequest req : results) {
            System.out.println("\n" + req);
        }

        System.out.printf("\nCompleted queue processing. %d patient(s) remain waiting.\n",
                manager.getWaitingQueue().size());
    }

    private void dischargePatient() {
        System.out.println("\n======================== DISCHARGE / RELEASE BED ========================");
        System.out.println("Currently admitted patients across all hospitals:");
        List<Patient> admitted = new ArrayList<>();
        for (Patient p : manager.getAllPatients().values()) {
            if (p.getStatus() == Patient.Status.ADMITTED_LOCAL || p.getStatus() == Patient.Status.REFERRED) {
                admitted.add(p);
                System.out.printf("  [%s] %-18s | Hospital: %-5s | Bed: %-12s | Status: %s\n",
                        p.getPatientId(), p.getName(), p.getAssignedHospitalId(),
                        p.getAssignedBedId(), p.getStatus());
            }
        }

        if (admitted.isEmpty()) {
            System.out.println("No patients currently admitted across any hospitals.");
            return;
        }

        String patientId = prompt("\nEnter Patient ID to discharge: ").trim();
        Patient patient = manager.getPatient(patientId);
        if (patient == null || (patient.getStatus() != Patient.Status.ADMITTED_LOCAL && patient.getStatus() != Patient.Status.REFERRED)) {
            System.out.println("[ERROR] Patient ID not found or patient is not currently admitted.");
            return;
        }

        String hospitalId = patient.getAssignedHospitalId();
        boolean discharged = manager.dischargePatient(hospitalId, patientId);
        if (discharged) {
            System.out.printf("\n[SUCCESS] Patient %s discharged from %s.\n", patient.getName(), hospitalId);
            System.out.println("Note: Released bed and doctor immediately evaluated for waiting list patients!");
        } else {
            System.out.println("[ERROR] Could not complete discharge operation.");
        }
    }

    private void updateDoctorDutyStatus() {
        System.out.println("\n======================== UPDATE DOCTOR DUTY STATUS ========================");
        System.out.println("Doctors List:");
        for (Hospital h : manager.getAllHospitals()) {
            for (Doctor d : h.getAllDoctors()) {
                System.out.printf("  [%-6s @ %-5s] Dr. %-18s (%-16s) | Duty: %-8s | Active Patient: %s\n",
                        d.getDoctorId(), d.getHospitalId(), d.getName(), d.getSpecialization(),
                        d.isOnDuty() ? "ON-DUTY" : "OFF-DUTY",
                        d.getAssignedPatientId() != null ? d.getAssignedPatientId() : "None");
            }
        }

        String hospitalId = prompt("\nEnter Hospital ID (e.g., NDGH, VSDH, TCHC, KTH): ").trim().toUpperCase();
        String doctorId = prompt("Enter Doctor ID (e.g., DOC-01): ").trim().toUpperCase();

        Hospital hospital = manager.getHospital(hospitalId);
        if (hospital == null) {
            System.out.println("[ERROR] Unknown hospital ID: " + hospitalId);
            return;
        }
        Doctor doctor = hospital.getDoctor(doctorId);
        if (doctor == null) {
            System.out.println("[ERROR] Doctor ID not found at " + hospitalId);
            return;
        }

        System.out.println("Current status: " + (doctor.isOnDuty() ? "ON-DUTY" : "OFF-DUTY"));
        String newStatusStr = prompt("Set duty status (1 = ON-DUTY, 0 = OFF-DUTY): ").trim();
        boolean newStatus = "1".equals(newStatusStr);

        try {
            boolean updated = manager.setDoctorDutyStatus(hospitalId, doctorId, newStatus);
            if (updated) {
                System.out.printf("\n[SUCCESS] Dr. %s duty status updated to %s.\n",
                        doctor.getName(), (newStatus ? "ON-DUTY" : "OFF-DUTY"));
            }
        } catch (IllegalStateException ex) {
            System.out.println("[ERROR] " + ex.getMessage());
        }
    }

    private void updateRoadTravelTime() {
        System.out.println("\n======================== UPDATE ROAD TRAVEL TIME ========================");
        System.out.println("Locations: L1 (Nalam Central), L2 (North Jcn), L3 (Vallam Cross), L4 (East Circle),");
        System.out.println("           L5 (Thoppur Rural), L6 (South Bridge), L7 (Industrial), L8 (Kadur Hills), L9 (West Bypass)");

        String locA = prompt("Enter Location A ID (e.g., L1): ").trim().toUpperCase();
        String locB = prompt("Enter Location B ID (e.g., L2): ").trim().toUpperCase();
        int newTime = promptInt("Enter New Travel Time in minutes: ", 1, 300);

        boolean updated = manager.updateTravelTime(locA, locB, newTime);
        if (updated) {
            System.out.printf("\n[SUCCESS] Road travel time between %s and %s updated to %d minutes.\n",
                    locA, locB, newTime);
            System.out.println("New shortest routes will be computed automatically on next referral attempt.");
        } else {
            System.out.println("[ERROR] Direct road connection between " + locA + " and " + locB + " does not exist.");
        }
    }

    private void updatePatientSeverity() {
        System.out.println("\n================ UPDATE PATIENT SEVERITY (HEAP RE-RANKING) ================");
        List<Patient> waiting = manager.getWaitingListSnapshot();
        if (waiting.isEmpty()) {
            System.out.println("No patients currently waiting in queue.");
            return;
        }

        System.out.println("Waiting Patients:");
        for (Patient p : waiting) {
            System.out.printf("  [%s] %-18s | Urgency: %-8s | Facility: %s\n",
                    p.getPatientId(), p.getName(), p.getSeverity(), p.getRequiredFacility());
        }

        String ptId = prompt("\nEnter Patient ID to re-rank: ").trim();
        Patient pt = manager.getPatient(ptId);
        if (pt == null) {
            System.out.println("[ERROR] Patient not found.");
            return;
        }

        System.out.println("Select New Severity:");
        System.out.println("  1. CRITICAL");
        System.out.println("  2. SERIOUS");
        System.out.println("  3. MINOR");
        int choice = promptInt("Choice [1-3]: ", 1, 3);
        Severity newSev = switch (choice) {
            case 1 -> Severity.CRITICAL;
            case 2 -> Severity.SERIOUS;
            default -> Severity.MINOR;
        };

        boolean reordered = manager.updatePatientSeverity(ptId, newSev);
        if (reordered) {
            System.out.printf("\n[SUCCESS] Patient %s severity updated to %s. PriorityQueue re-ranked!\n",
                    pt.getName(), newSev);
        }
    }

    private void loadAndSimulateSyntheticBatch() {
        System.out.println("\n================= LOAD & SIMULATE 22 SYNTHETIC PATIENT REQUESTS =================");
        List<SyntheticDataGenerator.SyntheticPatientRecord> batch = SyntheticDataGenerator.getSyntheticPatientBatch();
        System.out.printf("Ingesting batch of %d synthetic emergency patient requests...\n", batch.size());

        for (SyntheticDataGenerator.SyntheticPatientRecord rec : batch) {
            manager.registerPatient(rec.id, rec.name, rec.age, rec.gender, rec.severity, rec.requiredFacility);
        }

        System.out.printf("[SUCCESS] %d patients registered and placed in PriorityQueue.\n", batch.size());
        System.out.println("Processing admissions across primary and referral network...");

        List<AdmissionRequest> results = manager.processWaitingQueue();
        int admittedPrimary = 0;
        int referred = 0;
        int waiting = 0;

        for (AdmissionRequest req : results) {
            if (req.getDisposition() == AdmissionRequest.Disposition.ADMITTED_PRIMARY) admittedPrimary++;
            else if (req.getDisposition() == AdmissionRequest.Disposition.REFERRED_AND_RESERVED) referred++;
            else waiting++;
        }

        System.out.println("\n----------------------------- BATCH OUTCOME SUMMARY ----------------------------");
        System.out.printf("  Total Requests Processed:      %d\n", results.size());
        System.out.printf("  Admitted Locally at NDGH:      %d\n", admittedPrimary);
        System.out.printf("  Referred & Bed Reserved:       %d\n", referred);
        System.out.printf("  Kept Waiting (Capacity Full):  %d\n", waiting);
        System.out.println("--------------------------------------------------------------------------------");
    }

    private void runAutomatedVerificationTests() {
        TestRunner.main(new String[0]);
    }

    private void viewAuditHistory() {
        System.out.println("\n============================== EVENT AUDIT HISTORY ==============================");
        List<AuditRecord> history = manager.getAuditHistory();
        if (history.isEmpty()) {
            System.out.println("Audit log is currently empty.");
            return;
        }

        System.out.printf("Total Recorded Transactions (ArrayList): %d\n\n", history.size());
        for (AuditRecord rec : history) {
            System.out.println(rec);
        }
    }

    private String prompt(String message) {
        System.out.print(message);
        return scanner.nextLine();
    }

    private int promptInt(String message, int min, int max) {
        while (true) {
            System.out.print(message);
            String input = scanner.nextLine().trim();
            try {
                int val = Integer.parseInt(input);
                if (val >= min && val <= max) {
                    return val;
                }
                System.out.printf("Please enter a number between %d and %d.\n", min, max);
            } catch (NumberFormatException e) {
                System.out.println("Invalid integer input.");
            }
        }
    }
}
