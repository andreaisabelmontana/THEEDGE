(function () {
  'use strict';
  var frame = document.querySelector('[data-robo-planner]');
  var data = document.getElementById('case-copy');
  var copy = {};
  try { copy = JSON.parse(data.textContent); } catch (_) { /* English remains useful without this enhancement. */ }
  var titles = {
    en: 'ROBOPRENEUR deployment planner',
    es: 'Planificador de despliegue ROBOPRENEUR',
    de: 'ROBOPRENEUR Einsatzplaner'
  };
  function renderLanguage(language) {
    var lang = Object.prototype.hasOwnProperty.call(titles, language) ? language : 'en';
    if (frame) {
      var address = new URL(frame.getAttribute('src'), document.baseURI);
      frame.title = titles[lang];
      if (address.searchParams.get('lang') !== lang) {
        address.searchParams.set('lang', lang);
        frame.src = address.href;
      }
    }
    document.querySelectorAll('[data-robo-alt]').forEach(function (element) {
      var key = element.getAttribute('data-robo-alt');
      var value = (copy[lang] || copy.en || {})[key];
      if (value) element.alt = value;
    });
  }
  document.addEventListener('edge:lang', function (event) { renderLanguage(event.detail); });
  renderLanguage(document.documentElement.lang || 'en');
  window.addEventListener('message', function (event) {
    if (!frame || event.origin !== window.location.origin || event.source !== frame.contentWindow) return;
    var message = event.data;
    if (!message || message.type !== 'robopreneur-planner-height') return;
    var height = Number(message.height);
    if (Number.isFinite(height) && height >= 300 && height <= 5000) {
      frame.style.height = Math.ceil(height) + 'px';
    }
  });
})();
