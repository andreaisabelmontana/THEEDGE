/* Native dragging and scroll snap work even if this enhancement cannot load. */
(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)');
  document.querySelectorAll('[data-media-carousel]').forEach(carousel => {
    if (carousel.dataset.carouselReady) return;
    const track = carousel.querySelector('.am-media-carousel__track');
    const slides = [...track.children];
    const controls = carousel.querySelector('.am-media-carousel__controls');
    const previous = controls.querySelector('[data-media-prev]');
    const next = controls.querySelector('[data-media-next]');
    const play = controls.querySelector('[data-media-play]');
    const count = controls.querySelector('[data-media-current]');
    const videos = slides.map(slide => slide.querySelector('video'));
    let index = 0, rotation = !reduce.matches, visible = false, hovering = false, interacting = false;
    let timer = 0, frame = 0, destination = null;
    carousel.dataset.carouselReady = 'true';
    controls.hidden = false;
    track.setAttribute('data-lenis-prevent-touch', '');

    function cancel() { clearTimeout(timer); timer = 0; }
    function updateButton() {
      play.hidden = reduce.matches;
      play.textContent = rotation ? 'Pause' : 'Play';
      play.setAttribute('aria-label', (rotation ? 'Pause ' : 'Play ') + play.dataset.title + ' slideshow');
    }
    function setRotation(enabled) { rotation = enabled && !reduce.matches; updateButton(); schedule(); }
    function schedule() {
      cancel();
      if (!rotation || hovering || interacting || !visible || document.hidden || videos[index]) return;
      timer = setTimeout(() => step(1), 2500);
    }
    function step(delta) {
      if (!track.clientWidth) return;
      const target = (index + delta + slides.length) % slides.length;
      if (videos[index] && !videos[index].paused) videos[index].pause();
      const wrap = Math.abs(target - index) > 1;
      index = target;
      count.textContent = String(index + 1);
      destination = index;
      track.scrollTo({left:index * track.clientWidth,behavior:(reduce.matches || wrap) ? 'auto' : 'smooth'});
      if (videos[index]) setRotation(false);
      else schedule();
    }
    function byUser(delta) { setRotation(false); step(delta); }
    previous.addEventListener('click', () => byUser(-1));
    next.addEventListener('click', () => byUser(1));
    play.addEventListener('click', () => {
      if (rotation) { setRotation(false); return; }
      if (videos[index]) step(1);
      setRotation(true);
    });
    track.addEventListener('keydown', event => {
      if (event.target !== track || !['ArrowLeft','ArrowRight'].includes(event.key)) return;
      event.preventDefault(); byUser(event.key === 'ArrowLeft' ? -1 : 1);
    });
    function beginManualScroll() { destination = null; setRotation(false); }
    track.addEventListener('pointerdown', beginManualScroll, {passive:true});
    track.addEventListener('touchstart', beginManualScroll, {passive:true});
    track.addEventListener('wheel', beginManualScroll, {passive:true});
    track.addEventListener('scroll', () => {
      if (frame) cancelAnimationFrame(frame);
      frame = requestAnimationFrame(() => {
        frame = 0;
        if (!track.clientWidth) return;
        const active = Math.max(0,Math.min(slides.length - 1,Math.round(track.scrollLeft / track.clientWidth)));
        if (destination !== null) {
          if (active !== destination) return;
          destination = null;
        }
        if (active === index) return;
        if (videos[index] && !videos[index].paused) videos[index].pause();
        index = active; count.textContent = String(index + 1);
        if (videos[index]) setRotation(false);
      });
    },{passive:true});
    carousel.addEventListener('pointerenter', event => { if(event.pointerType!=='touch'){hovering=true;schedule();} });
    carousel.addEventListener('pointerleave', () => { hovering=false;schedule(); });
    carousel.addEventListener('focusin', () => { interacting=true;schedule(); });
    carousel.addEventListener('focusout', event => { if(!carousel.contains(event.relatedTarget)){interacting=false;schedule();} });
    videos.filter(Boolean).forEach(video => video.addEventListener('play', () => setRotation(false)));
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(([entry]) => { visible=entry.intersectionRatio>=.35;schedule(); },{threshold:[0,.35]}).observe(carousel);
    }
    document.addEventListener('visibilitychange', schedule);
    reduce.addEventListener?.('change', () => setRotation(false));
    updateButton();
  });
})();
