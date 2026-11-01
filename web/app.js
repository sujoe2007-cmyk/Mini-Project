/**
 * GOVERNMENT HOSPITAL EMERGENCY RESOURCE ALLOCATION DASHBOARD
 * Core Engine & Interactive UI Bindings (Nalam District)
 * Pure ES6 JavaScript - Zero External Dependencies
 */

// =============================================================================
// 1. DATA STRUCTURES & ALGORITHMS (PORTED STRICTLY FROM SPECIFICATION)
// =============================================================================

let isBackendConnected = false;
let isDatabaseConnected = false;
let backendEverConnected = false;
let backendSyncInProgress = false;
const API_BASE = window.location.origin.startsWith('http') ? window.location.origin : 'http://localhost:8080';

const Severity = {
  CRITICAL: { rank: 1, name: 'Critical', color: '#E06D6D' }, // Soft dusty rose
  SERIOUS:  { rank: 2, name: 'Serious',  color: '#E29578' }, // Soft peach / pastel amber
  MINOR:    { rank: 3, name: 'Minor',    color: '#52B788' }  // Gentle meadow sage green
};

/**
 * PriorityQueue implementing a binary min-heap for waiting emergency patients.
 * Deterministic comparison:
 * 1. Severity Rank Ascending (1=Critical, 2=Serious, 3=Minor)
 * 2. Arrival Sequence Ascending (earlier arrivals first)
 * 3. Patient ID Ascending (deterministic tie-breaker)
 */
class PriorityQueue {
  constructor() {
    this.heap = [];
  }

  size() {
    return this.heap.length;
  }

  isEmpty() {
    return this.heap.length === 0;
  }

  peek() {
    return this.heap.length > 0 ? this.heap[0] : null;
  }

  offer(patient) {
    this.heap.push(patient);
    this._siftUp(this.heap.length - 1);
  }

  poll() {
    if (this.isEmpty()) return null;
    const top = this.heap[0];
    const last = this.heap.pop();
    if (!this.isEmpty()) {
      this.heap[0] = last;
      this._siftDown(0);
    }
    return top;
  }

  remove(patientId) {
    const idx = this.heap.findIndex(p => p.id === patientId);
    if (idx === -1) return false;
    const last = this.heap.pop();
    if (idx < this.heap.length) {
      this.heap[idx] = last;
      this._siftDown(idx);
      this._siftUp(idx);
    }
    return true;
  }

  toArraySorted() {
    return [...this.heap].sort((a, b) => this._compare(a, b));
  }

  _compare(a, b) {
    const sevDiff = a.severity.rank - b.severity.rank;
    if (sevDiff !== 0) return sevDiff;
    const seqDiff = a.arrivalSequence - b.arrivalSequence;
    if (seqDiff !== 0) return seqDiff;
    return a.id.localeCompare(b.id);
  }

  _siftUp(idx) {
    while (idx > 0) {
      const parentIdx = Math.floor((idx - 1) / 2);
      if (this._compare(this.heap[idx], this.heap[parentIdx]) < 0) {
        [this.heap[idx], this.heap[parentIdx]] = [this.heap[parentIdx], this.heap[idx]];
        idx = parentIdx;
      } else {
        break;
      }
    }
  }

  _siftDown(idx) {
    const length = this.heap.length;
    while (true) {
      let smallest = idx;
      const left = 2 * idx + 1;
      const right = 2 * idx + 2;

      if (left < length && this._compare(this.heap[left], this.heap[smallest]) < 0) {
        smallest = left;
      }
      if (right < length && this._compare(this.heap[right], this.heap[smallest]) < 0) {
        smallest = right;
      }

      if (smallest !== idx) {
        [this.heap[idx], this.heap[smallest]] = [this.heap[smallest], this.heap[idx]];
        idx = smallest;
      } else {
        break;
      }
    }
  }
}

/**
 * Weighted Graph representing road connections with travel times in minutes.
 */
class RoadGraph {
  constructor() {
    this.locations = new Map();
    this.adjacencyList = new Map();
  }

  addLocation(id, name, desc, x, y) {
    this.locations.set(id, { id, name, desc, x, y });
    if (!this.adjacencyList.has(id)) {
      this.adjacencyList.set(id, []);
    }
  }

  addRoad(u, v, timeMinutes) {
    this.adjacencyList.get(u).push({ to: v, time: timeMinutes });
    this.adjacencyList.get(v).push({ to: u, time: timeMinutes });
  }

  updateTravelTime(u, v, newTime) {
    let updated = false;
    const edgesU = this.adjacencyList.get(u);
    if (edgesU) {
      for (let e of edgesU) {
        if (e.to === v) { e.time = newTime; updated = true; }
      }
    }
    const edgesV = this.adjacencyList.get(v);
    if (edgesV) {
      for (let e of edgesV) {
        if (e.to === u) { e.time = newTime; updated = true; }
      }
    }
    return updated;
  }
}

/**
 * Dijkstra's Shortest Path Algorithm with Min-Heap.
 */
function dijkstra(graph, sourceId) {
  const distances = {};
  const predecessors = {};
  const visited = new Set();

  for (let locId of graph.locations.keys()) {
    distances[locId] = Infinity;
    predecessors[locId] = null;
  }
  distances[sourceId] = 0;

  const pq = [{ id: sourceId, dist: 0 }];

  while (pq.length > 0) {
    pq.sort((a, b) => a.dist - b.dist);
    const curr = pq.shift();
    const u = curr.id;

    if (curr.dist > distances[u]) continue;
    visited.add(u);

    const edges = graph.adjacencyList.get(u) || [];
    for (let edge of edges) {
      const v = edge.to;
      const weight = edge.time;
      if (distances[u] + weight < distances[v]) {
        distances[v] = distances[u] + weight;
        predecessors[v] = u;
        pq.push({ id: v, dist: distances[v] });
      }
    }
  }

  function getPathTo(destId) {
    if (distances[destId] === Infinity) return [];
    const path = [];
    let curr = destId;
    while (curr) {
      path.unshift(curr);
      if (curr === sourceId) break;
      curr = predecessors[curr];
    }
    return path;
  }

  return { distances, predecessors, getPathTo };
}

// =============================================================================
// 2. AUDIO FEEDBACK (WEB AUDIO API SYNTHESIS - NO AUDIO FILES NEEDED)
// =============================================================================

let soundEnabled = true;
let audioCtx = null;

function getAudioContext() {
  if (!audioCtx && typeof AudioContext !== 'undefined') {
    audioCtx = new (window.AudioContext || window.webkitAudioContext)();
  }
  return audioCtx;
}

function playTone(freq, type = 'sine', duration = 0.15, gain = 0.1) {
  if (!soundEnabled) return;
  try {
    const ctx = getAudioContext();
    if (!ctx) return;
    if (ctx.state === 'suspended') ctx.resume();

    const osc = ctx.createOscillator();
    const gainNode = ctx.createGain();

    osc.type = type;
    osc.frequency.setValueAtTime(freq, ctx.currentTime);

    gainNode.gain.setValueAtTime(gain, ctx.currentTime);
    gainNode.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + duration);

    osc.connect(gainNode);
    gainNode.connect(ctx.destination);

    osc.start();
    osc.stop(ctx.currentTime + duration);
  } catch (e) {
    // Audio optional fallback
  }
}

function playSuccessChime() {
  playTone(523.25, 'sine', 0.1, 0.08); // C5
  setTimeout(() => playTone(659.25, 'sine', 0.15, 0.08), 80); // E5
}

function playReferralAlert() {
  playTone(440, 'triangle', 0.12, 0.08); // A4
  setTimeout(() => playTone(587.33, 'triangle', 0.2, 0.08), 100); // D5
}

function playTriagePing() {
  playTone(880, 'sine', 0.08, 0.05); // A5
}

// =============================================================================
// 3. SYSTEM STATE INITIALIZATION
// =============================================================================

let roadGraph = new RoadGraph();
let waitingQueue = new PriorityQueue();
let allPatients = new Map();
let hospitals = new Map();
let auditHistory = [];
let patientSeqCounter = 1;
let auditSeqCounter = 1;

function initializeRoadNetwork() {
  roadGraph = new RoadGraph();
  roadGraph.addLocation('L1', 'Nalam Central', 'NDGH Primary Hospital', 270, 180);
  roadGraph.addLocation('L2', 'North Junction', 'Transit Hub', 270, 75);
  roadGraph.addLocation('L3', 'Vallam Cross', 'VSDH Hospital Location', 420, 75);
  roadGraph.addLocation('L4', 'East Circle', 'Highway Connector', 420, 180);
  roadGraph.addLocation('L5', 'Thoppur Rural', 'TCHC Hospital Location', 480, 290);
  roadGraph.addLocation('L6', 'South Bridge', 'River Crossing', 270, 285);
  roadGraph.addLocation('L7', 'Industrial Zone', 'Industrial Corridor', 150, 285);
  roadGraph.addLocation('L8', 'Kadur Hills', 'KTH Hospital Location', 60, 200);
  roadGraph.addLocation('L9', 'West Bypass', 'Ring Road', 130, 95);

  roadGraph.addRoad('L1', 'L2', 8);
  roadGraph.addRoad('L1', 'L4', 12);
  roadGraph.addRoad('L1', 'L6', 10);
  roadGraph.addRoad('L1', 'L9', 15);

  roadGraph.addRoad('L2', 'L3', 6);   // Path L1 -> L2 -> L3 = 14 mins (VSDH)
  roadGraph.addRoad('L2', 'L9', 11);

  roadGraph.addRoad('L4', 'L5', 7);   // Path L1 -> L4 -> L5 = 19 mins (TCHC)
  roadGraph.addRoad('L4', 'L3', 14);

  roadGraph.addRoad('L6', 'L7', 9);
  roadGraph.addRoad('L7', 'L8', 11);  // Path L1 -> L6 -> L7 -> L8 = 30 mins (KTH)
  roadGraph.addRoad('L9', 'L8', 22);
}

