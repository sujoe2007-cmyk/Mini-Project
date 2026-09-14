# Government Hospital Emergency Resource Allocation System (Nalam District)

**Mini Project 4: Government Hospital Resource Allocation**  
**Core Technologies:** Pure Java (JDK 25 / Java SE), Object-Oriented Programming (OOP), and Data Structures.

---

## Quick Start (Windows)

To compile and run the project, double-click or run the provided batch scripts from this folder:

### 1. Launch Interactive Web Dashboard & Server
```powershell
.\start_website.bat
```
*Starts the local HTTP web server on port 8080 and automatically opens the dashboard in your default web browser.*
*(Alternatively, simply double-click `start_website.bat` or run `.\run_ui.bat`)*

### 2. Compile Java Source Code
```powershell
.\compile.bat
```
*Compiles all Java source code and test files into the `bin/` directory.*

### 3. Run Java Interactive Console Application
```powershell
.\run.bat
```
*Launches the menu-driven emergency department coordinator console application.*

### 4. Run Automated Verification Test Suite
```powershell
.\test.bat
```
*Executes the automated test suite covering all mandatory edge and failure cases (22/22 passed, 100% success rate).*

---

## Directory Structure

```
Mini Project/
├── compile.bat                           # Script to compile all sources
├── run.bat                               # Script to launch interactive console application
├── test.bat                              # Script to run automated test suite
├── bin/                                  # Compiled .class binaries
├── docs/                                 # Academic Project Deliverables
│   ├── problem_analysis.md               # Assumptions, rules, case study, and SDG alignment
│   ├── uml_class_diagram.md              # Mermaid UML class diagram & OOP mapping
│   ├── data_structures_and_algorithms.md # Description, justification, and complexity analysis
│   ├── test_results.md                   # Sample data and documented test run traces
│   └── project_report.md                 # Project report, CLI output captures, limitations, and future work
├── src/
│   └── com/hospital/
│       ├── model/
│       │   ├── Severity.java             # Critical, Serious, Minor triage enum
│       │   ├── Patient.java              # Patient entity with deterministic Comparable
│       │   ├── Bed.java                  # Abstract Bed (Encapsulated state)
│       │   ├── GeneralBed.java           # Subclass with polymorphic supportsRequirement()
│       │   ├── ICUBed.java               # Subclass with polymorphic supportsRequirement()
│       │   ├── Doctor.java               # Encapsulated duty status & patient assignments
│       │   ├── Hospital.java             # Composed of Beds & Doctors via HashMaps
│       │   ├── AdmissionRequest.java     # Admission transaction result
│       │   └── AuditRecord.java          # Event log record for ArrayList history
│       ├── policy/
│       │   ├── AdmissionPolicy.java      # Abstraction interface for eligibility
│       │   └── StandardAdmissionPolicy.java # Concrete two-resource atomic policy
│       ├── routing/
│       │   ├── Location.java             # Geographical vertex
│       │   ├── RoadEdge.java             # Weighted edge (travel time in minutes)
│       │   ├── RoadGraph.java            # Weighted graph representing 9 district locations
│       │   ├── DijkstraResult.java       # Shortest distances & reconstructed path
│       │   └── RoutePlanner.java         # Dijkstra's Algorithm using Min-Heap
│       ├── service/
│       │   ├── ReferralService.java      # Capacity filtering & fastest reachable hospital selection
│       │   └── HospitalSystemManager.java# Coordinator using PriorityQueue, HashMap, and ArrayList
│       ├── data/
│       │   └── SyntheticDataGenerator.java# 4 hospitals, 9 locations, 8 doctors, 22 patients
│       └── HospitalApp.java              # Interactive console application
└── tests/
    └── TestRunner.java                   # Automated test runner for mandatory test scenarios
```

---

## Key Data Structures & Algorithms Used

| Component | Purpose in Project |
| :--- | :--- |
| **`PriorityQueue`** | Waiting emergency patients ordered deterministically by severity first, then arrival sequence. Supports dynamic re-ranking. |
| **`HashMap`** | Constant-time $O(1)$ lookup of patients, beds, doctors, and hospitals by unique string ID. |
| **`Weighted Graph`** | Interconnected network of 9 road locations in Nalam District with travel-time edge weights. |
| **`ArrayList`** | Chronological event audit log of all admissions, discharges, referrals, and duty changes. |
| **`Dijkstra's Algorithm + Min-Heap`** | Calculates fastest reachable eligible hospital for emergency transfers when local beds are full. |

---

## Mandatory Test Deliverables Verified

1. **No ICU Bed**: Critical patient arrives; primary hospital ICU beds are full. System runs Dijkstra to find the fastest eligible referral hospital (VSDH @ 14 mins) and reserves its bed immediately before confirmation.
2. **No Matching Doctor**: Free ICU bed exists, but Critical Care doctors are off-duty. Local admission is safely held; patient waits in queue. When a doctor reports on duty, admission is granted automatically.
3. **Simultaneous Demand for One Bed**: Only 1 General bed remains. A Minor patient arrives first, and a Serious patient arrives second. The PriorityQueue prioritizes the Serious patient due to higher clinical urgency.
4. **Discharge Enabling Admission**: All beds are full; patient is waiting in queue. When an admitted patient is discharged, the released bed immediately triggers waiting queue re-evaluation and admits the waiting patient.
5. **Priority Ties & Dynamic Re-ranking**: Demonstrates deterministic tie-breaking and heap re-ordering using `remove()` and `offer()` when patient clinical urgency changes.

---

## UN Sustainable Development Goals (SDG) Alignment
- **SDG 3 (Good Health and Well-being)**: Eliminates triage delays for life-threatened patients and minimizes emergency transfer times.
- **SDG 9 (Industry, Innovation, and Infrastructure)**: Upgrades fragile manual registers to an automated, deterministic dispatch system using open standard algorithms.
- **SDG 10 (Reduced Inequalities)**: Transparent, needs-based clinical triage eliminates subjective queue bias and integrates rural health centers into district-wide care.
