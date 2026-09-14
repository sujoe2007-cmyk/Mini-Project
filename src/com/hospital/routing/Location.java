package com.hospital.routing;

import java.util.Objects;

/**
 * Represents a geographical node/location in Nalam District.
 */
public class Location {

    private final String locationId;
    private final String name;
    private final String description;

    public Location(String locationId, String name, String description) {
        this.locationId = Objects.requireNonNull(locationId, "locationId cannot be null");
        this.name = Objects.requireNonNull(name, "name cannot be null");
        this.description = description;
    }

    public String getLocationId() {
        return locationId;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Location location)) return false;
        return Objects.equals(locationId, location.locationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(locationId);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s (%s)", locationId, name, description != null ? description : "");
    }
}
