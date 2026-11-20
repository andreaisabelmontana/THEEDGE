/* The markup is readable without JavaScript. CSS handles accessible motion;
   this removes the legacy control and provides failed-image text fallbacks. */
(function () {
  'use strict';
  var assets = new URL('./organizations/', document.currentScript.src);
  // Add the three distinct Experience affiliations to the shared carousel,
  // using the same artwork and destinations as the Experience records.
  var additions = [
    { key: 'alcaldia', label: 'Alcaldía Mayor de Bogotá', href: 'https://bogota.gov.co/',
      image: 'alcaldia-bogota.jpg', height: 58, width: 118 },
    { key: 'microsoft', label: 'Microsoft Student Ambassadors', title: 'Microsoft',
      href: 'https://mvp.microsoft.com/studentambassadors', image: 'microsoft-symbol.svg',
      height: 30, width: 152 },
    { key: 'gdg', label: 'Google Developer Groups', title: 'Google Developer Groups',
      href: 'https://developers.google.com/community/gdg', image: 'gdg-symbol.svg',
      height: 22, width: 228 }
  ];
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
  function appendAdditionalOrganizations(group) {
    additions.forEach(function (organization) {
      var className = 'am-org-lockup--' + organization.key;
      if (group.querySelector('.' + className)) return;
      var link = document.createElement('a');
      link.className = 'am-org-lockup ' + className;
      link.href = organization.href;
      link.target = '_blank';
      link.rel = 'noopener noreferrer';
      link.setAttribute('aria-label', organization.label);
      link.setAttribute('data-taxi-ignore', '');
      link.style.width = organization.title ? 'max-content' : organization.width + 'px';
      if (organization.title) link.style.minWidth = organization.width + 'px';
      if (group.closest('[aria-hidden="true"]')) link.tabIndex = -1;
      var logo = document.createElement('img');
      logo.src = new URL(organization.image, assets).href;
      logo.alt = organization.label;
      logo.style.height = organization.height + 'px';
      link.appendChild(logo);
      if (organization.title) {
        var title = document.createElement('span');
        title.textContent = organization.title;
        title.style.cssText = 'font:600 15px/1.2 Arial,sans-serif;white-space:nowrap;color:#203047';
        link.appendChild(title);
      }
      group.appendChild(link);
    });
  }
  function routeContactButtons() {
    var contact = new URL('../../contact/', assets);
    document.querySelectorAll(
      '.footer-bg-bottom-btn-w a.btn-w[href^="mailto:"], a[data-nav-link-highlight][href^="mailto:"]'
    ).forEach(function (link) {
      link.href = contact.href;
      link.setAttribute('data-taxi-ignore', '');
    });
  }
  function boot() {
    routeContactButtons();
    document.querySelectorAll('.is-footer a[data-site-art-link]').forEach(function (link) { link.remove(); });
    document.querySelectorAll('.am-org-marquee').forEach(function (marquee) {
      marquee.querySelectorAll('.am-org-group').forEach(appendAdditionalOrganizations);
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
      var viewport = marquee.querySelector('.am-org-viewport');
      var track = marquee.querySelector('.am-org-track');
      if (!viewport || !track || track.children.length !== 2) return;
      var group = track.firstElementChild;
      var repeat = track.lastElementChild;
      function fillTrack() {
        var width = group.getBoundingClientRect().width;
        if (!width) return;
        // The last frame must still cover the whole viewport after moving one group.
        var copies = Math.max(2, Math.ceil(viewport.clientWidth / width) + 1);
        while (track.children.length < copies) track.appendChild(repeat.cloneNode(true));
        while (track.children.length > copies) track.lastElementChild.remove();
        track.style.setProperty('--am-org-shift', -width + 'px');
        marquee.setAttribute('data-motion-ready', 'true');
      }
      fillTrack();
      if (typeof ResizeObserver === 'function') {
        var observer = new ResizeObserver(fillTrack);
        observer.observe(viewport);
        observer.observe(group);
      } else {
        window.addEventListener('resize', fillTrack);
      }
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
