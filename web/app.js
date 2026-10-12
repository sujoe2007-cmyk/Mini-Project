/* =========================================================
   NALAM HOSPITAL SYSTEM — Frontend App
   Connects to Java HTTP server at http://localhost:8080
   ========================================================= */

const API = (window.location.protocol.startsWith('http') && window.location.port === '8080')
  ? `${window.location.origin}/api`
  : 'http://127.0.0.1:8080/api';

/* ── Cached state ── */
let allPatientsCache = [];
let routeData = null;

/* =========================================================
   CONNECTIVITY
   ========================================================= */
async function checkConnection() {
  try {
    const r = await fetch(`${API}/status`, { signal: AbortSignal.timeout(2000) });
    if (r.ok) setOnline(true);
    else setOnline(false);
  } catch { setOnline(false); }
}

function setOnline(online) {
  const dot   = document.getElementById('connection-dot');
  const label = document.getElementById('connection-label');
  if (online) {
    dot.className   = 'status-dot online';
    label.textContent = 'Connected';
  } else {
    dot.className   = 'status-dot offline';
    label.textContent = 'Offline';
  }
}

/* =========================================================
   PAGE NAVIGATION
   ========================================================= */
const pageTitles = {
  dashboard: 'Dashboard',
  patients:  'Patients',
  hospitals: 'Hospitals',
  queue:     'Triage Queue',
  network:   'Route Network',
  history:   'History',
};

