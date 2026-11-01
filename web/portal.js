/* Server-backed account and patient portal. */
const PORTAL_SESSION_KEY = 'nalamPortalSessionV2';
const PORTAL_USER_KEY = 'nalamPortalUserV2';
const token = () => localStorage.getItem(PORTAL_SESSION_KEY);
async function portalApi(path, options={}) {
  const headers={ 'Content-Type':'application/json', ...(options.headers||{}) };
  if(token()) headers.Authorization=`Bearer ${token()}`;
  const response=await fetch(path,{cache:'no-store',...options,headers});
  const data=await response.json().catch(()=>({}));
  if(!response.ok) throw new Error(data.error||`Request failed (${response.status}).`);
  return data;
}
function portalMessage(message,error=false,id='portalAuthMessage') {
  const el=document.getElementById(id); if(!el)return; el.textContent=message; el.classList.toggle('error',error);
}
function portalUser(){try{return JSON.parse(localStorage.getItem(PORTAL_USER_KEY)||'null')}catch{return null}}
function setPortalView(view) {
  document.getElementById('portalAuth').hidden=view!=='auth';
  document.getElementById('patientPortal').hidden=view!=='patient';
  const manager=view==='management';
  document.querySelector('.gov-top-ribbon').hidden=!manager;
  document.querySelector('.app-wrapper').hidden=!manager;
  document.querySelector('.hospital-footer').hidden=!manager;
}
function portalEscape(v){return String(v??'—').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]))}
async function loadPatientPortal(user){
  document.getElementById('patientPortalName').textContent=user.name;
  document.getElementById('patientPortalWelcome').textContent=user.email;
  setPortalView('patient'); await refreshPatientPortal();
}
async function refreshPatientPortal(){
  const record=document.getElementById('patientPortalRecord'), availability=document.getElementById('patientHospitalAvailability');
  try {
    const [patient,hospitals]=await Promise.all([portalApi('/api/patient/me'),portalApi('/api/availability')]);
    const intake=document.getElementById('patientIntakeCard');
    if(!patient.registered){
      intake.hidden=false; document.getElementById('patientPortalId').textContent='Patient ID is assigned after registration';
      document.getElementById('patientPortalStatus').textContent='Registration needed';
      document.getElementById('patientPortalStatus').className='patient-status status-waiting';
      record.textContent='Complete the care registration form to receive your patient ID and status.';
    } else {
      intake.hidden=true; document.getElementById('patientPortalId').textContent=`Patient ID · ${patient.id}`;
      document.getElementById('patientPortalStatus').textContent=patient.status.replaceAll('_',' ');
      document.getElementById('patientPortalStatus').className=`patient-status status-${patient.status.toLowerCase()}`;
      const hospital=hospitals.find(h=>h.id===patient.assignedHospitalId);
      const rows=[['Age',patient.age],['Triage priority',patient.severity],['Required care',patient.requiredFacility],['Hospital',hospital?.name||(patient.status==='WAITING'?'Awaiting placement':'—')],['Bed',patient.assignedBedId||'—'],['Doctor',patient.assignedDoctorId||'—'],['Referral details',patient.referralNotes||'—']];
      record.innerHTML=rows.map(([k,v])=>`<div class="patient-detail-row"><span>${portalEscape(k)}</span><strong>${portalEscape(v)}</strong></div>`).join('');
    }
    availability.innerHTML=hospitals.map(h=>`<div class="availability-row"><div><strong>${portalEscape(h.name)}</strong><small>${portalEscape(h.locationId)} · ${h.availableDoctors} doctors available</small></div><span>${h.availableBeds} beds available<br><small>${h.availableIcuBeds} ICU · ${h.availableGeneralBeds} general</small></span></div>`).join('');
  } catch(e){record.textContent=e.message;availability.textContent=e.message;}
}
document.addEventListener('DOMContentLoaded',()=>{
 const login=document.getElementById('portalLoginForm'), signup=document.getElementById('portalSignupForm');
 document.querySelectorAll('[data-auth-view]').forEach(btn=>btn.addEventListener('click',()=>{
   const isSignup=btn.dataset.authView==='signup'; login.hidden=isSignup; signup.hidden=!isSignup;
   document.querySelectorAll('[data-auth-view]').forEach(b=>b.classList.toggle('active',b===btn)); portalMessage('');
 }));
 async function enter(user){localStorage.setItem(PORTAL_USER_KEY,JSON.stringify(user)); if(user.role==='PATIENT') await loadPatientPortal(user); else {setPortalView('management'); location.reload();}}
 login.addEventListener('submit',async e=>{e.preventDefault();try{
   const user=await portalApi('/api/auth/login',{method:'POST',body:JSON.stringify({email:document.getElementById('portalLoginEmail').value.trim(),password:document.getElementById('portalLoginPassword').value})});
   localStorage.setItem(PORTAL_SESSION_KEY,user.token); await enter(user);
 }catch(err){portalMessage(err.message,true)}});
 signup.addEventListener('submit',async e=>{e.preventDefault();const pw=document.getElementById('portalSignupPassword').value,confirm=document.getElementById('portalSignupConfirm').value;
   if(pw.length<8)return portalMessage('Use a password with at least 8 characters.',true); if(pw!==confirm)return portalMessage('The passwords do not match.',true);
   try {const user=await portalApi('/api/auth/signup',{method:'POST',body:JSON.stringify({name:document.getElementById('portalSignupName').value.trim(),email:document.getElementById('portalSignupEmail').value.trim(),password:pw,role:document.getElementById('portalSignupRole').value})});
     localStorage.setItem(PORTAL_SESSION_KEY,user.token); await enter(user);
   }catch(err){portalMessage(err.message,true)}
 });
 document.getElementById('patientIntakeForm').addEventListener('submit',async e=>{e.preventDefault();try{
   portalMessage('Submitting your registration…',false,'patientIntakeMessage');
   const result=await portalApi('/api/register',{method:'POST',body:JSON.stringify({name:portalUser()?.name,age:Number(document.getElementById('portalPatientAge').value),gender:document.getElementById('portalPatientGender').value,severity:document.getElementById('portalPatientSeverity').value,requiredFacility:document.getElementById('portalPatientFacility').value})});
   portalMessage(`Registration complete. Your patient ID is ${result.patient.id}.`,false,'patientIntakeMessage'); await refreshPatientPortal();
 }catch(err){portalMessage(err.message,true,'patientIntakeMessage')}});
 const logout=async()=>{try{await portalApi('/api/auth/logout',{method:'POST'})}catch{} localStorage.removeItem(PORTAL_SESSION_KEY);localStorage.removeItem(PORTAL_USER_KEY);location.reload()};
 document.getElementById('patientPortalLogout').addEventListener('click',logout); document.getElementById('managementPortalLogout').addEventListener('click',logout);
 document.getElementById('patientPortalRefresh').addEventListener('click',refreshPatientPortal);
 const user=portalUser(); if(token()&&user?.role==='PATIENT')loadPatientPortal(user); else if(token()&&user?.role==='MANAGEMENT')setPortalView('management'); else setPortalView('auth');
});
