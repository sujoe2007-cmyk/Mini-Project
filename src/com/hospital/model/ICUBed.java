package com.hospital.model;

/**
 * Intensive Care Unit (ICU) Bed subclass.
 * Overrides supportsRequirement() to validate high-dependency, ventilator, and critical care needs.
 */
public class ICUBed extends Bed {

    private final boolean hasVentilator;
    private final boolean hasAdvancedTelemetry;

    public ICUBed(String bedId, String hospitalId, String bedNumber, boolean hasVentilator, boolean hasAdvancedTelemetry) {
        super(bedId, hospitalId, bedNumber);
        this.hasVentilator = hasVentilator;
        this.hasAdvancedTelemetry = hasAdvancedTelemetry;
    }

    public boolean isHasVentilator() {
        return hasVentilator;
    }

    public boolean isHasAdvancedTelemetry() {
        return hasAdvancedTelemetry;
    }

    @Override
    public String getBedType() {
        return "ICU" + (hasVentilator ? "+VENT" : "");
    }

    @Override
    public boolean supportsRequirement(String facilityRequirement) {
        if (facilityRequirement == null) return false;
        String req = facilityRequirement.trim().toLowerCase();
        return req.equals("icu") || req.equals("ventilator") || req.equals("critical care") || req.equals("critical");
    }
}