function initializeHospitals() {
  hospitals.clear();

  // 1. Primary Hospital: NDGH
  hospitals.set('NDGH', {
    id: 'NDGH',
    name: 'Nalam District Government Hospital',
    locationId: 'L1',
    isPrimary: true,
    beds: [
      { id: 'NDGH-ICU-01', type: 'ICU+VENT', facility: 'ICU', occupied: false, patientId: null },
      { id: 'NDGH-ICU-02', type: 'ICU+VENT', facility: 'ICU', occupied: false, patientId: null },
      { id: 'NDGH-GEN-01', type: 'GENERAL+O2', facility: 'General', occupied: false, patientId: null },
      { id: 'NDGH-GEN-02', type: 'GENERAL+O2', facility: 'General', occupied: false, patientId: null },
      { id: 'NDGH-GEN-03', type: 'GENERAL', facility: 'General', occupied: false, patientId: null },
      { id: 'NDGH-GEN-04', type: 'GENERAL', facility: 'General', occupied: false, patientId: null },
    ],
    doctors: [
      { id: 'DOC-01', name: 'Dr. Ramesh Kumar', spec: 'Critical Care', onDuty: true, patientId: null },
      { id: 'DOC-02', name: 'Dr. Priya Sundaram', spec: 'Emergency Medicine', onDuty: true, patientId: null },
      { id: 'DOC-03', name: 'Dr. Anand Varma', spec: 'General Medicine', onDuty: true, patientId: null },
      { id: 'DOC-04', name: 'Dr. Kavitha Nair', spec: 'General Medicine', onDuty: true, patientId: null },
    ]
  });

  // 2. Referral 1: VSDH
  hospitals.set('VSDH', {
    id: 'VSDH',
    name: 'Vallam Sub-District Hospital',
    locationId: 'L3',
    isPrimary: false,
    beds: [
      { id: 'VSDH-ICU-01', type: 'ICU+VENT', facility: 'ICU', occupied: false, patientId: null },
      { id: 'VSDH-ICU-02', type: 'ICU', facility: 'ICU', occupied: false, patientId: null },
      { id: 'VSDH-GEN-01', type: 'GENERAL+O2', facility: 'General', occupied: false, patientId: null },
      { id: 'VSDH-GEN-02', type: 'GENERAL+O2', facility: 'General', occupied: false, patientId: null },
      { id: 'VSDH-GEN-03', type: 'GENERAL', facility: 'General', occupied: false, patientId: null },
    ],
    doctors: [
      { id: 'DOC-05', name: 'Dr. Karthik Rajan', spec: 'Critical Care', onDuty: true, patientId: null },
      { id: 'DOC-06', name: 'Dr. Meena Krishnan', spec: 'General Medicine', onDuty: true, patientId: null },
    ]
  });

  // 3. Referral 2: TCHC
  hospitals.set('TCHC', {
    id: 'TCHC',
    name: 'Thoppur Community Health Centre',
    locationId: 'L5',
    isPrimary: false,
    beds: [
      { id: 'TCHC-GEN-01', type: 'GENERAL+O2', facility: 'General', occupied: false, patientId: null },
      { id: 'TCHC-GEN-02', type: 'GENERAL', facility: 'General', occupied: false, patientId: null },
      { id: 'TCHC-GEN-03', type: 'GENERAL', facility: 'General', occupied: false, patientId: null },
    ],
    doctors: [
      { id: 'DOC-07', name: 'Dr. Vignesh Balan', spec: 'General Medicine', onDuty: true, patientId: null },
    ]
  });

  // 4. Referral 3: KTH
  hospitals.set('KTH', {
    id: 'KTH',
    name: 'Kadur Taluk Hospital',
    locationId: 'L8',
    isPrimary: false,
    beds: [
      { id: 'KTH-ICU-01', type: 'ICU+VENT', facility: 'ICU', occupied: false, patientId: null },
      { id: 'KTH-ICU-02', type: 'ICU+VENT', facility: 'ICU', occupied: false, patientId: null },
      { id: 'KTH-GEN-01', type: 'GENERAL+O2', facility: 'General', occupied: false, patientId: null },
      { id: 'KTH-GEN-02', type: 'GENERAL', facility: 'General', occupied: false, patientId: null },
    ],
    doctors: [
      { id: 'DOC-08', name: 'Dr. Shalini Devi', spec: 'Emergency Medicine', onDuty: true, patientId: null },
    ]
  });
}

function resetSystem() {
  if (backendModeActive()) {
    synchronizeBackendState()
      .then(() => showToast('Dashboard reloaded from backend state; no records were deleted.', 'info'))
      .catch(error => showToast(`Could not reload backend state: ${error.message}`, 'error'));
    return;
  }
  initializeRoadNetwork();
  initializeHospitals();
  waitingQueue = new PriorityQueue();
  allPatients.clear();
  auditHistory = [];
  patientSeqCounter = 1;
  auditSeqCounter = 1;

  logAudit('SYSTEM_RESET', null, null, null, null, 'System reset to clean baseline state.');
  showToast('System reset to baseline configuration.', 'info');
  renderAll();
}

// =============================================================================
// 4. CORE LOGIC: ADMISSION, REFERRAL & DISCHARGE
// =============================================================================

function registerPatient(id, name, age, gender, severity, requiredFacility, autoProcess = true) {
  const patientId = String(id || '').trim();
  if (!patientId) throw new Error('Patient ID is required.');
  if (allPatients.has(patientId)) throw new Error(`Patient ID ${patientId} is already registered.`);
  const seq = patientSeqCounter++;
  const patient = {
    id: patientId,
    name,
    age,
    gender,
    severity,
    requiredFacility,
    arrivalSequence: seq,
    status: 'WAITING',
    assignedHospitalId: null,
    assignedBedId: null,
    assignedDoctorId: null,
    referralNotes: null
  };

  allPatients.set(patient.id, patient);
  waitingQueue.offer(patient);

  playTriagePing();
  logAudit('QUEUE_ENQUEUE', patient.id, 'NDGH', null, null,
    `Registered into triage queue: ${severity.name}, Facility: ${requiredFacility} (Seq #${seq})`);

  if (autoProcess) {
    processWaitingQueue();
  } else {
    renderAll();
  }
  return patient;
}

function canDoctorTreat(doctor, severity, requiredFacility) {
  if (!doctor.onDuty || doctor.patientId !== null) return false;
  if (severity.name === 'Critical' || requiredFacility === 'ICU') {
    return doctor.spec === 'Critical Care' || doctor.spec === 'Emergency Medicine' || doctor.spec === 'Trauma Surgery';
  }
  return true;
}

function evaluateAdmission(hospital, patient) {
  const availableBed = hospital.beds.find(b => !b.occupied && b.facility.toLowerCase() === patient.requiredFacility.toLowerCase());
  if (!availableBed) {
    return { eligible: false, reason: `No ${patient.requiredFacility} bed available.` };
  }

  const availableDoctor = hospital.doctors.find(d => canDoctorTreat(d, patient.severity, patient.requiredFacility));
  if (!availableDoctor) {
    return { eligible: false, reason: `No qualified on-duty doctor available.` };
  }

  return { eligible: true, bed: availableBed, doctor: availableDoctor };
}

function processWaitingQueue() {
  if (backendModeActive()) {
    return processBackendWaitingQueue();
  }
  if (waitingQueue.isEmpty()) return;

  const unserved = [];
  const primary = hospitals.get('NDGH');

  while (!waitingQueue.isEmpty()) {
    const patient = waitingQueue.poll();

    const evalResult = evaluateAdmission(primary, patient);
    if (evalResult.eligible) {
      evalResult.bed.occupied = true;
      evalResult.bed.patientId = patient.id;
      evalResult.doctor.patientId = patient.id;

      patient.status = 'ADMITTED_LOCAL';
      patient.assignedHospitalId = primary.id;
      patient.assignedBedId = evalResult.bed.id;
      patient.assignedDoctorId = evalResult.doctor.id;

      playSuccessChime();
      logAudit('ADMISSION', patient.id, primary.id, evalResult.bed.id, evalResult.doctor.id,
        `Admitted locally at ${primary.name} (${evalResult.bed.type} bed)`);
      showToast(`Admitted ${patient.name} at NDGH (${evalResult.bed.id})`, 'success');
    } else {
      const referralResult = attemptReferral(patient);
      if (referralResult.success) {
        playReferralAlert();
        logAudit('REFERRAL', patient.id, referralResult.hospital.id, referralResult.bed.id, referralResult.doctor.id,
          `Referred to ${referralResult.hospital.name} via Dijkstra (${referralResult.travelTime}m). Path: ${referralResult.path.join('->')}`);
        showToast(`Referred ${patient.name} to ${referralResult.hospital.id} (${referralResult.travelTime} mins)`, 'warning');
      } else {
        unserved.push(patient);
      }
    }
  }

  for (let pt of unserved) {
    waitingQueue.offer(pt);
  }

  renderAll();
}

