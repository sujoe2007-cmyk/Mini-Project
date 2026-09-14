import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

// =============================================================================
// 1. BED HIERARCHY (Inheritance & Polymorphism)
// =============================================================================

/**
 * Abstract Bed base class.
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

    /**
     * Polymorphic method to be overridden by subclasses.
     */
    public abstract boolean supportsRequirement(String facilityRequirement);

    public abstract String getBedType();

    /**
     * Encapsulation: bed state changes strictly through admitPatient().
     */
    public void admitPatient(String patientId) {
        if (this.occupied) {
            throw new IllegalStateException("Bed " + bedId + " is already occupied by patient " + currentPatientId);
        }
        this.occupied = true;
        this.currentPatientId = Objects.requireNonNull(patientId, "patientId cannot be null");
    }

    /**
     * Encapsulation: bed state changes strictly through dischargePatient().
     */
    public void dischargePatient() {
        if (!this.occupied) {
            throw new IllegalStateException("Bed " + bedId + " is already vacant.");
        }
        this.occupied = false;
        this.currentPatientId = null;
    }

    @Override
    public String toString() {
        return String.format("[%s] Bed %s (%s) @ %s - %s",
                bedId, bedNumber, getBedType(), hospitalId,
                occupied ? "OCCUPIED (Patient: " + currentPatientId + ")" : "VACANT");
    }
}

/**
 * General ward bed subclass overriding supportsRequirement().
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
 * Intensive Care Unit bed subclass overriding supportsRequirement().
 */
class ICUBed extends Bed {
    private final boolean hasVentilator;

    public ICUBed(String bedId, String hospitalId, String bedNumber, boolean hasVentilator) {
        super(bedId, hospitalId, bedNumber);
        this.hasVentilator = hasVentilator;
    }

    public boolean isHasVentilator() {
        return hasVentilator;
    }

    @Override
    public String getBedType() {
        return "ICU" + (hasVentilator ? "+VENT" : "");
    }

    @Override
    public boolean supportsRequirement(String facilityRequirement) {
        if (facilityRequirement == null) return false;
        String req = facilityRequirement.trim().toLowerCase();
        return req.equals("icu") || req.equals("ventilator") || req.equals("critical");
    }
}

// =============================================================================
// 2. DOCTOR (Encapsulation)
// =============================================================================

/**
 * Represents a medical officer on duty.
 * Encapsulation: Private duty status and patient assignment states change
 * exclusively through admitPatient(), dischargePatient(), and setDutyStatus().
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

    /**
     * Encapsulation: duty state updated strictly via setDutyStatus().
     */
    public void setDutyStatus(boolean onDuty) {
        if (!onDuty && assignedPatientId != null) {
            throw new IllegalStateException("Doctor " + name + " cannot go off-duty while currently attending patient " + assignedPatientId);
        }
        this.onDuty = onDuty;
    }

    /**
     * Encapsulation: doctor patient assignment strictly via admitPatient().
     */
    public void admitPatient(String patientId) {
        if (!this.onDuty) {
            throw new IllegalStateException("Doctor " + name + " is OFF-DUTY and cannot admit patients.");
        }
        if (this.assignedPatientId != null) {
            throw new IllegalStateException("Doctor " + name + " is already attending patient " + assignedPatientId);
        }
        this.assignedPatientId = Objects.requireNonNull(patientId, "patientId cannot be null");
    }

    /**
     * Encapsulation: doctor patient release strictly via dischargePatient().
     */
    public void dischargePatient() {
        if (this.assignedPatientId == null) {
            throw new IllegalStateException("Doctor " + name + " has no assigned patient to discharge.");
        }
        this.assignedPatientId = null;
    }

    public boolean canTreat(int severity, String facilityRequirement) {
        if (!this.onDuty || this.assignedPatientId != null) {
            return false;
        }
        if (severity == Patient.CRITICAL || "ICU".equalsIgnoreCase(facilityRequirement)) {
            return specialization.equalsIgnoreCase("Critical Care") ||
                   specialization.equalsIgnoreCase("Emergency Medicine") ||
                   specialization.equalsIgnoreCase("Trauma Surgery");
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("[%s] Dr. %s (%s) @ %s - Duty: %s | Status: %s",
                doctorId, name, specialization, hospitalId,
                onDuty ? "ON-DUTY" : "OFF-DUTY",
                assignedPatientId != null ? "BUSY (Patient: " + assignedPatientId + ")" : "AVAILABLE");
    }
}

// =============================================================================
// 3. PATIENT (PriorityQueue Deterministic Ordering)
// =============================================================================

/**
 * Represents a patient arriving at emergency triage.
 * Implements Comparable for deterministic PriorityQueue ordering:
 * 1. Severity/Urgency rank (CRITICAL=1 > SERIOUS=2 > MINOR=3)
 * 2. Arrival Sequence (earlier arrival first)
 * 3. Patient ID (lexicographical tie-breaker)
 */
class Patient implements Comparable<Patient> {
    public static final int CRITICAL = 1;
    public static final int SERIOUS = 2;
    public static final int MINOR = 3;

    public enum Status {
        WAITING,
        ADMITTED,
        REFERRED,
        DISCHARGED
    }

    private final String patientId;
    private final String name;
    private final int age;
    private final String gender;
    private int severity; // 1 = Critical, 2 = Serious, 3 = Minor
    private String requiredFacility; // "General" or "ICU"
    private final long arrivalSequence;
    private final LocalDateTime arrivalTime;

    private Status status;
    private String assignedHospitalId;
    private String assignedBedId;
    private String assignedDoctorId;
    private String referralNotes;

