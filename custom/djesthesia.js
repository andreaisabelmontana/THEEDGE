/* Accessible, progressively enhanced viewer for the supplied project diagrams. */
(function () {
  'use strict';
  var data = document.getElementById('case-copy');
  var dialog = document.getElementById('dj-diagram-dialog');
  if (!data || !dialog) return;
  var content;
  try { content = JSON.parse(data.textContent); } catch (_) { return; }
  var links = Array.from(document.querySelectorAll('[data-dj-diagram]'));
  var image = dialog.querySelector('.dj-dialog-image');
  var stage = dialog.querySelector('.dj-dialog-stage');
  var title = document.getElementById('dj-dialog-title');
  var caption = document.getElementById('dj-dialog-caption');
  var close = dialog.querySelector('[data-dj-close]');
  var zoom = dialog.querySelector('[data-dj-zoom]');
  var original = dialog.querySelector('[data-dj-original]');
  var opener = null;
  var active = null;

  function copy() {
    return Object.assign({}, content.en, content[document.documentElement.lang] || {});
  }
  function render() {
    var text = copy();
    links.forEach(function (link) {
      var key = link.getAttribute('data-dj-diagram');
      link.setAttribute('aria-label', text.diagramOpenLabel.replace('{title}', text[key + 'Title']));
      link.querySelector('img').alt = text[key + 'Alt'];
    });
    close.setAttribute('aria-label', text.diagramClose);
    stage.setAttribute('aria-label', text.diagramStage);
    zoom.textContent = stage.classList.contains('is-zoomed') ? text.diagramFit : text.diagramZoom;
    zoom.setAttribute('aria-pressed', String(stage.classList.contains('is-zoomed')));
    original.querySelector('span').textContent = text.diagramOriginal;
    if (!active) return;
    title.textContent = text[active + 'Title'];
    caption.textContent = text[active + 'Caption'];
    image.alt = text[active + 'Alt'];
  }

  links.forEach(function (link) {
    link.addEventListener('click', function (event) {
      if (typeof dialog.showModal !== 'function' || event.ctrlKey || event.metaKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      opener = link;
      active = link.getAttribute('data-dj-diagram');
      image.src = link.href;
      original.href = link.href;
      stage.classList.remove('is-zoomed');
      stage.scrollTop = stage.scrollLeft = 0;
      render();
      dialog.showModal();
      document.body.classList.add('dj-viewer-open');
      close.focus();
    });
  });
  close.addEventListener('click', function () { dialog.close(); });
  zoom.addEventListener('click', function () {
    stage.classList.toggle('is-zoomed');
    stage.scrollTop = stage.scrollLeft = 0;
    render();
  });
  dialog.addEventListener('keydown', function (event) {
    if (event.key !== 'Tab') return;
    if (event.shiftKey && document.activeElement === close) {
      event.preventDefault();
      original.focus();
    } else if (!event.shiftKey && document.activeElement === original) {
      event.preventDefault();
      close.focus();
    }
  });
  dialog.addEventListener('click', function (event) {
    if (event.target !== dialog) return;
    var rect = dialog.getBoundingClientRect();
    if (event.clientX < rect.left || event.clientX > rect.right || event.clientY < rect.top || event.clientY > rect.bottom) dialog.close();
  });
  dialog.addEventListener('close', function () {
    document.body.classList.remove('dj-viewer-open');
    if (opener && document.contains(opener)) opener.focus();
  });
  document.addEventListener('edge:lang', render);
  render();
})();