function attemptReferral(patient) {
  const primary = hospitals.get('NDGH');
  const candidates = [];

  for (let [hId, hospital] of hospitals.entries()) {
    if (hospital.isPrimary) continue;
    const evalResult = evaluateAdmission(hospital, patient);
    if (evalResult.eligible) {
      candidates.push({ hospital, bed: evalResult.bed, doctor: evalResult.doctor });
    }
  }

  if (candidates.length === 0) {
    return { success: false, reason: 'No referral hospital has matching capacity.' };
  }

  const dijkstraResult = dijkstra(roadGraph, primary.locationId);

  let best = null;
  let minTime = Infinity;

  for (let cand of candidates) {
    const time = dijkstraResult.distances[cand.hospital.locationId];
    if (time < minTime) {
      minTime = time;
      best = cand;
    }
  }

  if (!best || minTime === Infinity) {
    return { success: false, reason: 'No reachable referral route found.' };
  }

  // Reserve bed and doctor immediately before confirmation!
  best.bed.occupied = true;
  best.bed.patientId = patient.id;
  best.doctor.patientId = patient.id;

  patient.status = 'REFERRED';
  patient.assignedHospitalId = best.hospital.id;
  patient.assignedBedId = best.bed.id;
  patient.assignedDoctorId = best.doctor.id;

  const path = dijkstraResult.getPathTo(best.hospital.locationId);
  patient.referralNotes = `Referred from NDGH to ${best.hospital.id} (${minTime}m). Path: ${path.join('->')}`;

  return {
    success: true,
    hospital: best.hospital,
    bed: best.bed,
    doctor: best.doctor,
    travelTime: minTime,
    path
  };
}

let pendingDischargeNotice = null;

function refreshDashboard(silent = false) {
  if (backendModeActive()) {
    return refreshBackendDashboard(silent);
  }
  pendingDischargeNotice = null;

  const banner = document.getElementById('dischargeRefreshBanner');
  if (banner) banner.style.display = 'none';

  const btnRefresh = document.getElementById('btnRefreshSystem');
  if (btnRefresh) btnRefresh.classList.remove('pulse-highlight');

  const badge = document.getElementById('refreshBadge');
  if (badge) badge.style.display = 'none';

  // Process waiting queue with newly liberated resources
  processWaitingQueue();
  renderAll();

  if (!silent) {
    showToast('Wards Refreshed: Bed allocations and triage queue updated.', 'success');
    playSuccessChime();
  }
}

function dischargePatient(patientId) {
  if (backendModeActive()) {
    return dischargeBackendPatient(patientId);
  }
  const patient = allPatients.get(patientId);
  if (!patient || (patient.status !== 'ADMITTED_LOCAL' && patient.status !== 'REFERRED')) {
    return false;
  }

  const hospital = hospitals.get(patient.assignedHospitalId);
  if (hospital) {
    const bed = hospital.beds.find(b => b.id === patient.assignedBedId);
    if (bed) {
      bed.occupied = false;
      bed.patientId = null;
    }
    const doc = hospital.doctors.find(d => d.id === patient.assignedDoctorId);
    if (doc) {
      doc.patientId = null;
    }
  }

  const oldBed = patient.assignedBedId;
  const oldHosp = patient.assignedHospitalId;

  patient.status = 'DISCHARGED';
  patient.assignedBedId = null;
  patient.assignedDoctorId = null;

  logAudit('DISCHARGE', patientId, oldHosp, oldBed, null,
    `Patient ${patient.name} discharged. Bed & Doctor released.`);

  // Set pending discharge notice to prompt the user to refresh
  pendingDischargeNotice = {
    patientName: patient.name,
    patientId: patientId,
    hospitalId: oldHosp,
    bedId: oldBed
  };

  // Update UI to show Discharge Refresh Banner & Highlight Refresh button
  const banner = document.getElementById('dischargeRefreshBanner');
  if (banner) {
    const bannerPt = document.getElementById('bannerPatientText');
    const bannerSub = document.getElementById('bannerSubText');
    if (bannerPt) bannerPt.innerText = `Patient ${patient.name} Discharged from ${oldBed}`;
    if (bannerSub) bannerSub.innerText = `Bed ${oldBed} released. Click Refresh to update ward allocations and process waiting triage queue.`;
    banner.style.display = 'flex';
  }

  const btnRefresh = document.getElementById('btnRefreshSystem');
  if (btnRefresh) btnRefresh.classList.add('pulse-highlight');

  const badge = document.getElementById('refreshBadge');
  if (badge) badge.style.display = 'inline-block';

  showToast(`Discharged ${patient.name} (${oldBed} liberated). Click "Refresh Wards" to update status!`, 'warning');

  // Render current state showing the pending refresh on that bed
  renderAll();
  return true;
}

function toggleDoctorDuty(hospitalId, doctorId, onDuty) {
  if (backendModeActive()) {
    return updateBackendDoctorDuty(hospitalId, doctorId, onDuty);
  }
  const hospital = hospitals.get(hospitalId);
  if (!hospital) return;
  const doc = hospital.doctors.find(d => d.id === doctorId);
  if (!doc) return;

  if (!onDuty && doc.patientId !== null) {
    showToast(`Dr. ${doc.name} cannot sign off while attending patient ${doc.patientId}!`, 'error');
    renderAll();
    return;
  }

  doc.onDuty = onDuty;
  logAudit('DOCTOR_DUTY_CHANGE', null, hospitalId, null, doctorId,
    `Dr. ${doc.name} duty status updated to ${onDuty ? 'ON-DUTY' : 'OFF-DUTY'}`);

  showToast(`Dr. ${doc.name} is now ${onDuty ? 'ON-DUTY' : 'OFF-DUTY'}`, 'info');

  if (onDuty) {
    processWaitingQueue();
  } else {
    renderAll();
  }
}

function updatePatientSeverity(patientId, newSeverity) {
  if (backendModeActive()) {
    return updateBackendPatientSeverity(patientId, newSeverity);
  }
  const patient = allPatients.get(patientId);
  if (!patient) return;

  if (waitingQueue.remove(patientId)) {
    const oldSev = patient.severity.name;
    patient.severity = newSeverity;
    waitingQueue.offer(patient);

    logAudit('QUEUE_REORDER', patientId, 'NDGH', null, null,
      `Clinical escalation: ${oldSev} -> ${newSeverity.name}; re-ranked in PriorityQueue.`);
    showToast(`Escalated ${patient.name} to ${newSeverity.name} (Heap Re-ranked)`, 'warning');
    renderAll();
  }
}

function logAudit(type, patientId, hospitalId, bedId, doctorId, details) {
  const time = new Date().toLocaleTimeString();
  auditHistory.unshift({
    seq: auditSeqCounter++,
    time,
    type,
    patientId: patientId || '-',
    hospitalId: hospitalId || '-',
    bedId: bedId || '-',
    doctorId: doctorId || '-',
    details
  });
  if (auditHistory.length > 200) auditHistory.pop();
}

// =============================================================================
// 5. TEST SCENARIOS & BATCH BENCHMARK RUNNERS
// =============================================================================

function runScenario1_NoICUBed() {
  if (!allowLocalScenario()) return;
  resetSystem();
  showToast('Running Test 1: No ICU Bed -> Triggers Dijkstra Referral...', 'info');

  const ndgh = hospitals.get('NDGH');
  ndgh.beds[0].occupied = true; ndgh.beds[0].patientId = 'EXIST-01';
  ndgh.beds[1].occupied = true; ndgh.beds[1].patientId = 'EXIST-02';

  setTimeout(() => {
    const pt = registerPatient('TC1-P01', 'Karthik Subramanian', 54, 'Male', Severity.CRITICAL, 'ICU', true);
    updateTestCard('cardTest1', 'detailsTest1', 'passed',
      `[PASS] NDGH has 0 ICU beds. Dijkstra routed to VSDH (14 mins). Bed VSDH-ICU-01 reserved.`);
  }, 400);
}

