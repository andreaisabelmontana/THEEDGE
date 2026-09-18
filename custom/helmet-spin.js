/* on-track hero: the helmet's spin timeline is normally scrubbed by scrolling
   through waypoint sections that this site's "helmet only" layout hides.
   Drive it on a clock instead:
   - intro: start large at the left edge, spin a full sweep while gliding and
     shrinking into the centre
   - after: settle into a slow continuous back-and-forth spin, centred. */
(function () {
  var INTRO_MS = 3200;   // left -> centre entrance
  var HALF_SWEEP = 7000; // ms for one full timeline sweep at cruise speed
  var startT = null;
  var reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');

  function tick(t) {
    try {
      var gl = window.edgeGL;
      if (gl && gl.params && gl.params.helmetScrollScene && gl.bounds && gl.bounds.helmetScroll) {
        if (startT === null) startT = t;
        var el = t - startT;

        // intro progress, eased
        var k = Math.min(el / INTRO_MS, 1);
        k = k * k * (3 - 2 * k);
        if (reducedMotion.matches) k = 1;

        // spin clock: time-warped so the intro packs in one full sweep,
        // then cruises seamlessly into the ping-pong
        var v = el < INTRO_MS
          ? (el / INTRO_MS) * HALF_SWEEP
          : HALF_SWEEP + (el - INTRO_MS);
        var p = (v % (HALF_SWEEP * 2)) / HALF_SWEEP; // 0..2
        if (p > 1) p = 2 - p;                        // ping-pong 0..1..0
        p = p * p * (3 - 2 * p);
        if (reducedMotion.matches) p = 0.45;
        gl.params.helmetScrollScene.PROGRESS = p;

        // position/size: big at the left, settling to centre
        // sized down so the whole helmet clears the title block below it: at
        // 0.85 of the viewport height its base ran past the hero and the chin
        // was cut off
        var hero = document.querySelector('.s.is-on-track-home');
        var clean = document.body.classList.contains('projects-page');
        var mobile = window.innerWidth <= 600;
        var heroHeight = hero ? hero.offsetHeight : window.innerHeight * 0.56;
        var available = mobile ? 210 : heroHeight - 116;
        var endSize = clean
          ? Math.min(available * 0.9, window.innerWidth * (mobile ? 0.60 : 0.34))
          : Math.min(window.innerHeight * 0.40, window.innerWidth * 0.30);
        var startSize = clean
          ? Math.min(available, window.innerWidth * (mobile ? 0.66 : 0.40))
          : Math.min(window.innerHeight * 0.56, window.innerWidth * 0.42);
        var size = startSize + (endSize - startSize) * k;
        var startX = window.innerWidth * (clean ? (mobile ? 0.35 : 0.67) : 0.2);
        var endX = window.innerWidth * (clean && !mobile ? 0.76 : 0.5);
        gl.bounds.helmetScroll.left = startX + (endX - startX) * k;
        gl.bounds.helmetScroll.top = clean
          ? (mobile ? 198 : 76 + (heroHeight - 76) / 2)
          : window.innerHeight * 0.28 - window.scrollY * 0.5;
        gl.bounds.helmetScroll.width = size;
        gl.bounds.helmetScroll.height = size;
      }
    } catch (e) { /* never let one bad frame kill the loop */ }
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
})();
