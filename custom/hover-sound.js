(() => {
  const AudioEngine=window.AudioContext||window.webkitAudioContext;
  if(!AudioEngine||!('PointerEvent' in window))return;
  const buttons=[
    'button:not([disabled])','input[type="button"]:not([disabled])','input[type="submit"]:not([disabled])',
    '[role="button"]:not([aria-disabled="true"])','a.btn-w','a.btn','a.portfolio-cv-link',
    'a.otot-card','a.project-card','a.project-preview-btn','a.nav-menu-link-w'
  ].join(',');
  let engine,buffer,loading,last=-Infinity,playing;
  // This is the original "mouse" cue assigned to the UN sidebar dots.
  const file='https://projects.elkanodata.com/WIP/unhq-space/audio/mouse.mp3';
  const prepare=async e=>{
    if(!e.isTrusted||document.hidden)return;
    if(!engine)engine=new AudioEngine();
    if(engine.state!=='running')engine.resume().catch(()=>{});
    if(buffer||loading)return;
    loading=fetch(file,{credentials:'omit',referrerPolicy:'no-referrer'})
      .then(r=>{if(!r.ok)throw Error(r.status);return r.arrayBuffer();})
      .then(data=>engine.decodeAudioData(data))
      .then(data=>{buffer=data;})
      .catch(()=>{}).finally(()=>{loading=null;});
  };
  document.addEventListener('pointerdown',prepare,{passive:true,capture:true});
  document.addEventListener('keydown',prepare,{capture:true});
  document.addEventListener('pointerover',e=>{
    if(e.pointerType!=='mouse'||document.hidden||!buffer||engine?.state!=='running')return;
    const button=e.target instanceof Element?e.target.closest(buttons):null;
    if(!button||button.closest('[data-no-hover-sound], [inert], [aria-disabled="true"]'))return;
    if(e.relatedTarget instanceof Node&&button.contains(e.relatedTarget))return;
    const now=engine.currentTime;
    if(now-last<.1)return;
    last=now;
    if(playing){try{playing.stop();}catch{}}
    const cue=engine.createBufferSource(),gain=engine.createGain();
    cue.buffer=buffer;
    gain.gain.value=.15;
    cue.connect(gain);gain.connect(engine.destination);
    cue.onended=()=>{cue.disconnect();gain.disconnect();if(playing===cue)playing=null;};
    playing=cue;
    cue.start();
  },{passive:true});
})();
