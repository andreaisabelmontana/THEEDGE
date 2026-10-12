/* Leadership cards progressively open a native, addressable detail dialog. */
(function () {
  'use strict';
  const dialog = document.getElementById('leadership-details');
  const cards = Array.from(document.querySelectorAll('[data-leadership-detail]'));
  if (!dialog || !cards.length || typeof dialog.showModal !== 'function') return;

  const content = {
    'campus-life': { photos: ['basketball'] },
    'ie-scitech': { photos: ['lab', 'vr', 'classroom', 'stage', 'datathon'] },
    microsoft: { photos: [], credential: 'Microsoft AI & ML Engineering' },
    gdg: { photos: [], credential: 'Google AI' }
  };
  const words = {
    en: { open: 'View details', close: 'Close', heading: 'Leadership', photos: 'Photos', training: 'Related training', fullImage: 'Open full image', activityTitle: 'Project Contributor' },
    es: { open: 'Ver detalles', close: 'Cerrar', heading: 'Liderazgo', photos: 'Fotos', training: 'Formación relacionada', fullImage: 'Abrir imagen completa', activityTitle: 'Colaboradora en proyectos' },
    de: { open: 'Details ansehen', close: 'Schließen', heading: 'Engagement', photos: 'Fotos', training: 'Verwandte Weiterbildung', fullImage: 'Vollständiges Bild öffnen', activityTitle: 'Projektmitarbeit' }
  };
  const prefix = '#leadership-';
  const stateField = 'edgeLeadershipDetails';
  const title = dialog.querySelector('[data-details-title]');
  const summary = dialog.querySelector('[data-details-summary]');
  const photos = dialog.querySelector('[data-details-photos]');
  const training = dialog.querySelector('[data-details-training]');
  const closeButton = dialog.querySelector('[data-details-close]');
  let language = 'en';
  let activeKey = null;
  let returnFocus = null;
  let scrollLock = null;
  let closing = false;
  let baseURL = '';
  let pendingRestore = null;
  let restoreFrame = 0;

  function cardFor(key) { return cards.find(card => card.dataset.leadershipDetail === key); }
  function buttonFor(key) { return cardFor(key).querySelector('[data-details-open]'); }
  function keyFromURL() {
    const key = location.hash.slice(prefix.length);
    return location.hash.indexOf(prefix) === 0 && Object.prototype.hasOwnProperty.call(content, key) ? key : null;
  }
  function ownedEntry(key) {
    const state = history.state && history.state[stateField];
    return state && state.key === key && typeof state.base === 'string' ? state : null;
  }
  function stateFor(key, base) {
    return Object.assign({}, history.state, { [stateField]: { key: key, base: base } });
  }
  function withoutIDs(node) {
    node.removeAttribute('id');
    node.querySelectorAll('[id]').forEach(child => child.removeAttribute('id'));
    return node;
  }
  function localize(requested) {
    language = Object.prototype.hasOwnProperty.call(words, requested) ? requested : 'en';
    document.querySelectorAll('[data-leadership-detail-copy]').forEach(node => {
      const value = words[language][node.dataset.leadershipDetailCopy];
      if (value) node.textContent = value;
    });
    cards.forEach(card => {
      const label = card.querySelector('h3').textContent.trim() + ' — ' + card.querySelector('.am-career-org').textContent.trim();
      card.querySelector('[data-details-open]').setAttribute('aria-label', words[language].open + ': ' + label);
    });
    dialog.querySelectorAll('[data-details-full-image]').forEach(link => {
      const caption = link.closest('figure').querySelector('h4');
      link.setAttribute('aria-label', words[language].fullImage + ': ' + (caption ? caption.textContent : link.querySelector('img').alt));
    });
    if (activeKey) title.textContent = cardFor(activeKey).querySelector('h3').textContent;
  }
  function renderContent(key) {
    const card = cardFor(key);
    title.textContent = card.querySelector('h3').textContent;
    summary.replaceChildren();
    ['.am-org-row', '.am-career-date', 'p[data-experience-copy$="-copy"]'].forEach(selector => {
      const node = card.querySelector(selector);
      if (node) summary.appendChild(withoutIDs(node.cloneNode(true)));
    });
    photos.replaceChildren();
    content[key].photos.forEach(photoKey => {
      const source = document.querySelector('.am-leadership-grid [data-leadership-photo="' + photoKey + '"]');
      if (!source) return;
      const figure = withoutIDs(source.closest('figure').cloneNode(true));
      figure.className = 'am-leadership-details-photo';
      const link = figure.querySelector('a');
      link.removeAttribute('data-leadership-photo');
      link.removeAttribute('aria-controls');
      link.removeAttribute('aria-haspopup');
      link.setAttribute('data-details-full-image', '');
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      const img = link.querySelector('img');
      img.src = source.href;
      img.removeAttribute('srcset');
      img.removeAttribute('sizes');
      photos.appendChild(figure);
    });
    photos.parentNode.hidden = !photos.children.length;
    training.replaceChildren();
    if (content[key].credential) {
      const credential = Array.from(document.querySelectorAll('#credentials .am-credential-card')).find(node =>
        node.querySelector('h4') && node.querySelector('h4').textContent.trim() === content[key].credential);
      if (credential) {
        const copy = withoutIDs(credential.querySelector('.am-credential-card-body').cloneNode(true));
        training.appendChild(copy);
      }
    }
    training.parentNode.hidden = !training.children.length;
    localize(language);
    dialog.scrollTop = 0;
  }
  function lockScroll() {
    if (scrollLock) return;
    const body = document.body;
    const engine = window.lenis;
    const canPause = engine && typeof engine.stop === 'function' && typeof engine.start === 'function' && typeof engine.scrollTo === 'function';
    const wasStopped = canPause ? !!engine.isStopped : true;
    if (canPause && !wasStopped) engine.stop();
    const properties = ['position', 'top', 'left', 'right', 'width', 'overflow', 'padding-right', 'box-sizing'];
    scrollLock = { x: window.scrollX, y: window.scrollY, styles: {}, restoration: history.scrollRestoration, engine: canPause ? engine : null, wasStopped: wasStopped };
    properties.forEach(property => {
      scrollLock.styles[property] = [body.style.getPropertyValue(property), body.style.getPropertyPriority(property)];
    });
    const gutter = Math.max(0, window.innerWidth - document.documentElement.clientWidth);
    const padding = parseFloat(window.getComputedStyle(body).paddingRight) || 0;
    body.style.position = 'fixed';
    body.style.top = -scrollLock.y + 'px';
    body.style.left = -scrollLock.x + 'px';
    body.style.right = '0';
    body.style.width = '100%';
    body.style.boxSizing = 'border-box';
    body.style.overflow = 'hidden';
    if (gutter) body.style.paddingRight = padding + gutter + 'px';
    document.documentElement.classList.add('am-leadership-details-viewing');
    history.scrollRestoration = 'manual';
  }
  function restorePosition(saved, destination) {
    if (saved.engine) saved.engine.scrollTo(saved.y, { immediate: true, force: true });
    window.scrollTo({ left: saved.x, top: saved.y, behavior: 'instant' });
    if (destination && destination.isConnected) {
      destination.scrollIntoView({ block: 'start', behavior: 'instant' });
      if (saved.engine) saved.engine.scrollTo(window.scrollY, { immediate: true, force: true });
    }
  }
  function finishRestore(restoreFocus) {
    if (restoreFrame) window.cancelAnimationFrame(restoreFrame);
    restoreFrame = 0;
    if (!pendingRestore) return;
    const pending = pendingRestore;
    pendingRestore = null;
    restorePosition(pending.saved, pending.destination);
    history.scrollRestoration = pending.saved.restoration;
    if (restoreFocus && pending.focus && pending.focus.isConnected) pending.focus.focus({ preventScroll: true });
  }
  function unlockScroll() {
    if (!scrollLock) return;
    const saved = scrollLock;
    scrollLock = null;
    Object.keys(saved.styles).forEach(property => {
      const [value, priority] = saved.styles[property];
      if (value) document.body.style.setProperty(property, value, priority);
      else document.body.style.removeProperty(property);
    });
    document.documentElement.classList.remove('am-leadership-details-viewing');
    restorePosition(saved);
    if (saved.engine && !saved.wasStopped) saved.engine.start();
    return saved;
  }
  function closeView(restoreFocus, destination) {
    const focus = returnFocus;
    activeKey = null;
    returnFocus = null;
    closing = false;
    if (dialog.open) dialog.close();
    const saved = unlockScroll();
    if (saved) {
      pendingRestore = { saved: saved, focus: focus, destination: destination };
      // History traversal and native dialog focus restoration complete after popstate.
      if (restoreFocus) restoreFrame = window.requestAnimationFrame(() => {
        restoreFrame = window.requestAnimationFrame(() => finishRestore(true));
      });
      else finishRestore(false);
    }
  }
  function show(key, trigger) {
    if (activeKey === key && dialog.open) return;
    finishRestore(false);
    activeKey = key;
    returnFocus = trigger || buttonFor(key);
    const entry = ownedEntry(key);
    baseURL = entry ? entry.base : location.pathname + location.search + '#leadership';
    renderContent(key);
    if (!dialog.open) {
      lockScroll();
      try { dialog.showModal(); }
      catch (error) { closeView(true); return; }
    }
    closeButton.focus({ preventScroll: true });
  }
  function syncURL() {
    closing = false;
    const key = keyFromURL();
    if (key) show(key);
    else if (activeKey || scrollLock) {
      // Only redirect an unrelated hash while actually closing a detail view.
      let destination;
      if (baseURL && new URL(baseURL, location.href).href !== location.href && location.hash) {
        try { destination = document.getElementById(decodeURIComponent(location.hash.slice(1))); } catch (error) { /* Invalid fragment. */ }
      }
      closeView(true, destination);
      baseURL = '';
    }
  }
  function requestClose() {
    if (!activeKey || closing) return;
    const key = keyFromURL();
    if (key && ownedEntry(key)) {
      closing = true;
      history.back();
    } else {
      if (key) history.replaceState(history.state, '', baseURL);
      closeView(true);
    }
  }

  cards.forEach(card => {
    const button = card.querySelector('[data-details-open]');
    button.hidden = false;
    button.addEventListener('click', () => {
      if (closing) return;
      const key = card.dataset.leadershipDetail;
      if (activeKey === key && dialog.open) return;
      const base = location.href;
      history.pushState(stateFor(key, base), '', prefix + key);
      show(key, button);
    });
  });
  closeButton.addEventListener('click', requestClose);
  dialog.addEventListener('cancel', event => { event.preventDefault(); requestClose(); });
  dialog.addEventListener('keydown', event => {
    if (event.key === 'Escape') {
      event.preventDefault();
      event.stopPropagation();
      requestClose();
    }
    if (event.key !== 'Tab') return;
    const controls = Array.from(dialog.querySelectorAll('button:not([disabled]), a[href], [tabindex="0"]')).filter(node => !node.closest('[hidden]'));
    const first = controls[0], last = controls[controls.length - 1];
    if (!first) { event.preventDefault(); return; }
    if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last.focus(); }
    else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first.focus(); }
  });
  dialog.addEventListener('click', event => {
    if (event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) requestClose();
  });
  dialog.addEventListener('close', () => { if (activeKey && !dialog.open) requestClose(); });
  window.addEventListener('popstate', syncURL);
  window.addEventListener('hashchange', syncURL);
  window.addEventListener('pagehide', () => { closeView(false); finishRestore(false); });
  window.addEventListener('pageshow', syncURL);
  document.addEventListener('edge:lang', event => localize(event.detail));
  localize(document.documentElement.lang);
  const initialKey = keyFromURL();
  if (initialKey && !ownedEntry(initialKey)) {
    const initialURL = location.href;
    const base = location.pathname + location.search + '#leadership';
    history.replaceState(history.state, '', base);
    document.getElementById('leadership').scrollIntoView({ block: 'start', behavior: 'instant' });
    history.pushState(stateFor(initialKey, base), '', initialURL);
  }
  syncURL();
})();
