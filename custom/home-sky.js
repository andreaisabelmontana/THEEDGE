(() => {
  const sky = document.querySelector('.home-showcase > .home-sky');
  if (!sky) return;
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  if (!('IntersectionObserver' in window)) {
    if (!motion.matches) sky.classList.add('is-visible');
    return;
  }
  let visible = false;
  const render = () => sky.classList.toggle('is-visible', visible && !motion.matches && !document.hidden);
  new IntersectionObserver(([entry]) => {
    visible = entry.isIntersecting;
    render();
  }).observe(sky);
  motion.addEventListener?.('change', render);
  document.addEventListener('visibilitychange', render);
})();
