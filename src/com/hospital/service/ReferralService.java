package com.hospital.service;

import com.hospital.model.*;
import com.hospital.routing.*;

import java.util.*;

/**
 * ReferralService orchestrates hospital referrals when local admission fails.
 * Composition: ReferralService uses RoutePlanner and RoadGraph.
 *
 * Algorithm / Workflow:
 * 1. Filter referral hospitals for facility eligibility (bed.supportsRequirement) and capacity.
 * 2. If candidates exist, execute Dijkstra's algorithm from primary hospital location.
 * 3. Select the fastest reachable eligible hospital (minimum travel time).
 * 4. Reserve the bed immediately before confirming referral.
 */
public class ReferralService {

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

    /**
     * Finds and reserves a bed at the fastest reachable eligible referral hospital.
     */
    public ReferralResult referPatient(Hospital primaryHospital,
                                       Collection<Hospital> allHospitals,
                                       Patient patient) {
        Objects.requireNonNull(primaryHospital, "primaryHospital cannot be null");
        Objects.requireNonNull(patient, "patient cannot be null");

        // 1. Filter referral hospitals having:
        //    a) Not the primary hospital
        //    b) An available bed satisfying supportsRequirement(requiredFacility)
        //    c) An available doctor to receive patient
        List<Hospital> eligibleCandidates = new ArrayList<>();
        Map<String, Bed> candidateBeds = new HashMap<>();
        Map<String, Doctor> candidateDoctors = new HashMap<>();

        for (Hospital h : allHospitals) {
            if (h.getHospitalId().equals(primaryHospital.getHospitalId())) {
                continue; // Skip primary
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

        // 2. Execute Dijkstra's Algorithm + Min-Heap from primary hospital location
        DijkstraResult dijkstraResult = routePlanner.calculateShortestPaths(roadGraph, primaryHospital.getLocationId());

        // 3. Select the fastest reachable candidate hospital (deterministic tie-breaking by ID)
        Hospital fastestHospital = null;
        int minTravelTime = Integer.MAX_VALUE;

        for (Hospital candidate : eligibleCandidates) {
            int travelTime = dijkstraResult.getDistanceTo(candidate.getLocationId());
            if (travelTime < minTravelTime) {
                minTravelTime = travelTime;
                fastestHospital = candidate;
            } else if (travelTime == minTravelTime && fastestHospital != null) {
                // Deterministic tie-breaker
                if (candidate.getHospitalId().compareTo(fastestHospital.getHospitalId()) < 0) {
                    fastestHospital = candidate;
                }
            }
        }

        if (fastestHospital == null || minTravelTime == Integer.MAX_VALUE) {
            return ReferralResult.failure("No reachable route found to any eligible referral hospital.");
        }

        // 4. Reserve bed and doctor immediately before confirmation
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
