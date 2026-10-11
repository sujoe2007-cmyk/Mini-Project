# Data Structures and Algorithms Description and Justification

This document details the selection, purpose, and algorithmic complexity of all data structures and algorithms used in the **Government Hospital Emergency Resource Allocation System**.

---

## 1. Algorithmic Complexity Summary Matrix
 
| Algorithm / Data Structure | Implementation Class | Core Project Functionality | Time Complexity (Average / Worst-Case) | Space Complexity |
| :--- | :--- | :--- | :--- | :--- |
| **`PriorityQueue` (Min-Heap)** | `java.util.PriorityQueue<Patient>` | Deterministic multi-key triage ordering by `Severity` (asc rank), `arrivalSequence` (asc), and `patientId` (asc tie-break). | **Enqueue**: $O(\log N)$<br>**Poll (Extract Min)**: $O(\log N)$<br>**Peek**: $O(1)$<br>**Dynamic Re-rank (`remove + offer`)**: $O(N)$ removal + $O(\log N)$ insert | $O(N)$ |
| **`HashMap`** | `java.util.HashMap` / `LinkedHashMap` | Constant-time indexing and lookups for hospitals, beds, doctors, and patients by unique alphanumeric string IDs. | **Get / Put / Remove**: $O(1)$ average, $O(N)$ worst-case under hash collisions | $O(N)$ |
| **Weighted Graph (`RoadGraph`)** | Custom Adjacency List `Map<String, List<RoadEdge>>` | Models district road topology across 9 geographical nodes ($V=9, E=11$). | **Add Vertex**: $O(1)$<br>**Add/Update Edge**: $O(1)$<br>**Incident Edge Query**: $O(\text{deg}(u))$ | $O(V + E)$ |
| **`ArrayList`** | `java.util.ArrayList<AuditRecord>` | Chronological append-only event ledger for system transitions and accountability. | **Append**: Amortized $O(1)$<br>**Sequential Iteration**: $O(N)$ | $O(N)$ |
| **Dijkstra's Shortest Path Algorithm** | `RoutePlanner` with Min-Heap (`PriorityQueue<HeapEntry>`) | Computes fastest reachable eligible referral hospital after facility and bed capacity pre-filtering. | **Path Calculation**: $O((V + E) \log V)$<br>**Path Reconstruction**: $O(V)$ | $O(V)$ |

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
