const finite = Number.isFinite;
export const median = values => {
  const a=[...values].sort((x,y)=>x-y);const n=a.length;
  return n ? (a[Math.floor((n-1)/2)]+a[Math.floor(n/2)])/2 : null;
};
export const sub = (a,b) => a.map((x,i)=>x-b[i]);
export const dot = (a,b) => a.reduce((s,x,i)=>s+x*b[i],0);
export const cross = (a,b) => [a[1]*b[2]-a[2]*b[1],a[2]*b[0]-a[0]*b[2],a[0]*b[1]-a[1]*b[0]];
export const length = a => Math.hypot(...a);
const vector = (a,n) => Array.isArray(a)&&a.length===n&&a.every(finite);
const fail = message => {throw Error(message);};
export function rigid(t) {
  if(!Array.isArray(t)||t.length!==4||!t.every(r=>vector(r,4))||
    t[3].some((x,i)=>Math.abs(x-(i===3?1:0))>1e-6)) return false;
  const r=t.slice(0,3).map(row=>row.slice(0,3));
  return r.every((a,i)=>r.every((b,j)=>Math.abs(dot(a,b)-(i===j?1:0))<1e-4))&&
    Math.abs(dot(r[0],cross(r[1],r[2]))-1)<1e-4;
}
export function transform(t,p) {return t.slice(0,3).map(row=>dot(row.slice(0,3),p)+row[3]);}
export function inversePoint(t,p) {
  const q=sub(p,t.slice(0,3).map(r=>r[3]));
  return [0,1,2].map(i=>q.reduce((s,x,j)=>s+x*t[j][i],0));
}
export function validateCalibration(input) {
  if(!input||input.schema!==1||input.units!=='mm'||input.dictionary!=='DICT_4X4_100')fail('Expected schema 1, mm and DICT_4X4_100');
  const b=structuredClone(input), c=b.camera;
  if(!c?.id||!c.version||!Number.isInteger(c.width)||!Number.isInteger(c.height)||c.width<16||c.height<16||c.width*c.height>16000000)fail('Invalid camera identity/dimensions');
  if(!Array.isArray(c.K)||c.K.length!==3||!c.K.every(r=>vector(r,3))||c.K[0][0]<=0||c.K[1][1]<=0||
    c.K[0][1]!==0||c.K[1][0]!==0||c.K[2].some((v,i)=>v!==(i===2?1:0)))fail('Invalid zero-skew pinhole intrinsics');
  if(!Array.isArray(c.distortion)||![4,5,8,12,14].includes(c.distortion.length)||!c.distortion.every(finite))fail('Invalid Brown-Conrady coefficients');
  if(!Array.isArray(b.fixtures)||b.fixtures.length!==3)fail('Provide two hand fixtures and one body reference');
  const fixtureIds=new Set(), markerIds=new Set();
  for(const f of b.fixtures) {
    if(typeof f.id!=='string'||!f.id||!f.version||fixtureIds.has(f.id)||!vector(f.tip,3)||!Array.isArray(f.faces)||f.faces.length<3)fail('Invalid fixture identity, endpoint or faces');
    fixtureIds.add(f.id);
    for(const face of f.faces) {
      if(!Number.isInteger(face.id)||face.id<0||face.id>=100||markerIds.has(face.id))fail('Duplicate or invalid marker IDs');
      markerIds.add(face.id);
      const p=face.corners;
      if(!Array.isArray(p)||p.length!==4||!p.every(v=>vector(v,3)))fail('Four canonical 3D corners required');
      const a=sub(p[1],p[0]), d=sub(p[3],p[0]), n=cross(a,d);
      if(length(n)<1e-6||Math.abs(dot(sub(p[2],p[0]),n))/length(n)>0.01||
        p.some((v,i)=>p.some((w,j)=>i!==j&&length(sub(v,w))<0.1))||
        p.some((v,i)=>dot(cross(sub(p[(i+1)%4],v),sub(p[(i+2)%4],p[(i+1)%4])),n)<=1e-6))fail('Degenerate/nonplanar marker face');
    }
  }
  const a=b.assignments;
  if(!a||new Set([a.upper,a.lower,a.body]).size!==3||![a.upper,a.lower,a.body].every(id=>fixtureIds.has(id))||
    !['right-hand-up','left-hand-up'].includes(a.position)||!rigid(b.bodyTransform))fail('Invalid assignments/body registration');
  const q=b.quality;
  if(!q?.id||!q.version||!['exploratory','validated'].includes(q.validation)||
    !['maxRmsPx','maxResidualPx','minEdgePx','minGeometryRatio','maxCondition','maxFrameGapMs','maxAgeMs','maxSpeedMmS','maxXmm','maxZmm','ambiguityRmsPx','ambiguityDistanceMm','ambiguityAngleDeg'].every(k=>finite(q[k])&&q[k]>0)||
    q.minGeometryRatio>=1||q.maxAgeMs>5000||q.maxFrameGapMs>1000)fail('Invalid versioned quality profile');
  b.researchEligible=q.validation==='validated'&&typeof q.evidence==='string'&&q.evidence.trim().length>0&&
    c.status==='verified'&&b.fixtures.every(f=>f.status==='verified'&&typeof f.evidence==='string'&&f.evidence.trim())&&
    typeof b.bodyRegistrationEvidence==='string'&&b.bodyRegistrationEvidence.trim().length>0;
  return b;
}
export function bodyMeasurement(poses,b) {
  const a=b.assignments;
  for(const id of [a.upper,a.lower,a.body])if(!rigid(poses[id]))fail('Missing/invalid current rigid pose');
  const endpoint=id=>{
    const f=b.fixtures.find(f=>f.id===id);
    return transform(b.bodyTransform,inversePoint(poses[a.body],transform(poses[id],f.tip)));
  };
  const upper=endpoint(a.upper),lower=endpoint(a.lower),delta=sub(lower,upper);
  return {upper,lower,delta,overlapMm:delta[1],distanceMm:length(delta)};
}
export class TrialProtocol {
  attempts=[];active=null;candidate=null;
  get sessionScore(){const a=this.attempts.filter(a=>a.valid&&!a.practice);return a.length===3?median(a.map(a=>a.score)):null;}
  start(time,eligible,practice=false) {
    if(!finite(time)||this.active||this.candidate||(!practice&&(this.sessionScore!==null||this.attempts.filter(a=>!a.practice).length>=5)))fail('Attempt unavailable');
    this.active={started:time,eligible:!!eligible,practice:!!practice,window:[],reasons:[],last:null};
  }
  observe(time,score,passing,maxGap,reason='quality') {
    const a=this.active;if(!a)return null;
    if(!finite(time)||time<a.started||a.last!==null&&time<a.last)return this.stop('nonmonotonic-time',time);
    if(time-a.started>=15000)return this.stop('timeout',time);
    if(a.last!==null&&time-a.last>maxGap){a.window=[];a.reasons.push('observation-gap');}
    a.last=time;
    if(!passing||!finite(score)){a.window=[];a.reasons.push(reason);return null;}
    a.window.push({time,score});
    if(time-a.window[0].time>=1000){
      this.candidate={...a,ended:time,score:median(a.window.map(s=>s.score)),valid:false};
      this.active=null;return this.candidate;
    }
    return null;
  }
  confirm(seated) {
    if(!this.candidate)fail('No candidate awaiting seating check');
    const a=this.candidate;a.valid=!!seated&&a.eligible;
    a.reason=!seated?'post-seating-failed':!a.eligible?'exploratory-calibration':null;
    this.attempts.push(a);this.candidate=null;return a;
  }
  stop(reason='operator-stop',time=this.active?.last??0) {
    const a=this.active||this.candidate;if(!a)return null;
    const result={...a,ended:time,valid:false,reason,score:a.score??null};
    this.attempts.push(result);this.active=null;this.candidate=null;return result;
  }
}
