package tests;

import com.hospital.data.SyntheticDataGenerator;
import com.hospital.model.*;
import com.hospital.service.HospitalSystemManager;

import java.util.List;

/**
 * Automated Verification Suite covering all mandatory test deliverables:
 * 1. No ICU Bed (Triggers Dijkstra Shortest-Path Referral & Bed Reservation)
 * 2. No Matching Doctor (Blocks Admission; Patient Queued; Resumes upon Duty Toggle)
 * 3. Simultaneous Demand for One Bed (Severity first, Arrival order second)
 * 4. Discharge Enabling Admission (Immediate Reflection in Waiting List)
 * 5. Deterministic Priority Ties & Dynamic Re-ranking
 */
public class TestRunner {

    private static int passedTests = 0;
    private static int totalTests = 0;

    public static void main(String[] args) {
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

    /**
     * Test Case 1: No ICU Bed at Primary Hospital
     * Both ICU beds at NDGH are occupied.
     * Critical patient arrives needing ICU.
     * System invokes ReferralService, runs Dijkstra to find fastest reachable hospital (VSDH @ 14 mins),
     * and reserves its bed immediately.
     */
    private static void runTestCase1_NoICUBed_TriggersReferral() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 1: No ICU Bed at Primary Hospital -> Dijkstra Shortest-Path Referral");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();
        Hospital primary = manager.getPrimaryHospital();

        // Step 1: Artificially occupy all ICU beds at NDGH
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

        // Step 2: Critical emergency patient arrives requiring ICU
        Patient criticalPt = manager.registerPatient("TC1-P01", "Karthik Subramanian", 54, "M",
                Severity.CRITICAL, "ICU");

        // Step 3: Process waiting queue
        List<AdmissionRequest> results = manager.processWaitingQueue();
        AdmissionRequest req = results.get(0);

        // Verification:
        // NDGH has 0 ICU beds -> ReferralService checks VSDH (14 min via L1->L2->L3) vs KTH (30 min)
        // VSDH is fastest reachable eligible hospital
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

    /**
     * Test Case 2: No Matching Doctor
     * ICU Bed is free, but all Critical Care doctors are off-duty.
     * Patient cannot be admitted locally; remains waiting in queue.
     * When a doctor returns to duty, admission is immediately granted.
     */
    private static void runTestCase2_NoMatchingDoctor_BlocksAdmission() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 2: No Matching Doctor -> Admission Blocked & Queued; Restores on Duty");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();
        Hospital primary = manager.getPrimaryHospital();

        // Step 1: Set both Critical Care specialists off-duty
        manager.setDoctorDutyStatus("NDGH", "DOC-01", false);
        manager.setDoctorDutyStatus("NDGH", "DOC-02", false);

        // Step 2: Also occupy referral ICU beds so referral cannot mask the doctor shortage
        for (Hospital h : manager.getAllHospitals()) {
            if (!h.isPrimary()) {
                for (Bed b : h.getAllBeds()) {
                    if (b instanceof ICUBed && !b.isOccupied()) {
                        b.admitPatient("DUMMY-" + b.getBedId());
                    }
                }
            }
        }

        // Verify ICU bed is available locally
        assertTrue("TC2.1", primary.getAvailableBedsCount(ICUBed.class) > 0,
                "Primary hospital has free ICU bed.");

        // Step 3: Critical patient arrives requiring ICU
        Patient patient = manager.registerPatient("TC2-P01", "Meenakshi Sundaram", 62, "F",
                Severity.CRITICAL, "ICU");

        // Step 4: Process queue -> Admission must fail due to doctor unavailability
        List<AdmissionRequest> results = manager.processWaitingQueue();
        AdmissionRequest req = results.get(0);

        assertTrue("TC2.2", req.getDisposition() == AdmissionRequest.Disposition.QUEUED_NO_DOCTOR,
                "Admission rejected due to doctor unavailability: " + req.getRemarks());
        assertTrue("TC2.3", patient.getStatus() == Patient.Status.WAITING,
                "Patient remains safely queued in WAITING status.");

        // Step 5: Doctor reports for duty
        System.out.println("  [ACTION] Dr. Ramesh Kumar (DOC-01) reports on duty...");
        manager.setDoctorDutyStatus("NDGH", "DOC-01", true);

        // Doctor reporting on duty auto-triggers queue processing!
        assertTrue("TC2.4", patient.getStatus() == Patient.Status.ADMITTED_LOCAL,
                "Patient automatically admitted upon doctor duty resumption!");
        assertTrue("TC2.5", "DOC-01".equals(patient.getAssignedDoctorId()),
                "Doctor DOC-01 assigned to patient.");
    }

    /**
     * Test Case 3: Simultaneous Demand for One Bed
     * Only 1 General bed remains.
     * Patient 1 (Minor, arrival seq 10) arrives FIRST.
     * Patient 2 (Serious, arrival seq 11) arrives SECOND.
     * Queue processes Severity first -> Patient 2 gets the bed; Patient 1 waits.
     */
    private static void runTestCase3_SimultaneousDemand_PrioritizesSeverityFirst() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 3: Simultaneous Demand for 1 Bed -> Process Severity First, Arrival Second");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();
        Hospital primary = manager.getPrimaryHospital();

