package com.hospital.policy;

import com.hospital.model.Bed;
import com.hospital.model.Doctor;
import com.hospital.model.Hospital;
import com.hospital.model.Patient;

import java.util.Optional;

/**
 * Standard hospital admission policy implementing AdmissionPolicy.
 * Strict rule: Both a suitable bed AND an appropriate on-duty doctor must be simultaneously available.
 */
public class StandardAdmissionPolicy implements AdmissionPolicy {

    @Override
    public PolicyResult evaluate(Hospital hospital, Patient patient) {
        if (hospital == null || patient == null) {
            return new PolicyResult(false, null, null, "Hospital or Patient is null");
        }

        // 1. Check for required bed supporting the facility
        Optional<Bed> availableBedOpt = hospital.findAvailableBed(patient.getRequiredFacility());
        if (availableBedOpt.isEmpty()) {
            return PolicyResult.rejectedNoBed(patient.getRequiredFacility());
        }

        // 2. Check for an on-duty, unassigned doctor capable of treating the patient's severity/needs
        Optional<Doctor> availableDoctorOpt = hospital.findAvailableDoctor(patient.getSeverity(), patient.getRequiredFacility());
        if (availableDoctorOpt.isEmpty()) {
            return PolicyResult.rejectedNoDoctor("No on-duty doctor available matching clinical requirements (" +
                    patient.getSeverity() + " / " + patient.getRequiredFacility() + ").");
        }

        // 3. Both resources are confirmed
        return PolicyResult.approved(availableBedOpt.get(), availableDoctorOpt.get());
    }
}
