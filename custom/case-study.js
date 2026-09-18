/* Case narratives are local to these static pages. Keep English useful without JS. */
(function () {
  'use strict';
  const node = document.getElementById('case-copy');
  if (!node) return;
  let content;
  try { content = JSON.parse(node.textContent); } catch (_) { return; }
  const labels = {
    en: { skip: 'Skip to project', back: 'All projects', focus: 'Focus', tools: 'Tools', role: 'My contribution', action: 'See it in action', video: 'Recorded demo', preview: 'Project preview', live: 'Launch demo', source: 'View source', youtube: 'Watch on YouTube', challenge: 'The challenge', idea: 'The idea', build: 'The build', outcome: 'What came out of it', previous: 'Previous project', next: 'Next project', all: 'All projects', projects: 'Projects', experience: 'Experience', contact: 'Contact', menu: 'Menu', home: 'Andrea Montaña · Home', detail: 'Implementation notes', sourceIntro: 'More technical detail in the project repository.', videoNote: 'A recorded look at the concept. Open on YouTube for playback options.', artNote: 'Project artwork. Open the demo to explore the interface.', screenshotNote: 'A view of the working project. Open the demo to explore it.' },
    es: { skip: 'Ir al proyecto', back: 'Todos los proyectos', focus: 'Enfoque', tools: 'Herramientas', role: 'Mi contribución', action: 'Míralo en acción', video: 'Demo grabada', preview: 'Vista del proyecto', live: 'Abrir demo', source: 'Ver código', youtube: 'Ver en YouTube', challenge: 'El reto', idea: 'La idea', build: 'El desarrollo', outcome: 'El resultado', previous: 'Proyecto anterior', next: 'Siguiente proyecto', all: 'Todos los proyectos', projects: 'Proyectos', experience: 'Experiencia', contact: 'Contacto', menu: 'Menú', home: 'Andrea Montaña · Inicio', detail: 'Detalles de implementación', sourceIntro: 'Más detalles técnicos en el repositorio del proyecto.', videoNote: 'Una muestra grabada del concepto. Ábrela en YouTube para ver las opciones de reproducción.', artNote: 'Imagen del proyecto. Abre la demo para explorar la interfaz.', screenshotNote: 'Una vista del proyecto en funcionamiento. Abre la demo para explorarlo.' },
    de: { skip: 'Zum Projekt springen', back: 'Alle Projekte', focus: 'Schwerpunkt', tools: 'Werkzeuge', role: 'Mein Beitrag', action: 'In Aktion', video: 'Aufgezeichnete Demo', preview: 'Projektvorschau', live: 'Demo öffnen', source: 'Quellcode ansehen', youtube: 'Auf YouTube ansehen', challenge: 'Die Aufgabe', idea: 'Die Idee', build: 'Die Umsetzung', outcome: 'Das Ergebnis', previous: 'Vorheriges Projekt', next: 'Nächstes Projekt', all: 'Alle Projekte', projects: 'Projekte', experience: 'Erfahrung', contact: 'Kontakt', menu: 'Menü', home: 'Andrea Montaña · Startseite', detail: 'Details zur Umsetzung', sourceIntro: 'Weitere technische Details stehen im Projekt-Repository.', videoNote: 'Ein aufgezeichneter Einblick in das Konzept. Wiedergabeoptionen sind auf YouTube verfügbar.', artNote: 'Projektmotiv. Die Demo zeigt die interaktive Oberfläche.', screenshotNote: 'Ein Einblick in das laufende Projekt. Die Demo lädt zum Erkunden ein.' }
  };
  const accessibility = {
    en: {language:'Language', mobile:'Mobile navigation', more:'More projects'},
    es: {language:'Idioma', mobile:'Navegación móvil', more:'Más proyectos'},
    de: {language:'Sprache', mobile:'Mobile Navigation', more:'Weitere Projekte'}
  };
  labels.en.play = 'Play demo';
  labels.es.play = 'Reproducir demo';
  labels.de.play = 'Demo abspielen';
  function render(language) {
    const lang = labels[language] ? language : 'en';
    const copy = Object.assign({}, labels[lang], content.en, content[lang] || {});
    // Per-language prose overrides English; shared interface labels remain independent.
    document.querySelectorAll('[data-case]').forEach(function (element) {
      const key = element.getAttribute('data-case');
      const value = Object.prototype.hasOwnProperty.call(labels[lang], key) ? labels[lang][key] : copy[key];
      if (value !== undefined && element.textContent !== value) element.textContent = value;
    });
    const summary = content[lang] && content[lang].summary || content.en.summary;
    const description = document.querySelector('meta[name="description"]');
    if (description) description.content = summary;
    const home = document.querySelector('.nav-brand-link');
    if (home) home.setAttribute('aria-label', labels[lang].home);
    [['[data-lang-switch]', 'language'], ['.case-mobile-menu nav', 'mobile'], ['.case-pager', 'more']].forEach(function (item) {
      const element = document.querySelector(item[0]);
      if (element) element.setAttribute('aria-label', accessibility[lang][item[1]]);
    });
  }
  document.addEventListener('edge:lang', function (event) { render(event.detail); });
  render(document.documentElement.lang || 'en');
  document.querySelectorAll('[data-case-play]').forEach(function (button) {
    const media = button.closest('[data-video-id]');
    if (!media || !/^[A-Za-z0-9_-]{11}$/.test(media.getAttribute('data-video-id'))) return;
    button.hidden = false;
    button.addEventListener('click', function () {
      const frame = document.createElement('iframe');
      frame.src = 'https://www.youtube-nocookie.com/embed/' + media.getAttribute('data-video-id') + '?autoplay=1';
      frame.title = media.getAttribute('data-video-title');
      frame.referrerPolicy = 'strict-origin-when-cross-origin';
      frame.allow = 'autoplay; accelerometer; encrypted-media; gyroscope; picture-in-picture; fullscreen';
      frame.allowFullscreen = true;
      media.replaceChildren(frame);
      media.classList.add('is-playing');
      frame.focus();
    }, { once: true });
  });
  if ('IntersectionObserver' in window && !window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
    const observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) { entry.target.classList.remove('is-waiting'); observer.unobserve(entry.target); }
      });
    }, { threshold: 0.12 });
    document.querySelectorAll('.case-reveal').forEach(function (element) {
      element.classList.add('is-waiting'); observer.observe(element);
    });
  }
  document.addEventListener('keydown', function (event) {
    if (event.key === 'Escape') {
      const menu = document.querySelector('.case-mobile-menu[open]');
      if (menu) { menu.open = false; menu.querySelector('summary').focus(); }
    }
  });
})();
