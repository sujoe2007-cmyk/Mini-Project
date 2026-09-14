package com.hospital.routing;

import java.util.*;

/**
 * Encapsulates the output of Dijkstra's algorithm:
 * shortest distances and reconstructed shortest paths from the source location.
 */
public class DijkstraResult {

    private final String sourceLocationId;
    private final Map<String, Integer> distances;
    private final Map<String, String> predecessors;

    public DijkstraResult(String sourceLocationId, Map<String, Integer> distances, Map<String, String> predecessors) {
        this.sourceLocationId = sourceLocationId;
        this.distances = new HashMap<>(distances);
        this.predecessors = new HashMap<>(predecessors);
    }

    public String getSourceLocationId() {
        return sourceLocationId;
    }

    public boolean hasPathTo(String destinationLocationId) {
        Integer dist = distances.get(destinationLocationId);
        return dist != null && dist < Integer.MAX_VALUE;
    }

    public int getDistanceTo(String destinationLocationId) {
        return distances.getOrDefault(destinationLocationId, Integer.MAX_VALUE);
    }

    /**
     * Reconstructs the sequence of location IDs from source to destination along the shortest path.
     */
    public List<String> getPathTo(String destinationLocationId) {
        if (!hasPathTo(destinationLocationId)) {
            return Collections.emptyList();
        }
        LinkedList<String> path = new LinkedList<>();
        String curr = destinationLocationId;
        while (curr != null) {
            path.addFirst(curr);
            if (curr.equals(sourceLocationId)) {
                break;
            }
            curr = predecessors.get(curr);
        }
        return path;
    }

    public Map<String, Integer> getAllDistances() {
        return Collections.unmodifiableMap(distances);
    }
}