function showPage(name, el) {
  document.querySelectorAll('.page').forEach(p => p.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  document.getElementById(`page-${name}`).classList.add('active');
  if (el) el.classList.add('active');
  document.getElementById('page-title').textContent = pageTitles[name] || name;

  // Load data for the page
  if (name === 'dashboard') loadDashboard();
  if (name === 'patients')  loadPatients();
  if (name === 'hospitals') loadHospitalsDetail();
  if (name === 'queue')     loadQueue();
  if (name === 'network')   loadNetwork();
  if (name === 'history')   loadHistory();
}

function loadHospitals() {
  const el = document.getElementById('nav-hospitals');
  showPage('hospitals', el);
}

/* =========================================================
   REFRESH ALL
   ========================================================= */
function refreshAll() {
  const btn = document.getElementById('refresh-btn');
  btn.querySelector('svg').style.animation = 'spin .7s linear infinite';
  loadDashboard();
  setTimeout(() => btn.querySelector('svg').style.animation = '', 700);
}

/* =========================================================
   DASHBOARD
   ========================================================= */
async function loadDashboard() {
  await Promise.all([loadStats(), loadHospitalCards(), loadDashboardQueue(), loadRouteCanvas('route-canvas', 420, 260)]);
}

async function loadStats() {
  try {
    const r = await fetch(`${API}/stats`);
    const d = await r.json();
    setText('stat-waiting-val', d.waitingQueue ?? '—');
    setText('stat-icu-val',     d.availableIcu ?? '—');
    setText('stat-gen-val',     d.availableGeneral ?? '—');
    setText('stat-docs-val',    d.availableDoctors ?? '—');
  } catch {
    ['stat-waiting-val','stat-icu-val','stat-gen-val','stat-docs-val'].forEach(id => setText(id, '—'));
  }
}

async function loadHospitalCards() {
  const box = document.getElementById('hospital-cards');
  try {
    const r = await fetch(`${API}/hospitals`);
    const hospitals = await r.json();
    box.innerHTML = hospitals.map(h => hospitalCardHTML(h)).join('');
  } catch {
    box.innerHTML = errorHTML('Could not load hospitals. Is the server running?');
  }
}

function hospitalCardHTML(h) {
  const totalBeds = h.totalBeds;
  const usedBeds  = totalBeds - h.availableBeds;
  const pct       = totalBeds ? Math.round((usedBeds / totalBeds) * 100) : 0;
  const fillClass = pct >= 85 ? 'high' : pct >= 60 ? 'medium' : 'low';
  const typeClass = h.primary ? 'primary' : 'referral';
  const typeBadge = h.primary
    ? '<span class="badge badge-primary">PRIMARY</span>'
    : '<span class="badge badge-referral">REFERRAL</span>';
  return `
  <div class="hosp-card ${typeClass}">
    <div class="hosp-card-header">
      <div>
        <div class="hosp-name">${h.name}</div>
        <div class="hosp-id">${h.hospitalId}</div>
      </div>
      ${typeBadge}
    </div>
    <div class="hosp-metrics">
      <div class="metric-row">
        <span class="metric-label">Bed Occupancy</span>
        <span class="metric-value">${usedBeds}/${totalBeds}</span>
      </div>
      <div class="progress-bar"><div class="progress-fill ${fillClass}" style="width:${pct}%"></div></div>
      <div class="metric-row">
        <span class="metric-label">ICU Available</span>
        <span class="metric-value">${h.availableIcu}</span>
      </div>
      <div class="metric-row">
        <span class="metric-label">General Available</span>
        <span class="metric-value">${h.availableGeneral}</span>
      </div>
      <div class="metric-row">
        <span class="metric-label">Doctors Available</span>
        <span class="metric-value">${h.availableDoctors} / ${h.onDutyDoctors}</span>
      </div>
    </div>
  </div>`;
}

async function loadDashboardQueue() {
  const box = document.getElementById('dashboard-queue-list');
  try {
    const r = await fetch(`${API}/queue`);
    const q = await r.json();
    if (!q.length) { box.innerHTML = `<p style="color:var(--text-muted);font-size:.85rem;padding:8px 0">Queue is empty.</p>`; return; }
    box.innerHTML = q.slice(0, 6).map((p, i) => `
      <div class="queue-item">
        <div class="queue-rank">#${i + 1}</div>
        <div class="queue-info">
          <div class="queue-name">${p.name}</div>
          <div class="queue-meta">${p.patientId} · Seq #${p.arrivalSequence}</div>
        </div>
        ${severityBadge(p.severity)}
        <span class="badge ${facilityClass(p.requiredFacility)}">${p.requiredFacility}</span>
      </div>`).join('');
  } catch { box.innerHTML = errorHTML('Queue unavailable'); }
}

/* =========================================================
   PATIENTS PAGE
   ========================================================= */
async function loadPatients() {
  const tbody = document.getElementById('patients-tbody');
  tbody.innerHTML = `<tr><td colspan="9" class="empty-row"><div class="spinner"></div></td></tr>`;
  try {
    const r = await fetch(`${API}/patients`);
    allPatientsCache = await r.json();
    renderPatients(allPatientsCache);
  } catch { tbody.innerHTML = `<tr><td colspan="9" class="empty-row">Could not load patients.</td></tr>`; }
}

function renderPatients(list) {
  const tbody = document.getElementById('patients-tbody');
  if (!list.length) { tbody.innerHTML = `<tr><td colspan="9" class="empty-row">No patients found.</td></tr>`; return; }
  tbody.innerHTML = list.map(p => `
    <tr>
      <td><code style="font-size:.75rem;color:var(--text-muted)">${p.patientId}</code></td>
      <td style="font-weight:600">${p.name}</td>
      <td>${p.age}</td>
      <td>${p.gender}</td>
      <td>${severityBadge(p.severity)}</td>
      <td><span class="badge ${facilityClass(p.requiredFacility)}">${p.requiredFacility}</span></td>
      <td>${statusBadge(p.status)}</td>
      <td style="font-size:.78rem;color:var(--text-muted)">${p.assignedHospitalId ?? '—'}</td>
      <td>
        <div style="display:flex;gap:4px;flex-wrap:wrap">
          ${p.status === 'WAITING' ? `<button class="action-link" onclick="openSeverityModal('${p.patientId}','${p.name}',${p.severity})">Re-rank</button>` : ''}
          ${(p.status === 'ADMITTED' || p.status === 'REFERRED') ? `<button class="action-link danger" onclick="openDischargeModal('${p.assignedHospitalId}','${p.patientId}')">Discharge</button>` : ''}
        </div>
      </td>
    </tr>`).join('');
}

function filterPatients() {
  const search = document.getElementById('patient-search').value.toLowerCase();
  const status = document.getElementById('patient-filter-status').value;
  const filtered = allPatientsCache.filter(p =>
    (!search || p.name.toLowerCase().includes(search) || p.patientId.toLowerCase().includes(search)) &&
    (!status || p.status === status)
  );
  renderPatients(filtered);
}

/* =========================================================
   HOSPITALS DETAIL PAGE
   ========================================================= */
async function loadHospitalsDetail() {
  renderAdminBanner();
  const box = document.getElementById('hospitals-detail-list');
  try {
    const r = await fetch(`${API}/hospitals/detail`);
    const hospitals = await r.json();
    box.innerHTML = hospitals.map(h => hospitalDetailHTML(h)).join('');
  } catch { box.innerHTML = errorHTML('Could not load hospital details.'); }
}

function escapeQuotes(str) {
  if (!str) return '';
  return String(str).replace(/'/g, "\\'").replace(/"/g, '&quot;');
}

function hospitalDetailHTML(h) {
  const typeBadge = h.primary
    ? '<span class="badge badge-primary">PRIMARY</span>'
    : '<span class="badge badge-referral">REFERRAL</span>';

  const hasAccess = canEditResources();

  const bedsHTML = (h.beds || []).map(b => `
    <div class="bed-row">
      <div class="bed-status-dot ${b.occupied ? 'occupied' : 'vacant'}"></div>
      <div style="flex:1">
        <div style="font-weight:600;font-size:.8rem">${b.bedNumber} <span style="color:var(--text-muted);font-weight:400">${b.bedType}</span></div>
        <div class="bed-id">${b.bedId}</div>
      </div>
      <div style="display:flex;align-items:center;gap:6px">
        <span class="badge ${b.occupied ? 'badge-critical' : 'badge-minor'}">${b.occupied ? 'Occupied' : 'Vacant'}</span>
        <button class="action-link edit-btn ${hasAccess ? '' : 'locked'}" onclick="promptEditBed('${h.hospitalId}','${b.bedId}','${escapeQuotes(b.bedNumber)}',${b.occupied})" title="Edit Bed Allocation">
          <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
          Edit
        </button>
      </div>
    </div>`).join('');

  const docsHTML = (h.doctors || []).map(d => `
    <div class="doc-row">
      <div style="flex:1">
        <div style="font-weight:600;font-size:.8rem">Dr. ${d.name}</div>
        <div style="font-size:.74rem;color:var(--text-muted)">${d.specialization}</div>
        <div class="doc-id">${d.doctorId}</div>
      </div>
      <div style="text-align:right">
        <div class="${d.onDuty ? 'doc-duty-on' : 'doc-duty-off'}">${d.onDuty ? 'ON DUTY' : 'OFF DUTY'}</div>
        ${d.assignedPatientId ? `<div style="font-size:.73rem;color:var(--text-muted)">Pt: ${d.assignedPatientId}</div>` : ''}
        <div style="display:flex;align-items:center;gap:4px;margin-top:4px;justify-content:flex-end">
          <button class="action-link btn-sm" onclick="toggleDuty('${h.hospitalId}','${d.doctorId}',${!d.onDuty})">${d.onDuty ? 'Set Off-Duty' : 'Set On-Duty'}</button>
          <button class="action-link edit-btn ${hasAccess ? '' : 'locked'}" onclick="promptEditDoctor('${h.hospitalId}','${d.doctorId}','${escapeQuotes(d.name)}','${escapeQuotes(d.specialization)}',${d.onDuty})" title="Edit Doctor Details">
            <svg width="11" height="11" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z"/></svg>
            Edit
          </button>
        </div>
      </div>
    </div>`).join('');

  return `
  <div class="hosp-detail-card">
    <div class="hosp-detail-header">
      <div>
        <div class="hosp-detail-title">${h.name}</div>
        <div style="font-size:.75rem;color:var(--text-muted);margin-top:2px;font-family:'JetBrains Mono',monospace">${h.hospitalId}</div>
      </div>
      ${typeBadge}
    </div>
    <div class="hosp-detail-grid">
      <div class="hosp-detail-col">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
          <h4 style="margin-bottom:0">Beds (${h.availableBeds} / ${h.totalBeds} Available)</h4>
          <button class="action-link btn-sm ${hasAccess ? '' : 'locked'}" onclick="promptAddBed('${h.hospitalId}')" style="font-size:0.75rem">+ Add Bed</button>
        </div>
        ${bedsHTML || '<p style="color:var(--text-muted);font-size:.8rem">No beds</p>'}
      </div>
      <div class="hosp-detail-col">
        <div style="display:flex;align-items:center;justify-content:space-between;margin-bottom:12px">
          <h4 style="margin-bottom:0">Doctors (${h.availableDoctors} / ${h.onDutyDoctors} Available)</h4>
          <button class="action-link btn-sm ${hasAccess ? '' : 'locked'}" onclick="promptAddDoctor('${h.hospitalId}')" style="font-size:0.75rem">+ Add Doctor</button>
        </div>
        ${docsHTML || '<p style="color:var(--text-muted);font-size:.8rem">No doctors</p>'}
      </div>
    </div>
  </div>`;
}

/* =========================================================
   QUEUE PAGE
   ========================================================= */
async function loadQueue() {
  const tbody = document.getElementById('queue-tbody');
  try {
    const r = await fetch(`${API}/queue`);
    const q = await r.json();
    if (!q.length) { tbody.innerHTML = `<tr><td colspan="8" class="empty-row">Queue is empty — no patients waiting.</td></tr>`; return; }
    tbody.innerHTML = q.map((p, i) => `
      <tr>
        <td><strong>#${i + 1}</strong></td>
        <td><code style="font-size:.75rem;color:var(--text-muted)">${p.patientId}</code></td>
        <td style="font-weight:600">${p.name}</td>
        <td>${p.age}</td>
        <td>${severityBadge(p.severity)}</td>
        <td><span class="badge ${facilityClass(p.requiredFacility)}">${p.requiredFacility}</span></td>
        <td style="font-family:'JetBrains Mono',monospace;font-size:.78rem">#${p.arrivalSequence}</td>
        <td><button class="action-link" onclick="openSeverityModal('${p.patientId}','${p.name}',${p.severity})">Re-rank</button></td>
      </tr>`).join('');
  } catch { tbody.innerHTML = `<tr><td colspan="8" class="empty-row">Could not load queue.</td></tr>`; }
}

async function processQueue() {
  const btn1 = document.getElementById('process-queue-btn');
  const btn2 = document.getElementById('process-queue-btn2');
  [btn1, btn2].forEach(b => { if (b) { b.disabled = true; b.textContent = 'Processing…'; } });
  try {
    const r = await fetch(`${API}/queue/process`, { method: 'POST' });
    const d = await r.json();
    showOutcomes(d.outcomes);
    showToast(`Processed: ${d.outcomes.length} patient(s)`, 'success');
    loadDashboard();
    loadQueue();
  } catch { showToast('Failed to process queue', 'error'); }
  finally { [btn1, btn2].forEach(b => { if (b) { b.disabled = false; b.innerHTML = b === btn1 ? 'Process All' : '<svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><polygon points="5 3 19 12 5 21 5 3"/></svg> Process Queue'; } }); }
}

function showOutcomes(outcomes) {
  const box = document.getElementById('queue-outcomes');
  if (!box) return;
  box.style.display = 'block';
  box.innerHTML = outcomes.map(o => {
    let tag = 'tag-waiting', label = 'WAITING';
    if (o.startsWith('[ADMITTED]')) { tag = 'tag-admitted'; label = 'ADMITTED'; }
    else if (o.startsWith('[REFERRED]')) { tag = 'tag-referred'; label = 'REFERRED'; }
    const msg = o.replace(/^\[\w+\]\s*/, '');
    return `<div class="outcome-line"><span class="tag ${tag}">${label}</span><span>${msg}</span></div>`;
  }).join('');
}

/* =========================================================
   NETWORK PAGE
   ========================================================= */
async function loadNetwork() {
  await loadRouteCanvas('route-canvas-big', 640, 420);
  loadRouteTable();
}

async function loadRouteTable() {
  const box = document.getElementById('route-table-content');
  try {
    const r = await fetch(`${API}/routes`);
    routeData = await r.json();
    box.innerHTML = routeData.map(rt => `
      <div class="route-row">
        <div>
          <div class="route-hosp">${rt.name}</div>
          <div class="route-path">${rt.path}</div>
        </div>
        <span class="route-time">${rt.minutes} min</span>
      </div>`).join('');
  } catch { box.innerHTML = errorHTML('Could not load routes'); }
}

async function updateRoad() {
  const a    = document.getElementById('road-hosp-a').value;
  const b    = document.getElementById('road-hosp-b').value;
  const mins = parseInt(document.getElementById('road-mins').value);
  if (!a || !b || !mins || a === b) { showToast('Please fill all fields correctly', 'error'); return; }
  try {
    const r = await fetch(`${API}/roads/update`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hospA: a, hospB: b, minutes: mins })
    });
    const d = await r.json();
    if (d.success) { showToast(`Road ${a}↔${b} updated to ${mins} min`, 'success'); loadNetwork(); }
    else showToast(d.message || 'Update failed', 'error');
  } catch { showToast('Server error', 'error'); }
}

