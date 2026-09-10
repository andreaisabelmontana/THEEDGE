/* Two things that go wrong because this site is her template with different
   content in it, and both of them look like "the purple is in the wrong place".

   1. Stale scroll triggers.

      Her bundle drives the page's theme from GSAP ScrollTriggers: each band of
      the page declares a theme, and the footer's is the bright one, which since
      she recoloured --color--lime reads as magenta. ScrollTrigger measures
      every trigger's start and end once, at init.

      These pages change height well after that. Sections are hidden, the hero
      band was released from a fixed 88vh, the experience page lost a 7000px
      photo track. Every measurement taken at init is then wrong, and a theme
      meant to begin at the footer can begin at the top of the document, which
      paints the whole page magenta under the starfield.

      ScrollTrigger recalculates on resize, and gsap is not exposed globally
      here, so a few resize events after the layout has settled are what put the
      triggers back where the content actually is. ui.js already did this on the
      landing page; every other page needed it too.

   2. A stuck transition curtain.

      .transition-w is the full-screen panel Taxi wipes across the screen
      between pages, at z-index 9999. Her bundle loads a per-page module on each
      navigation and those modules throw here, because the template pins
      sections this site does not have. If one dies mid-transition, nothing runs
      the hide half of the wipe and the curtain stays up over a page that has
      otherwise loaded fine.

      The bundle is minified and not ours to repair, so this watches the curtain
      and puts it back the way a finished transition leaves it. A transition
      that completes normally is well done inside the delay, so this never cuts
      one short. */
(function () {
  /* long enough that her loader, which runs about ten seconds cold, is gone
     and the real layout is in place before anything is measured */
  var SETTLE = [1200, 2600, 5000, 9000];

  function refresh() {
    window.dispatchEvent(new Event('resize'));
  }

  function painted(el) {
    var cs = getComputedStyle(el);
    return cs.display !== 'none' &&
      cs.visibility !== 'hidden' &&
      parseFloat(cs.opacity) > 0.02;
  }

  function clearCurtain() {
    var el = document.querySelector('.transition-w');
    if (!el || !painted(el)) return;
    el.style.visibility = 'hidden';
    el.style.pointerEvents = 'none';
    el.style.backgroundColor = 'transparent';
  }

  function settle() {
    SETTLE.forEach(function (d) {
      setTimeout(function () {
        refresh();
        clearCurtain();
      }, d);
    });
  }

  if (document.readyState === 'complete') settle();
  else window.addEventListener('load', settle);

  // taxi swaps the view in place, so re-run for the page that arrives
  window.addEventListener('popstate', settle);
  document.addEventListener('click', function (e) {
    if (e.target.closest && e.target.closest('a[href]')) settle();
  }, true);
})();
