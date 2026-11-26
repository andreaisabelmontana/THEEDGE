(() => {
  const sky = document.querySelector('.home-showcase > .home-sky');
  const video = sky?.querySelector('video');
  const button = document.querySelector('[data-home-sky-toggle]');
  if (!video || !button) return;
  const motion = matchMedia('(prefers-reduced-motion: reduce)');
  const labels = {
    en: ['Pause stars', 'Play stars'],
    es: ['Pausar estrellas', 'Animar estrellas'],
    de: ['Sterne anhalten', 'Sterne abspielen'],
  };
  let visible = !('IntersectionObserver' in window);
  let enabled = !motion.matches;
  function label() {
    const lang = document.documentElement.lang.split('-')[0];
    button.textContent = (labels[lang] || labels.en)[enabled ? 0 : 1];
  }
  function update() {
    label();
    if (!visible || !enabled || document.hidden) {
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
  video.addEventListener('playing', () => {
    sky.classList.add('has-film');
    button.hidden = false;
  });
  button.addEventListener('click', () => { enabled = !enabled; update(); });
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      update();
    }).observe(sky);
  }
  document.addEventListener('visibilitychange', update);
  document.addEventListener('edge:lang', label);
  motion.addEventListener?.('change', () => { enabled = !motion.matches; update(); });
  update();
})();
