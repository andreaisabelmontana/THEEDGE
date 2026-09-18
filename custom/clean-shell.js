/* Small, progressive navigation layer. The existing scene/menu/language
   scripts keep ownership of their state and handlers. */
(function () {
  'use strict';
  document.body.classList.add('clean-portfolio');

  const words = {
    en: ['Projects', 'Experience', 'Contact', 'Main navigation', 'Language', 'Andrea Montaña · Home'],
    es: ['Proyectos', 'Experiencia', 'Contacto', 'Navegación principal', 'Idioma', 'Andrea Montaña · Inicio'],
    de: ['Projekte', 'Erfahrung', 'Kontakt', 'Hauptnavigation', 'Sprache', 'Andrea Montaña · Startseite'],
  };
  const languageNames = { en: 'English', es: 'Español', de: 'Deutsch' };
  let surfaceFrame = 0;

  function updateSurface() {
    surfaceFrame = 0;
    const nav = document.querySelector('.nav');
    const boundary = nav ? nav.getBoundingClientRect().bottom : 76;
    let surface = 'dark';
    document.querySelectorAll('[data-surface]').forEach(function (section) {
      const rect = section.getBoundingClientRect();
      if (rect.height > 0 && rect.top <= boundary && rect.bottom > boundary) {
        surface = section.getAttribute('data-surface') === 'light' ? 'light' : 'dark';
      }
    });
    if (document.body.getAttribute('data-active-surface') !== surface) {
      document.body.setAttribute('data-active-surface', surface);
    }
  }

  function scheduleSurface() {
    if (!surfaceFrame) surfaceFrame = window.requestAnimationFrame(updateSurface);
  }

  function language() {
    return words[document.documentElement.lang] ? document.documentElement.lang : 'en';
  }

  function translate(lang) {
    const text = words[lang] || words.en;
    const switcher = document.querySelector('[data-lang-switch]');
    if (switcher) {
      switcher.setAttribute('aria-label', text[4]);
      switcher.querySelectorAll('button[data-lang]').forEach(function (button) {
        const code = button.getAttribute('data-lang');
        if (languageNames[code]) {
          button.setAttribute('aria-label', languageNames[code]);
          button.setAttribute('lang', code);
        }
      });
    }
    const home = document.querySelector('.nav-brand-link');
    if (home) home.setAttribute('aria-label', text[5]);
  }

  function boot() {
    const inner = document.querySelector('.nav .nav-inner');
    if (!inner) return;
    // Keep navigation in the existing menu, separate from language controls.
    inner.querySelectorAll('.clean-nav-links').forEach(function (links) { links.remove(); });
    translate(language());
    updateSurface();
  }

  document.addEventListener('edge:lang', function (event) { translate(event.detail); });
  document.addEventListener('keydown', function (event) {
    if (event.key !== 'Escape' || event.defaultPrevented) return;
    const menuButton = document.querySelector('.nav .nav-ham[aria-expanded="true"]');
    if (!menuButton) return;
    event.preventDefault();
    menuButton.click();
    menuButton.focus({ preventScroll: true });
  });
  window.addEventListener('scroll', scheduleSurface, { passive: true });
  window.addEventListener('resize', scheduleSurface);
  window.addEventListener('load', scheduleSurface);
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
