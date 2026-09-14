# Problem Analysis: Government Hospital Emergency Resource Allocation System

## 1. Case Study & Problem Background
A fictional government hospital located in the **Nalam District** receives emergency patients continuously throughout the 24-hour cycle. In many district public healthcare facilities, duty coordinators manage emergency intakes using paper-based registers to track:
1. Patient admissions and triage waiting queues.
2. Bed allocations across General Wards and Intensive Care Units (ICU).
3. On-duty doctors and specialists.
4. Telephonic inquiries to nearby secondary and taluk hospitals when local beds or specialized equipment are exhausted.

### Pain Points of the Manual Process
- **Shared Arrival-Order Queue**: Patients with diverse clinical urgencies (e.g., polytrauma vs. minor abrasions) share a single chronological list, delaying resuscitation of life-threatened patients.
- **Resource Desynchronization**: A bed or doctor can erroneously appear available in multiple registers or remain flagged as busy after discharge.
- **Uncoordinated & Suboptimal Referrals**: Patients requiring specialized facilities (e.g., mechanical ventilation) are frequently sent to unequipped centers or distant hospitals without verified bed reservations.
- **Delayed Waiting List Reflection**: Beds vacated upon patient discharge are not immediately communicated to the waiting triage queue, resulting in critical idle bed capacity while patients wait outside.

---

## 2. Project Scope and Operational Assumptions

### Geographical and Network Scope
- **District Topology**: Model 9 strategic geographical locations connected via an undirected, weighted road network where edge weights represent travel time in minutes.
- **Hospitals**:
  - **1 Primary Facility**: Nalam District Government Hospital (NDGH) located at Nalam Central (`L1`).
  - **3 Referral Facilities**:
    - Vallam Sub-District Hospital (VSDH) located at Vallam Cross (`L3`).
    - Thoppur Community Health Centre (TCHC) located at Thoppur Rural (`L5`).
    - Kadur Taluk Hospital (KTH) located at Kadur Hills (`L8`).

### Resource Constraints
- **Beds**:
  - `GeneralBed`: Standard ward bed with optional oxygen supply. Supports General, Observation, and Ward requests.
  - `ICUBed`: High-dependency critical care bed with mechanical ventilators and advanced multipara telemetry. Supports ICU, Ventilator, and Critical Care requests.
- **Doctors**: 8 medical officers across the 4 institutions with designated clinical roles (Critical Care, Emergency Medicine, General Medicine) and binary duty status (`ON-DUTY` vs `OFF-DUTY`).
- **Patient Intake**: Baseline synthetic batch of 22 patient requests spanning Critical, Serious, and Minor severities.

---

## 3. Allocation and Processing Rules

### Rule 1: Clinical Urgency First, Arrival Order Second
Triage prioritization enforces strict two-tiered sorting:
$$\text{Priority} = (\text{Severity Rank Ascending}, \text{Arrival Sequence Ascending}, \text{Patient ID Ascending})$$
- `CRITICAL` (Rank 1): Imminent life threat (e.g., cardiac arrest, septic shock, tension pneumothorax).
- `SERIOUS` (Rank 2): Acute illness needing timely admission (e.g., open fractures, severe asthma, DKA).
- `MINOR` (Rank 3): Non-urgent, hemodynamically stable conditions (e.g., suturing, sprains, mild gastroenteritis).
- Ties between identical severity levels are deterministically resolved by earlier arrival sequence.

### Rule 2: Two-Resource Atomic Reservation
Admission is confirmed **only** when both:
1. A vacant bed supporting the requested facility is verified via polymorphic `supportsRequirement()`.
2. An on-duty, unassigned doctor clinically qualified for the condition is available.
Both resources are locked simultaneously. If either resource is missing, local admission fails.

### Rule 3: Fastest Reachable Eligible Referral Routing
When local admission at the primary hospital fails:
1. **Facility & Capacity Filtering**: Query candidate referral hospitals that have *both* an eligible vacant bed and an available doctor. Hospitals lacking the required bed type (e.g., TCHC lacking ICU) are filtered out immediately.
2. **Shortest Travel-Time Optimization**: Execute Dijkstra's algorithm with a min-heap from the primary hospital's location. Select the reachable eligible hospital with the minimum travel time.
3. **Pre-confirmation Reservation**: The target bed is reserved *before* confirming the referral to prevent telephonic race conditions.

### Rule 4: Immediate Waiting List Reflection upon Discharge
Whenever an admitted patient is discharged:
1. Both the bed and doctor are immediately liberated.
2. The waiting priority queue is automatically re-evaluated to immediately allocate the liberated capacity to the highest-ranking waiting patient.

### Rule 5: Dynamic Re-ranking Invariant
When a waiting patient's condition deteriorates (e.g., escalating from Serious to Critical), the patient record is extracted from the PriorityQueue, mutated, and re-inserted (`remove()` and `offer()`) to preserve the binary heap invariant.

---

## 4. Alignment with UN Sustainable Development Goals (SDGs)

### SDG 3: Good Health and Well-being
- Eliminates emergency room triage delays for critical patients by automating severity-first queue ordering.
- Minimizes emergency transfer transit time using Dijkstra's shortest path calculation, reducing mortality during inter-hospital transfers.

### SDG 9: Industry, Innovation, and Infrastructure
- Replaces brittle manual paper registers with an automated, synchronized allocation engine.
- Optimizes public healthcare infrastructure utilization across primary and rural referral networks without expensive commercial software.

### SDG 10: Reduced Inequalities
- Provides deterministic, transparent, and equitable emergency care access based strictly on clinical need rather than arrival bias or socio-economic influence.
- Ensures rural peripheral health centers (e.g., Thoppur and Kadur) are seamlessly integrated into district-wide resource sharing.
