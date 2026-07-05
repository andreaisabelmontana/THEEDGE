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

  function tick(t) {
    try {
      var gl = window.landoGL;
      if (gl && gl.params && gl.params.helmetScrollScene && gl.bounds && gl.bounds.helmetScroll) {
        if (startT === null) startT = t;
        var el = t - startT;

        // intro progress, eased
        var k = Math.min(el / INTRO_MS, 1);
        k = k * k * (3 - 2 * k);

        // spin clock: time-warped so the intro packs in one full sweep,
        // then cruises seamlessly into the ping-pong
        var v = el < INTRO_MS
          ? (el / INTRO_MS) * HALF_SWEEP
          : HALF_SWEEP + (el - INTRO_MS);
        var p = (v % (HALF_SWEEP * 2)) / HALF_SWEEP; // 0..2
        if (p > 1) p = 2 - p;                        // ping-pong 0..1..0
        p = p * p * (3 - 2 * p);
        gl.params.helmetScrollScene.PROGRESS = p;

        // position/size: big at the left, settling to centre
        var endSize = Math.min(window.innerHeight * 0.85, window.innerWidth * 0.6);
        var startSize = Math.min(window.innerHeight * 1.2, window.innerWidth * 0.85);
        var size = startSize + (endSize - startSize) * k;
        var startX = window.innerWidth * 0.2;
        var endX = window.innerWidth / 2;
        gl.bounds.helmetScroll.left = startX + (endX - startX) * k;
        gl.bounds.helmetScroll.top = window.innerHeight * 0.52 - window.scrollY * 0.5;
        gl.bounds.helmetScroll.width = size;
        gl.bounds.helmetScroll.height = size;
      }
    } catch (e) { /* never let one bad frame kill the loop */ }
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
})();