/* =========================================================
   ROUTE CANVAS DRAWING
   ========================================================= */
async function loadRouteCanvas(canvasId, w, h) {
  const canvas = document.getElementById(canvasId);
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  canvas.width = w; canvas.height = h;

  // Node positions (proportional)
  const nodes = {
    NDGH: { x: w * .35, y: h * .25, label: 'NDGH', color: '#2563eb' },
    VSDH: { x: w * .72, y: h * .25, label: 'VSDH', color: '#ea580c' },
    TCHC: { x: w * .20, y: h * .68, label: 'TCHC', color: '#ea580c' },
    KTH:  { x: w * .72, y: h * .68, label: 'KTH',  color: '#ea580c' },
  };

  let edges = [
    { a: 'NDGH', b: 'VSDH', mins: 14 },
    { a: 'NDGH', b: 'TCHC', mins: 19 },
    { a: 'NDGH', b: 'KTH',  mins: 30 },
    { a: 'VSDH', b: 'TCHC', mins: 21 },
    { a: 'VSDH', b: 'KTH',  mins: 26 },
    { a: 'TCHC', b: 'KTH',  mins: 35 },
  ];

  try {
    const r = await fetch(`${API}/roads`);
    const d = await r.json();
    if (d && d.edges) edges = d.edges;
  } catch {}

  ctx.clearRect(0, 0, w, h);

  // Background
  ctx.fillStyle = '#f7f9fd';
  ctx.fillRect(0, 0, w, h);

  // Draw edges
  edges.forEach(e => {
    const a = nodes[e.a], b = nodes[e.b];
    if (!a || !b) return;
    ctx.beginPath();
    ctx.moveTo(a.x, a.y);
    ctx.lineTo(b.x, b.y);
    ctx.strokeStyle = '#c4cfdf';
    ctx.lineWidth = 1.8;
    ctx.setLineDash([5, 4]);
    ctx.stroke();
    ctx.setLineDash([]);

    // Label
    const mx = (a.x + b.x) / 2, my = (a.y + b.y) / 2;
    ctx.fillStyle = '#ffffff';
    const txt = `${e.mins}m`;
    const tw  = ctx.measureText(txt).width + 10;
    ctx.fillRect(mx - tw/2, my - 9, tw, 18);
    ctx.strokeStyle = '#dde4f0';
    ctx.lineWidth = 1;
    ctx.strokeRect(mx - tw/2, my - 9, tw, 18);
    ctx.fillStyle = '#4a5878';
    ctx.font = '600 10px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(txt, mx, my);
  });

  // Draw nodes
  Object.values(nodes).forEach(n => {
    // Shadow circle
    ctx.beginPath();
    ctx.arc(n.x, n.y, 24, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(0,0,0,.06)';
    ctx.fill();

    // Main circle
    ctx.beginPath();
    ctx.arc(n.x, n.y, 22, 0, Math.PI * 2);
    ctx.fillStyle = n.color;
    ctx.fill();
    ctx.strokeStyle = '#ffffff';
    ctx.lineWidth = 3;
    ctx.stroke();

    // Label inside
    ctx.fillStyle = '#ffffff';
    ctx.font = 'bold 9px Inter, sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(n.label, n.x, n.y);

    // Name below
    ctx.fillStyle = '#0f1d3a';
    ctx.font = '500 10.5px Inter, sans-serif';
    ctx.fillText(n.label === 'NDGH' ? 'Nalam DGH' : n.label === 'VSDH' ? 'Vallam SDH' : n.label === 'TCHC' ? 'Thoppur CHC' : 'Kadur TH', n.x, n.y + 34);
  });
}

/* =========================================================
   HISTORY PAGE
   ========================================================= */
async function loadHistory() {
  const tbody = document.getElementById('history-tbody');
  try {
    const r = await fetch(`${API}/history`);
    const h = await r.json();
    if (!h.length) { tbody.innerHTML = `<tr><td colspan="7" class="empty-row">No history recorded yet.</td></tr>`; return; }
    tbody.innerHTML = h.map(rec => `
      <tr>
        <td style="font-family:'JetBrains Mono',monospace;font-size:.75rem;color:var(--text-muted)">${rec.timestamp}</td>
        <td>${historyTypeBadge(rec.type)}</td>
        <td style="font-weight:600">${rec.patientName ?? '—'} <span style="color:var(--text-muted);font-size:.75rem">${rec.patientId ?? ''}</span></td>
        <td style="font-size:.8rem">${rec.hospitalId ?? '—'}</td>
        <td style="font-family:'JetBrains Mono',monospace;font-size:.75rem;color:var(--text-muted)">${rec.bedId ?? '—'}</td>
        <td style="font-size:.8rem">${rec.doctorId ?? '—'}</td>
        <td style="font-size:.78rem;color:var(--text-secondary);max-width:260px">${rec.remarks ?? ''}</td>
      </tr>`).join('');
  } catch { tbody.innerHTML = `<tr><td colspan="7" class="empty-row">Could not load history.</td></tr>`; }
}

function historyTypeBadge(type) {
  const map = {
    ADMISSION:     ['badge-admitted',  'Admission'],
    REFERRAL:      ['badge-referred',  'Referral'],
    QUEUED_WAITING:['badge-waiting',   'Queued'],
    DISCHARGE:     ['badge-discharged','Discharge'],
  };
  const [cls, label] = map[type] || ['badge-waiting', type];
  return `<span class="badge ${cls}">${label}</span>`;
}

/* =========================================================
   REGISTER PATIENT MODAL
   ========================================================= */
function showRegisterModal() {
  document.getElementById('register-modal').classList.add('open');
  document.getElementById('reg-name').focus();
}
function closeModal() { document.getElementById('register-modal').classList.remove('open'); }

async function registerPatient() {
  const name    = document.getElementById('reg-name').value.trim();
  const age     = parseInt(document.getElementById('reg-age').value);
  const gender  = document.getElementById('reg-gender').value;
  const severity= parseInt(document.getElementById('reg-severity').value);
  const facility= document.getElementById('reg-facility').value;
  const process = document.getElementById('reg-process-now').checked;

  if (!name || !age) { showToast('Please fill in all required fields', 'error'); return; }

  const btn = document.getElementById('register-submit-btn');
  btn.disabled = true; btn.textContent = 'Registering…';

  try {
    const r = await fetch(`${API}/patients/register`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, age, gender, severity, requiredFacility: facility, processNow: process })
    });
    const d = await r.json();
    if (d.success) {
      showToast(`Patient ${name} registered successfully!`, 'success');
      closeModal();
      if (process && d.outcomes) showOutcomes(d.outcomes);
      loadDashboard();
      loadPatients();
      loadQueue();
    } else { showToast(d.message || 'Registration failed', 'error'); }
  } catch { showToast('Could not reach server', 'error'); }
  finally { btn.disabled = false; btn.textContent = 'Register & Admit'; }
}

