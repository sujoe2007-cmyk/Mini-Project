package com.hospital.policy;

import com.hospital.model.Bed;
import com.hospital.model.Doctor;
import com.hospital.model.Hospital;
import com.hospital.model.Patient;

import java.util.Optional;

/**
 * Abstraction layer: AdmissionPolicy abstracts the rules determining patient eligibility for admission.
 */
public interface AdmissionPolicy {

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

    /**
     * Evaluates whether a patient can be admitted to the given hospital under this policy.
     */
    PolicyResult evaluate(Hospital hospital, Patient patient);
}
