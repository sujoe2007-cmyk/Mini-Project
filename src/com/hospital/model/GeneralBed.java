package com.hospital.model;

/**
 * General Ward Bed subclass.
 * Overrides supportsRequirement() to validate standard ward/general admissions.
 */
public class GeneralBed extends Bed {

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
