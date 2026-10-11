package com.hospital.service;

import com.hospital.model.*;
import com.hospital.policy.AdmissionPolicy;
import com.hospital.policy.StandardAdmissionPolicy;
import com.hospital.routing.RoadGraph;
import com.hospital.routing.RoutePlanner;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * HospitalSystemManager is the central coordinator for emergency admission,
 * bed/doctor reservation, waiting list triage, and regional referrals.
 *
 * Employs:
 * - PriorityQueue for waiting patients (ordered by severity, arrival sequence, ID)
 * - HashMap for fast ID lookup of patients, hospitals, beds, and doctors
 * - ArrayList for admission, discharge, and referral audit history
 * - AdmissionPolicy for checking local eligibility
 * - ReferralService for Dijkstra-based shortest-path referral routing
 */
public class HospitalSystemManager {

    // 1. PriorityQueue: Waiting patients ordered deterministically by severity and arrival
    private final PriorityQueue<Patient> waitingQueue;

    // 2. HashMaps: Fast lookup by ID
    private final Map<String, Patient> allPatients;
    private final Map<String, Hospital> hospitals;

    // 3. ArrayList: Complete chronological event audit history
    private final List<AuditRecord> auditHistory;

    // Policies and Services
    private final AdmissionPolicy admissionPolicy;
    private final ReferralService referralService;
    private final RoadGraph roadGraph;
    private final Hospital primaryHospital;

    // Sequence counters
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

        // Register primary hospital
        this.hospitals.put(primaryHospital.getHospitalId(), primaryHospital);

        // Register referral hospitals
        if (referralHospitals != null) {
            for (Hospital h : referralHospitals) {
                this.hospitals.put(h.getHospitalId(), h);
            }
        }

