import {validateCalibration,TrialProtocol,length,sub} from './core.js';
import {saveSession,loadSessions} from './storage.js';
const $=id=>document.getElementById(id),video=$('video'),canvas=$('view'),context=canvas.getContext('2d');
const raw=document.createElement('canvas'),rawContext=raw.getContext('2d',{willReadFrequently:true});
let bundle,worker,stream,busy=false,ready=false,generation=0,frameId=0,previous,lastReply=0,frames=0,rateStart=0,protocol=new TrialProtocol(),session=null,writing=Promise.resolve(),storageFailed=false;
const error=e=>{$('error').textContent=String(e.message??e);};
function controls(){
  $('attempt').disabled=!ready||!bundle||storageFailed||!!protocol.active||!!protocol.candidate||protocol.sessionScore!==null||protocol.attempts.filter(a=>!a.practice).length>=5;
  $('cancel').disabled=!protocol.active;$('post').hidden=!protocol.candidate;
  $('start').disabled=!!stream||!!worker;$('stop').disabled=!stream&&!worker;
  for(const id of ['calibration','example','camera','binding','participant','consent'])$(id).disabled=!!stream||!!worker||!!protocol.active||!!protocol.candidate||!!session;
  $('practice').disabled=!!protocol.active||!!protocol.candidate;
  $('new').disabled=!!stream||!!worker||!!protocol.active||!!protocol.candidate;
  $('attempts').replaceChildren(...protocol.attempts.map((a,i)=>{const li=document.createElement('li');li.textContent=`${i+1}. ${a.practice?'Familiarization':'Scored attempt'} · ${a.valid?'accepted':a.reason??'unsuccessful'}${a.score===null?'':` · ${a.score?.toFixed(1)} mm`}`;return li;}));
  $('sessionScore').textContent=protocol.sessionScore===null?'Position incomplete · no session score.':`Session median: ${protocol.sessionScore.toFixed(1)} mm · ${bundle.assignments.position}`;
}
function persist(){
  if(!session)return Promise.resolve();session.attempts=structuredClone(protocol.attempts);session.sessionScore=protocol.sessionScore;
  const snapshot=structuredClone(session);
  writing=writing.then(()=>saveSession(snapshot));
  writing.catch(e=>{storageFailed=true;protocol.stop('storage-failed',performance.now());stopCamera('storage-failed');error(`Local storage failed. Export the in-memory record now. ${e.message}`);controls();});return writing;
}
function clearMetrics(){for(const id of ['score','dx','dz'])$(id).textContent='—';previous=null;}
function stopCamera(reason='camera-stopped'){
  ++generation;worker?.terminate();worker=null;stream?.getTracks().forEach(t=>t.stop());stream=null;video.srcObject=null;ready=false;busy=false;clearMetrics();
  protocol.stop(reason,performance.now());if(!storageFailed)persist();$('state').textContent='Camera off';$('placeholder').style.display='grid';controls();
}
async function setBundle(input){
  bundle=validateCalibration(input);$('calibrationStatus').textContent=`${bundle.camera.id} / ${bundle.camera.version} · ${bundle.researchEligible?'Validated bundle; camera binding still required':'EXPLORATORY ONLY — cannot produce valid research scores'}`;
  $('position').textContent=`${bundle.assignments.position} · upper ${bundle.assignments.upper} · lower ${bundle.assignments.lower}`;clearMetrics();controls();
}
$('calibration').onchange=async e=>{try{await setBundle(JSON.parse(await e.target.files[0].text()));}catch(e){bundle=null;error(e);controls();}};
$('example').onclick=async()=>{try{await setBundle(await (await fetch('./examples/exploratory.json')).json());}catch(e){error(e);}};
$('start').onclick=async()=>{
  const token=++generation;
  try{
    if(!bundle)throw Error('Import a calibration bundle first.');if(!$('binding').checked)throw Error('Verify camera/calibration binding first.');
    $('error').textContent='';$('state').textContent='Requesting camera…';$('start').disabled=true;$('stop').disabled=false;
    const c=bundle.camera,selected=$('camera').value;
    const acquired=await navigator.mediaDevices.getUserMedia({audio:false,video:{width:{exact:c.width},height:{exact:c.height},...(selected?{deviceId:{exact:selected}}:{facingMode:'environment'})}});
    if(token!==generation){acquired.getTracks().forEach(t=>t.stop());return;}
    stream=acquired;video.srcObject=stream;await video.play();if(token!==generation)return;
    if(video.videoWidth!==c.width||video.videoHeight!==c.height)throw Error('Actual camera resolution does not match calibration.');
    raw.width=canvas.width=c.width;raw.height=canvas.height=c.height;
    stream.getTracks().forEach(t=>t.onended=()=>stopCamera('camera-ended'));
    worker=new Worker('./worker.js');worker.onerror=e=>{error(e.message);stopCamera('worker-error');};
    worker.onmessage=async({data:r})=>{
      if(token!==generation)return;
      if(r.type==='error'){error(r.message);stopCamera('worker-error');return;}
      if(r.type==='ready'){ready=true;rateStart=performance.now();frames=0;lastReply=rateStart;$('state').textContent='Observing';controls();return;}
      try{
        const now=performance.now(),q=bundle.quality;lastReply=now;frames++;
        let reason=now-r.time>q.maxAgeMs?'stale-frame':Object.values(r.results).find(x=>!x.ok)?.reason;
        const m=r.measurement;
        if(m&&!reason&&(Math.abs(m.delta[0])>q.maxXmm||Math.abs(m.delta[2])>q.maxZmm))reason='outside-alignment-limits';
        if(m&&!reason){if(!previous||r.time-previous.time>q.maxFrameGapMs)reason='motion-baseline-required';else if(Math.max(length(sub(m.upper,previous.m.upper)),length(sub(m.lower,previous.m.lower)))*1000/(r.time-previous.time)>q.maxSpeedMmS)reason='motion-too-fast';}
        previous=m?{time:r.time,m}:null;
        context.drawImage(video,0,0,canvas.width,canvas.height);context.strokeStyle='#75e4c1';context.lineWidth=2;context.font='16px sans-serif';context.fillStyle='#75e4c1';
        for(const o of r.observations){context.beginPath();o.corners.forEach((p,i)=>i?context.lineTo(...p):context.moveTo(...p));context.closePath();context.stroke();context.fillText(String(o.id),...o.corners[0]);}
        if(m&&!reason){$('score').textContent=m.overlapMm.toFixed(1);$('dx').textContent=m.delta[0].toFixed(1);$('dz').textContent=m.delta[2].toFixed(1);}else{for(const id of ['score','dx','dz'])$(id).textContent='—';}
        $('quality').textContent=reason??(bundle.researchEligible?'Quality checks passing':'Exploratory estimates · unvalidated bundle');
        $('performance').textContent=`${(frames*1000/(now-rateStart)).toFixed(1)} processed fps · ${r.processingMs.toFixed(0)} ms compute · ${(now-r.time).toFixed(0)} ms frame age`;
        if(protocol.active){
          const evidence={...r,received:now,reason:reason??null,calibration:{camera:bundle.camera.version,fixtures:bundle.fixtures.map(f=>[f.id,f.version]),quality:bundle.quality.version}};
          if(session.rgbConsent)evidence.rgb=raw.toDataURL('image/jpeg',.65);
          session.frames.push(evidence);protocol.observe(r.time,m?.overlapMm,!reason,q.maxFrameGapMs,reason);await persist();controls();
        }
      }catch(e){error(e);stopCamera('processing-error');}finally{busy=false;}
    };
    worker.postMessage({type:'configure',bundle});$('placeholder').style.display='none';controls();
    const capture=()=>{
      if(token!==generation)return;
      if(ready&&!busy&&!document.hidden){busy=true;rawContext.drawImage(video,0,0);const pixels=rawContext.getImageData(0,0,raw.width,raw.height).data;worker.postMessage({type:'frame',id:++frameId,time:performance.now(),width:raw.width,height:raw.height,pixels:pixels.buffer},[pixels.buffer]);}
      if(video.requestVideoFrameCallback)video.requestVideoFrameCallback(capture);else requestAnimationFrame(capture);
    };capture();
  }catch(e){if(token===generation){error(e);stopCamera('camera-start-failed');}}
};
$('stop').onclick=()=>stopCamera();
$('attempt').onclick=async()=>{
  try{
    if(!ready||storageFailed||!bundle)throw Error('Camera is not ready.');
    if(!$('participant').value.trim())throw Error('Enter a participant study ID before starting the camera.');
    if(!$('preseat').checked)throw Error('Confirm assignments and seating before every attempt.');
    session??={id:crypto.randomUUID(),created:new Date().toISOString(),participant:$('participant').value.trim(),bundle:structuredClone(bundle),cameraSettings:stream.getVideoTracks()[0].getSettings(),rgbConsent:$('consent').checked,retention:'indefinite-local-until-browser-eviction',frames:[],attempts:[]};
    protocol.start(performance.now(),bundle.researchEligible,$('practice').checked);previous=null;$('preseat').checked=false;await persist();controls();
  }catch(e){error(e);}
};
$('cancel').onclick=()=>{protocol.stop('operator-stop',performance.now());persist();controls();};
for(const [id,seated] of [['accept',true],['reject',false]])$(id).onclick=()=>{protocol.confirm(seated);persist();controls();};
$('swap').onclick=()=>{stopCamera('assignment-or-seating-change');$('binding').checked=false;$('preseat').checked=false;error('Paused. Confirm physical jig assignments, calibration and seating before restarting. Start a new position session to change assignments.');};
$('new').onclick=()=>{session=null;protocol=new TrialProtocol();storageFailed=false;writing=Promise.resolve();clearMetrics();controls();};
document.addEventListener('visibilitychange',()=>{if(document.hidden)stopCamera('page-hidden');});
setInterval(()=>{const now=performance.now();if(protocol.active&&now-protocol.active.started>=15000){protocol.stop('timeout',now);persist();controls();}if(ready&&now-lastReply>bundle.quality.maxAgeMs){clearMetrics();$('quality').textContent='Waiting for fresh observations';}},100);
async function records(){try{await writing;const saved=await loadSessions();if(session&&!saved.some(s=>s.id===session.id))saved.push(structuredClone(session));return saved;}catch{if(session)return [structuredClone(session)];throw Error('Saved records could not be read.');}}
function download(text,type,suffix){const url=URL.createObjectURL(new Blob([text],{type})),a=document.createElement('a');a.href=url;a.download=`reachsense-${new Date().toISOString().slice(0,10)}.${suffix}`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);}
$('json').onclick=async()=>{try{const saved=await records();if(!$('includeRGB').checked)for(const s of saved)for(const f of s.frames??[])delete f.rgb;download(JSON.stringify({schema:1,mode:'rgb-only',sessions:saved},null,2),'application/json','json');}catch(e){error(e);}};
$('csv').onclick=async()=>{try{const rows=[['session','participant','position','attempt','practice','valid','score_mm','reason']];for(const s of await records())for(const [i,a] of (s.attempts??[]).entries())rows.push([s.id,s.participant,s.bundle.assignments.position,i+1,a.practice,a.valid,a.score??'',a.reason??'']);download(rows.map(r=>r.map(v=>'"'+String(v).replaceAll('"','""')+'"').join(',')).join('\r\n'),'text/csv','csv');}catch(e){error(e);}};
if(navigator.mediaDevices)navigator.mediaDevices.enumerateDevices().then(devices=>{for(const d of devices.filter(d=>d.kind==='videoinput')){const o=document.createElement('option');o.value=d.deviceId;o.textContent=d.label||`Camera ${$('camera').options.length}`;$('camera').append(o);}}).catch(error);
controls();
