/* Snap positions are native; enhancement adds rotation, focus pause and dragging. */
(() => {
  const reduce = matchMedia('(prefers-reduced-motion: reduce)');
  document.querySelectorAll('[data-media-carousel]').forEach(carousel => {
    if (carousel.dataset.carouselReady) return;
    const track = carousel.querySelector('.am-media-carousel__track');
    const slides = [...track.children];
    const play = carousel.querySelector('[data-media-play]');
    const videos = slides.map(slide => slide.querySelector('video'));
    let index = 0, destination = null, rotation = !reduce.matches, visible = false;
    let hovering = false, interacting = false, timer = 0, idle = 0, manual = false, drag = null;
    carousel.dataset.carouselReady = 'true';
    track.setAttribute('data-lenis-prevent-touch', '');

    function updateButton() {
      play.hidden = reduce.matches;
      play.textContent = rotation ? 'Pause' : 'Play';
      play.setAttribute('aria-label', (rotation ? 'Pause ' : 'Play ') + play.dataset.title + ' slideshow');
    }
    function schedule() {
      clearTimeout(timer);
      if (!rotation || hovering || interacting || manual || drag || !visible || document.hidden || videos[index]?.paused === false) return;
      timer = setTimeout(() => go(index + 1), videos[index] ? 6500 : 2500);
    }
    function nearest() {
      return Math.max(0, Math.min(slides.length - 1, Math.round(track.scrollLeft / (track.clientWidth || 1))));
    }
    function setIndex(target) {
      if (target !== index && videos[index]?.paused === false) videos[index].pause();
      index = target;
    }
    function go(i) {
      if (!track.clientWidth) return;
      const target = (i + slides.length) % slides.length;
      const wrap = Math.abs(target - index) > 1;
      setIndex(target);
      destination = target;
      track.scrollTo({left:index * track.clientWidth,behavior:(reduce.matches || wrap) ? 'auto' : 'smooth'});
      schedule();
    }
    function holdManual() {
      destination = null;
      manual = true;
      clearTimeout(idle);
      idle = setTimeout(() => { manual = false;schedule(); }, 3800);
      schedule();
    }
    [['prev', -1], ['next', 1]].forEach(([name, offset]) => {
      const control = carousel.querySelector('[data-media-' + name + ']');
      if (!control) return;
      control.hidden = false;
      control.addEventListener('click', () => { holdManual(); go(index + offset); });
    });
    play.addEventListener('click', () => { rotation = !rotation && !reduce.matches;updateButton();schedule(); });
    track.addEventListener('keydown', event => {
      if (event.target !== track || !['ArrowLeft','ArrowRight'].includes(event.key)) return;
      event.preventDefault();holdManual();go(index + (event.key === 'ArrowLeft' ? -1 : 1));
    });
    track.addEventListener('touchstart', holdManual, {passive:true});
    track.addEventListener('wheel', holdManual, {passive:true});
    track.addEventListener('scroll', () => {
      if (!track.clientWidth) return;
      const active = nearest();
      if (destination !== null) {
        if (active !== destination) return;
        destination = null;
      }
      if (active !== index) { setIndex(active);holdManual(); }
    }, {passive:true});

    track.addEventListener('pointerdown', event => {
      if (event.pointerType !== 'mouse' || event.button !== 0 || event.target.closest('video,button,a')) return;
      drag = {x:event.clientX,left:track.scrollLeft,pointer:event.pointerId,moved:false};
      holdManual();
    });
    track.addEventListener('pointermove', event => {
      if (!drag || drag.pointer !== event.pointerId) return;
      const dx = event.clientX - drag.x;
      if (!drag.moved && Math.abs(dx) < 6) return;
      if (!drag.moved) {
        drag.moved = true;
        track.setPointerCapture(event.pointerId);
        track.setAttribute('data-media-dragging', '');
      }
      track.scrollLeft = drag.left - dx;
    });
    function finishDrag(event) {
      if (!drag || drag.pointer !== event.pointerId) return;
      const {moved} = drag;
      drag = null;
      track.removeAttribute('data-media-dragging');
      holdManual();
      if (moved) go(nearest());
    }
    track.addEventListener('pointerup', finishDrag);
    track.addEventListener('pointercancel', finishDrag);
    track.addEventListener('pointerleave', event => { if (drag && !drag.moved) finishDrag(event); });
    track.addEventListener('dragstart', event => { if (event.target.closest('img')) event.preventDefault(); });
    carousel.addEventListener('pointerenter', event => { if(event.pointerType!=='touch'){hovering=true;schedule();} });
    carousel.addEventListener('pointerleave', () => { hovering=false;schedule(); });
    carousel.addEventListener('focusin', () => { interacting=true;schedule(); });
    carousel.addEventListener('focusout', event => { if(!carousel.contains(event.relatedTarget)){interacting=false;schedule();} });
    videos.filter(Boolean).forEach(video => ['play','pause','ended'].forEach(event => video.addEventListener(event,schedule)));
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(([entry]) => { visible=entry.intersectionRatio>=.35;schedule(); },{threshold:[0,.35]}).observe(carousel);
    }
    document.addEventListener('visibilitychange', schedule);
    reduce.addEventListener?.('change', () => { rotation = !reduce.matches && rotation;updateButton();schedule(); });
    updateButton();
  });
})();