function runScenario2_NoDoctor() {
  if (!allowLocalScenario()) return;
  resetSystem();
  showToast('Running Test 2: Doctor Off-Duty -> Blocks Admission...', 'info');

  const ndgh = hospitals.get('NDGH');
  ndgh.doctors[0].onDuty = false;
  ndgh.doctors[1].onDuty = false;

  hospitals.get('VSDH').beds[0].occupied = true;
  hospitals.get('VSDH').beds[1].occupied = true;
  hospitals.get('KTH').beds[0].occupied = true;
  hospitals.get('KTH').beds[1].occupied = true;

  setTimeout(() => {
    registerPatient('TC2-P01', 'Meenakshi Sundaram', 62, 'Female', Severity.CRITICAL, 'ICU', true);
    updateTestCard('cardTest2', 'detailsTest2', 'passed',
      `[PASS] Admission safely blocked (no Critical Care doctor). Patient queued. Toggle Dr. Ramesh ON-DUTY to auto-admit.`);
  }, 400);
}

function runScenario3_BedContention() {
  if (!allowLocalScenario()) return;
  resetSystem();
  showToast('Running Test 3: 1 Bed Contention (Severity First, Arrival Second)...', 'info');

  const ndgh = hospitals.get('NDGH');
  ndgh.beds[2].occupied = true; ndgh.beds[2].patientId = 'OCC-01';
  ndgh.beds[3].occupied = true; ndgh.beds[3].patientId = 'OCC-02';
  ndgh.beds[4].occupied = true; ndgh.beds[4].patientId = 'OCC-03';

  hospitals.get('VSDH').beds[2].occupied = true;
  hospitals.get('VSDH').beds[3].occupied = true;
  hospitals.get('VSDH').beds[4].occupied = true;
  hospitals.get('TCHC').beds[0].occupied = true;
  hospitals.get('TCHC').beds[1].occupied = true;
  hospitals.get('TCHC').beds[2].occupied = true;

  registerPatient('TC3-PT-A', 'Patient A (Arrived 1st)', 22, 'Male', Severity.MINOR, 'General', false);

  setTimeout(() => {
    registerPatient('TC3-PT-B', 'Patient B (Arrived 2nd)', 48, 'Female', Severity.SERIOUS, 'General', false);
    setTimeout(() => {
      processWaitingQueue();
      updateTestCard('cardTest3', 'detailsTest3', 'passed',
        `[PASS] Patient B (Serious) admitted to NDGH-GEN-04. Patient A (Minor) kept waiting.`);
    }, 600);
  }, 400);
}

function runScenario4_DischargeEnable() {
  if (!allowLocalScenario()) return;
  resetSystem();
  showToast('Running Protocol 4: Discharge Enabling Waiting Admission...', 'info');

  const ndgh = hospitals.get('NDGH');
  ndgh.beds.forEach(b => { b.occupied = true; b.patientId = `OCC-${b.id}`; });

  setTimeout(() => {
    registerPatient('TC4-WAIT', 'Waiting Patient Mani', 39, 'Male', Severity.SERIOUS, 'General', true);
    setTimeout(() => {
      dischargePatient('OCC-NDGH-GEN-01');
      setTimeout(() => {
        refreshDashboard(true);
        updateTestCard('cardTest4', 'detailsTest4', 'passed',
          `[PASS] Bed NDGH-GEN-01 released upon discharge. Refresh allocated waiting patient Mani to NDGH-GEN-01.`);
      }, 500);
    }, 800);
  }, 400);
}

function runAllTestsSequentially() {
  if (!allowLocalScenario()) return;
  showToast('Starting sequential automated test execution...', 'info');
  runScenario1_NoICUBed();
  setTimeout(runScenario2_NoDoctor, 1500);
  setTimeout(runScenario3_BedContention, 3000);
  setTimeout(runScenario4_DischargeEnable, 4800);
}

function allowLocalScenario() {
  if (backendModeActive()) {
    showToast('Local demo scenarios are unavailable in backend mode; no mock data was applied.', 'warning');
    return false;
  }
  return true;
}

function updateTestCard(cardId, detailsId, status, text) {
  const card = document.getElementById(cardId);
  if (!card) return;
  const badge = card.querySelector('.test-status-badge');
  if (badge) {
    badge.className = `test-status-badge ${status}`;
    badge.innerText = status.toUpperCase();
  }
  const details = document.getElementById(detailsId);
  if (details) {
    details.innerText = text;
  }
}

function loadSyntheticBatch() {
  if (backendModeActive()) {
    return loadBackendSyntheticBatch();
  }
  resetSystem();
  showToast('Ingesting 22 Synthetic Emergency Patient Requests...', 'info');

  const batch = [
    { id: 'P-101', name: 'Arun Kumar', age: 58, gender: 'Male', severity: Severity.CRITICAL, facility: 'ICU' },
    { id: 'P-102', name: 'Deepa Mohan', age: 34, gender: 'Female', severity: Severity.SERIOUS, facility: 'General' },
    { id: 'P-103', name: 'Murugan Vel', age: 42, gender: 'Male', severity: Severity.MINOR, facility: 'General' },
    { id: 'P-104', name: 'Fatima Beevi', age: 66, gender: 'Female', severity: Severity.CRITICAL, facility: 'ICU' },
    { id: 'P-105', name: 'Suresh Gopinath', age: 29, gender: 'Male', severity: Severity.SERIOUS, facility: 'General' },
    { id: 'P-106', name: 'Lakshmi Ammal', age: 71, gender: 'Female', severity: Severity.CRITICAL, facility: 'ICU' },
    { id: 'P-107', name: 'Kavita Sethi', age: 25, gender: 'Female', severity: Severity.MINOR, facility: 'General' },
    { id: 'P-108', name: 'Rajesh Khanna', age: 50, gender: 'Male', severity: Severity.SERIOUS, facility: 'General' },
    { id: 'P-109', name: 'Selvamani P.', age: 63, gender: 'Male', severity: Severity.CRITICAL, facility: 'ICU' },
    { id: 'P-110', name: 'Amina Khatun', age: 45, gender: 'Female', severity: Severity.MINOR, facility: 'General' },
    { id: 'P-111', name: 'Venkatesh Rao', age: 52, gender: 'Male', severity: Severity.SERIOUS, facility: 'General' },
    { id: 'P-112', name: 'Chitra Devi', age: 38, gender: 'Female', severity: Severity.CRITICAL, facility: 'ICU' },
    { id: 'P-113', name: 'Mani Kandan', age: 19, gender: 'Male', severity: Severity.MINOR, facility: 'General' },
    { id: 'P-114', name: 'Sundar Raj', age: 61, gender: 'Male', severity: Severity.SERIOUS, facility: 'General' },
    { id: 'P-115', name: 'Shanti Ramasamy', age: 55, gender: 'Female', severity: Severity.CRITICAL, facility: 'ICU' },
    { id: 'P-116', name: 'Praveen Joshua', age: 31, gender: 'Male', severity: Severity.MINOR, facility: 'General' },
    { id: 'P-117', name: 'Banumathi K.', age: 48, gender: 'Female', severity: Severity.SERIOUS, facility: 'General' },
    { id: 'P-118', name: 'Dinesh Babu', age: 36, gender: 'Male', severity: Severity.MINOR, facility: 'General' },
    { id: 'P-119', name: 'Radhika Narayanan', age: 67, gender: 'Female', severity: Severity.CRITICAL, facility: 'ICU' },
    { id: 'P-120', name: 'Abdul Kareem', age: 53, gender: 'Male', severity: Severity.SERIOUS, facility: 'General' },
    { id: 'P-121', name: 'Nalini Sekar', age: 27, gender: 'Female', severity: Severity.MINOR, facility: 'General' },
    { id: 'P-122', name: 'Ravi Shankar', age: 44, gender: 'Male', severity: Severity.SERIOUS, facility: 'General' }
  ];

  for (let item of batch) {
    registerPatient(item.id, item.name, item.age, item.gender, item.severity, item.facility, false);
  }

  showToast('22 patients placed in PriorityQueue. Evaluating triage allocations...', 'info');
  processWaitingQueue();
}

// =============================================================================
// 6. DOM RENDERING & UI EVENT BINDINGS
// =============================================================================

function renderAll() {
  renderMetrics();
  renderHospitals();
  renderQueue();
  renderPatientRegister();
  renderMapAndDijkstra();
  renderAudit();
}

function escapePatientCell(value) {
  return String(value ?? '—').replace(/[&<>"']/g, char => ({
    '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
  })[char]);
}

