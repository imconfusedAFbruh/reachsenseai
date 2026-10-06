import { createRequire } from 'node:module';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { resolve, relative, extname } from 'node:path';
import assert from 'node:assert/strict';

const require = createRequire(process.env.REACHSENSE_PLAYWRIGHT_ROOT
  ? `${process.env.REACHSENSE_PLAYWRIGHT_ROOT}/package.json` : import.meta.url);
const { chromium } = require('playwright');
const root = resolve(import.meta.dirname, '..');
const server = createServer(async (req, res) => {
  try {
    if (req.url === '/probe.html') { res.end('<!doctype html><title>Runtime probe</title>'); return; }
    const pathname=new URL(req.url, 'http://localhost').pathname;
    const file = resolve(root, '.' + (pathname==='/'?'/index.html':pathname));
    if (relative(root, file).startsWith('..')) throw Error('Outside web root');
    const bytes = await readFile(file);
    res.setHeader('Content-Type', ({'.js':'text/javascript','.json':'application/json','.html':'text/html','.css':'text/css'})[extname(file)] || 'application/octet-stream');
    res.end(bytes);
  } catch { res.writeHead(404); res.end(); }
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const url = `http://127.0.0.1:${server.address().port}`;
const browser = await chromium.launch({ headless: true, channel: process.env.REACHSENSE_BROWSER_CHANNEL });
try {
  const page = await browser.newPage();
  await page.goto(url + '/probe.html');
  await page.addScriptTag({ url: url + '/vendor/opencv.js' });
  await page.waitForFunction(() => window.cv?.Mat, { timeout: 30000 });
  const capabilities = await page.evaluate(() => {
    const cv = window.cv;
    window.readyCV = cv;
    return ['aruco_ArucoDetector','aruco_DetectorParameters','getPredefinedDictionary',
      'generateImageMarker','solvePnP','solvePnPRansac','solvePnPRefineLM','Rodrigues','projectPoints']
      .map(name => [name, typeof cv[name]]);
  });
  for (const [name, kind] of capabilities) assert.equal(kind, 'function', `${name} unavailable`);
  console.log('PASS actual browser OpenCV runtime capabilities');
  const poseCheck=await page.evaluate(async()=>{
    const {solveFixture}=await import('/engine.js'); const cv=window.readyCV;
    const fixture={id:'a',tip:[0,0,0],faces:[
      {id:21,corners:[[-20,15,0],[0,15,0],[0,-5,0],[-20,-5,0]]},
      {id:22,corners:[[5,15,5],[15,15,15],[15,-5,15],[5,-5,5]]},
      {id:23,corners:[[-20,-10,0],[0,-10,0],[0,-20,10],[-20,-20,10]]}]};
    const camera={K:[[800,0,480],[0,800,360],[0,0,1]],distortion:[0,0,0,0,0]};
    const profile={minGeometryRatio:.001,minEdgePx:8,maxRmsPx:2,maxResidualPx:5,maxCondition:1e8,
      ambiguityRmsPx:.25,ambiguityDistanceMm:5,ambiguityAngleDeg:5};
    // Independent known pinhole projection: x_C=x+12, y_C=y-8, z_C=z+450.
    const observations=fixture.faces.map(f=>({id:f.id,corners:f.corners.map(([x,y,z])=>
      [800*(x+12)/(z+450)+480,800*(y-8)/(z+450)+360])}));
    const solved=solveFixture(cv,fixture,observations,camera,profile);
    const planar=solveFixture(cv,fixture,observations.slice(0,1),camera,profile);
    return {solved,planar};
  });
  assert.ok(poseCheck.solved?.ok,JSON.stringify(poseCheck.solved));
  assert.ok(Math.abs(poseCheck.solved.pose[0][3]-12)<.05);
  assert.ok(Math.abs(poseCheck.solved.pose[1][3]+8)<.05);
  assert.ok(Math.abs(poseCheck.solved.pose[2][3]-450)<.05);
  assert.equal(poseCheck.planar.ok,false);
  console.log('PASS real common PnP recovery and planar rejection');
  const workerCheck=await page.evaluate(async()=>{
    const bundle=await(await fetch('/examples/exploratory.json')).json();
    return new Promise(resolve=>{
      const w=new Worker('/worker.js');const timeout=setTimeout(()=>{w.terminate();resolve({type:'timeout'});},30000);
      w.onmessage=({data:r})=>{if(r.type==='ready'){const pixels=new Uint8Array(960*720*4).fill(255);w.postMessage({type:'frame',id:1,time:1,width:960,height:720,pixels:pixels.buffer},[pixels.buffer]);}else{clearTimeout(timeout);w.terminate();resolve(r);}};
      w.postMessage({type:'configure',bundle});
    });
  });
  assert.equal(workerCheck.type,'frame',JSON.stringify(workerCheck));
  assert.equal(workerCheck.measurement,null);
  assert.ok(Object.values(workerCheck.results).every(r=>r.reason==='reference-unobserved'));
  console.log('PASS actual detector worker rejects unobserved fixtures');
  await page.goto(url+'/');
  assert.equal(await page.locator('#attempt').isDisabled(),true);
  await page.locator('#example').click();
  await page.waitForFunction(()=>document.querySelector('#calibrationStatus').textContent.includes('EXPLORATORY'));
  await page.locator('#start').click();
  assert.match(await page.locator('#error').textContent(),/binding/);
  assert.equal(await page.locator('#attempt').isDisabled(),true);
  const downloadPromise=page.waitForEvent('download');await page.locator('#json').click();
  const download=await downloadPromise;
  assert.match(download.suggestedFilename(),/\.json$/);
  console.log('PASS calibration binding, exploratory labeling, blocked attempts and JSON export');
  const cameraPage=await browser.newPage();
  await cameraPage.addInitScript(()=>{
    navigator.mediaDevices.getUserMedia=async()=>{
      const c=document.createElement('canvas');c.width=960;c.height=720;
      const paint=()=>{const x=c.getContext('2d');x.fillStyle='white';x.fillRect(0,0,960,720);requestAnimationFrame(paint);};paint();
      return c.captureStream(30);
    };
  });
  await cameraPage.goto(url+'/');await cameraPage.locator('#participant').fill('browser-check');
  await cameraPage.locator('#example').click();
  await cameraPage.waitForFunction(()=>document.querySelector('#calibrationStatus').textContent.includes('EXPLORATORY'));
  await cameraPage.locator('#binding').check();await cameraPage.locator('#start').click();
  await cameraPage.waitForFunction(()=>!document.querySelector('#attempt').disabled);
  await cameraPage.locator('#preseat').check();await cameraPage.locator('#attempt').click();
  await cameraPage.waitForFunction(()=>document.querySelector('#performance').textContent.includes('processed fps'));
  // Wait for the recorded frame transaction, then verify the active attempt survives persistence.
  await cameraPage.waitForFunction(()=>/· [1-9]\d* recorded observations/.test(document.querySelector('#trialStatus').textContent));
  const activeRecord=await cameraPage.evaluate(async()=>{const {loadSessions}=await import('/storage.js');return (await loadSessions()).find(s=>s.participant==='browser-check'&&s.frames.length>0);});
  assert.ok(activeRecord.active,'Active attempt must be persisted, not only finalized attempts');
  assert.equal(activeRecord.frames[0].rgb,undefined,'Unconsented RGB must not be stored');
  await cameraPage.locator('#stop').click();
  assert.equal(await cameraPage.locator('#binding').isChecked(),false,'Camera binding must be reconfirmed after stop');
  const dPromise=cameraPage.waitForEvent('download');await cameraPage.locator('#json').click();
  const file=await(await dPromise).path();const exported=JSON.parse(await readFile(file,'utf8'));
  assert.equal(exported.sessions[0].attempts[0].reason,'camera-stopped');
  assert.ok(exported.sessions[0].frames.length>0);
  await cameraPage.reload();const persistedPromise=cameraPage.waitForEvent('download');await cameraPage.locator('#json').click();
  const persisted=JSON.parse(await readFile(await(await persistedPromise).path(),'utf8'));
  assert.equal(persisted.sessions[0].attempts[0].reason,'camera-stopped');
  console.log('PASS fake camera, per-frame retention, active persistence, stopped attempt and reload export');
  await cameraPage.close();
} finally { await browser.close(); await new Promise(r => server.close(r)); }
