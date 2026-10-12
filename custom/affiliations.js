/* The markup is readable without JavaScript. CSS handles accessible motion;
   this removes the legacy control and provides failed-image text fallbacks. */
(function () {
  'use strict';
  function boot() {
    document.querySelectorAll('.am-org-marquee').forEach(function (marquee) {
      marquee.setAttribute('data-motion-ready', 'true');
      var button = marquee.querySelector('.am-org-motion');
      if (button) button.remove();
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