        this.admissionPolicy = new StandardAdmissionPolicy();
        RoutePlanner routePlanner = new RoutePlanner();
        this.referralService = new ReferralService(routePlanner, this.roadGraph);
    }

    /**
     * Registers an arriving patient and places them into the PriorityQueue.
     */
    public synchronized Patient registerPatient(String patientId, String name, int age, String gender,
                                                Severity severity, String requiredFacility) {
        if (patientId == null || patientId.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient ID cannot be null or empty.");
        }
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Patient name cannot be null or empty.");
        }
        if (age < 0 || age > 130) {
            throw new IllegalArgumentException("Patient age must be between 0 and 130: " + age);
        }
        if (requiredFacility == null || requiredFacility.trim().isEmpty()) {
            throw new IllegalArgumentException("Required facility cannot be null or empty.");
        }
        Objects.requireNonNull(severity, "severity cannot be null");

        String pId = patientId.trim();
        if (allPatients.containsKey(pId)) {
            throw new IllegalArgumentException("A patient with ID " + pId + " is already registered.");
        }

        long seq = patientSeqCounter.getAndIncrement();
        Patient patient = new Patient(pId, name.trim(), age, gender, severity, requiredFacility.trim(), seq);
        allPatients.put(pId, patient);
        waitingQueue.offer(patient);

        logAudit(AuditRecord.EventType.QUEUE_ENQUEUE, pId, primaryHospital.getHospitalId(),
                null, null, String.format("Registered and enqueued: Urgency=%s, ReqFacility=%s (Seq #%d)",
                        severity, requiredFacility, seq));
        return patient;
    }

    /**
     * Updates the severity of a patient.
     * Demonstrates explicit remove-and-reinsert into PriorityQueue to maintain heap invariant:
     * "Priority ties must have a deterministic order; remove and reinsert queued records when their ranking fields change."
     */
    public synchronized boolean updatePatientSeverity(String patientId, Severity newSeverity) {
        if (patientId == null || patientId.trim().isEmpty() || newSeverity == null) {
            return false;
        }
        Patient patient = allPatients.get(patientId.trim());
        if (patient == null) {
            return false;
        }

        if (waitingQueue.contains(patient)) {
            // Remove from PriorityQueue
            waitingQueue.remove(patient);
            // Mutate ranking field
            Severity old = patient.getSeverity();
            patient.setSeverity(newSeverity);
            // Reinsert to re-heapify
            waitingQueue.offer(patient);

            logAudit(AuditRecord.EventType.QUEUE_REORDER, patient.getPatientId(), primaryHospital.getHospitalId(),
                    null, null, String.format("Severity updated from %s to %s; re-queued with updated priority",
                            old, newSeverity));
            return true;
        } else {
            patient.setSeverity(newSeverity);
            return true;
        }
    }

    /**
     * Processes the waiting queue:
     * Evaluates highest priority patients first.
     * 1. Attempts local admission at primary hospital via AdmissionPolicy (requires Bed + Doctor).
     * 2. If local admission fails, invokes ReferralService (Dijkstra + min-heap) to refer to fastest eligible hospital.
     * 3. If referral also cannot accommodate, patient remains in waiting queue.
     *
     * @return List of AdmissionRequest transaction results
     */
    public synchronized List<AdmissionRequest> processWaitingQueue() {
        List<AdmissionRequest> results = new ArrayList<>();
        List<Patient> unservedPatients = new ArrayList<>();
        int reqIdCounter = 1;

        while (!waitingQueue.isEmpty()) {
            Patient patient = waitingQueue.poll();
            String reqId = "REQ-" + System.currentTimeMillis() + "-" + (reqIdCounter++);
            AdmissionRequest request = new AdmissionRequest(reqId, patient, primaryHospital.getHospitalId());

            // 1. Try local admission at Primary Hospital
            AdmissionPolicy.PolicyResult policyResult = admissionPolicy.evaluate(primaryHospital, patient);

            if (policyResult.isEligible()) {
                // Local admission succeeded
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
                // Local admission failed -> Attempt referral to fastest reachable hospital
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
                    // Both local admission and referral failed -> Patient must wait
                    request.setDisposition(policyResult.getReason().contains("bed")
                            ? AdmissionRequest.Disposition.QUEUED_NO_BED
                            : AdmissionRequest.Disposition.QUEUED_NO_DOCTOR);
                    request.setRemarks("Admission/Referral deferred: " + policyResult.getReason() + " | Referral error: " + referralResult.getMessage());
                    unservedPatients.add(patient);
                    results.add(request);
                }
            }
        }

        // Reinsert unserved patients back into the PriorityQueue
        for (Patient unserved : unservedPatients) {
            waitingQueue.offer(unserved);
        }

        return results;
    }

    /**
     * Discharges a patient from a hospital, liberating the bed and doctor.
     * Automatically triggers re-evaluation of the waiting queue so released beds
     * are immediately reflected and allocated to the highest-urgency waiting patient.
     */
    public synchronized boolean dischargePatient(String hospitalId, String patientId) {
        if (hospitalId == null || patientId == null) {
            return false;
        }
        Hospital hospital = hospitals.get(hospitalId.trim());
        if (hospital == null) {
            return false;
        }

        Patient patient = allPatients.get(patientId.trim());
        String bedId = (patient != null) ? patient.getAssignedBedId() : null;
        String docId = (patient != null) ? patient.getAssignedDoctorId() : null;

        boolean discharged = hospital.dischargePatient(patientId.trim());
        if (discharged) {
            if (patient != null) {
                patient.setStatus(Patient.Status.DISCHARGED);
                patient.setAssignedBedId(null);
                patient.setAssignedDoctorId(null);
            }

            logAudit(AuditRecord.EventType.DISCHARGE, patientId.trim(), hospitalId.trim(), bedId, docId,
                    "Patient " + patientId.trim() + " discharged from " + hospital.getName() + "; Bed and Doctor released.");

            // Immediate reflection: Released bed triggers allocation attempt for waiting list!
            processWaitingQueue();
            return true;
        }
        return false;
    }

    /**
     * Updates doctor duty status.
     * Fulfills: "Enter doctor duty status manually".
     */
    public synchronized boolean setDoctorDutyStatus(String hospitalId, String doctorId, boolean onDuty) {
        if (hospitalId == null || doctorId == null) return false;
        Hospital hospital = hospitals.get(hospitalId.trim());
        if (hospital == null) return false;
        Doctor doctor = hospital.getDoctor(doctorId.trim());
        if (doctor == null) return false;

        doctor.setDutyStatus(onDuty);
        logAudit(AuditRecord.EventType.DOCTOR_DUTY_CHANGE, null, hospitalId.trim(), null, doctorId.trim(),
                "Dr. " + doctor.getName() + " duty status set to " + (onDuty ? "ON-DUTY" : "OFF-DUTY"));

        // If a doctor reported for duty, attempt to process waiting queue
        if (onDuty) {
            processWaitingQueue();
        }
        return true;
    }

    /**
     * Updates road travel time in the weighted graph.
     * Fulfills: "Enter travel-time updates manually".
     */
    public synchronized boolean updateTravelTime(String locationA, String locationB, int newTravelTimeMinutes) {
        if (locationA == null || locationB == null || newTravelTimeMinutes < 0) return false;
        return roadGraph.updateTravelTime(locationA.trim(), locationB.trim(), newTravelTimeMinutes);
    }

    private void logAudit(AuditRecord.EventType type, String patientId, String hospitalId,
                          String bedId, String doctorId, String details) {
        auditHistory.add(new AuditRecord(auditSeqCounter.getAndIncrement(), type,
                patientId, hospitalId, bedId, doctorId, details));
    }

    // Getters
    public synchronized PriorityQueue<Patient> getWaitingQueue() {
        return waitingQueue;
    }

    public synchronized List<Patient> getWaitingListSnapshot() {
        // Return a copy sorted in heap order for display without disturbing the priority queue
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
