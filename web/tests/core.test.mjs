import test from 'node:test';
import assert from 'node:assert/strict';
import { validateCalibration, bodyMeasurement, TrialProtocol } from '../core.js';

const identity = [[1,0,0,0],[0,1,0,0],[0,0,1,0],[0,0,0,1]];
const pose = (x,y,z) => [[1,0,0,x],[0,1,0,y],[0,0,1,z],[0,0,0,1]];
export function bundle() {
  const fixture = (id, first) => ({id, version:'1', status:'exploratory', tip:[0,0,0], faces:[
    {id:first, corners:[[-5,5,0],[5,5,0],[5,-5,0],[-5,-5,0]]},
    {id:first+1, corners:[[0,5,5],[0,5,15],[0,-5,15],[0,-5,5]]},
    {id:first+2, corners:[[-5,0,5],[5,0,5],[5,0,15],[-5,0,15]]}]});
  return {schema:1, units:'mm', dictionary:'DICT_4X4_100', camera:{id:'test',version:'1',width:960,height:720,
    K:[[800,0,480],[0,800,360],[0,0,1]],distortion:[0,0,0,0,0]},
    fixtures:[fixture('a',21),fixture('b',31),fixture('back',41)],
    assignments:{upper:'a',lower:'b',body:'back',position:'right-hand-up'},
    bodyTransform:identity.map(r=>[...r]), quality:{id:'test-preview',version:'1',validation:'exploratory',
      maxRmsPx:2,maxResidualPx:5,minEdgePx:8,minGeometryRatio:0.01,maxCondition:1e8,
      maxFrameGapMs:200,maxAgeMs:500,maxSpeedMmS:30,maxXmm:40,maxZmm:40,
      ambiguityRmsPx:.25,ambiguityDistanceMm:5,ambiguityAngleDeg:5}};
}

test('reject missing, malformed, duplicate, improper and incompatible calibration',()=>{
  assert.throws(()=>validateCalibration(null));
  for (const edit of [b=>b.units='meters',b=>b.camera.K[0][0]=NaN,
    b=>b.fixtures[1].faces[0].id=21,b=>b.bodyTransform[0][0]=-1,
    b=>b.fixtures[0].faces[0].corners[0]=[1,2],b=>b.quality.maxAgeMs=-1,
    b=>b.assignments.body='a',b=>delete b.quality.ambiguityRmsPx]) {
    const b=bundle(); edit(b); assert.throws(()=>validateCalibration(b));
  }
  assert.equal(validateCalibration(bundle()).units,'mm');
});

test('body registration compensates camera orientation and preserves signed overlap and offsets',()=>{
  const b=bundle();
  const back=[[1,0,0,10],[0,-1,0,20],[0,0,-1,500],[0,0,0,1]];
  const m=bodyMeasurement({a:pose(10,30,500),b:pose(13,15,502),back},b);
  assert.deepEqual(m.upper,[0,-10,0]); assert.deepEqual(m.lower,[3,5,-2]);
  assert.deepEqual(m.delta,[3,15,-2]); assert.equal(m.overlapMm,15);
  assert.equal(bodyMeasurement({a:pose(10,15,500),b:pose(10,30,500),back},b).overlapMm,-15);
  assert.throws(()=>bodyMeasurement({a:pose(0,0,0)},b));
});

test('continuous window resets on rejection/gap and requires post-seating confirmation',()=>{
  const p=new TrialProtocol(); p.start(0,true,false);
  assert.equal(p.observe(0,1,true,200),null);
  p.observe(150,2,true,200); p.observe(300,null,false,200);
  for(let t=400;t<1400;t+=100) assert.equal(p.observe(t,t/100,true,200),null);
  const candidate=p.observe(1400,14,true,200);
  assert.equal(candidate.score,9); assert.equal(p.attempts.length,0);
  p.confirm(false); assert.equal(p.attempts[0].valid,false);
  const q=new TrialProtocol();q.start(0,true,false);q.observe(0,1,true,200);
  assert.equal(q.observe(1000,1,true,200),null);
  assert.equal(q.observe(15000,null,false,200).reason,'timeout');
});

test('three accepted trials give median; five failed attempts remain incomplete and exploratory cannot score',()=>{
  const p=new TrialProtocol();
  for(const score of [8,2,5]) {
    p.start(0,true,false);for(let t=0;t<=1000;t+=100)p.observe(t,score,true,200);p.confirm(true);
  }
  assert.equal(p.sessionScore,5);assert.throws(()=>p.start(0,true,false));
  const q=new TrialProtocol();
  for(let n=0;n<5;n++){q.start(0,false,false);q.observe(15000,null,false,200);}
  assert.equal(q.sessionScore,null);assert.throws(()=>q.start(0,false,false));
  const demo=new TrialProtocol(); demo.start(0,false,false);
  for(let t=0;t<=1000;t+=100)demo.observe(t,42,true,200);
  demo.confirm(true);assert.equal(demo.attempts[0].valid,false);
});
