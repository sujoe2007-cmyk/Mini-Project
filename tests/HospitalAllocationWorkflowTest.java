package tests;

import com.hospital.data.DatabaseManager;
import com.hospital.data.SyntheticDataGenerator;
import com.hospital.model.AdmissionRequest;
import com.hospital.model.Bed;
import com.hospital.model.Doctor;
import com.hospital.model.Hospital;
import com.hospital.model.Patient;
import com.hospital.model.Severity;
import com.hospital.service.HospitalSystemManager;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assume;

import java.util.List;

import static org.junit.Assert.*;

public class HospitalAllocationWorkflowTest {

    private HospitalSystemManager manager;

    @Before
    public void setUp() {
        Assume.assumeFalse("Workflow tests must use in-memory state and never write to a database.",
                DatabaseManager.isDatabaseAvailable());
        manager = SyntheticDataGenerator.initializeSystem();
    }

    @Test
    public void admitsLocallyAndDoesNotAdmitTheSamePatientTwice() {
        Patient patient = register("FLOW-LOCAL", Severity.SERIOUS, "General");

        List<AdmissionRequest> admissions = manager.processWaitingQueue();

        assertEquals(1, admissions.size());
        assertEquals(AdmissionRequest.Disposition.ADMITTED_PRIMARY, admissions.get(0).getDisposition());
        assertEquals(Patient.Status.ADMITTED_LOCAL, patient.getStatus());
        assertEquals("NDGH", patient.getAssignedHospitalId());
        assertTrue(manager.processWaitingQueue().isEmpty());
        assertEquals(Patient.Status.ADMITTED_LOCAL, patient.getStatus());
        assertEquals(patient.getPatientId(),
                manager.getPrimaryHospital().getBed(patient.getAssignedBedId()).getCurrentPatientId());
    }

    @Test
    public void refersToReachableHospitalWithRequiredBedAndDoctor() {
        Hospital primary = manager.getPrimaryHospital();
        primary.getBed("NDGH-ICU-01").admitPatient("FIXTURE-ICU-1");
        primary.getBed("NDGH-ICU-02").admitPatient("FIXTURE-ICU-2");
        primary.getDoctor("DOC-01").admitPatient("FIXTURE-ICU-1");
        primary.getDoctor("DOC-02").admitPatient("FIXTURE-ICU-2");
        Patient patient = register("FLOW-REFERRAL", Severity.CRITICAL, "ICU");

        List<AdmissionRequest> admissions = manager.processWaitingQueue();

        assertEquals(1, admissions.size());
        AdmissionRequest referral = admissions.get(0);
        assertEquals(AdmissionRequest.Disposition.REFERRED_AND_RESERVED, referral.getDisposition());
        assertEquals("VSDH", referral.getAllocatedHospitalId());
        assertEquals(Patient.Status.REFERRED, patient.getStatus());
        Hospital destination = manager.getHospital(referral.getAllocatedHospitalId());
        assertTrue(destination.getBed(referral.getAllocatedBedId()).isOccupied());
        assertEquals(patient.getPatientId(),
                destination.getBed(referral.getAllocatedBedId()).getCurrentPatientId());
        assertEquals(patient.getPatientId(),
                destination.getDoctor(referral.getAllocatedDoctorId()).getAssignedPatientId());
    }

    @Test
    public void keepsPatientWaitingAndAllowsExplicitClinicalEscalationWhenNoHospitalIsEligible() {
        occupyAllBeds();
        Patient patient = register("FLOW-NO-CAPACITY", Severity.MINOR, "General");

        List<AdmissionRequest> results = manager.processWaitingQueue();

        assertEquals(1, results.size());
        assertTrue(results.get(0).getDisposition().name().startsWith("QUEUED_"));
        assertEquals(Patient.Status.WAITING, patient.getStatus());
        assertTrue(manager.getWaitingQueue().contains(patient));

        assertTrue(manager.updatePatientSeverity(patient.getPatientId(), Severity.CRITICAL));
        assertEquals(Severity.CRITICAL, patient.getSeverity());
        assertEquals(Patient.Status.WAITING, patient.getStatus());
        List<AdmissionRequest> retried = manager.processWaitingQueue();
        assertEquals(1, retried.size());
        assertEquals(AdmissionRequest.Disposition.QUEUED_NO_BED, retried.get(0).getDisposition());
        assertEquals(Patient.Status.WAITING, patient.getStatus());
        assertEquals(1, manager.getWaitingListSnapshot().size());
    }

    @Test
    public void dischargeReconsidersWaitingPatientAndAllocatesReleasedBed() {
        occupyAllBeds();
        Patient waiting = register("FLOW-DISCHARGE-QUEUE", Severity.SERIOUS, "General");
        manager.processWaitingQueue();
        assertEquals(Patient.Status.WAITING, waiting.getStatus());

        assertTrue(manager.dischargePatient("NDGH", "FIXTURE-NDGH-GEN-04"));

        assertEquals(Patient.Status.ADMITTED_LOCAL, waiting.getStatus());
        assertEquals("NDGH-GEN-04", waiting.getAssignedBedId());
        assertEquals(waiting.getPatientId(),
                manager.getPrimaryHospital().getBed("NDGH-GEN-04").getCurrentPatientId());
        assertFalse(manager.getWaitingQueue().contains(waiting));
    }

    private Patient register(String id, Severity severity, String facility) {
        return manager.registerPatient(id, "Workflow Test Patient", 40, "Unspecified", severity, facility);
    }

    private void occupyAllBeds() {
        for (Hospital hospital : manager.getAllHospitals()) {
            for (Bed bed : hospital.getAllBeds()) {
                if (!bed.isOccupied()) {
                    bed.admitPatient("FIXTURE-" + bed.getBedId());
                }
            }
        }
    }
}