        // Fill all General beds except one (NDGH-GEN-04)
        primary.getBed("NDGH-GEN-01").admitPatient("OCC-01");
        primary.getBed("NDGH-GEN-02").admitPatient("OCC-02");
        primary.getBed("NDGH-GEN-03").admitPatient("OCC-03");

        // Fill referral general beds to isolate local contention
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

        // Patient A arrives FIRST (Minor severity)
        Patient ptA = manager.registerPatient("TC3-PT-A", "Patient A (Arrived 1st)", 22, "M",
                Severity.MINOR, "General");

        // Patient B arrives SECOND (Serious severity)
        Patient ptB = manager.registerPatient("TC3-PT-B", "Patient B (Arrived 2nd)", 48, "F",
                Severity.SERIOUS, "General");

        assertTrue("TC3.2", ptA.getArrivalSequence() < ptB.getArrivalSequence(),
                "Pt A arrived earlier (Seq " + ptA.getArrivalSequence() + " < Seq " + ptB.getArrivalSequence() + ").");

        // Process waiting queue
        manager.processWaitingQueue();

        assertTrue("TC3.3", ptB.getStatus() == Patient.Status.ADMITTED_LOCAL,
                "Patient B (SERIOUS) was admitted first due to higher clinical severity!");
        assertTrue("TC3.4", ptA.getStatus() == Patient.Status.WAITING,
                "Patient A (MINOR) kept waiting as capacity exhausted.");
    }

    /**
     * Test Case 4: A Discharge Enabling Admission
     * All beds full; a patient is waiting in queue.
     * When a patient is discharged, the released bed is immediately reflected
     * and allocated to the waiting patient.
     */
    private static void runTestCase4_DischargeEnablesWaitingAdmission() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 4: Discharge Enabling Admission -> Immediate Waiting List Reflection");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();
        Hospital primary = manager.getPrimaryHospital();

        // Fill all beds
        for (Bed b : primary.getAllBeds()) {
            if (!b.isOccupied()) {
                b.admitPatient("OCC-" + b.getBedId());
            }
        }

        // Fill referral beds
        for (Hospital h : manager.getAllHospitals()) {
            if (!h.isPrimary()) {
                for (Bed b : h.getAllBeds()) {
                    if (!b.isOccupied()) {
                        b.admitPatient("DUMMY-" + b.getBedId());
                    }
                }
            }
        }

        // Patient arrives and must wait
        Patient waitingPt = manager.registerPatient("TC4-WAIT", "Waiting Patient Mani", 39, "M",
                Severity.SERIOUS, "General");
        manager.processWaitingQueue();

        assertTrue("TC4.1", waitingPt.getStatus() == Patient.Status.WAITING,
                "Patient is waiting due to zero available beds.");

        // Discharge an occupant from NDGH-GEN-01
        System.out.println("  [ACTION] Discharging occupant OCC-NDGH-GEN-01 from NDGH-GEN-01...");
        boolean discharged = manager.dischargePatient("NDGH", "OCC-NDGH-GEN-01");
        assertTrue("TC4.2", discharged, "Occupant discharged successfully.");

        // Verify that discharge immediately triggered re-evaluation and admitted the waiting patient
        assertTrue("TC4.3", waitingPt.getStatus() == Patient.Status.ADMITTED_LOCAL,
                "Waiting patient immediately admitted upon bed discharge!");
        assertTrue("TC4.4", "NDGH-GEN-01".equals(waitingPt.getAssignedBedId()),
                "Assigned to the newly vacated bed NDGH-GEN-01.");
    }

    /**
     * Test Case 5: Priority Ties and Dynamic Re-ranking
     * Demonstrates deterministic tie-breaking and heap re-ordering via remove/re-insert.
     */
    private static void runTestCase5_PriorityTiesAndDynamicReranking() {
        System.out.println("\n--------------------------------------------------------------------------------");
        System.out.println("TEST CASE 5: Priority Ties & Dynamic Re-ranking (Remove & Reinsert Heap)");
        System.out.println("--------------------------------------------------------------------------------");

        HospitalSystemManager manager = SyntheticDataGenerator.initializeSystem();

        // 1. Two patients with identical severity (SERIOUS)
        Patient pt1 = manager.registerPatient("TC5-P01", "Tie Pt 1", 30, "M", Severity.SERIOUS, "General");
        Patient pt2 = manager.registerPatient("TC5-P02", "Tie Pt 2", 32, "F", Severity.SERIOUS, "General");

        List<Patient> snapshot = manager.getWaitingListSnapshot();
        assertTrue("TC5.1", snapshot.get(0).getPatientId().equals("TC5-P01"),
                "Priority tie broken deterministically by arrival sequence (Pt 1 before Pt 2).");

        // 2. Dynamically escalate Pt 2 to CRITICAL
        System.out.println("  [ACTION] Clinically escalating Pt 2 from SERIOUS to CRITICAL...");
        boolean updated = manager.updatePatientSeverity("TC5-P02", Severity.CRITICAL);
        assertTrue("TC5.2", updated, "Severity successfully updated with heap re-insertion.");

        List<Patient> updatedSnapshot = manager.getWaitingListSnapshot();
        assertTrue("TC5.3", updatedSnapshot.get(0).getPatientId().equals("TC5-P02"),
                "Pt 2 is now at head of PriorityQueue after dynamic re-ranking!");
    }
}