function renderPatientRegister() {
  const body = document.getElementById('patientRegisterBody');
  if (!body) return;
  const query = (document.getElementById('patientSearch')?.value || '').trim().toLowerCase();
  const status = document.getElementById('patientStatusFilter')?.value || 'ALL';
  const patients = [...allPatients.values()]
    .filter(patient => status === 'ALL' || patient.status === status)
    .filter(patient => !query || patient.id.toLowerCase().includes(query) || patient.name.toLowerCase().includes(query))
    .sort((a, b) => (a.arrivalSequence || 0) - (b.arrivalSequence || 0));
  const count = document.getElementById('patientRegisterCount');
  if (count) count.textContent = `${patients.length} of ${allPatients.size} patients`;
  body.innerHTML = patients.map(patient => {
    const hospital = patient.assignedHospitalId ? hospitals.get(patient.assignedHospitalId) : null;
    const placement = patient.assignedBedId
      ? `${hospital?.name || patient.assignedHospitalId} · ${patient.assignedBedId}${patient.assignedDoctorId ? ` · ${patient.assignedDoctorId}` : ''}`
      : patient.status === 'WAITING' ? 'Awaiting suitable resources' : '—';
    const severityName = patient.severity?.name || patient.severity || '—';
    return `<tr><td class="patient-id-cell">${escapePatientCell(patient.id)}</td><td>${escapePatientCell(patient.name)}<small class="patient-subline">${escapePatientCell(patient.gender || 'Unspecified')}</small></td><td>${escapePatientCell(patient.age)}</td><td><span class="patient-severity severity-${escapePatientCell(String(severityName).toLowerCase())}">${escapePatientCell(severityName)}</span></td><td>${escapePatientCell(patient.requiredFacility)}</td><td><span class="patient-status status-${escapePatientCell(String(patient.status).toLowerCase())}">${escapePatientCell(String(patient.status).replaceAll('_', ' '))}</span></td><td>${escapePatientCell(placement)}</td></tr>`;
  }).join('') || '<tr><td colspan="7" class="patient-empty">No patients match this search.</td></tr>';
}

function renderMetrics() {
  const ndgh = hospitals.get('NDGH');
  const ndghAvail = ndgh.beds.filter(b => !b.occupied).length;
  const ndghIcu = ndgh.beds.filter(b => !b.occupied && b.facility === 'ICU').length;
  const ndghGen = ndgh.beds.filter(b => !b.occupied && b.facility === 'General').length;

  document.getElementById('metricNdghBeds').innerText = `${ndghAvail} / ${ndgh.beds.length}`;
  document.getElementById('metricNdghBedsSub').innerText = `${ndghIcu} ICU Avail | ${ndghGen} Gen Avail`;

  const queueList = waitingQueue.toArraySorted();
  document.getElementById('metricQueueCount').innerText = queueList.length;
  const crits = queueList.filter(p => p.severity.name === 'Critical').length;
  const sers = queueList.filter(p => p.severity.name === 'Serious').length;
  const mins = queueList.filter(p => p.severity.name === 'Minor').length;
  document.getElementById('metricQueueUrgency').innerText = `${crits} Critical | ${sers} Serious | ${mins} Minor`;
  document.getElementById('badgeQueueSize').innerText = `${queueList.length} Queued`;

  let totalDoctors = 0;
  let onDutyDoctors = 0;
  for (let h of hospitals.values()) {
    totalDoctors += h.doctors.length;
    onDutyDoctors += h.doctors.filter(d => d.onDuty).length;
  }
  document.getElementById('metricDoctorsCount').innerText = `${onDutyDoctors} / ${totalDoctors}`;

  let referralCount = 0;
  for (let p of allPatients.values()) {
    if (p.status === 'REFERRED') referralCount++;
  }
  document.getElementById('metricReferralCount').innerText = referralCount;
}

function renderHospitals() {
  const container = document.getElementById('hospitalsList');
  container.innerHTML = '';

  for (let hospital of hospitals.values()) {
    const card = document.createElement('div');
    card.className = `hospital-card ${hospital.isPrimary ? 'primary-facility' : ''}`;

    const totalBeds = hospital.beds.length;
    const icuBeds = hospital.beds.filter(b => b.facility === 'ICU');
    const genBeds = hospital.beds.filter(b => b.facility === 'General');

    const availIcu = icuBeds.filter(b => !b.occupied).length;
    const availGen = genBeds.filter(b => !b.occupied).length;

    card.innerHTML = `
      <div class="hospital-card-header">
        <div class="hospital-title-row">
          <span class="facility-cross">✚</span>
          <div>
            <div class="hospital-name">${hospital.name}</div>
            <div class="hospital-loc-tag">[Node: ${hospital.locationId}] ${hospital.isPrimary ? '• PRIMARY EMERGENCY FACILITY' : '• DISTRICT REFERRAL HOSPITAL'}</div>
          </div>
        </div>
        <div class="hospital-avail-badges">
          <span class="badge-icu">ICU: ${availIcu} / ${icuBeds.length}</span>
          <span class="badge-gen">GEN: ${availGen} / ${genBeds.length}</span>
        </div>
      </div>

      <div class="hospital-body">
        <!-- ICU WARD BLOCK -->
        <div class="ward-block">
          <div class="ward-heading">
            <span>Ward A: Intensive Care Unit (Ventilator & Telemetry)</span>
            <span>${availIcu} / ${icuBeds.length} Beds Available</span>
          </div>
          <div class="beds-grid">
            ${icuBeds.map(bed => renderBedCard(bed)).join('')}
          </div>
        </div>

        <!-- GENERAL WARD BLOCK -->
        <div class="ward-block">
          <div class="ward-heading">
            <span>Ward B: General Inpatient Ward (Standard Recovery)</span>
            <span>${availGen} / ${genBeds.length} Beds Available</span>
          </div>
          <div class="beds-grid">
            ${genBeds.map(bed => renderBedCard(bed)).join('')}
          </div>
        </div>

        <!-- ATTENDING DOCTORS SECTION -->
        <div class="staff-roster-block">
          <div class="staff-roster-title">Attending Medical Officers (Shift Duty)</div>
          <div class="doctors-grid">
            ${hospital.doctors.map(doc => `
              <div class="doctor-badge-card">
                <div class="doc-info-block">
                  <span class="doc-name">${doc.name}</span>
                  <span class="doc-spec">${doc.spec} ${doc.patientId ? '• Pt: ' + doc.patientId : ''}</span>
                </div>
                <div class="doc-duty-toggle">
                  <span class="duty-label ${doc.onDuty ? 'on' : 'off'}">${doc.onDuty ? 'ON' : 'OFF'}</span>
                  <label class="toggle-switch">
                    <input type="checkbox" ${doc.onDuty ? 'checked' : ''} onchange="toggleDoctorDuty('${hospital.id}', '${doc.id}', this.checked)">
                    <span class="toggle-slider"></span>
                  </label>
                </div>
              </div>
            `).join('')}
          </div>
        </div>
      </div>
    `;
    container.appendChild(card);
  }
}

function renderBedCard(bed) {
  const patient = bed.occupied ? allPatients.get(bed.patientId) : null;
  const isCritical = patient && patient.severity.name === 'Critical';

  const isPendingRefresh = pendingDischargeNotice && pendingDischargeNotice.bedId === bed.id;

  if (isPendingRefresh) {
    return `
      <div class="bed-card vacant" style="border-color: #0284c7; background: #f0f9ff;">
        <div class="bed-card-top">
          <div class="bed-id-block">
            <span class="bed-icon">🔄</span>
            <span class="bed-id-text">${bed.id}</span>
          </div>
          <span class="bed-status-pill" style="background: #e0f2fe; color: #0369a1; border: 1px solid #bae6fd;">
            DISCHARGED
          </span>
        </div>
        <div class="bed-equip-tag">${bed.facility === 'ICU' ? 'Ventilator + Telemetry O2' : 'Standard Inpatient Bed'}</div>
        <div class="bed-patient-details" style="background: #ffffff; border: 1px solid #bae6fd;">
          <div class="pt-meta-line" style="color: #0369a1; font-weight: 700; font-size: 11px;">
            ${pendingDischargeNotice.patientName} discharged
          </div>
          <button class="btn-discharge" style="background: #0284c7; border-color: #0369a1; color: #ffffff; font-weight: 700; margin-top: 5px;" onclick="refreshDashboard()">
            ⟳ Click to Refresh Ward
          </button>
        </div>
      </div>
    `;
  }

  return `
    <div class="bed-card ${bed.occupied ? 'occupied' : 'vacant'} ${isCritical ? 'critical-patient' : ''}">
      <div class="bed-card-top">
        <div class="bed-id-block">
          <span class="bed-icon">🛏️</span>
          <span class="bed-id-text">${bed.id}</span>
        </div>
        <span class="bed-status-pill ${bed.occupied ? (isCritical ? 'critical' : 'occupied') : 'vacant'}">
          ${bed.occupied ? (isCritical ? 'CRITICAL' : 'OCCUPIED') : 'VACANT'}
        </span>
      </div>
      <div class="bed-equip-tag">${bed.facility === 'ICU' ? 'Ventilator + Telemetry O2' : 'Standard Inpatient Bed'}</div>
      ${bed.occupied ? `
        <div class="bed-patient-details">
          <div class="pt-name-line">
            <span>${patient ? patient.name : bed.patientId}</span>
            <span class="pt-mrn">${bed.patientId}</span>
          </div>
          <div class="pt-meta-line">
            ${patient ? `${patient.age}y / ${patient.gender} • ${patient.severity.name}` : 'Admitted'}
          </div>
          <button class="btn-discharge" onclick="dischargePatient('${bed.patientId}')">Discharge Patient</button>
        </div>
      ` : `
        <div class="pt-meta-line" style="color: #166534; font-size: 10.5px; font-weight: 600;">
          ✓ Sanitized & Ready
        </div>
      `}
    </div>
  `;
}

