package com.hospital.routing;

import java.util.*;

/**
 * Weighted graph representing road connections between locations in Nalam District.
 * Supports bidirectional weighted edges where weights represent travel time in minutes.
 */
public class RoadGraph {

    private final Map<String, Location> locations;
    private final Map<String, List<RoadEdge>> adjacencyList;

    public RoadGraph() {
        this.locations = new LinkedHashMap<>();
        this.adjacencyList = new LinkedHashMap<>();
    }

    public void addLocation(Location location) {
        locations.put(location.getLocationId(), location);
        adjacencyList.putIfAbsent(location.getLocationId(), new ArrayList<>());
    }

    /**
     * Adds an undirected (bidirectional) road connection with travel time in minutes.
     */
    public void addRoad(String locationA, String locationB, int travelTimeMinutes) {
        if (!locations.containsKey(locationA) || !locations.containsKey(locationB)) {
            throw new IllegalArgumentException("Both locations must exist in graph: " + locationA + ", " + locationB);
        }
        adjacencyList.get(locationA).add(new RoadEdge(locationA, locationB, travelTimeMinutes));
        adjacencyList.get(locationB).add(new RoadEdge(locationB, locationA, travelTimeMinutes));
    }

    /**
     * Updates road travel time between two connected locations (both directions).
     * Fulfills project requirement: "Enter travel-time updates manually".
     */
    public boolean updateTravelTime(String locationA, String locationB, int newTravelTimeMinutes) {
        boolean updated = false;
        List<RoadEdge> edgesA = adjacencyList.get(locationA);
        if (edgesA != null) {
            for (RoadEdge edge : edgesA) {
                if (edge.getToLocationId().equals(locationB)) {
                    edge.setTravelTimeMinutes(newTravelTimeMinutes);
                    updated = true;
                }
            }
        }

        List<RoadEdge> edgesB = adjacencyList.get(locationB);
        if (edgesB != null) {
            for (RoadEdge edge : edgesB) {
                if (edge.getToLocationId().equals(locationA)) {
                    edge.setTravelTimeMinutes(newTravelTimeMinutes);
                    updated = true;
                }
            }
        }
        return updated;
    }

    public Location getLocation(String locationId) {
        return locations.get(locationId);
    }

    public Collection<Location> getAllLocations() {
        return Collections.unmodifiableCollection(locations.values());
    }

    public List<RoadEdge> getEdgesFrom(String locationId) {
        return adjacencyList.getOrDefault(locationId, Collections.emptyList());
    }

    public Map<String, List<RoadEdge>> getAdjacencyList() {
        return Collections.unmodifiableMap(adjacencyList);
    }

    public boolean containsLocation(String locationId) {
        return locations.containsKey(locationId);
    }
}