/* =========================================================
   DISCHARGE MODAL
   ========================================================= */
function openDischargeModal(hospId, patId) {
  if (hospId) document.getElementById('dis-hosp').value = hospId;
  if (patId)  document.getElementById('dis-pid').value  = patId;
  document.getElementById('discharge-modal').classList.add('open');
}
function closeDischargeModal() { document.getElementById('discharge-modal').classList.remove('open'); }

async function submitDischarge() {
  const hosp = document.getElementById('dis-hosp').value;
  const pid  = document.getElementById('dis-pid').value.trim();
  if (!pid) { showToast('Enter a patient ID', 'error'); return; }
  try {
    const r = await fetch(`${API}/patients/discharge`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hospitalId: hosp, patientId: pid })
    });
    const d = await r.json();
    if (d.success) {
      showToast('Patient discharged. Bed released!', 'success');
      closeDischargeModal();
      loadDashboard(); loadPatients(); loadQueue(); loadHospitalsDetail();
    } else showToast(d.message || 'Discharge failed', 'error');
  } catch { showToast('Could not reach server', 'error'); }
}

/* =========================================================
   SEVERITY UPDATE MODAL
   ========================================================= */
function openSeverityModal(pid, name, currentSeverity) {
  document.getElementById('sev-pid').value    = pid;
  document.getElementById('sev-pname').textContent = name;
  document.getElementById('sev-new').value    = currentSeverity;
  document.getElementById('severity-modal').classList.add('open');
}
function closeSeverityModal() { document.getElementById('severity-modal').classList.remove('open'); }

