/* Transition-overlay watchdog: the purple page-transition overlay is dismissed
   by the animation bundle after each navigation. If that dismissal ever fails
   (script error, janky load, stalled animation), the overlay stays parked over
   the page and the site looks like an empty purple wall. Normal wipes last
   well under two seconds, so an overlay that stays fully visible for four
   straight seconds is stuck — fade it out the same way the bundle would.
   Inline styles only; the bundle's own GSAP writes override them on the next
   normal transition. */
(function () {
  if (window.__edgeTransitionWatchdog) return; window.__edgeTransitionWatchdog = true;

  var stuckSince = null;
  setInterval(function () {
    var t = document.querySelector('.transition-w');
    if (!t) return;
    var cs = getComputedStyle(t);
    var covering = cs.visibility !== 'hidden' && cs.display !== 'none' && parseFloat(cs.opacity) > 0.5;
    if (!covering) { stuckSince = null; return; }
    var now = Date.now();
    if (stuckSince === null) { stuckSince = now; return; }
    if (now - stuckSince > 4000) {
      t.style.transition = 'opacity 0.35s ease';
      t.style.opacity = '0';
      setTimeout(function () {
        t.style.visibility = 'hidden';
        t.style.opacity = '';
        t.style.transition = '';
      }, 400);
      stuckSince = null;
    }
  }, 1000);
})();
