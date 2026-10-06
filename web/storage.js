let database;
async function open() {
  database??=new Promise((resolve,reject)=>{
    const request=indexedDB.open('reachsense-rgb',1);
    request.onupgradeneeded=()=>request.result.createObjectStore('sessions',{keyPath:'id'});
    request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);request.onblocked=()=>reject(Error('Local storage is blocked by another tab'));
  });
  return database;
}
export async function saveSession(session) {
  const db=await open();
  return new Promise((resolve,reject)=>{
    const tx=db.transaction('sessions','readwrite');tx.objectStore('sessions').put(session);
    tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error??Error('Storage aborted'));
  });
}
export async function loadSessions(){
  const db=await open();
  return new Promise((resolve,reject)=>{const r=db.transaction('sessions').objectStore('sessions').getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
}
