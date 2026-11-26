(() => {
  const section=document.querySelector('#stargazing');
  const movie=section?.querySelector('video');
  const toggle=section?.querySelector('[data-contact-orbit-toggle]');
  if (!movie||!toggle)return;
  const reduce=matchMedia('(prefers-reduced-motion: reduce)');
  const dictionary={en:['Pause animation','Play animation'],es:['Pausar animación','Reproducir animación'],de:['Animation anhalten','Animation abspielen']};
  let visible=!('IntersectionObserver' in window), wanted=!reduce.matches;
  const labels=()=>{const words=dictionary[document.documentElement.lang]||dictionary.en;toggle.textContent=words[movie.paused?1:0];};
  const sync=()=>{
    if(!visible||!wanted||document.hidden){movie.pause();labels();return;}
    movie.muted=true;
    movie.play().catch(labels);
  };
  toggle.hidden=false;
  toggle.addEventListener('click',()=>{wanted=movie.paused;sync();});
  movie.addEventListener('playing',labels);
  movie.addEventListener('pause',labels);
  movie.addEventListener('error',()=>{toggle.hidden=true;});
  if('IntersectionObserver' in window){
    new IntersectionObserver(([entry])=>{visible=entry.isIntersecting;sync();},{threshold:.12}).observe(section);
  }
  document.addEventListener('visibilitychange',sync);
  document.addEventListener('edge:lang',labels);
  reduce.addEventListener?.('change',()=>{wanted=!reduce.matches;sync();});
  labels();
  sync();
})();
