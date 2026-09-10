/* staggered entrance.

   anything with data-reveal starts translated down and transparent, then
   settles in dom order. data-reveal="120" delays that one element by 120ms so
   a hero can cascade eyebrow -> name -> copy -> badge -> links.

   below the fold the same elements wait for an intersection observer instead
   of firing on load, so a section is animating when it is actually looked at.

   the class is added by script, and the starting state is defined here rather
   than in the stylesheet, so with javascript off the page renders as plain
   static content instead of a screen of invisible divs. */
(function () {
  var nodes = [].slice.call(document.querySelectorAll('[data-reveal]'));
  if (!nodes.length) return;

  var reduced = window.matchMedia &&
    window.matchMedia('(prefers-reduced-motion: reduce)').matches;
  if (reduced) return;

  var style = document.createElement('style');
  style.textContent =
    '[data-reveal].reveal-init{opacity:0;transform:translateY(14px)}' +
    '[data-reveal].reveal-in{opacity:1;transform:none;' +
    'transition:opacity .7s var(--ease-out),transform .7s var(--ease-out)}';
  document.head.appendChild(style);

  nodes.forEach(function (n) { n.classList.add('reveal-init'); });

  function show(node) {
    var delay = parseInt(node.getAttribute('data-reveal'), 10) || 0;
    setTimeout(function () {
      node.classList.remove('reveal-init');
      node.classList.add('reveal-in');
    }, delay);
  }

  var above = [];
  var below = [];
  nodes.forEach(function (n) {
    (n.getBoundingClientRect().top < window.innerHeight ? above : below).push(n);
  });

  above.forEach(show);

  if (!below.length) return;

  if (!('IntersectionObserver' in window)) {
    below.forEach(show);
    return;
  }

  var io = new IntersectionObserver(function (entries) {
    entries.forEach(function (e) {
      if (!e.isIntersecting) return;
      show(e.target);
      io.unobserve(e.target);
    });
  }, { threshold: 0.15 });

  below.forEach(function (n) { io.observe(n); });
})();
