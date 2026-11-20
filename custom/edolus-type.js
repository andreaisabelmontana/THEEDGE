/* Independent CSS/JS re-creation; no EDOLUS code, graphics, or audio. */
(() => {
  'use strict';
  const words = [...document.querySelectorAll('.edge-orbital-name [data-orbit-word]')];
  const title = document.querySelector('[data-edolus-title], .edge-core-title');
  const copy = document.querySelector('[data-edolus-copy], .edge-core-copy');
  if (!(words.length || (title && copy))) return;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const original = words.length ? words : [title, copy];
  const alpha = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789#%&/()=+*<>?';
  let raf = 0, finished = false, homePoll = 0, visibleSince = -1;
  const signature = words[0]?.closest('.edge-orbital-name') || null;
  const upper = node => Array.from(node.textContent.trim().toLocaleUpperCase(document.documentElement.lang || 'en'));
  const clear = () => original.forEach(node => {
    node.removeAttribute('data-edolus-frame');
    node.style.removeProperty('--edge-edolus-opacity');
  });
  const stop = () => {
    if (raf) cancelAnimationFrame(raf);
    raf = 0;
    clear();
    if (homePoll) clearTimeout(homePoll);
    homePoll = 0;
    visibleSince = -1;
  };
  function frameFor(node, text) { node.setAttribute('data-edolus-frame', text); }
  function start() {
    if (finished || reduced.matches || document.hidden) return;
    finished = true;
    original.forEach(n => n.style.setProperty('--edge-edolus-ink', getComputedStyle(n).color));
    const t0 = performance.now();
    if (words.length) {
      if (signature) signature.dataset.orbitState = 'playing';
      const letters = words.map(upper);
      const timing = letters.map(line => line.map(() => .25 + Math.random() * 1.24));
      let prev = -1;
      words.forEach(node => frameFor(node, ''));
      function scramble(t) {
        const elapsed = (t - t0) / 1000;
        if (elapsed >= 1.50) {
          if (signature) signature.dataset.orbitState = 'complete';
          return stop();
        }
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
    const bodyText = ['>', '>', ' ', ...Array.from(copy.textContent.trim())];
    const headingDuration = headingText.length * 43;
    const bodyStart = headingDuration + 160;
    frameFor(title, '_');
    frameFor(copy, '');
    function type(t) {
      const elapsed = Math.max(0, t - t0);
      const blink = Math.floor(elapsed / 250) % 2 === 0;
      if (elapsed < bodyStart) {
        const n = Math.min(headingText.length, Math.floor(elapsed / 43));
        frameFor(title, headingText.slice(0, n).join('') + (n < headingText.length || blink ? '_' : ''));
      } else {
        title.removeAttribute('data-edolus-frame');
        const n = Math.min(bodyText.length, Math.floor((elapsed - bodyStart) / 23));
        frameFor(copy, bodyText.slice(0, n).join('') + (n < bodyText.length || blink ? '_' : ''));
        if (elapsed >= bodyStart + bodyText.length * 23 + 270) return stop();
      }
      raf = requestAnimationFrame(type);
    }
    raf = requestAnimationFrame(type);
  }
  function readyForEntrance() {
    const r = (words[0] || title).getBoundingClientRect();
    if ((words.length && !document.body.classList.contains('edge-ready')) ||
        r.width <= 0 || r.height <= 0 || r.bottom < 0 || r.top > innerHeight) return false;
    const loader = document.getElementById('am-loader');
    const curtain = document.querySelector('.transition-w');
    const isCovering = node => {
      if (!node) return false;
      const css = getComputedStyle(node);
      return css.visibility !== 'hidden' && css.display !== 'none' && Number(css.opacity) > .05;
    };
    if (isCovering(loader) || isCovering(curtain)) return false;
    for(let node=(signature || title); node && node!==document.body; node=node.parentElement) {
      const css = getComputedStyle(node);
      if (css.display === 'none' || css.visibility !== 'visible' || Number(css.opacity) < .94) return false;
    }
    return true;
  }
  function waitForEntrance() {
    homePoll = 0;
    if (finished || reduced.matches || document.hidden) return;
    const now = performance.now();
    if (!readyForEntrance()) visibleSince = -1;
    else if (visibleSince < 0) visibleSince = now;
    else if (now - visibleSince >= 210) { start(); return; }
    homePoll = setTimeout(waitForEntrance, 80);
  }
  function watch() {
    if (finished || reduced.matches || document.hidden) return;
    if (!homePoll) waitForEntrance();
  }
  document.addEventListener('edge:lang', () => {
    if (words.length) return;
    stop();
    if (!finished) watch();
  });
  document.addEventListener('visibilitychange', () => {
    if (!document.hidden) { watch(); return; }
    if (signature?.dataset.orbitState === 'playing') {
      delete signature.dataset.orbitState;
      finished = false;
    }
    stop();
  });
  reduced.addEventListener?.('change', () => { if (reduced.matches) stop(); else watch(); });
  window.addEventListener('pagehide', stop);
  window.addEventListener('pageshow', event => { if (event.persisted) { finished = false; watch(); } });
  watch();
})();
