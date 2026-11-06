(function () {
  'use strict';
  var catalog = window.EDGE_ART;
  if (!catalog || !Array.isArray(catalog.works)) return;
  var works = catalog.works;
  var cards = Array.from(document.querySelectorAll('.art-work'));
  var filters = Array.from(document.querySelectorAll('[data-collection]'));
  var dialog = document.querySelector('.art-dialog');
  var menu = document.querySelector('.art-menu');
  var controls = document.querySelector('.art-controls');
  var selectedCollection = 'all';
  var currentWork = null;
  var currentVersion = 0;
  var returnFocus = null;
  var oldOverflow = '';
  var closingFromHistory = false;
  var lang = 'en';
  var copy = {
    en: {projects:'Projects',artNav:'Design & Art',experience:'Experience',contact:'Contact',home:'Home',menu:'Menu',skip:'Skip to artwork',eyebrow:'Andrea Montaña / Visual work',designTitle:'Design',artTitle:'& Art',intro:'Drawing, painting and digital worlds alongside my work in computer science and AI. A place for colour, close observation and small stories.',browse:'Browse the artwork ↓',digitalJump:'Digital design ↓',featureCaption:'2026 · One skyline, three versions',featureAction:'Explore the versions ↗',artworkLabel:'01 / Artwork',artworksTitle:'A few ways of seeing.',collectionsNote:'Thematic collections for browsing, rather than fixed series. Open any work to see it closely.',all:'All work',galleryHint:'Drawing, painting & ceramic · 2020–2026',designLabel:'02 / Digital design',designHeading:'When the picture becomes a place.',designIntro:'The same attention to character, colour and composition, carried into interactive work.',ladybugCopy:'A small character and a storybook meadow, brought to life through animation and movement.',almaCopy:'A thoughtful storefront for my family’s Colombian jewellery brand.',designCta:'View the design case study ↗',footerIdentity:'Computer Science & Artificial Intelligence · Art & design',close:'Close ×',versions:'Versions',original:'Open full image ↗',previous:'← Previous work',next:'Next work →',threeVersions:'Three versions',view:'View',work:'work',works:'works',of:'of',collections:'Artwork collections',navigation:'Main navigation',language:'Language'},
    es: {projects:'Proyectos',artNav:'Diseño y arte',experience:'Experiencia',contact:'Contacto',home:'Inicio',menu:'Menú',skip:'Ir a las obras',eyebrow:'Andrea Montaña / Trabajo visual',designTitle:'Diseño',artTitle:'y arte',intro:'Dibujo, pintura y mundos digitales junto a mi trabajo en informática e IA. Un espacio para el color, la observación y las pequeñas historias.',browse:'Explorar las obras ↓',digitalJump:'Diseño digital ↓',featureCaption:'2026 · Un horizonte, tres versiones',featureAction:'Explorar las versiones ↗',artworkLabel:'01 / Obras',artworksTitle:'Distintas formas de mirar.',collectionsNote:'Colecciones temáticas para explorar, no series fijas. Abre cualquier obra para verla de cerca.',all:'Todas las obras',galleryHint:'Dibujo, pintura y cerámica · 2020–2026',designLabel:'02 / Diseño digital',designHeading:'Cuando la imagen se convierte en un lugar.',designIntro:'La misma atención al personaje, el color y la composición, llevada al trabajo interactivo.',ladybugCopy:'Un pequeño personaje y una pradera de cuento, que cobran vida con animación y movimiento.',almaCopy:'Una tienda pensada con cuidado para la marca de joyería colombiana de mi familia.',designCta:'Ver el caso de diseño ↗',footerIdentity:'Informática e inteligencia artificial · Arte y diseño',close:'Cerrar ×',versions:'Versiones',original:'Abrir imagen completa ↗',previous:'← Obra anterior',next:'Siguiente obra →',threeVersions:'Tres versiones',view:'Ver',work:'obra',works:'obras',of:'de',collections:'Colecciones de obras',navigation:'Navegación principal',language:'Idioma'},
    de: {projects:'Projekte',artNav:'Design & Kunst',experience:'Erfahrung',contact:'Kontakt',home:'Startseite',menu:'Menü',skip:'Zu den Kunstwerken',eyebrow:'Andrea Montaña / Visuelle Arbeiten',designTitle:'Design',artTitle:'& Kunst',intro:'Zeichnung, Malerei und digitale Welten neben meiner Arbeit in Informatik und KI. Ein Raum für Farbe, genaues Hinsehen und kleine Geschichten.',browse:'Kunstwerke entdecken ↓',digitalJump:'Digitales Design ↓',featureCaption:'2026 · Eine Skyline, drei Fassungen',featureAction:'Fassungen entdecken ↗',artworkLabel:'01 / Kunstwerke',artworksTitle:'Verschiedene Arten zu sehen.',collectionsNote:'Thematische Sammlungen zum Erkunden, keine festen Serien. Jedes Werk lässt sich einzeln öffnen.',all:'Alle Werke',galleryHint:'Zeichnung, Malerei & Keramik · 2020–2026',designLabel:'02 / Digitales Design',designHeading:'Wenn aus dem Bild ein Ort wird.',designIntro:'Dieselbe Aufmerksamkeit für Figuren, Farbe und Komposition, in interaktive Arbeiten übertragen.',ladybugCopy:'Eine kleine Figur und eine Märchenwiese, durch Animation und Bewegung zum Leben erweckt.',almaCopy:'Ein sorgfältig gestalteter Shop für die kolumbianische Schmuckmarke meiner Familie.',designCta:'Design-Fallstudie ansehen ↗',footerIdentity:'Informatik & künstliche Intelligenz · Kunst & Design',close:'Schließen ×',versions:'Fassungen',original:'Vollständiges Bild öffnen ↗',previous:'← Vorheriges Werk',next:'Nächstes Werk →',threeVersions:'Drei Fassungen',view:'Ansehen:',work:'Werk',works:'Werke',of:'von',collections:'Kunstsammlungen',navigation:'Hauptnavigation',language:'Sprache'}
  };
  var media = {
    en: {'acrylic':'Acrylic','pencil':'Pencil','oil':'Oil','pen':'Pen','oil pastel':'Oil pastel','colour pencil':'Colour pencil','dry pastel':'Dry pastel','pen and highlighter':'Pen & highlighter','watercolour':'Watercolour','alcohol marker':'Alcohol marker','ceramic':'Ceramic'},
    es: {'acrylic':'Acrílico','pencil':'Lápiz','oil':'Óleo','pen':'Tinta','oil pastel':'Pastel al óleo','colour pencil':'Lápiz de color','dry pastel':'Pastel seco','pen and highlighter':'Tinta y resaltador','watercolour':'Acuarela','alcohol marker':'Marcador de alcohol','ceramic':'Cerámica'},
    de: {'acrylic':'Acryl','pencil':'Bleistift','oil':'Öl','pen':'Tinte','oil pastel':'Ölpastell','colour pencil':'Farbstift','dry pastel':'Pastell','pen and highlighter':'Tinte & Textmarker','watercolour':'Aquarell','alcohol marker':'Alkoholmarker','ceramic':'Keramik'}
  };
  function setText(el, text) { if (el && el.textContent !== text) el.textContent = text; }
  function asset(file) { return '../custom/design-art-assets/' + file; }
  function description(work) { return work.description[lang] || work.description.en; }
  function metadata(work) { return String(work.year) + (work.medium ? ' · ' + (media[lang][work.medium] || work.medium) : ''); }
  function visibleWorks() { return works.filter(function (work) { return selectedCollection === 'all' || work.collection === selectedCollection; }); }
  function selectCollection(value) {
    selectedCollection = catalog.collections[value] ? value : 'all';
    cards.forEach(function (card) { card.hidden = selectedCollection !== 'all' && card.dataset.artCollection !== selectedCollection; });
    filters.forEach(function (button) { button.setAttribute('aria-pressed', String(button.dataset.collection === selectedCollection)); });
    var count = visibleWorks().length;
    setText(document.querySelector('.art-count'), count + ' ' + copy[lang][count === 1 ? 'work' : 'works']);
  }
  function updateVersion(index) {
    if (!currentWork) return;
    var versions = currentWork.versions || [{file:currentWork.file,label:null}];
    currentVersion = Math.max(0, Math.min(index, versions.length - 1));
    var version = versions[currentVersion];
    var image = dialog.querySelector('.art-dialog-image');
    image.src = asset(version.file);
    image.alt = description(currentWork) + (version.label ? ' — ' + version.label[lang] : '');
    setText(dialog.querySelector('.art-dialog-meta'), version.label ? String(currentWork.year) + ' · ' + version.label[lang] : metadata(currentWork));
    dialog.querySelector('.art-original').href = asset(version.file);
    dialog.querySelectorAll('[data-version]').forEach(function (button) { button.setAttribute('aria-pressed', String(Number(button.dataset.version) === currentVersion)); });
  }
  function renderDialog() {
    if (!currentWork) return;
    var items = visibleWorks();
    var index = items.indexOf(currentWork);
    setText(dialog.querySelector('#art-dialog-title'), currentWork.title);
    setText(dialog.querySelector('.art-dialog-meta'), metadata(currentWork));
    setText(dialog.querySelector('.art-dialog-description'), description(currentWork));
    setText(dialog.querySelector('.art-dialog-counter'), (index + 1) + ' ' + copy[lang].of + ' ' + items.length);
    var story = dialog.querySelector('.art-dialog-story');
    story.hidden = !currentWork.story;
    setText(story, currentWork.story ? currentWork.story[lang] : '');
    var versionControls = dialog.querySelector('.art-version-controls');
    versionControls.hidden = !currentWork.versions;
    var versionButtons = dialog.querySelector('.art-versions');
    versionButtons.replaceChildren();
    (currentWork.versions || []).forEach(function (version, i) {
      var button = document.createElement('button');
      button.type = 'button'; button.className = 'art-version'; button.dataset.version = String(i);
      button.textContent = version.label[lang]; button.setAttribute('aria-pressed', String(i === currentVersion));
      versionButtons.appendChild(button);
    });
    dialog.querySelector('[data-art-previous]').disabled = index <= 0;
    dialog.querySelector('[data-art-next]').disabled = index >= items.length - 1;
    updateVersion(currentVersion);
  }
  function updateHash(push) {
    var url = location.pathname + location.search + '#work-' + currentWork.id;
    if (push) history.pushState({edgeArt:true}, '', url);
    else history.replaceState(history.state, '', url);
  }
  function openWork(id, trigger, fromHistory) {
    var work = works.find(function (candidate) { return candidate.id === id; });
    if (!work || typeof dialog.showModal !== 'function') return false;
    if (visibleWorks().indexOf(work) < 0) selectCollection('all');
    currentWork = work; currentVersion = 0;
    if (trigger) returnFocus = trigger;
    var wasOpen = dialog.open;
    renderDialog();
    if (!dialog.open) { oldOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; dialog.showModal(); }
    if (!fromHistory) updateHash(!wasOpen);
    return true;
  }
  function browse(direction) {
    var items = visibleWorks(); var index = items.indexOf(currentWork) + direction;
    if (index < 0 || index >= items.length) return;
    currentWork = items[index]; currentVersion = 0; renderDialog(); updateHash();
  }
  function translate(value) {
    lang = copy[value] ? value : 'en'; document.documentElement.lang = lang;
    try { localStorage.setItem('edge-lang', lang); } catch (error) {}
    document.querySelectorAll('[data-art-copy]').forEach(function (el) { var text = copy[lang][el.dataset.artCopy]; if (text) setText(el, text); });
    document.querySelectorAll('[data-lang]').forEach(function (button) { button.setAttribute('aria-pressed', String(button.dataset.lang === lang)); });
    document.querySelector('[data-lang-switch]').setAttribute('aria-label', copy[lang].language);
    document.querySelectorAll('nav[aria-label="Main navigation"],.art-nav-links,.art-menu nav').forEach(function (nav) { nav.setAttribute('aria-label', copy[lang].navigation); });
    controls.setAttribute('aria-label', copy[lang].collections);
    filters.forEach(function (button) { if (catalog.collections[button.dataset.collection]) setText(button, catalog.collections[button.dataset.collection][lang]); });
    cards.forEach(function (card) {
      var work = works.find(function (candidate) { return 'work-' + candidate.id === card.id; });
      setText(card.querySelector('.art-work-meta'), metadata(work)); setText(card.querySelector('.art-work-summary'), description(work));
      card.querySelector('img').alt = description(work);
    });
    document.querySelectorAll('[data-artwork]').forEach(function (link) { var work = works.find(function (candidate) { return candidate.id === link.dataset.artwork; }); if (work) link.setAttribute('aria-label', copy[lang].view + ' ' + work.title); });
    selectCollection(selectedCollection); if (dialog.open) renderDialog();
  }
  controls.hidden = false;
  document.addEventListener('click', function (event) {
    var artwork = event.target.closest('[data-artwork]');
    if (artwork && !event.ctrlKey && !event.metaKey && !event.shiftKey && event.button === 0) { if (openWork(artwork.dataset.artwork, artwork)) event.preventDefault(); return; }
    var filter = event.target.closest('[data-collection]'); if (filter) { selectCollection(filter.dataset.collection); return; }
    var language = event.target.closest('[data-lang]'); if (language) { translate(language.dataset.lang); return; }
    if (menu.open && !menu.contains(event.target)) menu.open = false;
  });
  dialog.querySelector('.art-dialog-close').addEventListener('click', function () { dialog.close(); });
  dialog.addEventListener('close', function () {
    document.body.style.overflow = oldOverflow;
    currentWork = null;
    if (closingFromHistory) closingFromHistory = false;
    else if (history.state && history.state.edgeArt) history.back();
    else history.replaceState(null, '', location.pathname + location.search + '#artworks');
    if (returnFocus && returnFocus.isConnected) returnFocus.focus({preventScroll:true});
  });
  dialog.addEventListener('click', function (event) {
    var version = event.target.closest('[data-version]'); if (version) updateVersion(Number(version.dataset.version));
    if (event.target.closest('[data-art-previous]')) browse(-1);
    if (event.target.closest('[data-art-next]')) browse(1);
    if (event.target === dialog) { var rect = dialog.getBoundingClientRect(); if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close(); }
  });
  document.addEventListener('keydown', function (event) {
    if (dialog.open && event.key === 'Tab') {
      var focusable = Array.from(dialog.querySelectorAll('button:not([disabled]), a[href]')).filter(function (el) { return el.getClientRects().length > 0; });
      var first = focusable[0], last = focusable[focusable.length - 1];
      if (event.shiftKey && (document.activeElement === first || document.activeElement === dialog)) { event.preventDefault(); last.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
      else if (!dialog.contains(document.activeElement)) { event.preventDefault(); first.focus(); }
    }
    if (dialog.open && !event.altKey && !event.ctrlKey && !event.metaKey && (event.key === 'ArrowLeft' || event.key === 'ArrowRight')) { event.preventDefault(); browse(event.key === 'ArrowLeft' ? -1 : 1); }
    if (event.key === 'Escape' && menu.open && !dialog.open) { menu.open = false; menu.querySelector('summary').focus(); }
  });
  function fromHash() {
    var id = location.hash.replace(/^#work-/, '');
    if (location.hash.indexOf('#work-') === 0 && works.some(function (work) { return work.id === id; })) {
      if (!dialog.open || !currentWork || currentWork.id !== id) openWork(id, document.querySelector('.art-card[data-artwork="' + CSS.escape(id) + '"]'), true);
    } else if (dialog.open) { closingFromHistory = true; dialog.close(); }
  }
  window.addEventListener('popstate', fromHash);
  window.addEventListener('hashchange', fromHash);
  var saved = 'en'; try { saved = localStorage.getItem('edge-lang') || 'en'; } catch (error) {}
  translate(saved); fromHash();
})();
