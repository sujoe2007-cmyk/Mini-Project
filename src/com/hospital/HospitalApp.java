package com.hospital;

import com.hospital.data.SyntheticDataGenerator;
import com.hospital.model.*;
import com.hospital.routing.*;
import com.hospital.service.HospitalSystemManager;
import tests.TestRunner;

import java.util.*;

/**
 * Menu-driven console application for Government Hospital Resource Allocation.
 * Fulfills all user interaction, manual data entry, queue processing, and simulation requirements.
 */
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

        // Waiting Queue Summary
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
