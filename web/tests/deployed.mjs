import {createRequire} from 'node:module';
import assert from 'node:assert/strict';
const require=createRequire(process.env.REACHSENSE_PLAYWRIGHT_ROOT?`${process.env.REACHSENSE_PLAYWRIGHT_ROOT}/package.json`:import.meta.url);
const {chromium}=require('playwright');
const browser=await chromium.launch({headless:true,channel:process.env.REACHSENSE_BROWSER_CHANNEL});
try{
 const page=await browser.newPage(),errors=[];page.on('pageerror',e=>errors.push(e.message));
 const response=await page.goto('https://imconfusedafbruh.github.io/reachsenseai/');assert.equal(response.status(),200);
 assert.match(await page.title(),/ReachSense/);assert.equal(await page.locator('#attempt').isDisabled(),true);
 await page.locator('#example').click();await page.waitForFunction(()=>document.querySelector('#calibrationStatus').textContent.includes('EXPLORATORY'));
 const result=await page.evaluate(async()=>{
  const bundle=await(await fetch('./examples/exploratory.json')).json();
  return new Promise(resolve=>{const w=new Worker('./worker.js');const timer=setTimeout(()=>{w.terminate();resolve({type:'timeout'});},30000);
   w.onmessage=({data:r})=>{if(r.type==='ready'){const pixels=new Uint8Array(bundle.camera.width*bundle.camera.height*4).fill(255);w.postMessage({type:'frame',id:1,time:1,width:bundle.camera.width,height:bundle.camera.height,pixels:pixels.buffer},[pixels.buffer]);}else{clearTimeout(timer);w.terminate();resolve(r);}};
   w.onerror=e=>{clearTimeout(timer);w.terminate();resolve({type:'error',message:e.message});};w.postMessage({type:'configure',bundle});
  });
 });
 assert.equal(result.type,'frame',JSON.stringify(result));assert.equal(result.measurement,null);assert.equal(errors.length,0,errors.join('\n'));
 console.log('PASS deployed HTTPS UI, project-subpath assets, WASM worker and missing-reference rejection');
}finally{await browser.close();}
