package com.hospital.routing;

import java.util.*;

/**
 * RoutePlanner implements Dijkstra's Shortest Path Algorithm using a Min-Heap (PriorityQueue).
 * Fulfills project requirements:
 * - "Dijkstra's algorithm + min-heap: Fastest reachable hospital after facility and capacity filtering."
 * - "Implement the main graph traversal or allocation logic yourselves."
 */
public class RoutePlanner {

    /**
     * Entry element stored in the Min-Heap for Dijkstra's algorithm.
     * Compares distances in ascending order, using location ID as deterministic tie-breaker.
     */
    private static class HeapEntry implements Comparable<HeapEntry> {
        private final String locationId;
        private final int distance;

        public HeapEntry(String locationId, int distance) {
            this.locationId = locationId;
            this.distance = distance;
        }

        public String getLocationId() {
            return locationId;
        }

        public int getDistance() {
            return distance;
        }

        @Override
        public int compareTo(HeapEntry other) {
            int cmp = Integer.compare(this.distance, other.distance);
            if (cmp != 0) {
                return cmp;
            }
            return this.locationId.compareTo(other.locationId);
        }
    }

    /**
     * Executes Dijkstra's algorithm from the specified source location on the weighted graph.
     * @param graph the road network graph
     * @param sourceLocationId the starting location ID (e.g. Primary Hospital location "L1")
     * @return DijkstraResult containing shortest travel times and path predecessors to all nodes
     */
    public DijkstraResult calculateShortestPaths(RoadGraph graph, String sourceLocationId) {
        Objects.requireNonNull(graph, "graph cannot be null");
        Objects.requireNonNull(sourceLocationId, "sourceLocationId cannot be null");

        if (!graph.containsLocation(sourceLocationId)) {
            throw new IllegalArgumentException("Source location does not exist in graph: " + sourceLocationId);
        }

        Map<String, Integer> distances = new HashMap<>();
        Map<String, String> predecessors = new HashMap<>();

        // Initialize distances: 0 for source, infinity for all other nodes
        for (Location loc : graph.getAllLocations()) {
            distances.put(loc.getLocationId(), Integer.MAX_VALUE);
        }
        distances.put(sourceLocationId, 0);

        // Min-Heap (PriorityQueue) for Dijkstra's exploration
        PriorityQueue<HeapEntry> minHeap = new PriorityQueue<>();
        minHeap.offer(new HeapEntry(sourceLocationId, 0));

        Set<String> visited = new HashSet<>();

        while (!minHeap.isEmpty()) {
            HeapEntry current = minHeap.poll();
            String u = current.getLocationId();
            int currentDist = current.getDistance();

            // Skip stale entries if already relaxed with a shorter distance
            if (currentDist > distances.get(u)) {
                continue;
            }

            visited.add(u);

            // Relax all adjacent outgoing edges
            for (RoadEdge edge : graph.getEdgesFrom(u)) {
                String v = edge.getToLocationId();
                int weight = edge.getTravelTimeMinutes();

                if (distances.get(u) != Integer.MAX_VALUE) {
                    int newDist = distances.get(u) + weight;
                    if (newDist < distances.get(v)) {
                        distances.put(v, newDist);
                        predecessors.put(v, u);
                        minHeap.offer(new HeapEntry(v, newDist));
                    }
                }
            }
        }

        return new DijkstraResult(sourceLocationId, distances, predecessors);
    }
}