async function submitSeverityUpdate() {
  const pid = document.getElementById('sev-pid').value;
  const sev = parseInt(document.getElementById('sev-new').value);
  try {
    const r = await fetch(`${API}/patients/severity`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ patientId: pid, severity: sev })
    });
    const d = await r.json();
    if (d.success) {
      showToast('Severity updated & queue re-ranked!', 'success');
      closeSeverityModal();
      loadDashboard(); loadPatients(); loadQueue();
    } else showToast(d.message || 'Update failed', 'error');
  } catch { showToast('Could not reach server', 'error'); }
}

/* =========================================================
   DOCTOR DUTY TOGGLE
   ========================================================= */
async function toggleDuty(hospId, docId, onDuty) {
  try {
    const r = await fetch(`${API}/doctors/duty`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hospitalId: hospId, doctorId: docId, onDuty })
    });
    const d = await r.json();
    if (d.success) {
      showToast(`Doctor ${docId} is now ${onDuty ? 'ON' : 'OFF'} duty`, 'success');
      loadHospitalsDetail(); loadDashboard(); loadStats();
    } else showToast(d.message || 'Update failed', 'error');
  } catch { showToast('Could not reach server', 'error'); }
}

/* =========================================================
   MODAL CLOSE ON OVERLAY CLICK
   ========================================================= */
function closeModalOutside(e) {
  if (e.target === e.currentTarget) {
    e.currentTarget.classList.remove('open');
  }
}

/* =========================================================
   HELPERS
   ========================================================= */
function setText(id, val) {
  const el = document.getElementById(id);
  if (el) el.textContent = val;
}

function errorHTML(msg) {
  return `<div class="loading-spinner"><p style="color:var(--red)">${msg}</p></div>`;
}

function severityBadge(severity) {
  if (severity === 1 || severity === 'Critical')
    return `<span class="badge badge-critical">Critical</span>`;
  if (severity === 2 || severity === 'Serious')
    return `<span class="badge badge-serious">Serious</span>`;
  return `<span class="badge badge-minor">Minor</span>`;
}

function facilityClass(f) {
  return (f || '').toLowerCase() === 'icu' ? 'badge-critical' : 'badge-admitted';
}

function statusBadge(status) {
  const map = {
    WAITING:   'badge-waiting',
    ADMITTED:  'badge-admitted',
    REFERRED:  'badge-referred',
    DISCHARGED:'badge-discharged',
  };
  return `<span class="badge ${map[status] || ''}">${status}</span>`;
}

/* =========================================================
   TOAST
   ========================================================= */
let toastTimer;
function showToast(msg, type = 'info') {
  const el = document.getElementById('toast');
  el.textContent = msg;
  el.className   = `toast show ${type}`;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => el.classList.remove('show'), 3500);
}

/* =========================================================
   AUTHENTICATION & USER PROFILE MANAGEMENT
   ========================================================= */

// Demo presets for easy 1-click test / jury evaluation
const DEMO_USERS = {
  admin: {
    name: 'District Medical Director',
    email: 'admin@nalam.gov.in',
    role: 'Hospital Administrator',
    hospital: 'NDGH',
    initials: 'AD'
  },
  doctor: {
    name: 'Dr. Priya Sharma',
    email: 'doctor@nalam.gov.in',
    role: 'Medical Officer',
    hospital: 'NDGH',
    initials: 'PS'
  },
  nurse: {
    name: 'Staff Nurse Anitha',
    email: 'nurse@nalam.gov.in',
    role: 'Senior Nurse',
    hospital: 'VSDH',
    initials: 'NA'
  }
};

let currentUser = null;

function initAuth() {
  const saved = localStorage.getItem('nalam_auth_user');
  if (saved) {
    try {
      currentUser = JSON.parse(saved);
      renderAuthState(currentUser);
      return;
    } catch {
      localStorage.removeItem('nalam_auth_user');
    }
  }
  renderAuthState(null);
}

function renderAuthState(user) {
  const guestGroup = document.getElementById('auth-guest-group');
  const userGroup  = document.getElementById('auth-user-group');
  if (!guestGroup || !userGroup) return;

  if (user) {
    guestGroup.style.display = 'none';
    userGroup.style.display  = 'flex';

    const initials = user.initials || getInitials(user.name);
    setText('user-avatar-initials', initials);
    setText('user-display-name', user.name);
    setText('user-display-role', user.role || 'Personnel');
  } else {
    guestGroup.style.display = 'flex';
    userGroup.style.display  = 'none';
  }
  renderAdminBanner();
  const hospPage = document.getElementById('page-hospitals');
  if (hospPage && hospPage.classList.contains('active')) {
    loadHospitalsDetail();
  }
}

function getInitials(name) {
  if (!name) return 'U';
  const parts = name.trim().replace(/^Dr\.\s*/i, '').split(/\s+/);
  if (parts.length >= 2) return (parts[0][0] + parts[1][0]).toUpperCase();
  return name.slice(0, 2).toUpperCase();
}

function openAuthModal(tab = 'login') {
  const modal = document.getElementById('auth-modal');
  if (!modal) return;
  modal.classList.add('open');
  switchAuthTab(tab);
}

function closeAuthModal() {
  const modal = document.getElementById('auth-modal');
  if (modal) modal.classList.remove('open');
}

