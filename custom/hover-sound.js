(() => {
  const AudioEngine=window.AudioContext||window.webkitAudioContext;
  if(!AudioEngine||!('PointerEvent' in window))return;
  const buttons=[
    'button:not([disabled])','input[type="button"]:not([disabled])','input[type="submit"]:not([disabled])',
    '[role="button"]:not([aria-disabled="true"])','a.btn-w','a.btn','a.portfolio-cv-link',
    'a.otot-card','a.project-card','a.project-preview-btn','a.nav-menu-link-w'
  ].join(',');
  const languages='[data-lang-switch] button[data-lang]';
  // The UN sidebar uses "mouse"; its changing timeline numbers use "numbers".
  const base='https://projects.elkanodata.com/WIP/unhq-space/audio/';
  const buffers=new Map(),pending=new Map();
  let engine,last=-Infinity,playing;
  function load(name){
    if(buffers.has(name))return Promise.resolve(buffers.get(name));
    if(pending.has(name))return pending.get(name);
    const request=fetch(base+name+'.mp3',{credentials:'omit',referrerPolicy:'no-referrer'})
      .then(r=>{if(!r.ok)throw Error(r.status);return r.arrayBuffer();})
      .then(bytes=>engine.decodeAudioData(bytes))
      .then(data=>{buffers.set(name,data);return data;})
      .catch(()=>null).finally(()=>pending.delete(name));
    pending.set(name,request);
    return request;
  }
  function unlock(){
    if(document.hidden)return false;
    if(!engine){try{engine=new AudioEngine();}catch{return false;}}
    if(engine.state!=='running')engine.resume().catch(()=>{});
    return true;
  }
  function play(buffer){
    if(!buffer||document.hidden||engine?.state!=='running')return;
    const now=engine.currentTime;
    if(now-last<.1)return;
    last=now;
    if(playing){try{playing.stop();}catch{}}
    const cue=engine.createBufferSource(),gain=engine.createGain();
    cue.buffer=buffer;
    gain.gain.value=.15;
    cue.connect(gain);gain.connect(engine.destination);
    cue.onended=()=>{cue.disconnect();gain.disconnect();if(playing===cue)playing=null;};
    playing=cue;cue.start();
  }
  function prepare(e){
    if(e.isTrusted&&unlock())load('mouse');
  }
  document.addEventListener('pointerdown',prepare,{passive:true,capture:true});
  document.addEventListener('keydown',prepare,{capture:true});
  document.addEventListener('click',async e=>{
    const lang=e.target instanceof Element?e.target.closest(languages):null;
    if(!e.isTrusted||!lang||lang.getAttribute('aria-pressed')==='true'||lang.disabled||!unlock())return;
    const time=performance.now();
    const cue=await load('numbers');
    if(performance.now()-time<1200)play(cue);
  },true);
  document.addEventListener('pointerover',e=>{
    if(e.pointerType!=='mouse'||document.hidden||!engine||engine.state!=='running')return;
    const button=e.target instanceof Element?e.target.closest(buttons):null;
    if(!button||button.closest('[data-no-hover-sound], [inert], [aria-disabled="true"]'))return;
    if(e.relatedTarget instanceof Node&&button.contains(e.relatedTarget))return;
    if(button.matches(languages)){load('numbers');return;}
    play(buffers.get('mouse'));
  },{passive:true});
})();