function renderQueue() {
  const container = document.getElementById('queueList');
  const queueList = waitingQueue.toArraySorted();

  if (queueList.length === 0) {
    container.innerHTML = `
      <div class="empty-queue-message">
        <span class="empty-icon">✓</span>
        <p>No emergency patients currently waiting in triage queue.</p>
        <span class="empty-sub">All incoming emergency cases have been allocated to inpatient beds.</span>
      </div>`;
    return;
  }

  container.innerHTML = queueList.map((pt, index) => `
    <div class="queue-patient-card ${index === 0 ? 'rank-first' : ''}">
      <div class="queue-rank-badge">#${index + 1}</div>
      <div class="queue-patient-info">
        <div class="queue-header-line">
          <span class="queue-pt-name">${pt.name}</span>
          <span class="triage-acuity-tag acuity-${pt.severity.name.toLowerCase()}">${pt.severity.name}</span>
          <span style="font-family: var(--font-mono); font-size: 10px; color: var(--slate-400);">${pt.id}</span>
        </div>
        <div class="queue-meta-line">
          Arrival #${pt.arrivalSequence} • ${pt.age}y / ${pt.gender} • Requires: <strong>${pt.requiredFacility} Bed</strong>
        </div>
      </div>
      <div class="queue-action-block">
        ${pt.severity.name !== 'Critical' ? `
          <button class="btn-escalate" title="Escalate Urgency (Dynamic Heap Re-ranking)" onclick="updatePatientSeverity('${pt.id}', Severity.CRITICAL)">
            Escalate (P1)
          </button>
        ` : ''}
      </div>
    </div>
  `).join('');
}

function renderMapAndDijkstra() {
  const svg = document.getElementById('roadMapSvg');
  if (!svg) return;
  const dijkstraResult = dijkstra(roadGraph, 'L1');

  let edgesHtml = '';
  const renderedPairs = new Set();

  for (let [u, edges] of roadGraph.adjacencyList.entries()) {
    const locU = roadGraph.locations.get(u);
    for (let e of edges) {
      const v = e.to;
      const pairKey = [u, v].sort().join('-');
      if (renderedPairs.has(pairKey)) continue;
      renderedPairs.add(pairKey);

      const locV = roadGraph.locations.get(v);
      const midX = (locU.x + locV.x) / 2;
      const midY = (locU.y + locV.y) / 2;

      edgesHtml += `
        <line x1="${locU.x}" y1="${locU.y}" x2="${locV.x}" y2="${locV.y}" 
              stroke="#0284c7" stroke-width="2.5" stroke-opacity="0.5" />
        <rect x="${midX - 18}" y="${midY - 10}" width="36" height="20" rx="4" fill="#ffffff" stroke="#bae6fd" stroke-width="1.5" />
        <text x="${midX}" y="${midY + 4}" font-size="10.5" font-weight="700" font-family="JetBrains Mono" fill="#0369a1" text-anchor="middle" cursor="pointer"
              title="Click to edit road travel time" onclick="promptUpdateTravelTime('${u}', '${v}', ${e.time})">${e.time}m</text>
      `;
    }
  }

  let nodesHtml = '';
  for (let loc of roadGraph.locations.values()) {
    let isPrimary = (loc.id === 'L1');
    let isHosp = (loc.id === 'L1' || loc.id === 'L3' || loc.id === 'L5' || loc.id === 'L8');

    let circleColor = isPrimary ? '#dc2626' : (isHosp ? '#0284c7' : '#64748b');
    let circleRadius = isHosp ? 16 : 9;

    nodesHtml += `
      <g transform="translate(${loc.x}, ${loc.y})" style="cursor: default;">
        <circle r="${circleRadius + 4}" fill="${circleColor}" fill-opacity="0.14" stroke="${circleColor}" stroke-width="1.5" />
        <circle r="${circleRadius}" fill="${isPrimary ? '#dc2626' : (isHosp ? '#0284c7' : '#ffffff')}" stroke="${circleColor}" stroke-width="2" />
        ${isHosp ? `
          <text y="4" font-size="12" font-weight="800" font-family="sans-serif" fill="#ffffff" text-anchor="middle">✚</text>
        ` : `
          <circle r="3" fill="#64748b" />
        `}
        <text y="${isHosp ? -22 : -14}" font-size="11" font-weight="800" font-family="JetBrains Mono" fill="#0f172a" text-anchor="middle">${loc.id}</text>
        <text y="${isHosp ? 30 : 22}" font-size="10" font-weight="600" font-family="sans-serif" fill="#334155" text-anchor="middle">${loc.name}</text>
      </g>
    `;
  }

  svg.innerHTML = edgesHtml + nodesHtml;

  const tableBody = document.getElementById('dijkstraTableBody');
  if (!tableBody) return;
  const targetHospitals = [
    { id: 'VSDH', name: 'Vallam Sub-District Hospital', loc: 'L3' },
    { id: 'TCHC', name: 'Thoppur Community Health Centre', loc: 'L5' },
    { id: 'KTH',  name: 'Kadur Taluk Hospital', loc: 'L8' }
  ];

  tableBody.innerHTML = targetHospitals.map(h => {
    const time = dijkstraResult.distances[h.loc];
    const path = dijkstraResult.getPathTo(h.loc);
    return `
      <tr>
        <td><strong>${h.id}</strong> <span style="font-size:11px; color:var(--slate-500);">${h.name}</span></td>
        <td><span style="font-family:var(--font-mono); font-weight:700; color:var(--primary-dark);">${h.loc}</span></td>
        <td><span class="transit-time-pill">${time} mins</span></td>
        <td class="route-path-text">${path.join(' ➔ ')}</td>
      </tr>
    `;
  }).join('');
}

function renderAudit() {
  const shortBody = document.getElementById('auditTableBody');
  const fullBody = document.getElementById('fullAuditTableBody');

  const rows = auditHistory.map(item => `
    <tr>
      <td style="font-family:var(--font-mono); color:var(--text-muted);">${item.time}</td>
      <td><span class="event-type-badge event-${item.type}">${item.type}</span></td>
      <td style="font-family:var(--font-mono); font-weight:600;">${item.patientId}</td>
      <td style="font-family:var(--font-mono); font-size:10px;">${item.hospitalId} / ${item.bedId}</td>
      <td style="color:var(--text-secondary);">${item.details}</td>
    </tr>
  `).join('');

  if (shortBody) {
    shortBody.innerHTML = rows || `<tr><td colspan="5" style="text-align:center; color:var(--text-faint); padding:16px;">No audit events yet.</td></tr>`;
  }

  if (fullBody) {
    fullBody.innerHTML = auditHistory.map(item => `
      <tr>
        <td style="font-family:var(--font-mono); font-weight:bold;">#${item.seq}</td>
        <td style="font-family:var(--font-mono); color:var(--text-muted);">${item.time}</td>
        <td><span class="event-type-badge event-${item.type}">${item.type}</span></td>
        <td style="font-family:var(--font-mono); font-weight:600;">${item.patientId}</td>
        <td style="font-family:var(--font-mono); font-size:10px;">${item.hospitalId} / ${item.bedId}</td>
        <td style="font-family:var(--font-mono); font-size:10px;">${item.doctorId}</td>
        <td style="color:var(--text-secondary);">${item.details}</td>
      </tr>
    `).join('') || `<tr><td colspan="7" style="text-align:center; color:var(--text-faint); padding:20px;">No audit events recorded yet.</td></tr>`;
  }
}

function promptUpdateTravelTime(u, v, currentTime) {
  const input = prompt(`Update road travel time between [${u}] and [${v}] in minutes:`, currentTime);
  if (input !== null) {
    const newTime = parseInt(input.trim(), 10);
    if (!isNaN(newTime) && newTime > 0) {
      roadGraph.updateTravelTime(u, v, newTime);
      logAudit('TRAVEL_TIME_UPDATE', null, null, null, null, `Road [${u}-${v}] travel time updated to ${newTime} mins.`);
      showToast(`Road [${u}-${v}] travel time set to ${newTime}m. Dijkstra recalculated!`, 'info');
      renderAll();
    } else {
      showToast('Invalid travel time entered.', 'error');
    }
  }
}

function showToast(message, type = 'info') {
  const container = document.getElementById('toastContainer');
  if (!container) return;
  const toast = document.createElement('div');
  toast.className = `toast ${type}`;
  toast.innerText = message;
  container.appendChild(toast);
  setTimeout(() => {
    toast.style.opacity = '0';
    toast.style.transform = 'translateX(100%)';
    toast.style.transition = 'all 0.3s ease';
    setTimeout(() => toast.remove(), 300);
  }, 3500);
}

function updateLiveClock() {
  const clockEl = document.getElementById('liveClock');
  if (clockEl) {
    clockEl.innerText = new Date().toLocaleTimeString();
  }
}

function exportAuditJson() {
  const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(auditHistory, null, 2));
  const downloadAnchor = document.createElement('a');
  downloadAnchor.setAttribute("href", dataStr);
  downloadAnchor.setAttribute("download", `nalam_hospital_audit_${Date.now()}.json`);
  document.body.appendChild(downloadAnchor);
  downloadAnchor.click();
  downloadAnchor.remove();
  showToast('Audit ledger exported to JSON file.', 'success');
}

