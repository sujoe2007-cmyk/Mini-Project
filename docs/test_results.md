# Sample Input Data and Documented Test Results

This document provides complete documentation of the test scenarios, input data sets, step-by-step verification traces, and execution results for the **Government Hospital Emergency Resource Allocation System**.

---

## 1. Test Suite Summary

All tests are executed via `test.bat` or `java -cp bin tests.TestRunner`.

| Test ID | Scenario | Input Conditions | Expected Behavior | Actual Result | Status |
| :--- | :--- | :--- | :--- | :--- | :---: |
| **TC-1** | **No ICU Bed Available** | NDGH ICU beds (2/2 occupied). Critical patient `TC1-P01` arrives requiring "ICU". | Local admission fails. Dijkstra selects fastest reachable eligible hospital (VSDH @ 14 mins). Bed `VSDH-ICU-01` reserved before confirmation. | Referred to VSDH (14 min via `L1->L2->L3`). Bed reserved. | **PASS** |
| **TC-2** | **No Matching Doctor Available** | Free ICU bed available at NDGH, but both Critical Care doctors (`DOC-01`, `DOC-02`) set to `OFF-DUTY`. | Admission blocked due to doctor unavailability. Patient `TC2-P01` remains in queue (`WAITING`). When `DOC-01` reports on duty, patient is immediately admitted. | Blocked when doctor off-duty. Auto-admitted when `DOC-01` sets on-duty. | **PASS** |
| **TC-3** | **Simultaneous Demand for One Bed** | Exactly 1 General Bed remaining. `Patient A` (Minor, arrived 1st, Seq 1) and `Patient B` (Serious, arrived 2nd, Seq 2). | PriorityQueue evaluates clinical severity first. `Patient B` (Serious) admitted; `Patient A` (Minor) kept waiting. | `Patient B` admitted to `NDGH-GEN-04`; `Patient A` held waiting in queue. | **PASS** |
| **TC-4** | **Discharge Enabling Admission** | All beds full; Patient `TC4-WAIT` (Serious) waiting in queue. Admitted occupant discharged from `NDGH-GEN-01`. | Releasing bed immediately triggers queue re-evaluation. Liberated bed allocated to `TC4-WAIT`. | Bed liberated; waiting patient automatically admitted to `NDGH-GEN-01`. | **PASS** |
| **TC-5** | **Priority Ties & Dynamic Re-ranking** | Two Serious patients with identical urgency. Patient 2 dynamically escalated to Critical. | Tie broken by arrival sequence. Heap re-ranking moves Patient 2 to head of queue after condition deterioration. | Deterministic arrival order maintained; heap correctly re-orders after `remove()`/`offer()`. | **PASS** |

---

## 2. Detailed Scenario Verification Traces

### Test Case 1: No ICU Bed at Primary Hospital (Dijkstra Shortest-Path Referral)
#### Input Data:
- Primary Hospital: NDGH (`L1`)
- Occupied Beds: `NDGH-ICU-01` (Patient `EXT-01`), `NDGH-ICU-02` (Patient `EXT-02`)
- Arriving Patient: `TC1-P01`, Karthik Subramanian, 54, Male, Urgency: `CRITICAL`, Required Facility: `"ICU"`.
- Road Distances from `L1`:
  - `VSDH` (`L3`): 14 minutes (via `L1 -> L2 -> L3`)
  - `TCHC` (`L5`): 19 minutes (no ICU beds)
  - `KTH` (`L8`): 30 minutes (via `L1 -> L6 -> L7 -> L8`)

#### Execution Log:
```text
--------------------------------------------------------------------------------
TEST CASE 1: No ICU Bed at Primary Hospital -> Dijkstra Shortest-Path Referral
--------------------------------------------------------------------------------
  [PASS] TC1.1: Primary hospital NDGH has 0 available ICU beds.
  [PASS] TC1.2: Patient disposition is REFERRED_AND_RESERVED.
  [PASS] TC1.3: Fastest reachable hospital selected is VSDH (Vallam Sub-District Hospital).
  [PASS] TC1.4: Shortest travel time calculated by Dijkstra is 14 minutes.
  [PASS] TC1.5: Patient status transitioned to REFERRED.
  [PASS] TC1.6: Referral bed VSDH-ICU-01 was reserved immediately before confirmation.
```

