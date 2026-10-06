import {sub,length,transform} from './core.js';

export function solveFixture(cv,fixture,observations,camera,q) {
  const allocated=[];
  const mat=(rows,cols,type,data)=>{const m=data?cv.matFromArray(rows,cols,type,data):new cv.Mat();allocated.push(m);return m;};
  const reject=(reason,quality={})=>({ok:false,reason,quality});
  try {
    const selected=observations.filter(o=>fixture.faces.some(f=>f.id===o.id));
    if(new Set(selected.map(o=>o.id)).size!==selected.length)return reject('duplicate-marker-id');
    if(!selected.length)return reject('reference-unobserved');
    const object=[],image=[];
    for(const o of selected) {
      if(!Array.isArray(o.corners)||o.corners.length!==4||!o.corners.every(p=>p.length===2&&p.every(Number.isFinite)))return reject('malformed-corners');
      const edge=Math.min(...o.corners.map((p,i)=>length(sub(p,o.corners[(i+1)%4]))));
      if(edge<q.minEdgePx)return reject('marker-image-too-small',{minimumEdgePx:edge});
      object.push(...fixture.faces.find(f=>f.id===o.id).corners);image.push(...o.corners);
    }
    const center=[0,1,2].map(k=>object.reduce((s,p)=>s+p[k],0)/object.length);
    const covariance=[0,1,2].flatMap(i=>[0,1,2].map(j=>object.reduce((s,p)=>s+(p[i]-center[i])*(p[j]-center[j]),0)));
    const spectrum=mat(),basis=mat();cv.eigen(mat(3,3,cv.CV_64F,covariance),spectrum,basis);
    const geometryRatio=spectrum.data64F[2]/spectrum.data64F[0];
    if(!Number.isFinite(geometryRatio)||geometryRatio<q.minGeometryRatio)return reject('planar-or-degenerate-geometry',{geometryRatio});
    const obj=mat(object.length,1,cv.CV_64FC3,object.flat()),img=mat(image.length,1,cv.CV_64FC2,image.flat());
    const K=mat(3,3,cv.CV_64F,camera.K.flat()),dist=mat(camera.distortion.length,1,cv.CV_64F,camera.distortion);
    const r=mat(),t=mat();
    if(!cv.solvePnP(obj,img,K,dist,r,t,false,cv.SOLVEPNP_EPNP))return reject('pose-solver-failed');
    const candidates=[];
    const evaluate=(rv,tv)=>{
      cv.solvePnPRefineLM(obj,img,K,dist,rv,tv);
      const R=mat();cv.Rodrigues(rv,R);
      const pose=[0,1,2].map(i=>[...R.data64F.slice(i*3,i*3+3),tv.data64F[i]]);pose.push([0,0,0,1]);
      if(pose.flat().some(v=>!Number.isFinite(v))||object.some(p=>transform(pose,p)[2]<=0))return;
      const projected=mat(),jac=mat();cv.projectPoints(obj,rv,tv,K,dist,projected,jac,0);
      const residuals=image.map((p,i)=>Math.hypot(p[0]-projected.data64F[i*2],p[1]-projected.data64F[i*2+1]));
      const rms=Math.sqrt(residuals.reduce((s,v)=>s+v*v,0)/residuals.length);
      const norms=[0,1,2,3,4,5].map(i=>Math.sqrt(Array.from({length:jac.rows},(_,row)=>jac.data64F[row*jac.cols+i]**2).reduce((a,b)=>a+b,0)));
      const H=Array.from({length:36},(_,ij)=>{
        const i=Math.floor(ij/6),j=ij%6;
        return Array.from({length:jac.rows},(_,row)=>jac.data64F[row*jac.cols+i]*jac.data64F[row*jac.cols+j]).reduce((a,b)=>a+b,0)/(norms[i]*norms[j]);
      });
      const eigen=mat(),vectors=mat();cv.eigen(mat(6,6,cv.CV_64F,H),eigen,vectors);
      const condition=eigen.data64F[0]/eigen.data64F[5];
      candidates.push({pose,rms,maximum:Math.max(...residuals),condition,residuals});
    };
    evaluate(r,t);
    const baseR=Array.from(r.data64F),baseT=Array.from(t.data64F);
    for(const axis of [0,1]) {
      const initial=[...baseR];initial[axis]+=Math.PI;
      const alternateR=mat(3,1,cv.CV_64F,initial),alternateT=mat(3,1,cv.CV_64F,baseT);
      try {evaluate(alternateR,alternateT);}catch{/* Invalid alternative is retained as no supported competing solution. */}
    }
    candidates.sort((a,b)=>a.rms-b.rms);
    const best=candidates[0];if(!best)return reject('no-positive-depth-solution');
    const quality={geometryRatio,condition:best.condition,rmsPx:best.rms,maxResidualPx:best.maximum,
      markerIds:selected.map(o=>o.id),cornerResidualsPx:best.residuals,solutionsChecked:candidates.length,
      ambiguityMethod:'noncoplanar-geometry-plus-three-LM-initializations'};
    if(!Number.isFinite(best.condition)||best.condition<=0||best.condition>q.maxCondition)return reject('ill-conditioned-pose',quality);
    if(best.rms>q.maxRmsPx||best.maximum>q.maxResidualPx)return reject('reprojection-disagreement',quality);
    const tip=transform(best.pose,fixture.tip);
    for(const other of candidates.slice(1)) {
      const trace=best.pose.slice(0,3).reduce((s,row,i)=>s+row.slice(0,3).reduce((v,x,j)=>v+x*other.pose[i][j],0),0);
      const angle=Math.acos(Math.max(-1,Math.min(1,(trace-1)/2)))*180/Math.PI;
      if(other.rms<=best.rms+q.ambiguityRmsPx&&
        (length(sub(transform(other.pose,fixture.tip),tip))>q.ambiguityDistanceMm||angle>q.ambiguityAngleDeg))return reject('competing-pose-solutions',quality);
    }
    return {ok:true,pose:best.pose,tipCamera:tip,quality};
  }catch(error){return reject('solver-error',{detail:String(error.message??error)});}
  finally {for(const m of allocated.reverse())m.delete();}
}