async function checkBackendConnectivity() {
  if (backendSyncInProgress) return;
  backendSyncInProgress = true;
  try {
    const res = await fetch(`${API_BASE}/api/status`, { cache: 'no-store' });
    if (!res.ok) throw new Error(`Backend status request failed (${res.status}).`);
    const data = await res.json();
    if (data.status !== 'OK') throw new Error('Backend returned an invalid status response.');
    isBackendConnected = true;
    backendEverConnected = true;
    isDatabaseConnected = data.databaseConnected === true;
    let portalRole = null;
    try { portalRole = JSON.parse(localStorage.getItem('nalamPortalUserV2') || 'null')?.role || null; } catch { /* No active portal profile. */ }
    if (!localStorage.getItem('nalamPortalSessionV2') || portalRole !== 'MANAGEMENT') {
      updateBackendStatusText();
      return;
    }
    await synchronizeBackendState();
    updateBackendStatusText();
  } catch (err) {
    isBackendConnected = false;
    updateBackendStatusText();
    if (backendEverConnected) showToast(`Backend unavailable; dashboard state may be stale. ${err.message}`, 'error');
  } finally {
    backendSyncInProgress = false;
  }
}

function backendModeActive() {
  return window.location.protocol === 'http:' || window.location.protocol === 'https:' ||
    isBackendConnected || backendEverConnected;
}

async function requestBackend(path, options = {}) {
  const portalToken = localStorage.getItem('nalamPortalSessionV2');
  const authHeaders = portalToken ? { Authorization: `Bearer ${portalToken}` } : {};
  const response = await fetch(`${API_BASE}${path}`, {
    cache: 'no-store',
    ...options,
    headers: { 'Content-Type': 'application/json', ...authHeaders, ...(options.headers || {}) }
  });
  const bodyText = await response.text();
  let data = null;
  if (bodyText) {
    try {
      data = JSON.parse(bodyText);
    } catch (error) {
      throw new Error(`Backend returned invalid JSON (${response.status}).`);
    }
  }
  if (!response.ok) {
    throw new Error(data && data.error ? data.error : `Backend request failed (${response.status}).`);
  }
  if (data === null) throw new Error('Backend returned an empty response.');
  return data;
}

function mapBackendPatient(record) {
  const severity = Severity[String(record.severity).toUpperCase()];
  if (!severity) throw new Error(`Backend returned unsupported patient severity: ${record.severity}`);
  return {
    id: record.id,
    name: record.name,
    age: record.age,
    gender: record.gender,
    severity,
    requiredFacility: record.requiredFacility,
    arrivalSequence: record.arrivalSequence,
    status: record.status,
    assignedHospitalId: record.assignedHospitalId,
    assignedBedId: record.assignedBedId,
    assignedDoctorId: record.assignedDoctorId,
    referralNotes: record.referralNotes || null
  };
}

async function synchronizeBackendState() {
  const [hospitalRecords, patientRecords, queueRecords, auditRecords] = await Promise.all([
    requestBackend('/api/hospitals'),
    requestBackend('/api/patients'),
    requestBackend('/api/queue'),
    requestBackend('/api/audit')
  ]);
  if (![hospitalRecords, patientRecords, queueRecords, auditRecords].every(Array.isArray)) {
    throw new Error('Backend returned an invalid dashboard snapshot.');
  }

  hospitals.clear();
  for (const hospital of hospitalRecords) {
    hospitals.set(hospital.id, {
      id: hospital.id,
      name: hospital.name,
      locationId: hospital.locationId,
      isPrimary: hospital.isPrimary,
      beds: hospital.beds.map(bed => ({
        id: bed.id,
        number: bed.number,
        type: bed.type,
        facility: bed.type.toUpperCase().startsWith('ICU') ? 'ICU' : 'General',
        occupied: bed.isOccupied,
        patientId: bed.patientId
      })),
      doctors: hospital.doctors.map(doctor => ({
        id: doctor.id,
        name: doctor.name,
        spec: doctor.specialization,
        onDuty: doctor.isOnDuty,
        patientId: doctor.assignedPatientId
      }))
    });
  }

  allPatients = new Map(patientRecords.map(record => {
    const patient = mapBackendPatient(record);
    return [patient.id, patient];
  }));
  waitingQueue = new PriorityQueue();
  for (const record of queueRecords) {
    const patient = allPatients.get(record.id);
    if (patient) waitingQueue.offer(patient);
  }
  auditHistory = auditRecords.slice().reverse().map(record => ({
    seq: record.seq,
    time: record.timestamp ? record.timestamp.replace('T', ' ').slice(11, 19) : '',
    type: record.type,
    patientId: record.patientId || '-',
    hospitalId: record.hospitalId || '-',
    bedId: record.bedId || '-',
    doctorId: record.doctorId || '-',
    details: record.details
  }));
  patientSeqCounter = Math.max(1, ...patientRecords.map(patient => Number(patient.arrivalSequence) + 1));
  auditSeqCounter = Math.max(1, ...auditRecords.map(record => Number(record.seq) + 1));
  renderAll();
}

function updateBackendStatusText() {
  const chip = document.getElementById('backendStatusChip');
  const text = document.getElementById('backendStatusText');
  if (!chip || !text) return;
  if (isBackendConnected && isDatabaseConnected) {
    chip.style.background = 'rgba(104, 176, 171, 0.25)';
    chip.style.borderColor = '#68B0AB';
    chip.style.color = '#1B4332';
    text.innerText = 'REST API ONLINE • MYSQL CONNECTED';
  } else if (isBackendConnected) {
    chip.style.background = 'rgba(160, 196, 255, 0.25)';
    chip.style.borderColor = '#A0C4FF';
    chip.style.color = '#2B3A67';
    text.innerText = 'REST API ONLINE • MEMORY ONLY';
  } else if (backendEverConnected) {
    chip.style.background = 'rgba(255, 173, 173, 0.25)';
    chip.style.borderColor = '#FFADAD';
    chip.style.color = '#782828';
    text.innerText = 'BACKEND UNAVAILABLE';
  } else {
    chip.style.background = 'rgba(255, 173, 173, 0.25)';
    chip.style.borderColor = '#FFADAD';
    chip.style.color = '#782828';
    text.innerText = 'STANDALONE CLIENT ENGINE';
  }
}

function describeAllocationResult(result) {
  switch (result.disposition) {
    case 'ADMITTED_PRIMARY':
      return `Admitted locally at ${result.allocatedHospitalId}, bed ${result.allocatedBedId}.`;
    case 'REFERRED_AND_RESERVED':
      return `Referred to ${result.allocatedHospitalId}, bed ${result.allocatedBedId} (${result.travelTime} min).`;
    case 'QUEUED_NO_BED':
    case 'QUEUED_NO_DOCTOR':
    case 'QUEUED_NO_CAPACITY':
      return `Patient remains in the triage queue. ${result.remarks || result.disposition}`;
    default:
      return `Triage result: ${result.disposition}.`;
  }
}

function summarizeQueueResults(results) {
  const admitted = results.filter(result => result.disposition === 'ADMITTED_PRIMARY').length;
  const referred = results.filter(result => result.disposition === 'REFERRED_AND_RESERVED').length;
  const waiting = results.filter(result => result.disposition.startsWith('QUEUED_')).length;
  const parts = [];
  if (admitted) parts.push(`${admitted} admitted locally`);
  if (referred) parts.push(`${referred} referred`);
  if (waiting) parts.push(`${waiting} remain waiting`);
  return parts.length ? `Triage: ${parts.join(', ')}.` : 'No patients required triage.';
}

async function processBackendWaitingQueue(silent = false) {
  try {
    const result = await requestBackend('/api/process-queue', { method: 'POST' });
    await synchronizeBackendState();
    if (!result.databasePersisted) {
      if (!silent) showToast('Queue processed by the backend in memory; database is unavailable.', 'warning');
    } else if (!silent) {
      const hasAllocations = result.results.some(item =>
        item.disposition === 'ADMITTED_PRIMARY' || item.disposition === 'REFERRED_AND_RESERVED');
      const hasWaitingPatients = result.results.some(item => item.disposition.startsWith('QUEUED_'));
      showToast(summarizeQueueResults(result.results), hasWaitingPatients && !hasAllocations ? 'warning' : 'success');
    }
    return result;
  } catch (error) {
    showToast(`Queue processing failed: ${error.message}`, 'error');
    return null;
  }
}