---

### Test Case 2: No Matching Doctor Available
#### Input Data:
- Primary Hospital: NDGH (`L1`)
- Bed State: `NDGH-ICU-01` is vacant.
- Doctor State: `DOC-01` (Critical Care) = `OFF-DUTY`, `DOC-02` (Emergency Medicine) = `OFF-DUTY`.
- Arriving Patient: `TC2-P01`, Meenakshi Sundaram, 62, Female, Urgency: `CRITICAL`, Required Facility: `"ICU"`.

#### Execution Log:
```text
--------------------------------------------------------------------------------
TEST CASE 2: No Matching Doctor -> Admission Blocked & Queued; Restores on Duty
--------------------------------------------------------------------------------
  [PASS] TC2.1: Primary hospital has free ICU bed.
  [PASS] TC2.2: Admission rejected due to doctor unavailability: Ineligible: No on-duty doctor available matching clinical requirements (Critical / ICU).
  [PASS] TC2.3: Patient remains safely queued in WAITING status.
  [ACTION] Dr. Ramesh Kumar (DOC-01) reports on duty...
  [PASS] TC2.4: Patient automatically admitted upon doctor duty resumption!
  [PASS] TC2.5: Doctor DOC-01 assigned to patient.
```

---

### Test Case 3: Simultaneous Demand for One Bed
#### Input Data:
- Bed State: Exactly 1 General Bed available at NDGH (`NDGH-GEN-04`).
- Patient A (`TC3-PT-A`): Arrived at Seq 1, Severity: `MINOR`, Facility: `"General"`.
- Patient B (`TC3-PT-B`): Arrived at Seq 2, Severity: `SERIOUS`, Facility: `"General"`.

#### Execution Log:
```text
--------------------------------------------------------------------------------
TEST CASE 3: Simultaneous Demand for 1 Bed -> Process Severity First, Arrival Second
--------------------------------------------------------------------------------
  [PASS] TC3.1: Exactly 1 General Bed remaining at primary hospital.
  [PASS] TC3.2: Pt A arrived earlier (Seq 1 < Seq 2).
  [PASS] TC3.3: Patient B (SERIOUS) was admitted first due to higher clinical severity!
  [PASS] TC3.4: Patient A (MINOR) kept waiting as capacity exhausted.
```

---

### Test Case 4: A Discharge Enabling Admission
#### Input Data:
- Bed State: All General beds occupied.
- Waiting Patient: `TC4-WAIT`, Mani, 39, Male, Severity: `SERIOUS`, Facility: `"General"`.
- Action: Discharging occupant `OCC-NDGH-GEN-01` from `NDGH-GEN-01`.

#### Execution Log:
```text
--------------------------------------------------------------------------------
TEST CASE 4: Discharge Enabling Admission -> Immediate Waiting List Reflection
--------------------------------------------------------------------------------
  [PASS] TC4.1: Patient is waiting due to zero available beds.
  [ACTION] Discharging occupant OCC-NDGH-GEN-01 from NDGH-GEN-01...
  [PASS] TC4.2: Occupant discharged successfully.
  [PASS] TC4.3: Waiting patient immediately admitted upon bed discharge!
  [PASS] TC4.4: Assigned to the newly vacated bed NDGH-GEN-01.
```

---

## 3. Synthetic Batch Benchmark (22 Emergency Patients)

When processing the 22-patient synthetic emergency batch through `HospitalApp`:
- **Total Requests**: 22
- **Admitted Locally at NDGH**: 4 (2 ICU, 2 General until local capacity reached)
- **Referred to Regional Hospitals**: 4
  - `VSDH`: 2 patients (1 ICU, 1 General)
  - `TCHC`: 1 patient (General)
  - `KTH`: 1 patient (ICU)
- **Queued Waiting**: 14 (strictly ordered by clinical urgency: Critical first, Serious second, Minor third).
