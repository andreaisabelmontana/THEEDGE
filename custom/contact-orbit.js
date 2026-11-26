(() => {
  const scene=document.querySelector('#stargazing');
  const canvas=scene?.querySelector('[data-contact-velocity]');
  const button=scene?.querySelector('[data-contact-orbit-toggle]');
  const ctx=canvas?.getContext('2d');
  if(!ctx||!button)return;

  // Adapted from VelocityParticles, The Race to Save Space (UN / Elkanodata).
  // Retains its palette, six line-width bands, radial spawn, 60° camera,
  // wheel easing, and velocity ramp. Draws locally without the UN app runtime.
  const colors=['#438490','#6ab395','#81d5d7','#cefef8','#f3c08d','#fccdef','#fd9737'];
  const cameraZ=300, limit=2000, random=Math.random;
  const reduce=matchMedia('(prefers-reduced-motion: reduce)');
  const labels={en:['Pause animation','Play animation'],es:['Pausar animación','Reproducir animación'],de:['Animation anhalten','Animation abspielen']};
  let W=0,H=0,D=1,focal=1,particles=[],shown=false,enabled=!reduce.matches;
  let frame=0,previous=0,impulse=0,impulseTarget=0;

  function spawn(p,ramp=1) {
    const x=(random()-.5)*W,y=(random()-.5)*H,z=(random()-.5)*(-2*cameraZ);
    const d=Math.max(1,Math.hypot(x,y,z));
    p.x=x;p.y=y;p.z=z;p.dx=x/d;p.dy=y/d;p.dz=z/d;
    p.tail=50+random()*50;
    p.speed=(5+random()*5)*ramp;
    p.color=Math.floor(random()*colors.length);
    return p;
  }
  function resize() {
    const box=scene.getBoundingClientRect();
    if(!box.width||!box.height)return;
    const w=Math.round(box.width),h=Math.round(box.height),d=Math.min(devicePixelRatio||1,1.75);
    if(W===w&&H===h&&D===d)return;
    W=w;H=h;D=d;focal=H/(2*Math.tan(Math.PI/6));
    canvas.width=Math.round(W*D);canvas.height=Math.round(H*D);
    ctx.setTransform(D,0,0,D,0,0);
    particles=[];
    const count=W<620?125:300;
    for(let band=0;band<6;band++){
      const stroke=.5+random()*6.5;
      for(let j=0;j<count;j++)particles.push(spawn({stroke}));
    }
    if(!reduce.matches||enabled)paint(0);
  }
  function paint(step) {
    ctx.clearRect(0,0,W,H);
    if(!W||!H)return;
    const rect=scene.getBoundingClientRect();
    const progress=Math.max(0,Math.min(1,(innerHeight-rect.top)/(innerHeight+H)));
    impulse+=(impulseTarget-impulse)*.1;
    impulseTarget*=.86;
    const ramp=(1+Math.pow(progress,2)*30)*(1+Math.min(impulse,120)*.05);
    for(const p of particles){
      const v=p.speed*step;
      p.x+=p.dx*v;p.y+=p.dy*v;p.z+=p.dz*v;
      if(Math.hypot(p.x,p.y,p.z)+p.tail>limit)spawn(p,ramp*.35);
      const a=cameraZ-p.z,b=a-p.dz*p.tail;
      if(a<=1||b<=1)continue;
      const x=W/2+p.x*focal/a,y=H/2-p.y*focal/a;
      const tx=W/2+(p.x+p.dx*p.tail)*focal/b,ty=H/2-(p.y+p.dy*p.tail)*focal/b;
      if((x<0&&tx<0)||(x>W&&tx>W)||(y<0&&ty<0)||(y>H&&ty>H))continue;
      ctx.beginPath();ctx.moveTo(x,y);ctx.lineTo(tx,ty);
      ctx.lineCap=p.color===3?'round':'butt';ctx.lineWidth=p.stroke;
      ctx.strokeStyle=colors[p.color];
      ctx.globalAlpha=p.color===3?.92:.73;
      ctx.shadowBlur=p.color===3?12*D:0;
      if(p.color===3)ctx.shadowColor='#9eeaf8';
      ctx.stroke();
    }
    ctx.globalAlpha=1;ctx.shadowBlur=0;
  }
  function tick(time){
    frame=0;
    if(!shown||!enabled||document.hidden){previous=0;return;}
    paint(previous?Math.min(2,(time-previous)/16.667):1);
    previous=time;
    frame=requestAnimationFrame(tick);
  }
  function sync(){
    const lang=document.documentElement.lang.split('-')[0];
    button.textContent=(labels[lang]||labels.en)[enabled?0:1];
    if(!shown||!enabled||document.hidden){
      cancelAnimationFrame(frame);frame=0;previous=0;
    }else if(!frame)frame=requestAnimationFrame(tick);
  }
  button.hidden=false;
  button.addEventListener('click',()=>{enabled=!enabled;sync();});
  reduce.addEventListener?.('change',()=>{
    enabled=!reduce.matches;
    if(!enabled)ctx.clearRect(0,0,W,H);
    sync();
  });
  document.addEventListener('edge:lang',sync);
  document.addEventListener('visibilitychange',sync);
  window.addEventListener('wheel',e=>{if(shown&&enabled)impulseTarget=Math.abs(e.deltaY);},{passive:true});
  if('IntersectionObserver' in window){
    new IntersectionObserver(([e])=>{shown=e.isIntersecting;sync();},{threshold:.12}).observe(scene);
  }else shown=true;
  if('ResizeObserver' in window)new ResizeObserver(resize).observe(scene);
  else window.addEventListener('resize',resize,{passive:true});
  resize();sync();
})();