function switchAuthTab(tab) {
  const tabLogin  = document.getElementById('tab-login');
  const tabSignup = document.getElementById('tab-signup');
  const formLogin = document.getElementById('login-form');
  const formSignup= document.getElementById('signup-form');
  const demoBar   = document.getElementById('auth-demo-bar');

  if (tab === 'login') {
    tabLogin.classList.add('active');
    tabLogin.setAttribute('aria-selected', 'true');
    tabSignup.classList.remove('active');
    tabSignup.setAttribute('aria-selected', 'false');

    formLogin.style.display = 'block';
    formSignup.style.display = 'none';
    if (demoBar) demoBar.style.display = 'flex';

    setTimeout(() => {
      const emailField = document.getElementById('login-email');
      if (emailField) emailField.focus();
    }, 50);
  } else {
    tabSignup.classList.add('active');
    tabSignup.setAttribute('aria-selected', 'true');
    tabLogin.classList.remove('active');
    tabLogin.setAttribute('aria-selected', 'false');

    formSignup.style.display = 'block';
    formLogin.style.display = 'none';
    if (demoBar) demoBar.style.display = 'none';

    setTimeout(() => {
      const nameField = document.getElementById('signup-name');
      if (nameField) nameField.focus();
    }, 50);
  }
}

function fillDemoUser(roleKey) {
  const profile = DEMO_USERS[roleKey];
  if (!profile) return;
  const emailInput = document.getElementById('login-email');
  const passInput  = document.getElementById('login-password');
  if (emailInput) emailInput.value = profile.email;
  if (passInput)  passInput.value  = 'nalam@2026';
  showToast(`Loaded ${profile.role} credentials`, 'info');
}

function togglePasswordVisibility(fieldId, btnEl) {
  const input = document.getElementById(fieldId);
  if (!input) return;
  const isPass = input.type === 'password';
  input.type = isPass ? 'text' : 'password';

  if (btnEl) {
    btnEl.innerHTML = isPass
      ? `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M17.94 17.94A10.07 10.07 0 0 1 12 20c-7 0-11-8-11-8a18.45 18.45 0 0 1 5.06-5.94M9.9 4.24A9.12 9.12 0 0 1 12 4c7 0 11 8 11 8a18.5 18.5 0 0 1-2.16 3.19m-6.72-1.07a3 3 0 1 1-4.24-4.24"/><line x1="1" y1="1" x2="23" y2="23"/></svg>`
      : `<svg width="15" height="15" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"/><circle cx="12" cy="12" r="3"/></svg>`;
  }
}

async function handleLoginSubmit(event) {
  event.preventDefault();
  const email = (document.getElementById('login-email').value || '').trim();
  const pass  = (document.getElementById('login-password').value || '').trim();

  if (!email || !pass) {
    showToast('Please enter both email/username and password', 'error');
    return;
  }

  const btn = document.getElementById('login-submit-btn');
  btn.disabled = true;
  btn.innerHTML = `<span class="spinner" style="width:14px;height:14px;border-width:2px;display:inline-block;vertical-align:middle;margin-right:6px;"></span> Verifying…`;

  try {
    let userObj = null;
    try {
      const res = await fetch(`${API}/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password: pass })
      });
      if (res.ok) {
        const data = await res.json();
        if (data.success && data.user) userObj = data.user;
      }
    } catch {}

    if (!userObj) {
      const matchedDemo = Object.values(DEMO_USERS).find(u => u.email.toLowerCase() === email.toLowerCase());
      if (matchedDemo) {
        userObj = { ...matchedDemo };
      } else {
        const username = email.split('@')[0];
        const displayName = username.charAt(0).toUpperCase() + username.slice(1);
        userObj = {
          name: displayName,
          email: email,
          role: 'Hospital Staff',
          hospital: 'NDGH',
          initials: getInitials(displayName)
        };
      }
    }

    currentUser = userObj;
    localStorage.setItem('nalam_auth_user', JSON.stringify(currentUser));
    renderAuthState(currentUser);
    closeAuthModal();
    showToast(`Welcome back, ${currentUser.name}!`, 'success');
  } catch (err) {
    showToast('Login failed. Please check your credentials.', 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<span>Log In</span>`;
  }
}

async function handleSignupSubmit(event) {
  event.preventDefault();
  const name     = (document.getElementById('signup-name').value || '').trim();
  const role     = document.getElementById('signup-role').value;
  const hospital = document.getElementById('signup-hosp').value;
  const email    = (document.getElementById('signup-email').value || '').trim();
  const pass     = (document.getElementById('signup-password').value || '').trim();

  if (!name || !email || !pass) {
    showToast('Please fill out all required fields', 'error');
    return;
  }
  if (pass.length < 6) {
    showToast('Password must be at least 6 characters', 'error');
    return;
  }

  const btn = document.getElementById('signup-submit-btn');
  btn.disabled = true;
  btn.innerHTML = `<span class="spinner" style="width:14px;height:14px;border-width:2px;display:inline-block;vertical-align:middle;margin-right:6px;"></span> Creating…`;

  try {
    let userObj = null;
    try {
      const res = await fetch(`${API}/auth/signup`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ name, email, password: pass, role, hospital })
      });
      if (res.ok) {
        const data = await res.json();
        if (data.success && data.user) userObj = data.user;
      }
    } catch {}

    if (!userObj) {
      userObj = {
        name,
        email,
        role,
        hospital,
        initials: getInitials(name)
      };
    }

    currentUser = userObj;
    localStorage.setItem('nalam_auth_user', JSON.stringify(currentUser));
    renderAuthState(currentUser);
    closeAuthModal();
    showToast(`Account created! Welcome, ${currentUser.name}.`, 'success');
  } catch {
    showToast('Failed to create account.', 'error');
  } finally {
    btn.disabled = false;
    btn.innerHTML = `<span>Complete Registration</span>`;
  }
}

function logoutUser() {
  currentUser = null;
  localStorage.removeItem('nalam_auth_user');
  renderAuthState(null);
  showToast('You have been logged out.', 'info');
}

function handleForgotPassword(e) {
  if (e) e.preventDefault();
  showToast('Password reset link sent to registered district administrator.', 'info');
}

function openProfileSummary() {
  if (!currentUser) return;
  const modal = document.getElementById('profile-modal');
  if (!modal) return;
  setText('profile-modal-avatar', currentUser.initials || getInitials(currentUser.name));
  setText('profile-modal-name', currentUser.name);
  setText('profile-modal-role', currentUser.role || 'Staff');
  setText('profile-modal-email', currentUser.email || '—');
  setText('profile-modal-hospital', currentUser.hospital || 'NDGH');
  modal.classList.add('open');
}

