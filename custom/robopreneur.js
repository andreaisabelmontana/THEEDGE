/* Localized alternative text for the credited lab demonstration. */
(function () {
  'use strict';
  var data = document.getElementById('case-copy');
  if (!data) return;
  var copy;
  try { copy = JSON.parse(data.textContent); } catch (_) { return; }
  function render(language) {
    var text = copy[language] || copy.en;
    document.querySelectorAll('[data-robo-alt]').forEach(function (element) {
      var value = text[element.getAttribute('data-robo-alt')];
      if (value) element.alt = value;
    });
  }
  document.addEventListener('edge:lang', function (event) { render(event.detail); });
  render(document.documentElement.lang || 'en');
})();
