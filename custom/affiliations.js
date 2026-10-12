/* The markup is readable without JavaScript. Only the optional motion control
   and failed-image text fallback need a handler. No affiliation is inferred here. */
(function () {
  'use strict';
  function boot() {
    document.querySelectorAll('.am-org-marquee').forEach(function (marquee) {
      marquee.setAttribute('data-motion-ready', 'true');
      var button = marquee.querySelector('.am-org-motion');
      if (button) {
        button.hidden = false;
        button.addEventListener('click', function () {
          var paused = marquee.getAttribute('data-paused') !== 'true';
          marquee.setAttribute('data-paused', String(paused));
          button.setAttribute('aria-pressed', String(paused));
          button.setAttribute('aria-label', paused ? 'Resume organization logos' : 'Pause organization logos');
          button.textContent = paused ? 'Play' : 'Pause';
        });
      }
      marquee.querySelectorAll('img').forEach(function (img) {
        function fallback() {
          var item = img.closest('.am-org-lockup');
          if (!item || item.classList.contains('am-org-lockup--cyphy')) { img.hidden = true; return; }
          var text = document.createElement('span');
          text.textContent = img.alt;
          text.style.cssText = 'font:600 16px/1.2 Arial,sans-serif;color:' + (item.classList.contains('am-org-lockup--cng') ? '#fff' : '#234c83');
          item.replaceChildren(text);
        }
        img.addEventListener('error', fallback, { once: true });
        img.loading = 'eager';
        if (img.complete && !img.naturalWidth) fallback();
      });
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
