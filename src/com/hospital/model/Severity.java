package com.hospital.model;

/**
 * Severity level assigned by the emergency triage operator.
 * Ordered by clinical urgency: CRITICAL > SERIOUS > MINOR.
 */
public enum Severity {
    CRITICAL(1, "Critical", "Immediate life threat; requires urgent resuscitation or ICU care"),
    SERIOUS(2, "Serious", "Severe condition; requires timely acute intervention and monitoring"),
    MINOR(3, "Minor", "Non-life-threatening condition; stable and can wait for standard general bed");

    private final int rank;
    private final String displayName;
    private final String description;

    Severity(int rank, String displayName, String description) {
        this.rank = rank;
        this.displayName = displayName;
        this.description = description;
    }

    public int getRank() {
        return rank;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
