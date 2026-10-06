let database;
export async function saveSession(session) {
  database??=new Promise((resolve,reject)=>{
    const request=indexedDB.open('reachsense-rgb',1);
    request.onupgradeneeded=()=>request.result.createObjectStore('sessions',{keyPath:'id'});
    request.onsuccess=()=>resolve(request.result);request.onerror=()=>reject(request.error);
  });
  const db=await database;
  return new Promise((resolve,reject)=>{
    const tx=db.transaction('sessions','readwrite');tx.objectStore('sessions').put(session);
    tx.oncomplete=resolve;tx.onerror=()=>reject(tx.error);tx.onabort=()=>reject(tx.error??Error('Storage aborted'));
  });
}
export async function loadSessions(){
  await saveSessionInitialization();const db=await database;
  return new Promise((resolve,reject)=>{const r=db.transaction('sessions').objectStore('sessions').getAll();r.onsuccess=()=>resolve(r.result);r.onerror=()=>reject(r.error);});
}
async function saveSessionInitialization(){if(!database){await saveSession({id:'initialization',temporary:true});const db=await database;await new Promise((resolve,reject)=>{const t=db.transaction('sessions','readwrite');t.objectStore('sessions').delete('initialization');t.oncomplete=resolve;t.onerror=()=>reject(t.error);});}}
