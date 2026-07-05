/* on-track hero: the helmet's spin timeline is normally scrubbed by scrolling
   through pos1->pos2->pos3 waypoint sections, but this site hides those sections
   ("helmet only" layout). Drive the timeline on a clock instead, and keep the
   helmet centered in the hero viewport. */
(function () {
  var DURATION_MS = 14000; // one full forward+back sweep

  function tick(t) {
    try {
      var gl = window.landoGL;
      if (gl && gl.params && gl.params.helmetScrollScene && gl.bounds && gl.bounds.helmetScroll) {
        var p = (t % DURATION_MS) / (DURATION_MS / 2); // 0..2
        if (p > 1) p = 2 - p;                          // ping-pong 0..1..0
        p = p * p * (3 - 2 * p);                       // soft turn-arounds
        gl.params.helmetScrollScene.PROGRESS = p;

        // keep the helmet centered and fully in frame while the hero is on screen
        var size = Math.min(window.innerHeight * 0.85, window.innerWidth * 0.6);
        gl.bounds.helmetScroll.left = window.innerWidth / 2;
        gl.bounds.helmetScroll.top = window.innerHeight * 0.52 - window.scrollY * 0.5;
        gl.bounds.helmetScroll.width = size;
        gl.bounds.helmetScroll.height = size;
      }
    } catch (e) { /* never let one bad frame kill the loop */ }
    requestAnimationFrame(tick);
  }
  requestAnimationFrame(tick);
})();
