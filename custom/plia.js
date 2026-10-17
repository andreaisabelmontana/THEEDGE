/* Progressive enhancement only: navigation and disclosure links work without JS. */
(function () {
  'use strict';
  var links = Array.from(document.querySelectorAll('.plia-contents a[href^="#"]'));
  var sections = links.map(function (link) { return document.querySelector(link.getAttribute('href')); }).filter(Boolean);
  var queued = false;

  function markCurrentSection() {
    queued = false;
    var offset = window.matchMedia('(max-width: 820px)').matches ? 165 : 130;
    var current = sections[0];
    sections.forEach(function (section) {
      if (section.getBoundingClientRect().top <= offset) current = section;
    });
    links.forEach(function (link) {
      if (current && link.getAttribute('href') === '#' + current.id) link.setAttribute('aria-current', 'location');
      else link.removeAttribute('aria-current');
    });
  }

  function queueUpdate() {
    if (!queued) {
      queued = true;
      window.requestAnimationFrame(markCurrentSection);
    }
  }

  function openReferencedDetail() {
    if (!window.location.hash) return;
    var target = document.getElementById(window.location.hash.slice(1));
    if (target && target.tagName === 'DETAILS') target.open = true;
  }

  document.addEventListener('click', function (event) {
    var link = event.target.closest('a[href^="#"]');
    if (!link) return;
    var target = document.getElementById(link.getAttribute('href').slice(1));
    if (target && target.tagName === 'DETAILS') target.open = true;
  });

  window.addEventListener('scroll', queueUpdate, { passive: true });
  window.addEventListener('resize', queueUpdate);
  window.addEventListener('hashchange', openReferencedDetail);
  openReferencedDetail();
  markCurrentSection();
})();
