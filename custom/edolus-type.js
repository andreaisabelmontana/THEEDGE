/* Independent CSS/JS re-creation; no EDOLUS code, graphics, or audio. */
(() => {
  'use strict';
  const words = [...document.querySelectorAll('.edge-orbital-name [data-orbit-word]')];
  const title = document.querySelector('.edge-core-title');
  const copy = document.querySelector('.edge-core-copy');
  if (!(words.length || (title && copy))) return;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const original = words.length ? words : [title, copy];
  const alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&/()=+*<>?';
  let raf = 0, finished = false, observer;
  const upper = node => Array.from(node.textContent.trim().toLocaleUpperCase(document.documentElement.lang || 'en'));
  const clear = () => original.forEach(node => {
    node.removeAttribute('data-edolus-frame');
    node.style.removeProperty('--edge-edolus-opacity');
  });
  const stop = () => {
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
    clear();
  };
  function frameFor(node, text) { node.setAttribute('data-edolus-frame', text); }
  function start() {
    if (finished || reduced.matches || document.hidden) return;
    finished = true;
    observer?.disconnect();
    original.forEach(n => n.style.setProperty('--edge-edolus-ink', getComputedStyle(n).color));
    const t0 = performance.now();
    if (words.length) {
      const letters = words.map(upper);
      const timing = letters.map(line => line.map(() => .25 + Math.random() * 1.24));
      let prev = -1;
      words.forEach(node => frameFor(node, ''));
      function scramble(t) {
        const elapsed = (t - t0) / 1000;
        if (elapsed >= 1.50) return stop();
        const step = Math.floor(elapsed * 24);
        words.forEach((node, j) => {
          node.style.setProperty('--edge-edolus-opacity', String(Math.min(1, elapsed / 1.2)));
          if (step !== prev) frameFor(node, letters[j].map((c, i) => elapsed >= timing[j][i] ? c : alpha[Math.floor(Math.random() * alpha.length)]).join(''));
        });
        prev = step;
        raf = requestAnimationFrame(scramble);
      }
      raf = requestAnimationFrame(scramble);
      return;
    }
    const headingText = ['/', '/', ' ', ...upper(title)];
    const bodyText = ['>', '>', ' ', ...upper(copy)];
    const headingDuration = headingText.length * 50;
    const bodyStart = headingDuration + 250;
    frameFor(title, '_');
    frameFor(copy, '');
    function type(t) {
      const elapsed = Math.max(0, t - t0);
      const blink = Math.floor(elapsed / 250) % 2 === 0;
      if (elapsed < bodyStart) {
        const n = Math.min(headingText.length, Math.floor(elapsed / 50));
        frameFor(title, headingText.slice(0, n).join('') + (n < headingText.length || blink ? '_' : ''));
      } else {
        title.removeAttribute('data-edolus-frame');
        const n = Math.min(bodyText.length, Math.floor((elapsed - bodyStart) / 50));
        frameFor(copy, bodyText.slice(0, n).join('') + (n < bodyText.length || blink ? '_' : ''));
        if (elapsed >= bodyStart + bodyText.length * 50 + 500) return stop();
      }
      raf = requestAnimationFrame(type);
    }
    raf = requestAnimationFrame(type);
  }
  function watch() {
    if (finished || reduced.matches || document.hidden) return;
    const visibleNode = words[0] || title;
    if ('IntersectionObserver' in window) {
      observer?.disconnect();
      observer = new IntersectionObserver(entries => {
        if (entries.some(entry => entry.isIntersecting && entry.intersectionRatio > .1)) start();
      }, {threshold: .1});
      observer.observe(visibleNode);
    } else start();
  }
  document.addEventListener('edge:lang', stop);
  document.addEventListener('visibilitychange', () => { if (document.hidden) stop(); else watch(); });
  reduced.addEventListener?.('change', () => { if (reduced.matches) stop(); else watch(); });
  window.addEventListener('pagehide', stop);
  window.addEventListener('pageshow', event => { if (event.persisted) { finished = false; watch(); } });
  watch();
})();