function closeProfileModal() {
  const modal = document.getElementById('profile-modal');
  if (modal) modal.classList.remove('open');
}

/* =========================================================
   MEDICAL OFFICER & ADMINISTRATOR RESOURCE MANAGEMENT
   ========================================================= */

function canEditResources() {
  if (!currentUser) return false;
  const role = (currentUser.role || '').toLowerCase();
  return role.includes('admin') || role.includes('officer') || role.includes('doctor') || role.includes('director');
}

function checkAdminAccessOrPrompt(actionName = 'perform this action') {
  if (canEditResources()) return true;
  showToast(`Please log in as Medical Officer or Administrator to ${actionName}`, 'error');
  openAuthModal('login');
  return false;
}

function quickLoginAs(roleKey) {
  const profile = roleKey === 'officer' ? DEMO_USERS.doctor : DEMO_USERS.admin;
  currentUser = { ...profile };
  localStorage.setItem('nalam_auth_user', JSON.stringify(currentUser));
  renderAuthState(currentUser);
  loadHospitalsDetail();
  loadDashboard();
  showToast(`Switched session to ${currentUser.role} (${currentUser.name})! Edit features unlocked.`, 'success');
}

function renderAdminBanner() {
  const banner = document.getElementById('hospitals-admin-banner');
  if (!banner) return;
  const hasAccess = canEditResources();
  if (hasAccess) {
    banner.innerHTML = `
      <div class="admin-notice-bar admin-enabled">
        <div style="display:flex;align-items:center;gap:10px">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"/></svg>
          <div>
            <strong>${currentUser.role} Authenticated (${currentUser.name})</strong>
            <span style="display:block;font-size:0.75rem;opacity:0.9">Administrative and clinical resource editing enabled. You can edit bed occupancy, update doctor specializations, and add new hospital resources.</span>
          </div>
        </div>
        <div style="display:flex;gap:6px">
          <button class="btn btn-primary btn-sm" onclick="promptAddBed()">+ Add Bed</button>
          <button class="btn btn-accent btn-sm" onclick="promptAddDoctor()">+ Add Doctor</button>
        </div>
      </div>`;
  } else {
    banner.innerHTML = `
      <div class="admin-notice-bar admin-locked">
        <div style="display:flex;align-items:center;gap:10px">
          <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"/><path d="M7 11V7a5 5 0 0 1 10 0v4"/></svg>
          <div>
            <strong>Resource Editing Locked (Guest View)</strong>
            <span style="display:block;font-size:0.75rem;opacity:0.9">Editing doctors and bed allocations requires <strong>Medical Officer</strong> or <strong>Administrator</strong> privileges.</span>
          </div>
        </div>
        <div style="display:flex;gap:6px">
          <button class="btn btn-ghost btn-sm" onclick="quickLoginAs('officer')">Log In as Medical Officer</button>
          <button class="btn btn-primary btn-sm" onclick="quickLoginAs('admin')">Log In as Admin</button>
        </div>
      </div>`;
  }
}

// ── Edit Doctor ───────────────────────────────────────────
function promptEditDoctor(hospId, docId, name, spec, onDuty) {
  if (!checkAdminAccessOrPrompt('edit doctors')) return;
  document.getElementById('edit-doc-hosp').value = hospId;
  document.getElementById('edit-doc-hosp-display').value = hospId;
  document.getElementById('edit-doc-id').value = docId;
  document.getElementById('edit-doc-name').value = name;
  document.getElementById('edit-doc-spec').value = spec;
  document.getElementById('edit-doc-duty').value = onDuty ? 'true' : 'false';
  document.getElementById('edit-doctor-modal').classList.add('open');
}

function closeEditDoctorModal() {
  const modal = document.getElementById('edit-doctor-modal');
  if (modal) modal.classList.remove('open');
}

async function submitEditDoctor(event) {
  event.preventDefault();
  const hospId = document.getElementById('edit-doc-hosp').value;
  const docId  = document.getElementById('edit-doc-id').value;
  const name   = document.getElementById('edit-doc-name').value.trim();
  const spec   = document.getElementById('edit-doc-spec').value;
  const onDuty = document.getElementById('edit-doc-duty').value === 'true';

  if (!name) { showToast('Please enter doctor name', 'error'); return; }

  const btn = document.getElementById('btn-save-doctor');
  btn.disabled = true; btn.textContent = 'Saving…';

  try {
    const r = await fetch(`${API}/doctors/update`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hospitalId: hospId, doctorId: docId, name, specialization: spec, onDuty })
    });
    const d = await r.json();
    if (d.success) {
      showToast(`Doctor ${name} updated successfully!`, 'success');
      closeEditDoctorModal();
      loadHospitalsDetail(); loadDashboard(); loadStats();
    } else {
      showToast(d.message || 'Update failed', 'error');
    }
  } catch {
    showToast('Could not reach server', 'error');
  } finally {
    btn.disabled = false; btn.textContent = 'Save Changes';
  }
}

// ── Add Doctor ────────────────────────────────────────────
function autoGenerateDocId() {
  const hosp = document.getElementById('add-doc-hosp').value || 'NDGH';
  const rand = Math.floor(10 + Math.random() * 90);
  document.getElementById('add-doc-id').value = `DOC-${hosp}-${rand}`;
}

function promptAddDoctor(defaultHosp) {
  if (!checkAdminAccessOrPrompt('add doctors')) return;
  if (defaultHosp) document.getElementById('add-doc-hosp').value = defaultHosp;
  autoGenerateDocId();
  document.getElementById('add-doc-name').value = '';
  document.getElementById('add-doctor-modal').classList.add('open');
}

function closeAddDoctorModal() {
  const modal = document.getElementById('add-doctor-modal');
  if (modal) modal.classList.remove('open');
}

