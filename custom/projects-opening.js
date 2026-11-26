(() => {
  const header=document.querySelector('.project-un-header');
  const earth=header?.querySelector('.project-un-earth');
  const image=earth?.querySelector('img');
  if (!image || matchMedia('(prefers-reduced-motion: reduce)').matches) return;

  let frame=0;
  function update() {
    frame=0;
    const progress=Math.max(0,Math.min(1,-header.getBoundingClientRect().top/header.offsetHeight));
    image.style.setProperty('--un-scroll-y',(-progress*200)+'px');
    image.style.setProperty('--un-scroll-scale',1+progress*.5);
  }
  addEventListener('scroll',()=>{if(!frame)frame=requestAnimationFrame(update);},{passive:true});
  update();
})();