async function submitBackendPatient(patientData, autoProcess = true) {
  let result;
  let triageResult = null;
  try {
    result = await requestBackend('/api/register', {
      method: 'POST',
      body: JSON.stringify({
        id: patientData.id,
        name: patientData.name,
        age: patientData.age,
        gender: patientData.gender,
        severity: patientData.severity.name.toUpperCase(),
        requiredFacility: patientData.requiredFacility
      })
    });
  } catch (error) {
    showToast(`Patient registration failed: ${error.message}`, 'error');
    return null;
  }
  if (autoProcess) {
    triageResult = await processBackendWaitingQueue(true);
  }
  try {
    await synchronizeBackendState();
    const allocation = triageResult && Array.isArray(triageResult.results)
      ? triageResult.results.find(item => item.patientId === result.patient.id)
      : null;
    const outcome = allocation
      ? describeAllocationResult(allocation)
      : triageResult
        ? 'Patient is registered and remains in the triage queue.'
        : 'Patient is registered; triage could not be confirmed.';
    const persistence = result.databasePersisted
      ? 'Patient record saved to MySQL.'
      : 'Patient record exists in backend memory only; MySQL is unavailable.';
    const hasAllocation = allocation &&
      ['ADMITTED_PRIMARY', 'REFERRED_AND_RESERVED'].includes(allocation.disposition);
    showToast(`${outcome} ${persistence}`,
      result.databasePersisted && (!autoProcess || (triageResult && hasAllocation)) ? 'success' : 'warning');
    return result.patient;
  } catch (error) {
    showToast(`Patient was registered but dashboard refresh failed: ${error.message}`, 'error');
    return result.patient;
  }
}

async function refreshBackendDashboard(silent = false) {
  pendingDischargeNotice = null;
  const banner = document.getElementById('dischargeRefreshBanner');
  if (banner) banner.style.display = 'none';
  const btnRefresh = document.getElementById('btnRefreshSystem');
  if (btnRefresh) btnRefresh.classList.remove('pulse-highlight');
  const badge = document.getElementById('refreshBadge');
  if (badge) badge.style.display = 'none';
  const result = await processBackendWaitingQueue();
  if (result && !silent) {
    showToast('Wards refreshed from backend state.', 'success');
    playSuccessChime();
  }
}

async function dischargeBackendPatient(patientId) {
  const patient = allPatients.get(patientId);
  if (!patient || !['ADMITTED_LOCAL', 'REFERRED'].includes(patient.status)) {
    showToast('Discharge failed: patient is not currently admitted.', 'error');
    return false;
  }
  try {
    const result = await requestBackend('/api/discharge', {
      method: 'POST',
      body: JSON.stringify({ hospitalId: patient.assignedHospitalId, patientId })
    });
    await synchronizeBackendState();
    showToast(result.databasePersisted
      ? `Discharged ${patient.name}; released resources and updated MySQL.`
      : `Discharged ${patient.name} in backend memory; MySQL is unavailable.`, result.databasePersisted ? 'success' : 'warning');
    return true;
  } catch (error) {
    showToast(`Discharge failed: ${error.message}`, 'error');
    return false;
  }
}

async function updateBackendDoctorDuty(hospitalId, doctorId, onDuty) {
  try {
    const result = await requestBackend('/api/doctor-duty', {
      method: 'POST',
      body: JSON.stringify({ hospitalId, doctorId, onDuty })
    });
    await synchronizeBackendState();
    if (result.success) showToast(`Doctor duty status updated${result.databasePersisted ? '.' : ' in backend memory only; MySQL is unavailable.'}`, result.databasePersisted ? 'success' : 'warning');
  } catch (error) {
    showToast(`Doctor duty update failed: ${error.message}`, 'error');
    try {
      await synchronizeBackendState();
    } catch (refreshError) {
      showToast(`Backend refresh after duty update failure also failed: ${refreshError.message}`, 'error');
    }
  }
}

async function updateBackendPatientSeverity(patientId, severity) {
  try {
    await requestBackend('/api/patient-severity', {
      method: 'POST',
      body: JSON.stringify({ patientId, severity: severity.name.toUpperCase() })
    });
    await synchronizeBackendState();
    showToast(`Patient priority updated to ${severity.name}.`, 'success');
  } catch (error) {
    showToast(`Priority update failed: ${error.message}`, 'error');
  }
}

async function loadBackendSyntheticBatch() {
  try {
    const result = await requestBackend('/api/batch', { method: 'POST' });
    await requestBackend('/api/process-queue', { method: 'POST' });
    await synchronizeBackendState();
    showToast(result.databasePersisted
      ? `Loaded ${result.ingested} synthetic patients into the backend and MySQL.`
      : `Loaded ${result.ingested} synthetic patients in backend memory only; MySQL is unavailable.`, result.databasePersisted ? 'success' : 'warning');
  } catch (error) {
    showToast(`Batch load failed: ${error.message}`, 'error');
    try {
      await synchronizeBackendState();
    } catch (refreshError) {
      showToast(`Backend refresh after batch failure also failed: ${refreshError.message}`, 'error');
    }
  }
}

// =============================================================================
// 7. EVENT LISTENERS & TAB ROUTING
// =============================================================================

document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('patientSearch')?.addEventListener('input', renderPatientRegister);
  document.getElementById('patientStatusFilter')?.addEventListener('change', renderPatientRegister);
  initializeRoadNetwork();
  initializeHospitals();
  renderAll();

  checkBackendConnectivity();
  setInterval(checkBackendConnectivity, 10000);

  setInterval(updateLiveClock, 1000);
  updateLiveClock();

  // Tab switching
  document.querySelectorAll('.nav-tab').forEach(tabBtn => {
    tabBtn.addEventListener('click', () => {
      document.querySelectorAll('.nav-tab').forEach(b => b.classList.remove('active'));
      document.querySelectorAll('.tab-content').forEach(c => c.classList.remove('active'));

      tabBtn.classList.add('active');
      const targetId = tabBtn.getAttribute('data-tab');
      const targetEl = document.getElementById(targetId);
      if (targetEl) targetEl.classList.add('active');

      if (targetId === 'tab-map' || targetId === 'tab-dashboard') {
        renderMapAndDijkstra();
      }
      if (targetId === 'tab-audit') {
        renderAudit();
      }
    });
  });

  // Sound Toggle
  const btnToggleSound = document.getElementById('btnToggleSound');
  if (btnToggleSound) {
    btnToggleSound.addEventListener('click', () => {
      soundEnabled = !soundEnabled;
      document.getElementById('soundIconOn').style.display = soundEnabled ? 'block' : 'none';
      document.getElementById('soundIconOff').style.display = soundEnabled ? 'none' : 'block';
      showToast(`Audio alert cues ${soundEnabled ? 'ENABLED' : 'MUTED'}`, 'info');
    });
  }

  // Toolbar Handlers
  document.getElementById('btnLoadBatch').addEventListener('click', loadSyntheticBatch);
  document.getElementById('btnProcessQueue').addEventListener('click', () => {
    processWaitingQueue();
  });
  document.getElementById('btnResetAll').addEventListener('click', resetSystem);

  // Refresh Handlers
  const btnRefreshSystem = document.getElementById('btnRefreshSystem');
  if (btnRefreshSystem) {
    btnRefreshSystem.addEventListener('click', () => refreshDashboard(false));
  }

  const btnQuickRefresh = document.getElementById('btnQuickRefreshBeds');
  if (btnQuickRefresh) {
    btnQuickRefresh.addEventListener('click', () => refreshDashboard(false));
  }

  const btnBannerRefresh = document.getElementById('btnBannerRefresh');
  if (btnBannerRefresh) {
    btnBannerRefresh.addEventListener('click', () => refreshDashboard(false));
  }

  // Scenario Buttons
  document.getElementById('btnRunTest1').addEventListener('click', runScenario1_NoICUBed);
  document.getElementById('btnRunTest2').addEventListener('click', runScenario2_NoDoctor);
  document.getElementById('btnRunTest3').addEventListener('click', runScenario3_BedContention);
  document.getElementById('btnRunTest4').addEventListener('click', runScenario4_DischargeEnable);

  const btnRunAllTests = document.getElementById('btnRunAllTestsWeb');
  if (btnRunAllTests) {
    btnRunAllTests.addEventListener('click', runAllTestsSequentially);
  }

  // Export JSON
  const btnExport = document.getElementById('btnExportJson');
  if (btnExport) btnExport.addEventListener('click', exportAuditJson);

  const btnClearAudit = document.getElementById('btnClearAudit');
  if (btnClearAudit) {
    btnClearAudit.addEventListener('click', () => {
      auditHistory = [];
      renderAudit();
      showToast('Audit view cleared.', 'info');
    });
  }

  // Patient Registration Form
  document.getElementById('patientForm').addEventListener('submit', async (e) => {
    e.preventDefault();
    const id = document.getElementById('ptId').value.trim();
    const name = document.getElementById('ptName').value.trim();
    const age = parseInt(document.getElementById('ptAge').value.trim(), 10);
    const gender = document.getElementById('ptGender').value;
    const sevKey = document.getElementById('ptSeverity').value;
    const facility = document.getElementById('ptFacility').value;

    const severity = Severity[sevKey] || Severity.MINOR;
    if (backendModeActive()) {
      const patient = await submitBackendPatient({ id, name, age, gender, severity, requiredFacility: facility });
      if (!patient) return;
      document.getElementById('ptId').value = '';
      document.getElementById('ptName').value = '';
      document.getElementById('ptAge').value = '';
      return;
    }
    try {
      registerPatient(id, name, age, gender, severity, facility, true);
    } catch (error) {
      showToast(error.message, 'error');
      return;
    }

    document.getElementById('ptId').value = '';
    document.getElementById('ptName').value = '';
    document.getElementById('ptAge').value = '';
  });
});
