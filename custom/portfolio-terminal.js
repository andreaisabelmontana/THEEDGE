/* Progressive heading treatment. The actual heading text remains readable and localized. */
(() => {
  'use strict';
  const motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  const headline = document.querySelector('.project-intro h1, #career-title');
  const cards = Array.from(document.querySelectorAll('[data-i18n="panel.projects.title"], [data-i18n="panel.experience.title"]'));
  const headings = headline ? [headline] : cards;
  if (!headings.length) return;
  const name = document.querySelector('h1.display.text-impact-reg-brier');
  if (name) name.classList.add('edge-orbital-name');
  headings.forEach(h => h.classList.add('edge-orbital-heading', 'edge-terminal-heading'));
  const details = document.querySelectorAll('.project-intro [data-project-copy="intro"], .am-career-lead');
  details.forEach(el => el.classList.add('edge-terminal-detail'));
  let audio;
  let soundEnabled = false;
  let button;
  const labels = {
    en: ['Typing sound: off', 'Typing sound: on'],
    es: ['Sonido de teclas: apagado', 'Sonido de teclas: encendido'],
    de: ['Tastenton: aus', 'Tastenton: an']
  };
  function refreshSoundButton() {
    if (!button) return;
    button.textContent = (labels[document.documentElement.lang] || labels.en)[Number(soundEnabled)];
    button.setAttribute('aria-pressed', String(soundEnabled));
  }
  function tick(step) {
    if (!audio || !soundEnabled || audio.state !== 'running') return;
    const time = audio.currentTime;
    const wave = audio.createOscillator();
    const volume = audio.createGain();
    wave.type = 'triangle';
    wave.frequency.setValueAtTime(600 + (step % 3) * 44, time);
    wave.frequency.exponentialRampToValueAtTime(170, time + .036);
    volume.gain.setValueAtTime(.023, time);
    volume.gain.exponentialRampToValueAtTime(.001, time + .048);
    wave.connect(volume).connect(audio.destination);
    wave.start(time);
    wave.stop(time + .052);
  }
  const running = new Map();
  function finish(h) {
    const id = running.get(h);
    if (id !== undefined) window.clearTimeout(id);
    running.delete(h);
    h.removeAttribute('data-edge-frame');
  }
  function type(h) {
    finish(h);
    if (motion.matches || document.hidden) return;
    const raw = h.textContent.trim();
    if (!raw || raw.length > 35) return;
    const letters = Array.from(raw.toLocaleUpperCase(document.documentElement.lang || 'en'));
    h.style.setProperty('--edge-terminal-ink', window.getComputedStyle(h).color);
    h.dataset.edgeFrame = '// ';
    let n = 0;
    function step() {
      if (motion.matches || document.hidden || h.textContent.trim() !== raw || n >= letters.length) {
        finish(h);
        return;
      }
      n += 1;
      h.dataset.edgeFrame = '// ' + letters.slice(0, n).join('');
      tick(n);
      running.set(h, window.setTimeout(step, Math.min(52, 580 / letters.length)));
    }
    running.set(h, window.setTimeout(step, 65));
  }
  const Audio = window.AudioContext || window.webkitAudioContext;
  if (headline && Audio) {
    button = document.createElement('button');
    button.type = 'button';
    button.className = 'edge-terminal-sound';
    refreshSoundButton();
    (details[0] || headline).after(button);
    button.addEventListener('click', async () => {
      if (soundEnabled) {
        soundEnabled = false;
        refreshSoundButton();
        return;
      }
      try {
        audio = audio || new Audio();
        await audio.resume();
        soundEnabled = audio.state === 'running';
      } catch (_) { soundEnabled = false; }
      refreshSoundButton();
      if (soundEnabled) type(headline);
    });
  }
  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver(entries => {
      entries.forEach(entry => {
        if (!entry.isIntersecting) return;
        observer.unobserve(entry.target);
        type(entry.target);
      });
    }, { threshold: .34 });
    headings.forEach(h => observer.observe(h));
  } else headings.forEach(type);
  document.addEventListener('edge:lang', () => {
    headings.forEach(finish);
    refreshSoundButton();
  });
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) headings.forEach(finish);
  });
  motion.addEventListener?.('change', () => {
    if (motion.matches) headings.forEach(finish);
  });
})();
