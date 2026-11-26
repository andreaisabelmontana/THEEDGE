(() => {
  const sky = document.querySelector('.home-showcase > .home-sky');
  const video = sky?.querySelector('video');
  if (!video) return;
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  let visible = !('IntersectionObserver' in window);
  function update() {
    if (!visible || motion.matches || document.hidden) {
      video.pause();
      return;
    }
    if (!video.querySelector('source[src]')) {
      for (const source of video.querySelectorAll('source')) source.src = source.dataset.src;
      video.load();
    }
    video.muted = true;
    video.play().catch(() => {});
  }
  video.addEventListener('playing', () => sky.classList.add('has-film'));
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      update();
    }).observe(sky);
  }
  document.addEventListener('visibilitychange', update);
  motion.addEventListener?.('change', update);
  update();
})();