    public Patient(String patientId, String name, int age, String gender,
                   int severity, String requiredFacility, long arrivalSequence) {
        this.patientId = Objects.requireNonNull(patientId, "patientId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.age = age;
        this.gender = gender;
        this.severity = severity;
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

    public int getSeverity() {
        return severity;
    }

    public void setSeverity(int severity) {
        this.severity = severity;
    }

    public String getSeverityLabel() {
        return switch (severity) {
            case CRITICAL -> "Critical";
            case SERIOUS -> "Serious";
            default -> "Minor";
        };
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
     * 3. Patient ID ascending (strict tie-breaker)
     */
    @Override
    public int compareTo(Patient other) {
        if (this == other) return 0;
        int sevDiff = Integer.compare(this.severity, other.severity);
        if (sevDiff != 0) return sevDiff;
        int seqDiff = Long.compare(this.arrivalSequence, other.arrivalSequence);
        if (seqDiff != 0) return seqDiff;
        return this.patientId.compareTo(other.patientId);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (Age: %d, %s) - Urgency: %s, Facility: %s, Seq: #%d, Status: %s",
                patientId, name, age, gender, getSeverityLabel(), requiredFacility, arrivalSequence, status);
    }
}

// =============================================================================
// 4. HOSPITAL (Composition: contains Beds and Doctors in HashMaps)
// =============================================================================

/**
 * Hospital entity demonstrating Composition:
 * Contains collections of Bed and Doctor objects indexed by ID in HashMaps.
 */
class Hospital {
    private final String hospitalId;
    private final String name;
    private final boolean isPrimary;

    // Composition: Hospital contains Beds and Doctors indexed by ID (HashMap)
    private final Map<String, Bed> beds = new LinkedHashMap<>();
    private final Map<String, Doctor> doctors = new LinkedHashMap<>();

    public Hospital(String hospitalId, String name, boolean isPrimary) {
        this.hospitalId = Objects.requireNonNull(hospitalId, "hospitalId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.isPrimary = isPrimary;
    }

    public String getHospitalId() {
        return hospitalId;
    }

    public String getName() {
        return name;
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

    public Optional<Doctor> findAvailableDoctor(int severity, String facilityRequirement) {
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
        patient.setStatus(this.isPrimary ? Patient.Status.ADMITTED : Patient.Status.REFERRED);
    }

    public boolean dischargePatient(String patientId) {
        Bed targetBed = null;
        for (Bed bed : beds.values()) {
            if (patientId.equals(bed.getCurrentPatientId())) {
                targetBed = bed;
                break;
            }
        }

        if (targetBed == null) return false;

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

    public long getAvailableBedsCount() {
        return beds.values().stream().filter(b -> !b.isOccupied()).count();
    }

    public long getAvailableBedsCount(Class<? extends Bed> type) {
        return beds.values().stream().filter(b -> !b.isOccupied() && type.isInstance(b)).count();
    }

    public long getAvailableDoctorCount() {
        return doctors.values().stream().filter(Doctor::isAvailable).count();
    }

    public long getOnDutyDoctorCount() {
        return doctors.values().stream().filter(Doctor::isOnDuty).count();
    }

    @Override
    public String toString() {
        return String.format("%s (%s) [%s] - Beds: %d/%d Avail (ICU: %d, Gen: %d) | Doctors: %d/%d Avail",
                name, hospitalId, isPrimary ? "PRIMARY" : "REFERRAL",
                getAvailableBedsCount(), getTotalBedsCount(),
                getAvailableBedsCount(ICUBed.class), getAvailableBedsCount(GeneralBed.class),
                getAvailableDoctorCount(), getOnDutyDoctorCount());
    }
}

// =============================================================================
// 5. ADMISSION REQUEST (Transaction Record for ArrayList History)
// =============================================================================

/**
 * Represents an admission, discharge, or referral event.
 * Stored in ArrayList for historical audit.
 */
class AdmissionRequest {
    public enum Type {
        ADMISSION,
        REFERRAL,
        QUEUED_WAITING,
        DISCHARGE
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final String requestId;
    private final LocalDateTime timestamp;
    private final Type type;
    private final String patientId;
    private final String patientName;
    private final String hospitalId;
    private final String bedId;
    private final String doctorId;
    private final int travelTimeMinutes;
    private final String remarks;

    public AdmissionRequest(String requestId, Type type, String patientId, String patientName,
                            String hospitalId, String bedId, String doctorId,
                            int travelTimeMinutes, String remarks) {
        this.requestId = requestId;
        this.timestamp = LocalDateTime.now();
        this.type = type;
        this.patientId = patientId;
        this.patientName = patientName;
        this.hospitalId = hospitalId;
        this.bedId = bedId;
        this.doctorId = doctorId;
        this.travelTimeMinutes = travelTimeMinutes;
        this.remarks = remarks;
    }

    public String getRequestId() {
        return requestId;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public Type getType() {
        return type;
    }

    public String getPatientId() {
        return patientId;
    }

    public String getPatientName() {
        return patientName;
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

    public int getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public String getRemarks() {
        return remarks;
    }

    @Override
    public String toString() {
        return String.format("[%s] %-14s | Pt: %-6s (%-16s) | Hosp: %-5s | Bed: %-12s | Doc: %-6s | %s",
                timestamp.format(TIME_FMT), type,
                patientId != null ? patientId : "-",
                patientName != null ? patientName : "-",
                hospitalId != null ? hospitalId : "-",
                bedId != null ? bedId : "-",
                doctorId != null ? doctorId : "-",
                remarks);
    }
}

// =============================================================================
// 6. ADMISSION POLICY (Abstraction: abstracts eligibility)
// =============================================================================

/**
 * Abstraction: AdmissionPolicy abstracts patient eligibility rules.
 */
interface AdmissionPolicy {
    boolean isEligible(Hospital hospital, Patient patient);

    /**
     * Standard admission policy: both an appropriate bed AND an on-duty doctor must be available.
     */
    static AdmissionPolicy standard() {
        return (hospital, patient) ->
                hospital != null && patient != null
                && hospital.findAvailableBed(patient.getRequiredFacility()).isPresent()
                && hospital.findAvailableDoctor(patient.getSeverity(), patient.getRequiredFacility()).isPresent();
    }
}

// =============================================================================
// 7. ROUTE PLANNER (Dijkstra's Algorithm + Min-Heap on Weighted Graph)
// =============================================================================

/**
 * Implements Dijkstra's Shortest Path Algorithm using a Min-Heap (PriorityQueue)
 * over the weighted graph of hospital connections.
 */
class RoutePlanner {
    public static class RouteResult {
        private final Map<String, Integer> distances;
        private final Map<String, String> predecessors;

        public RouteResult(Map<String, Integer> distances, Map<String, String> predecessors) {
            this.distances = distances;
            this.predecessors = predecessors;
        }

        public int getDistanceTo(String targetNode) {
            return distances.getOrDefault(targetNode, Integer.MAX_VALUE);
        }

        public List<String> getPathTo(String sourceNode, String targetNode) {
            if (!distances.containsKey(targetNode) || distances.get(targetNode) == Integer.MAX_VALUE) {
                return Collections.emptyList();
            }
            LinkedList<String> path = new LinkedList<>();
            String curr = targetNode;
            while (curr != null) {
                path.addFirst(curr);
                if (curr.equals(sourceNode)) break;
                curr = predecessors.get(curr);
            }
            return path;
        }
    }

    /**
     * Computes shortest travel times from source hospital using Dijkstra + Min-Heap.
     */
    public RouteResult calculateShortestPaths(Map<String, Map<String, Integer>> graph, String source) {
        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();

        for (String node : graph.keySet()) {
            distances.put(node, Integer.MAX_VALUE);
        }
        distances.put(source, 0);

        // Min-Heap (PriorityQueue)
        PriorityQueue<Map.Entry<String, Integer>> minHeap =
                new PriorityQueue<>(Comparator.comparingInt(Map.Entry::getValue));
        minHeap.offer(Map.entry(source, 0));

        Set<String> visited = new HashSet<>();

        while (!minHeap.isEmpty()) {
            Map.Entry<String, Integer> current = minHeap.poll();
            String u = current.getKey();
            int d = current.getValue();

            if (d > distances.getOrDefault(u, Integer.MAX_VALUE)) {
                continue;
            }
            visited.add(u);

            Map<String, Integer> neighbors = graph.get(u);
            if (neighbors != null) {
                for (Map.Entry<String, Integer> edge : neighbors.entrySet()) {
                    String v = edge.getKey();
                    int weight = edge.getValue();
                    int newDist = d + weight;

                    if (newDist < distances.getOrDefault(v, Integer.MAX_VALUE)) {
                        distances.put(v, newDist);
                        predecessors.put(v, u);
                        minHeap.offer(Map.entry(v, newDist));
                    }
                }
            }
        }

        return new RouteResult(distances, predecessors);
    }
}

// =============================================================================
// 8. REFERRAL SERVICE (Uses RoutePlanner; filters facility & capacity)
// =============================================================================

/**
 * ReferralService orchestrates hospital referrals when local admission fails.
 * Composition: ReferralService uses RoutePlanner.
 * Workflow:
 * 1. Filters referral hospitals for available bed matching patient's required facility.
 * 2. Filters referral hospitals for an available on-duty doctor.
 * 3. Uses RoutePlanner (Dijkstra + Min-Heap) to find the fastest reachable eligible hospital.
 * 4. Reserves bed immediately before referral confirmation.
 */
class ReferralService {
    private final RoutePlanner routePlanner;

    public ReferralService(RoutePlanner routePlanner) {
        this.routePlanner = Objects.requireNonNull(routePlanner, "routePlanner cannot be null");
    }

    public boolean referPatient(Hospital primaryHospital,
                                Collection<Hospital> allHospitals,
                                Map<String, Map<String, Integer>> roadGraph,
                                Patient patient,
                                List<AdmissionRequest> history) {
        // 1. Filter referral hospitals with required bed AND doctor
        List<Hospital> candidates = new ArrayList<>();
        Map<String, Bed> candidateBeds = new HashMap<>();
        Map<String, Doctor> candidateDoctors = new HashMap<>();

        for (Hospital h : allHospitals) {
            if (h.getHospitalId().equals(primaryHospital.getHospitalId())) continue;

            Optional<Bed> bedOpt = h.findAvailableBed(patient.getRequiredFacility());
            if (bedOpt.isPresent()) {
                Optional<Doctor> docOpt = h.findAvailableDoctor(patient.getSeverity(), patient.getRequiredFacility());
                if (docOpt.isPresent()) {
                    candidates.add(h);
                    candidateBeds.put(h.getHospitalId(), bedOpt.get());
                    candidateDoctors.put(h.getHospitalId(), docOpt.get());
                }
            }
        }

        if (candidates.isEmpty()) {
            return false;
        }

        // 2. Dijkstra's Algorithm + Min-Heap from primary hospital
        RoutePlanner.RouteResult routes = routePlanner.calculateShortestPaths(roadGraph, primaryHospital.getHospitalId());

        // 3. Select the fastest reachable candidate hospital
        Hospital fastest = null;
        int minTime = Integer.MAX_VALUE;

        for (Hospital cand : candidates) {
            int time = routes.getDistanceTo(cand.getHospitalId());
            if (time < minTime) {
                minTime = time;
                fastest = cand;
            } else if (time == minTime && fastest != null) {
                if (cand.getHospitalId().compareTo(fastest.getHospitalId()) < 0) {
                    fastest = cand;
                }
            }
        }

        if (fastest == null || minTime == Integer.MAX_VALUE) {
            return false;
        }

        // 4. Reserve bed & doctor immediately
        Bed bed = candidateBeds.get(fastest.getHospitalId());
        Doctor doc = candidateDoctors.get(fastest.getHospitalId());

        fastest.admitPatient(patient, bed, doc);

        List<String> path = routes.getPathTo(primaryHospital.getHospitalId(), fastest.getHospitalId());
        String notes = String.format("Referred to %s via %s (%d min travel time). Bed: %s",
                fastest.getName(), String.join("->", path), minTime, bed.getBedId());
        patient.setReferralNotes(notes);

        history.add(new AdmissionRequest(
                "REQ-" + System.currentTimeMillis(),
                AdmissionRequest.Type.REFERRAL,
                patient.getPatientId(),
                patient.getName(),
                fastest.getHospitalId(),
                bed.getBedId(),
                doc.getDoctorId(),
                minTime,
                notes
        ));

        return true;
    }
}

// =============================================================================
// 9. MAIN APPLICATION (HospitalApp: Coordinator, CLI, & Automated Tests)
// =============================================================================

/**
 * Core application managing:
 * - PriorityQueue for triage
 * - HashMaps for fast entity lookup
 * - Weighted graph for road connections
 * - ArrayList for admission/discharge/referral history
 * - Interactive CLI & Automated Verification Tests
 */
public class HospitalApp {

    // Necessary Data Structures from Assignment Rubric:
    // 1. PriorityQueue: Waiting patients ordered by severity and arrival sequence
    private final PriorityQueue<Patient> waitingQueue = new PriorityQueue<>();

    // 2. HashMap: Patients, beds, doctors, and hospitals by ID
    private final Map<String, Patient> allPatients = new LinkedHashMap<>();
    private final Map<String, Hospital> hospitals = new LinkedHashMap<>();

    // 3. Weighted Graph: Hospital and road connections with travel times (Map of Maps)
    private final Map<String, Map<String, Integer>> roadGraph = new LinkedHashMap<>();

    // 4. ArrayList: Admission, discharge, and referral history
    private final List<AdmissionRequest> history = new ArrayList<>();

    // Services
    private final AdmissionPolicy admissionPolicy = AdmissionPolicy.standard();
    private final RoutePlanner routePlanner = new RoutePlanner();
    private final ReferralService referralService = new ReferralService(routePlanner);

    private final Hospital primaryHospital;
    private long patientSequenceCounter = 1;
    private final Scanner scanner = new Scanner(System.in);

    public HospitalApp() {
        // Initialize Nalam District baseline environment
        this.primaryHospital = initBaselineData();
    }

    public static void main(String[] args) {
        HospitalApp app = new HospitalApp();
        if (args.length > 0 && ("--test".equalsIgnoreCase(args[0]) || "-t".equalsIgnoreCase(args[0]))) {
            app.runAutomatedTests();
            return;
        }
        app.startMenu();
    }

    private Hospital initBaselineData() {
        // 1. Weighted Road Graph between hospitals (travel times in minutes)
        addRoad("NDGH", "VSDH", 14);
        addRoad("NDGH", "TCHC", 19);
        addRoad("NDGH", "KTH", 30);
        addRoad("VSDH", "TCHC", 21);
        addRoad("VSDH", "KTH", 26);
        addRoad("TCHC", "KTH", 35);

        // 2. Primary Hospital: NDGH (Nalam District Government Hospital)
        Hospital ndgh = new Hospital("NDGH", "Nalam District Government Hospital", true);
        ndgh.addBed(new ICUBed("NDGH-ICU-01", "NDGH", "ICU-101", true));
        ndgh.addBed(new ICUBed("NDGH-ICU-02", "NDGH", "ICU-102", true));
        ndgh.addBed(new GeneralBed("NDGH-GEN-01", "NDGH", "GEN-201", true));
        ndgh.addBed(new GeneralBed("NDGH-GEN-02", "NDGH", "GEN-202", true));
        ndgh.addBed(new GeneralBed("NDGH-GEN-03", "NDGH", "GEN-203", false));
        ndgh.addBed(new GeneralBed("NDGH-GEN-04", "NDGH", "GEN-204", false));

        ndgh.addDoctor(new Doctor("DOC-01", "Ramesh Kumar", "Critical Care", "NDGH", true));
        ndgh.addDoctor(new Doctor("DOC-02", "Priya Sundaram", "Emergency Medicine", "NDGH", true));
        ndgh.addDoctor(new Doctor("DOC-03", "Anand Varma", "General Medicine", "NDGH", true));
        ndgh.addDoctor(new Doctor("DOC-04", "Kavitha Nair", "General Medicine", "NDGH", true));
        hospitals.put("NDGH", ndgh);

        // 3. Referral Hospital 1: VSDH (Vallam Sub-District Hospital - 14 mins from NDGH)
        Hospital vsdh = new Hospital("VSDH", "Vallam Sub-District Hospital", false);
        vsdh.addBed(new ICUBed("VSDH-ICU-01", "VSDH", "ICU-301", true));
        vsdh.addBed(new ICUBed("VSDH-ICU-02", "VSDH", "ICU-302", false));
        vsdh.addBed(new GeneralBed("VSDH-GEN-01", "VSDH", "GEN-301", true));
        vsdh.addBed(new GeneralBed("VSDH-GEN-02", "VSDH", "GEN-302", true));
        vsdh.addBed(new GeneralBed("VSDH-GEN-03", "VSDH", "GEN-303", false));
        vsdh.addDoctor(new Doctor("DOC-05", "Karthik Rajan", "Critical Care", "VSDH", true));
        vsdh.addDoctor(new Doctor("DOC-06", "Meena Krishnan", "General Medicine", "VSDH", true));
        hospitals.put("VSDH", vsdh);

        // 4. Referral Hospital 2: TCHC (Thoppur Community Health Centre - 19 mins from NDGH)
        Hospital tchc = new Hospital("TCHC", "Thoppur Community Health Centre", false);
        tchc.addBed(new GeneralBed("TCHC-GEN-01", "TCHC", "GEN-501", true));
        tchc.addBed(new GeneralBed("TCHC-GEN-02", "TCHC", "GEN-502", false));
        tchc.addBed(new GeneralBed("TCHC-GEN-03", "TCHC", "GEN-503", false));
        tchc.addDoctor(new Doctor("DOC-07", "Vignesh Balan", "General Medicine", "TCHC", true));
        hospitals.put("TCHC", tchc);

        // 5. Referral Hospital 3: KTH (Kadur Taluk Hospital - 30 mins from NDGH)
        Hospital kth = new Hospital("KTH", "Kadur Taluk Hospital", false);
        kth.addBed(new ICUBed("KTH-ICU-01", "KTH", "ICU-801", true));
        kth.addBed(new ICUBed("KTH-ICU-02", "KTH", "ICU-802", false));
        kth.addBed(new GeneralBed("KTH-GEN-01", "KTH", "GEN-801", true));
        kth.addBed(new GeneralBed("KTH-GEN-02", "KTH", "GEN-802", false));
        kth.addDoctor(new Doctor("DOC-08", "Shalini Devi", "Emergency Medicine", "KTH", true));
        hospitals.put("KTH", kth);

        return ndgh;
    }

    private void addRoad(String hospA, String hospB, int minutes) {
        roadGraph.putIfAbsent(hospA, new LinkedHashMap<>());
        roadGraph.putIfAbsent(hospB, new LinkedHashMap<>());
        roadGraph.get(hospA).put(hospB, minutes);
        roadGraph.get(hospB).put(hospA, minutes);
    }

    // --- Core Operations ---

    public Patient registerPatient(String patientId, String name, int age, String gender,
                                   int severity, String requiredFacility) {
        long seq = patientSequenceCounter++;
        Patient patient = new Patient(patientId, name, age, gender, severity, requiredFacility, seq);
        allPatients.put(patientId, patient);
        waitingQueue.offer(patient);
        return patient;
    }

    /**
     * Dynamic Re-ranking requirement:
     * "Remove and reinsert queued records when their ranking fields change."
     */
    public boolean updatePatientSeverity(String patientId, int newSeverity) {
        Patient patient = allPatients.get(patientId);
        if (patient == null) return false;

        if (waitingQueue.contains(patient)) {
            waitingQueue.remove(patient); // Remove from min-heap
            patient.setSeverity(newSeverity); // Mutate rank
            waitingQueue.offer(patient); // Re-insert to re-heapify
            return true;
        } else {
            patient.setSeverity(newSeverity);
            return true;
        }
    }

    public List<String> processWaitingQueue() {
        List<String> outcomes = new ArrayList<>();
        List<Patient> unserved = new ArrayList<>();

        while (!waitingQueue.isEmpty()) {
            Patient patient = waitingQueue.poll();

            // 1. Check local admission via AdmissionPolicy
            if (admissionPolicy.isEligible(primaryHospital, patient)) {
                Bed bed = primaryHospital.findAvailableBed(patient.getRequiredFacility()).orElseThrow();
                Doctor doc = primaryHospital.findAvailableDoctor(patient.getSeverity(), patient.getRequiredFacility()).orElseThrow();

                primaryHospital.admitPatient(patient, bed, doc);

                String msg = String.format("Admitted locally at %s -> Bed: %s, Doctor: Dr. %s",
                        primaryHospital.getName(), bed.getBedId(), doc.getName());
                history.add(new AdmissionRequest(
                        "REQ-" + System.currentTimeMillis(),
                        AdmissionRequest.Type.ADMISSION,
                        patient.getPatientId(),
                        patient.getName(),
                        primaryHospital.getHospitalId(),
                        bed.getBedId(),
                        doc.getDoctorId(),
                        0,
                        msg
                ));
                outcomes.add("[ADMITTED] " + patient.getName() + " -> " + msg);
            } else {
                // 2. Local admission failed -> Attempt Dijkstra-based referral
                boolean referred = referralService.referPatient(primaryHospital, hospitals.values(), roadGraph, patient, history);
                if (referred) {
                    outcomes.add("[REFERRED] " + patient.getName() + " -> " + patient.getReferralNotes());
                } else {
                    // 3. Both local and referral capacity full -> Keep in queue
                    unserved.add(patient);
                    outcomes.add("[WAITING] " + patient.getName() + " -> Deferred: All suitable beds or doctors occupied.");
                }
            }
        }

        // Re-enqueue unserved patients
        for (Patient p : unserved) {
            waitingQueue.offer(p);
        }

        return outcomes;
    }

    public boolean dischargePatient(String hospitalId, String patientId) {
        Hospital hosp = hospitals.get(hospitalId);
        if (hosp == null) return false;

        Patient patient = allPatients.get(patientId);
        String bedId = (patient != null) ? patient.getAssignedBedId() : null;
        String docId = (patient != null) ? patient.getAssignedDoctorId() : null;

        boolean discharged = hosp.dischargePatient(patientId);
        if (discharged) {
            if (patient != null) {
                patient.setStatus(Patient.Status.DISCHARGED);
                patient.setAssignedBedId(null);
                patient.setAssignedDoctorId(null);
            }

            history.add(new AdmissionRequest(
                    "REQ-" + System.currentTimeMillis(),
                    AdmissionRequest.Type.DISCHARGE,
                    patientId,
                    patient != null ? patient.getName() : "-",
                    hospitalId,
                    bedId,
                    docId,
                    0,
                    "Discharged; bed and doctor released."
            ));

            // Immediate reflection: Released bed auto-evaluates waiting queue!
            processWaitingQueue();
            return true;
        }
        return false;
    }

    public boolean setDoctorDutyStatus(String hospitalId, String doctorId, boolean onDuty) {
        Hospital hosp = hospitals.get(hospitalId);
        if (hosp == null) return false;
        Doctor doc = hosp.getDoctor(doctorId);
        if (doc == null) return false;

        doc.setDutyStatus(onDuty);
        if (onDuty) {
            processWaitingQueue();
        }
        return true;
    }

    public boolean updateRoadTravelTime(String hospA, String hospB, int minutes) {
        if (!roadGraph.containsKey(hospA) || !roadGraph.get(hospA).containsKey(hospB)) {
            return false;
        }
        roadGraph.get(hospA).put(hospB, minutes);
        roadGraph.get(hospB).put(hospA, minutes);
        return true;
    }

    // --- Interactive Console Menu ---

    public void startMenu() {
        System.out.println("================================================================================");
        System.out.println("     GOVERNMENT HOSPITAL EMERGENCY RESOURCE ALLOCATION SYSTEM (NALAM DIST)      ");
        System.out.println("================================================================================");
        System.out.println("  Core Tech: Pure Java SE | Clean OOP | PriorityQueue | HashMap | Graph | Heap  ");
        System.out.println("================================================================================");

        boolean running = true;
        while (running) {
            System.out.println("\n---------------------------------- MAIN MENU -----------------------------------");
            System.out.println(" 1. View Hospital Capacities & Doctor Status");
            System.out.println(" 2. View Road Network & Dijkstra Shortest Travel Times from NDGH");
            System.out.println(" 3. Register New Emergency Patient");
            System.out.println(" 4. Process Waiting Queue (Severity-First Admission & Referrals)");
            System.out.println(" 5. Discharge Patient (Immediate Reflection in Waiting Queue)");
            System.out.println(" 6. Update Doctor Duty Status (Toggle On/Off Duty)");
            System.out.println(" 7. Update Road Travel Time (Manual Road Condition Update)");
            System.out.println(" 8. Update Waiting Patient Urgency (Heap Re-Ranking)");
            System.out.println(" 9. Run Mandatory Test Suite (4 Failure & Contention Cases)");
            System.out.println(" 10. View Admission / Discharge / Referral History (ArrayList)");
            System.out.println("  0. Exit");
            System.out.println("--------------------------------------------------------------------------------");

            System.out.print("Select an option [0-10]: ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> viewHospitals();
                case "2" -> viewRoads();
                case "3" -> handleRegisterPatient();
                case "4" -> handleProcessQueue();
                case "5" -> handleDischarge();
                case "6" -> handleDoctorDuty();
                case "7" -> handleRoadUpdate();
                case "8" -> handleSeverityUpdate();
                case "9" -> runAutomatedTests();
                case "10" -> viewHistory();
                case "0" -> {
                    System.out.println("Exiting system. Stay safe!");
                    running = false;
                }
                default -> System.out.println("Invalid choice!");
            }

            if (running) {
                System.out.println("\nPress ENTER to continue...");
                scanner.nextLine();
            }
        }
    }

    private void viewHospitals() {
        System.out.println("\n=========================== DISTRICT HOSPITAL STATUS ===========================");
        for (Hospital h : hospitals.values()) {
            System.out.println("\n" + h);
            System.out.println("  Beds:");
            for (Bed b : h.getAllBeds()) {
                System.out.printf("    - Bed %-12s Type: %-14s Status: %s\n",
                        b.getBedId(), b.getBedType(),
                        b.isOccupied() ? "OCCUPIED (Pt: " + b.getCurrentPatientId() + ")" : "VACANT");
            }
            System.out.println("  Doctors:");
            for (Doctor d : h.getAllDoctors()) {
                System.out.printf("    - [%s] Dr. %-18s (%-18s) Duty: %-8s Status: %s\n",
                        d.getDoctorId(), d.getName(), d.getSpecialization(),
                        d.isOnDuty() ? "ON-DUTY" : "OFF-DUTY",
                        d.getAssignedPatientId() != null ? "BUSY (Pt: " + d.getAssignedPatientId() + ")" : "FREE");
            }
        }

        System.out.println("\n---------------------------- WAITING QUEUE SUMMARY -----------------------------");
        System.out.printf("Patients currently waiting in PriorityQueue: %d\n", waitingQueue.size());
        List<Patient> snapshot = new ArrayList<>(waitingQueue);
        Collections.sort(snapshot);
        int rank = 1;
        for (Patient p : snapshot) {
            System.out.printf("  #%d [%s] %s | Urgency: %-8s | Facility: %-7s | Arrival Seq: #%d\n",
                    rank++, p.getPatientId(), p.getName(), p.getSeverityLabel(), p.getRequiredFacility(), p.getArrivalSequence());
        }
    }

    private void viewRoads() {
        System.out.println("\n===================== HOSPITAL ROAD NETWORK CONNECTIONS =====================");
        for (var entry : roadGraph.entrySet()) {
            System.out.printf("Hospital %s direct roads:\n", entry.getKey());
            for (var road : entry.getValue().entrySet()) {
                System.out.printf("  -> %s (%d mins)\n", road.getKey(), road.getValue());
            }
        }

        System.out.println("\n----------------- DIJKSTRA SHORTEST TRAVEL TIMES FROM NDGH ------------------");
        RoutePlanner.RouteResult routes = routePlanner.calculateShortestPaths(roadGraph, "NDGH");
        for (Hospital h : hospitals.values()) {
            if (h.isPrimary()) continue;
            int time = routes.getDistanceTo(h.getHospitalId());
            List<String> path = routes.getPathTo("NDGH", h.getHospitalId());
            System.out.printf("  %s (%s): %d mins | Route: %s\n",
                    h.getName(), h.getHospitalId(), time, String.join(" -> ", path));
        }
    }

    private void handleRegisterPatient() {
        System.out.println("\n======================== REGISTER EMERGENCY PATIENT ========================");
        String id = "P-" + (allPatients.size() + 101);
        System.out.print("Enter Patient Name: ");
        String name = scanner.nextLine().trim();
        System.out.print("Enter Age: ");
        int age = Integer.parseInt(scanner.nextLine().trim());
        System.out.print("Enter Gender (M/F/Other): ");
        String gender = scanner.nextLine().trim();

        System.out.println("Select Severity: 1 = CRITICAL, 2 = SERIOUS, 3 = MINOR");
        System.out.print("Choice [1-3]: ");
        int sev = Integer.parseInt(scanner.nextLine().trim());

        System.out.println("Select Facility: 1 = ICU, 2 = General");
        System.out.print("Choice [1-2]: ");
        int facChoice = Integer.parseInt(scanner.nextLine().trim());
        String facility = (facChoice == 1) ? "ICU" : "General";

        Patient p = registerPatient(id, name, age, gender, sev, facility);
        System.out.printf("[SUCCESS] Registered patient %s (#%d in sequence).\n", p.getName(), p.getArrivalSequence());

        System.out.print("Process waiting queue now? (Y/N): ");
        if ("Y".equalsIgnoreCase(scanner.nextLine().trim())) {
            handleProcessQueue();
        }
    }

    private void handleProcessQueue() {
        System.out.println("\n======================== PROCESSING WAITING QUEUE ========================");
        if (waitingQueue.isEmpty()) {
            System.out.println("Waiting queue is empty.");
            return;
        }
        List<String> results = processWaitingQueue();
        for (String r : results) {
            System.out.println("  " + r);
        }
    }

    private void handleDischarge() {
        System.out.println("\n======================== DISCHARGE / RELEASE BED ========================");
        System.out.print("Enter Hospital ID (e.g. NDGH, VSDH): ");
        String hid = scanner.nextLine().trim().toUpperCase();
        System.out.print("Enter Patient ID to discharge: ");
        String pid = scanner.nextLine().trim();

        boolean ok = dischargePatient(hid, pid);
        if (ok) {
            System.out.println("[SUCCESS] Patient discharged. Released bed and doctor immediately evaluated for waiting patients!");
        } else {
            System.out.println("[ERROR] Discharge failed. Check Hospital ID and Patient ID.");
        }
    }

    private void handleDoctorDuty() {
        System.out.println("\n======================== UPDATE DOCTOR DUTY STATUS ========================");
        System.out.print("Enter Hospital ID (e.g. NDGH): ");
        String hid = scanner.nextLine().trim().toUpperCase();
        System.out.print("Enter Doctor ID (e.g. DOC-01): ");
        String did = scanner.nextLine().trim().toUpperCase();
        System.out.print("Set duty (1 = ON-DUTY, 0 = OFF-DUTY): ");
        boolean onDuty = "1".equals(scanner.nextLine().trim());

        try {
            boolean ok = setDoctorDutyStatus(hid, did, onDuty);
            if (ok) {
                System.out.println("[SUCCESS] Doctor duty updated.");
            } else {
                System.out.println("[ERROR] Doctor not found.");
            }
        } catch (IllegalStateException ex) {
            System.out.println("[ERROR] " + ex.getMessage());
        }
    }

    private void handleRoadUpdate() {
        System.out.println("\n======================== UPDATE ROAD TRAVEL TIME ========================");
        System.out.print("Enter Hospital A ID: ");
        String hA = scanner.nextLine().trim().toUpperCase();
        System.out.print("Enter Hospital B ID: ");
        String hB = scanner.nextLine().trim().toUpperCase();
        System.out.print("Enter New Travel Time (minutes): ");
        int mins = Integer.parseInt(scanner.nextLine().trim());

        boolean ok = updateRoadTravelTime(hA, hB, mins);
        if (ok) {
            System.out.printf("[SUCCESS] Updated road time between %s and %s to %d mins.\n", hA, hB, mins);
        } else {
            System.out.println("[ERROR] Direct road connection does not exist.");
        }
    }

    private void handleSeverityUpdate() {
        System.out.println("\n================ UPDATE PATIENT SEVERITY (HEAP RE-RANKING) ================");
        System.out.print("Enter Patient ID: ");
        String pid = scanner.nextLine().trim();
        System.out.println("Select New Severity: 1 = CRITICAL, 2 = SERIOUS, 3 = MINOR");
        System.out.print("Choice [1-3]: ");
        int sev = Integer.parseInt(scanner.nextLine().trim());

        boolean ok = updatePatientSeverity(pid, sev);
        if (ok) {
            System.out.println("[SUCCESS] Severity updated and PriorityQueue re-ranked!");
        } else {
            System.out.println("[ERROR] Patient not found.");
        }
    }

    private void viewHistory() {
        System.out.println("\n============================== EVENT AUDIT HISTORY ==============================");
        if (history.isEmpty()) {
            System.out.println("No history recorded yet.");
            return;
        }
        for (AdmissionRequest rec : history) {
            System.out.println("  " + rec);
        }
    }

    // =========================================================================
    // AUTOMATED TEST SUITE (Verifies 4 Failure & Contention Cases from Rubric)
    // =========================================================================

    public void runAutomatedTests() {
        System.out.println("================================================================================");
        System.out.println("   GOVERNMENT HOSPITAL RESOURCE ALLOCATION - AUTOMATED TEST SUITE   ");
        System.out.println("================================================================================");

        int passed = 0;
        int total = 0;

        // --- TEST CASE 1: No ICU Bed -> Dijkstra Shortest-Path Referral ---
        System.out.println("\nTEST CASE 1: No ICU Bed at Primary Hospital -> Dijkstra Referral");
        HospitalApp app1 = new HospitalApp();
        Hospital p1 = app1.hospitals.get("NDGH");
        p1.getBed("NDGH-ICU-01").admitPatient("EXT-01");
        p1.getBed("NDGH-ICU-02").admitPatient("EXT-02");

        total++;
        if (p1.getAvailableBedsCount(ICUBed.class) == 0) {
            passed++;
            System.out.println("  [PASS] TC1.1: Primary hospital NDGH has 0 available ICU beds.");
        }

        Patient ptCrit = app1.registerPatient("TC1-P01", "Karthik S.", 54, "M", Patient.CRITICAL, "ICU");
        app1.processWaitingQueue();

        total++;
        if (ptCrit.getStatus() == Patient.Status.REFERRED && "VSDH".equals(ptCrit.getAssignedHospitalId())) {
            passed++;
            System.out.println("  [PASS] TC1.2: Critical patient referred to fastest reachable hospital (VSDH @ 14 mins).");
        }

        total++;
        Hospital vsdh = app1.hospitals.get("VSDH");
        Bed resBed = vsdh.getBed(ptCrit.getAssignedBedId());
        if (resBed.isOccupied() && ptCrit.getPatientId().equals(resBed.getCurrentPatientId())) {
            passed++;
            System.out.println("  [PASS] TC1.3: Referral ICU bed reserved immediately.");
        }

        // --- TEST CASE 2: No Matching Doctor -> Admission Blocked; Restores on Duty ---
        System.out.println("\nTEST CASE 2: No Matching Doctor -> Admission Blocked; Restores on Duty");
        HospitalApp app2 = new HospitalApp();
        app2.setDoctorDutyStatus("NDGH", "DOC-01", false);
        app2.setDoctorDutyStatus("NDGH", "DOC-02", false);
        // Fill referral ICU beds to isolate doctor unavailability at NDGH
        for (Hospital h : app2.hospitals.values()) {
            if (!h.isPrimary()) {
                for (Bed b : h.getAllBeds()) {
                    if (b instanceof ICUBed && !b.isOccupied()) b.admitPatient("DUMMY-" + b.getBedId());
                }
            }
        }

        Patient ptDoc = app2.registerPatient("TC2-P01", "Meenakshi S.", 62, "F", Patient.CRITICAL, "ICU");
        app2.processWaitingQueue();

        total++;
        if (ptDoc.getStatus() == Patient.Status.WAITING) {
            passed++;
            System.out.println("  [PASS] TC2.1: Admission blocked due to no matching doctor on duty; patient kept waiting.");
        }

        System.out.println("  [ACTION] Dr. Ramesh Kumar reports ON-DUTY...");
        app2.setDoctorDutyStatus("NDGH", "DOC-01", true);

        total++;
        if (ptDoc.getStatus() == Patient.Status.ADMITTED && "DOC-01".equals(ptDoc.getAssignedDoctorId())) {
            passed++;
            System.out.println("  [PASS] TC2.2: Patient automatically admitted upon doctor duty resumption!");
        }

        // --- TEST CASE 3: Simultaneous Demand for One Bed -> Severity First ---
        System.out.println("\nTEST CASE 3: Simultaneous Demand for One Bed -> Prioritizes Severity First");
        HospitalApp app3 = new HospitalApp();
        Hospital p3 = app3.hospitals.get("NDGH");
        p3.getBed("NDGH-GEN-01").admitPatient("OCC-01");
        p3.getBed("NDGH-GEN-02").admitPatient("OCC-02");
        p3.getBed("NDGH-GEN-03").admitPatient("OCC-03");
        for (Hospital h : app3.hospitals.values()) {
            if (!h.isPrimary()) {
                for (Bed b : h.getAllBeds()) {
                    if (b instanceof GeneralBed && !b.isOccupied()) b.admitPatient("DUMMY-" + b.getBedId());
                }
            }
        }

        Patient ptA = app3.registerPatient("TC3-PT-A", "Patient A (Arrived 1st)", 22, "M", Patient.MINOR, "General");
        Patient ptB = app3.registerPatient("TC3-PT-B", "Patient B (Arrived 2nd)", 48, "F", Patient.SERIOUS, "General");

        app3.processWaitingQueue();

        total++;
        if (ptB.getStatus() == Patient.Status.ADMITTED && ptA.getStatus() == Patient.Status.WAITING) {
            passed++;
            System.out.println("  [PASS] TC3.1: Serious patient admitted first despite arriving second; Minor patient waits.");
        }

        // --- TEST CASE 4: Discharge Enabling Admission ---
        System.out.println("\nTEST CASE 4: Discharge Enabling Admission -> Immediate Reflection");
        HospitalApp app4 = new HospitalApp();
        Hospital p4 = app4.hospitals.get("NDGH");
        for (Bed b : p4.getAllBeds()) {
            if (!b.isOccupied()) b.admitPatient("OCC-" + b.getBedId());
        }
        for (Hospital h : app4.hospitals.values()) {
            if (!h.isPrimary()) {
                for (Bed b : h.getAllBeds()) {
                    if (!b.isOccupied()) b.admitPatient("DUMMY-" + b.getBedId());
                }
            }
        }

        Patient ptWait = app4.registerPatient("TC4-WAIT", "Waiting Pt Mani", 39, "M", Patient.SERIOUS, "General");
        app4.processWaitingQueue();

        total++;
        if (ptWait.getStatus() == Patient.Status.WAITING) {
            passed++;
            System.out.println("  [PASS] TC4.1: Patient waiting because all beds are full.");
        }

        System.out.println("  [ACTION] Discharging occupant from NDGH-GEN-01...");
        app4.dischargePatient("NDGH", "OCC-NDGH-GEN-01");

        total++;
        if (ptWait.getStatus() == Patient.Status.ADMITTED && "NDGH-GEN-01".equals(ptWait.getAssignedBedId())) {
            passed++;
            System.out.println("  [PASS] TC4.2: Waiting patient immediately admitted upon bed discharge!");
        }

        // --- TEST CASE 5: Priority Ties & Dynamic Re-ranking ---
        System.out.println("\nTEST CASE 5: Priority Ties & Dynamic Heap Re-ranking");
        HospitalApp app5 = new HospitalApp();
        Patient pt1 = app5.registerPatient("TC5-P01", "Tie Pt 1", 30, "M", Patient.SERIOUS, "General");
        Patient pt2 = app5.registerPatient("TC5-P02", "Tie Pt 2", 32, "F", Patient.SERIOUS, "General");

        total++;
        if (app5.waitingQueue.peek().getPatientId().equals("TC5-P01")) {
            passed++;
            System.out.println("  [PASS] TC5.1: Priority tie broken deterministically by arrival sequence.");
        }

        System.out.println("  [ACTION] Clinically escalating Pt 2 from SERIOUS to CRITICAL...");
        app5.updatePatientSeverity("TC5-P02", Patient.CRITICAL);

        total++;
        if (app5.waitingQueue.peek().getPatientId().equals("TC5-P02")) {
            passed++;
            System.out.println("  [PASS] TC5.2: Pt 2 moved to head of PriorityQueue after remove-and-reinsert re-ranking!");
        }

        System.out.println("\n================================================================================");
        System.out.printf("   TEST SUITE SUMMARY: %d / %d TESTS PASSED (%.1f%% Success Rate)\n",
                passed, total, (passed * 100.0 / total));
        System.out.println("================================================================================");
    }
}
