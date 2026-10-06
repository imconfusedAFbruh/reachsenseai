importScripts('./vendor/opencv.js');
let calibration,engine,core,detector;
const ready=new Promise(resolve=>{const check=()=>{if(self.cv?.Mat)resolve();else setTimeout(check,30);};check();});
self.onmessage=async({data:m})=>{
  try {
    await ready;
    core??=await import('./core.js');engine??=await import('./engine.js');
    if(m.type==='configure') {
      calibration=core.validateCalibration(m.bundle);
      detector?.delete();
      const dictionary=cv.getPredefinedDictionary(cv.DICT_4X4_100);
      const parameters=new cv.aruco_DetectorParameters();
      const refine=new cv.aruco_RefineParameters(10,3,true);
      detector=new cv.aruco_ArucoDetector(dictionary,parameters,refine);
      dictionary.delete();parameters.delete();refine.delete();
      postMessage({type:'ready'});return;
    }
    const begin=performance.now(),src=cv.matFromArray(m.height,m.width,cv.CV_8UC4,new Uint8Array(m.pixels));
    const gray=new cv.Mat(),corners=new cv.MatVector(),ids=new cv.Mat(),rejected=new cv.MatVector();
    try {
      if(m.width!==calibration.camera.width||m.height!==calibration.camera.height)throw Error('Camera resolution differs from calibration');
      cv.cvtColor(src,gray,cv.COLOR_RGBA2GRAY);detector.detectMarkers(gray,corners,ids,rejected);
      const observations=[];
      for(let i=0;i<ids.rows;i++){const c=corners.get(i);observations.push({id:ids.data32S[i],corners:Array.from({length:4},(_,k)=>[c.data32F[k*2],c.data32F[k*2+1]])});c.delete();}
      const results=Object.fromEntries(calibration.fixtures.map(f=>[f.id,engine.solveFixture(cv,f,observations,calibration.camera,calibration.quality)]));
      const passing=Object.values(results).every(r=>r.ok);
      const measurement=passing?core.bodyMeasurement(Object.fromEntries(Object.entries(results).map(([id,r])=>[id,r.pose])),calibration):null;
      postMessage({type:'frame',id:m.id,time:m.time,observations,results,measurement,processingMs:performance.now()-begin});
    }finally{src.delete();gray.delete();corners.delete();ids.delete();rejected.delete();}
  }catch(error){postMessage({type:'error',message:String(error.message??error)});}
};
