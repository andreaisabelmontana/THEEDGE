/* The markup is readable without JavaScript. CSS handles accessible motion;
   this removes the legacy control and provides failed-image text fallbacks. */
(function () {
  'use strict';
  var organizationSites = {
    "ie": [
      "https://www.ie.edu/university/",
      "IE University"
    ],
    "cyphy": [
      "https://cyphy.life/",
      "CyPhy Life"
    ],
    "top": [
      "https://topliving.com.co/",
      "Top Living Inmobiliaria"
    ],
    "trinity": [
      "https://www.tcd.ie/",
      "Trinity College Dublin"
    ],
    "cng": [
      "https://www.cng.edu/",
      "Colegio Nueva Granada"
    ]
  };
  function boot() {
    document.querySelectorAll('.is-footer a[data-site-art-link]').forEach(function (link) { link.remove(); });
    document.querySelectorAll('.am-org-marquee').forEach(function (marquee) {
      marquee.setAttribute('data-motion-ready', 'true');
      Object.keys(organizationSites).forEach(function (key) {
        marquee.querySelectorAll('div.am-org-lockup--' + key).forEach(function (item) {
          var link = document.createElement('a');
          var site = organizationSites[key];
          link.className = item.className;
          link.href = site[0];
          link.target = '_blank';
          link.rel = 'noopener noreferrer';
          link.setAttribute('aria-label', site[1]);
          link.setAttribute('data-taxi-ignore', '');
          if (item.closest('[aria-hidden="true"]')) link.tabIndex = -1;
          while (item.firstChild) link.appendChild(item.firstChild);
          item.replaceWith(link);
        });
      });
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