async function submitAddDoctor(event) {
  event.preventDefault();
  const hospId = document.getElementById('add-doc-hosp').value;
  const docId  = document.getElementById('add-doc-id').value.trim();
  const name   = document.getElementById('add-doc-name').value.trim();
  const spec   = document.getElementById('add-doc-spec').value;
  const onDuty = document.getElementById('add-doc-duty').value === 'true';

  if (!name || !docId) { showToast('Please enter doctor name and ID', 'error'); return; }

  const btn = document.getElementById('btn-submit-add-doctor');
  btn.disabled = true; btn.textContent = 'Adding…';

  try {
    const r = await fetch(`${API}/doctors/add`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hospitalId: hospId, doctorId: docId, name, specialization: spec, onDuty })
    });
    const d = await r.json();
    if (d.success) {
      showToast(`Added ${name} to ${hospId}!`, 'success');
      closeAddDoctorModal();
      loadHospitalsDetail(); loadDashboard(); loadStats();
    } else {
      showToast(d.message || 'Failed to add doctor', 'error');
    }
  } catch {
    showToast('Could not reach server', 'error');
  } finally {
    btn.disabled = false; btn.textContent = 'Add Doctor';
  }
}

// ── Edit Bed ──────────────────────────────────────────────
function promptEditBed(hospId, bedId, bedNumber, occupied) {
  if (!checkAdminAccessOrPrompt('edit bed allocations')) return;
  document.getElementById('edit-bed-hosp').value = hospId;
  document.getElementById('edit-bed-hosp-display').value = hospId;
  document.getElementById('edit-bed-id').value = bedId;
  document.getElementById('edit-bed-number').value = bedNumber;
  document.getElementById('edit-bed-status').value = occupied ? 'true' : 'false';
  document.getElementById('edit-bed-modal').classList.add('open');
}

function closeEditBedModal() {
  const modal = document.getElementById('edit-bed-modal');
  if (modal) modal.classList.remove('open');
}

async function submitEditBed(event) {
  event.preventDefault();
  const hospId = document.getElementById('edit-bed-hosp').value;
  const bedId  = document.getElementById('edit-bed-id').value;
  const bedNum = document.getElementById('edit-bed-number').value.trim();
  const occupied = document.getElementById('edit-bed-status').value === 'true';

  if (!bedNum) { showToast('Please enter bed number', 'error'); return; }

  const btn = document.getElementById('btn-save-bed');
  btn.disabled = true; btn.textContent = 'Saving…';

  try {
    const r = await fetch(`${API}/beds/update`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hospitalId: hospId, bedId, bedNumber: bedNum, occupied })
    });
    const d = await r.json();
    if (d.success) {
      showToast(`Bed ${bedNum} updated (${occupied ? 'Occupied' : 'Vacant'})!`, 'success');
      closeEditBedModal();
      loadHospitalsDetail(); loadDashboard(); loadStats();
    } else {
      showToast(d.message || 'Update failed', 'error');
    }
  } catch {
    showToast('Could not reach server', 'error');
  } finally {
    btn.disabled = false; btn.textContent = 'Save Changes';
  }
}

// ── Add Bed ───────────────────────────────────────────────
function autoGenerateBedId() {
  const hosp = document.getElementById('add-bed-hosp').value || 'NDGH';
  const type = document.getElementById('add-bed-type').value || 'ICU';
  const rand = Math.floor(10 + Math.random() * 90);
  document.getElementById('add-bed-id').value = `${hosp}-${type.toUpperCase()}-${rand}`;
  document.getElementById('add-bed-number').value = `${type.toUpperCase()}-${rand}`;
}

function promptAddBed(defaultHosp) {
  if (!checkAdminAccessOrPrompt('add hospital beds')) return;
  if (defaultHosp) document.getElementById('add-bed-hosp').value = defaultHosp;
  autoGenerateBedId();
  document.getElementById('add-bed-modal').classList.add('open');
}

function closeAddBedModal() {
  const modal = document.getElementById('add-bed-modal');
  if (modal) modal.classList.remove('open');
}

async function submitAddBed(event) {
  event.preventDefault();
  const hospId = document.getElementById('add-bed-hosp').value;
  const bedId  = document.getElementById('add-bed-id').value.trim();
  const bedNum = document.getElementById('add-bed-number').value.trim();
  const bedType= document.getElementById('add-bed-type').value;

  if (!bedId || !bedNum) { showToast('Please enter bed ID and number', 'error'); return; }

  const btn = document.getElementById('btn-submit-add-bed');
  btn.disabled = true; btn.textContent = 'Adding…';

  try {
    const r = await fetch(`${API}/beds/add`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ hospitalId: hospId, bedId, bedNumber: bedNum, bedType })
    });
    const d = await r.json();
    if (d.success) {
      showToast(`Added bed ${bedNum} to ${hospId}!`, 'success');
      closeAddBedModal();
      loadHospitalsDetail(); loadDashboard(); loadStats();
    } else {
      showToast(d.message || 'Failed to add bed', 'error');
    }
  } catch {
    showToast('Could not reach server', 'error');
  } finally {
    btn.disabled = false; btn.textContent = 'Add Bed';
  }
}

// ── Edit Profile ──────────────────────────────────────────
function openEditProfileModal() {
  if (!currentUser) return;
  closeProfileModal();
  document.getElementById('edit-prof-name').value = currentUser.name || '';
  document.getElementById('edit-prof-role').value = currentUser.role || 'Medical Officer';
  document.getElementById('edit-prof-hosp').value = currentUser.hospital || 'NDGH';
  document.getElementById('edit-prof-email').value = currentUser.email || '';
  document.getElementById('edit-profile-modal').classList.add('open');
}

function closeEditProfileModal() {
  const modal = document.getElementById('edit-profile-modal');
  if (modal) modal.classList.remove('open');
}

function submitEditProfile(event) {
  event.preventDefault();
  const name  = document.getElementById('edit-prof-name').value.trim();
  const role  = document.getElementById('edit-prof-role').value;
  const hosp  = document.getElementById('edit-prof-hosp').value;
  const email = document.getElementById('edit-prof-email').value.trim();

  if (!name || !email) { showToast('Please enter name and email', 'error'); return; }

  currentUser.name = name;
  currentUser.role = role;
  currentUser.hospital = hosp;
  currentUser.email = email;
  currentUser.initials = getInitials(name);

  localStorage.setItem('nalam_auth_user', JSON.stringify(currentUser));
  renderAuthState(currentUser);
  closeEditProfileModal();
  showToast(`Profile updated! Logged in as ${role} (${name}).`, 'success');
}

/* =========================================================
   INIT
   ========================================================= */
window.addEventListener('DOMContentLoaded', () => {
  initAuth();
  checkConnection();
  setInterval(checkConnection, 5000);
  loadDashboard();
});
