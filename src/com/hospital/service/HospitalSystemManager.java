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
    private final com.hospital.data.HospitalDao hospitalDao;

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
        this.hospitalDao = new com.hospital.data.HospitalDao();

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

        // Attempt initial database synchronization / state restoration if MySQL is available
        initializeDatabaseIfAvailable();
    }

    private void initializeDatabaseIfAvailable() {
        if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
            try {
                hospitalDao.initializeSchema();
                for (Hospital h : hospitals.values()) {
                    hospitalDao.syncHospitalStructure(h);
                }
                restoreStateFromDatabase();
            } catch (Exception e) {
                System.err.println("[HospitalSystemManager] Notice: Database sync encountered issue: " + e.getMessage());
            }
        }
    }

    /**
     * Restores state from MySQL database into in-memory collections on startup.
     */
    public synchronized void restoreStateFromDatabase() {
        if (!com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
            return;
        }
        try {
            List<Patient> dbPatients = hospitalDao.findAllPatients();
            hospitalDao.restoreHospitalResourceState(hospitals.values());
            for (Patient p : dbPatients) {
                allPatients.put(p.getPatientId(), p);
                if (p.getArrivalSequence() >= patientSeqCounter.get()) {
                    patientSeqCounter.set(p.getArrivalSequence() + 1);
                }
                if (p.getStatus() == Patient.Status.WAITING) {
                    if (!waitingQueue.contains(p)) {
                        waitingQueue.offer(p);
                    }
                } else if (p.getStatus() == Patient.Status.ADMITTED_LOCAL || p.getStatus() == Patient.Status.REFERRED) {
                    Hospital h = hospitals.get(p.getAssignedHospitalId());
                    if (h != null && p.getAssignedBedId() != null) {
                        Bed bed = h.getBed(p.getAssignedBedId());
                        if (bed != null && !bed.isOccupied()) {
                            bed.admitPatient(p.getPatientId());
                        }
                    }
                    if (h != null && p.getAssignedDoctorId() != null) {
                        Doctor doctor = h.getDoctor(p.getAssignedDoctorId());
                        if (doctor != null && doctor.getAssignedPatientId() == null) {
                            doctor.admitPatient(p.getPatientId());
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.err.println("[HospitalSystemManager] Warning: Failed to restore state from MySQL: " + e.getMessage());
        }
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

        if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
            try {
                hospitalDao.savePatient(patient);
            } catch (java.sql.SQLException e) {
                patientSeqCounter.decrementAndGet();
                throw new IllegalStateException("Patient was not registered because database persistence failed.", e);
            }
        }

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
            Severity old = patient.getSeverity();
            waitingQueue.remove(patient);
            patient.setSeverity(newSeverity);

            if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
                try {
                    hospitalDao.savePatient(patient);
                } catch (java.sql.SQLException e) {
                    patient.setSeverity(old);
                    waitingQueue.offer(patient);
                    throw new IllegalStateException("Patient severity was not updated because database persistence failed.", e);
                }
            }

            waitingQueue.offer(patient);
            logAudit(AuditRecord.EventType.QUEUE_REORDER, patient.getPatientId(), primaryHospital.getHospitalId(),
                    null, null, String.format("Severity updated from %s to %s; re-queued with updated priority",
                            old, newSeverity));
            return true;
        } else {
            Severity old = patient.getSeverity();
            patient.setSeverity(newSeverity);
            if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
                try {
                    hospitalDao.savePatient(patient);
                } catch (java.sql.SQLException e) {
                    patient.setSeverity(old);
                    throw new IllegalStateException("Patient severity was not updated because database persistence failed.", e);
                }
            }
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

                if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
                    try {
                        hospitalDao.recordAdmissionTransaction(request, patient, bed, doc);
                    } catch (Exception e) {
                        rollbackFailedAdmission(patient, primaryHospital, unservedPatients);
                        throw new IllegalStateException("Admission was rolled back because database persistence failed.", e);
                    }
                }

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

                    if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
                        try {
                            hospitalDao.recordAdmissionTransaction(request, patient,
                                    referralResult.getReservedBed(), referralResult.getAssignedDoctor());
                        } catch (Exception e) {
                            rollbackFailedAdmission(patient, referralResult.getReferralHospital(), unservedPatients);
                            throw new IllegalStateException("Referral was rolled back because database persistence failed.", e);
                        }
                    }

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

    private void rollbackFailedAdmission(Patient patient, Hospital hospital, List<Patient> unservedPatients) {
        hospital.dischargePatient(patient.getPatientId());
        patient.setStatus(Patient.Status.WAITING);
        patient.setAssignedHospitalId(null);
        patient.setAssignedBedId(null);
        patient.setAssignedDoctorId(null);
        patient.setReferralNotes(null);
        for (Patient unserved : unservedPatients) {
            waitingQueue.offer(unserved);
        }
        waitingQueue.offer(patient);
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

        if (patient == null) {
            for (Bed bed : hospital.getAllBeds()) {
                if (patientId.trim().equals(bed.getCurrentPatientId())) {
                    bedId = bed.getBedId();
                    break;
                }
            }
            for (Doctor doctor : hospital.getAllDoctors()) {
                if (patientId.trim().equals(doctor.getAssignedPatientId())) {
                    docId = doctor.getDoctorId();
                    break;
                }
            }
            if (bedId == null) {
                return false;
            }
            if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
                try {
                    if (hospitalDao.hasPersistedResourceAssignment(patientId.trim())) {
                        throw new IllegalStateException("A persisted resource assignment has no restored patient record.");
                    }
                } catch (java.sql.SQLException e) {
                    throw new IllegalStateException("Could not verify the persisted resource assignment before discharge.", e);
                }
            }

            boolean discharged = hospital.dischargePatient(patientId.trim());
            if (discharged) {
                logAudit(AuditRecord.EventType.DISCHARGE, patientId.trim(), hospitalId.trim(), bedId, docId,
                        "In-memory-only patient " + patientId.trim() + " discharged from " + hospital.getName() + ".");
                processWaitingQueue();
            }
            return discharged;
        }

        if (bedId == null) {
            return false;
        }
        if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
            try {
                hospitalDao.recordDischargeTransaction(hospitalId.trim(), patientId.trim(), bedId, docId);
            } catch (java.sql.SQLException e) {
                System.err.println("[HospitalSystemManager] Discharge transaction failed for patient " +
                        patientId.trim() + ": " + e.getMessage());
                throw new IllegalStateException("Discharge was not completed because database persistence failed.", e);
            }
        }

        boolean discharged = hospital.dischargePatient(patientId.trim());
        if (discharged) {
            if (patient != null) {
                patient.setStatus(Patient.Status.DISCHARGED);
                patient.setAssignedHospitalId(null);
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

        boolean previousDutyStatus = doctor.isOnDuty();
        doctor.setDutyStatus(onDuty);
        if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
            try {
                hospitalDao.updateDoctorDutyStatus(doctor.getDoctorId(), onDuty);
            } catch (java.sql.SQLException e) {
                doctor.setDutyStatus(previousDutyStatus);
                throw new IllegalStateException("Doctor duty status was not updated because database persistence failed.", e);
            }
        }

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
        AuditRecord rec = new AuditRecord(auditSeqCounter.getAndIncrement(), type,
                patientId, hospitalId, bedId, doctorId, details);
        auditHistory.add(rec);

        if (com.hospital.data.DatabaseManager.isDatabaseAvailable()) {
            try {
                hospitalDao.saveAuditRecord(rec);
            } catch (Exception e) {
                System.err.println("[HospitalSystemManager] Warning: Audit record persistence failed: " + e.getMessage());
            }
        }
    }

    public com.hospital.data.HospitalDao getHospitalDao() {
        return hospitalDao;
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
