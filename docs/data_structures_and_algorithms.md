# Data Structures and Algorithms Description and Justification

This document details the selection, purpose, and algorithmic complexity of all data structures and algorithms used in the **Government Hospital Emergency Resource Allocation System**.

---

## 1. Summary Matrix

| Data Structure / Algorithm | Project Component | Primary Purpose | Time Complexity | Space Complexity |
| :--- | :--- | :--- | :--- | :--- |
| **`PriorityQueue` (Min/Max Heap)** | `waitingQueue` | Orders triage intake: Severity first, Arrival order second, Patient ID third. | Enqueue: $O(\log n)$<br>Poll: $O(\log n)$<br>Peek: $O(1)$ | $O(n)$ |
| **`HashMap`** | `allPatients`, `hospitals`, `beds`, `doctors` | Fast, constant-time indexing and lookup of entities by unique string ID. | Average Get/Put: $O(1)$<br>Worst-case: $O(n)$ | $O(n)$ |
| **Weighted Graph (`RoadGraph`)** | `adjacencyList`, `locations` | Models 9 road intersections and transit corridors with travel-time edge weights. | Add Vertex: $O(1)$<br>Add/Update Edge: $O(1)$<br>Query Neighbors: $O(\text{deg}(v))$ | $O(V + E)$ |
| **`ArrayList`** | `auditHistory` | Append-only chronological audit ledger for all admissions, discharges, and referrals. | Append: $O(1)$ amortized<br>Sequential Scan: $O(n)$ | $O(n)$ |
| **Dijkstra's Algorithm + Min-Heap** | `RoutePlanner.calculateShortestPaths` | Computes minimum travel-time paths to referral hospitals after facility filtering. | $O((V + E) \log V)$ | $O(V)$ |

---

## 2. In-Depth Justifications

### 2.1 PriorityQueue (Binary Heap)
- **Justification**: In an emergency department, a standard First-In-First-Out (FIFO) queue causes severe clinical risks because a critical trauma patient arriving at minute 10 would be trapped behind five minor abrasion patients who arrived at minute 5. A `PriorityQueue` implements a priority heap where the root element always yields the patient of greatest clinical need.
- **Deterministic Multi-Key Ordering**:
  ```java
  public int compareTo(Patient other) {
      int severityDiff = Integer.compare(this.severity.getRank(), other.severity.getRank());
      if (severityDiff != 0) return severityDiff;
      int seqDiff = Long.compare(this.arrivalSequence, other.arrivalSequence);
      if (seqDiff != 0) return seqDiff;
      return this.patientId.compareTo(other.patientId);
  }
  ```
- **Dynamic Re-ranking (Heap Invariant Maintenance)**:
  Java's `PriorityQueue` does not automatically re-heapify if an object's internal fields change while inside the heap. To adhere strictly to the specification (*"remove and reinsert queued records when their ranking fields change"*), `HospitalSystemManager` removes the record with `waitingQueue.remove(patient)`, updates the severity attribute, and calls `waitingQueue.offer(patient)`. This guarantees $O(\log n)$ restoration of the heap invariant without silent priority inversions.

---

### 2.2 HashMap
- **Justification**: Emergency operations demand immediate $O(1)$ access to resources without iterating through linear lists. 
- **Applications**:
  - `Map<String, Hospital>`: Resolves primary and secondary hospital metadata by facility code (e.g., `NDGH`, `VSDH`).
  - `Map<String, Bed>`: Provides instant lookup of bed occupancy by hardware ID.
  - `Map<String, Doctor>`: Enables real-time verification of doctor on-duty status and active patient assignment.
  - `Map<String, Patient>`: Ensures fast patient record retrieval during discharges or clinical updates.

---

### 2.3 Weighted Graph (`RoadGraph`)
- **Justification**: Road corridors between urban district hospitals and peripheral taluk centers cannot be represented with simple Euclidean distances. Road topography, traffic conditions, road classes, and hill passes dictate travel duration.
- **Representation**: An adjacency list `Map<String, List<RoadEdge>>` is chosen over an adjacency matrix because the road network is sparse ($|E| \ll |V|^2$, with 9 nodes and ~11 bidirectional road links). The adjacency list consumes $O(V + E)$ space compared to $O(V^2)$ for a matrix and allows fast iteration over incident roads during Dijkstra relaxation.
- **Dynamic Updates**: Manual travel-time adjustments (e.g., due to monsoon landslides or traffic congestion) execute in $O(\text{deg}(u))$ by updating edge weights directly.

---

### 2.4 ArrayList
- **Justification**: Regulatory hospital governance and medical audit protocols require an immutable, append-only chronological history of every resource transaction. An `ArrayList` delivers constant-time amortized $O(1)$ appends and cache-friendly contiguous memory locality when rendering audit logs.

---

### 2.5 Dijkstra's Algorithm with Min-Heap
- **Justification**: When the primary hospital's beds or doctors are exhausted, sending an ambulance to the wrong hospital or an unequipped facility is life-threatening. The referral engine uses a two-phase optimization:
  1. **Facility & Capacity Pre-Filtering**: The system only considers referral candidate hospitals that currently possess an available bed satisfying `bed.supportsRequirement(patient.getRequiredFacility())` and an on-duty medical officer.
  2. **Shortest Travel-Time Route Calculation**: Dijkstra's algorithm runs from the primary hospital's vertex (`L1`) using a Min-Heap (`PriorityQueue<HeapEntry>`). It computes the exact minimum transit time to each candidate location and reconstructs the step-by-step route coordinates.
- **Complexity**: With $|V| = 9$ and $|E| = 11$, Dijkstra executes in microseconds ($\sim O((V + E) \log V)$), ensuring zero latency during emergency transfers.
