(() => {
  const header=document.querySelector('.project-un-header');
  const earth=header?.querySelector('.project-un-earth');
  const image=earth?.querySelector('img');
  const copy=[...header?.querySelectorAll('.project-intro h1,.project-intro p') ?? []];
  if (!earth || !image || matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  // UN opening: Earth rise, then staggered title and copy, then scroll-driven zoom.
  if (scrollY<innerHeight*.4 && !location.hash && earth.animate) {
    earth.animate(
      [{transform:'translateY(100vh) scale(1.1)'},{transform:'translateY(0) scale(1)'}],
      {duration:2000,easing:'cubic-bezier(.65,0,.35,1)'}
    );
    copy.forEach((item,n)=>item.animate(
      [{transform:'translateY(70px)',opacity:0},{transform:'translateY(0)',opacity:1}],
      {duration:1500,delay:1120+100*n,fill:'backwards',easing:'cubic-bezier(.22,1,.36,1)'}
    ));
  }
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
