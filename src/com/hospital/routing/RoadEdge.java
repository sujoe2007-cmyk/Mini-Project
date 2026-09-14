package com.hospital.routing;

import java.util.Objects;

/**
 * Represents a weighted edge in the road network graph.
 * Weight is road travel time in minutes.
 */
public class RoadEdge {

    private final String fromLocationId;
    private final String toLocationId;
    private int travelTimeMinutes;

    public RoadEdge(String fromLocationId, String toLocationId, int travelTimeMinutes) {
        this.fromLocationId = Objects.requireNonNull(fromLocationId, "fromLocationId cannot be null");
        this.toLocationId = Objects.requireNonNull(toLocationId, "toLocationId cannot be null");
        if (travelTimeMinutes < 0) {
            throw new IllegalArgumentException("Travel time cannot be negative: " + travelTimeMinutes);
        }
        this.travelTimeMinutes = travelTimeMinutes;
    }

    public String getFromLocationId() {
        return fromLocationId;
    }

    public String getToLocationId() {
        return toLocationId;
    }

    public int getTravelTimeMinutes() {
        return travelTimeMinutes;
    }

    public void setTravelTimeMinutes(int travelTimeMinutes) {
        if (travelTimeMinutes < 0) {
            throw new IllegalArgumentException("Travel time cannot be negative: " + travelTimeMinutes);
        }
        this.travelTimeMinutes = travelTimeMinutes;
    }

    @Override
    public String toString() {
        return String.format("%s -> %s (%d min)", fromLocationId, toLocationId, travelTimeMinutes);
    }
}
