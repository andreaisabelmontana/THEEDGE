/* online/offline split: pixel cursor trail over the ON-LINE / OFF-LINE
   sticky section. Pointer movement paints grid-snapped squares onto an
   overlay canvas; each square's hue is derived from where it was painted
   (cool hues left/top, warm right/bottom) and it dissolves over ~0.9s.
   Self-contained, no deps; skipped entirely under prefers-reduced-motion. */
(function () {
  var GRID = 16;        // px, square size + snap step (CSS pixels)
  var LIFE_MS = 900;    // how long a square takes to fade out
  var MAX_CELLS = 800;  // hard cap so long scrubbing stays cheap
  var HUE_SPAN_X = 200; // hue degrees swept across the section width
  var HUE_SPAN_Y = 80;  // extra sweep down the height
  var HUE_DRIFT = 12;   // slow whole-palette drift, degrees per second

  if (window.matchMedia && window.matchMedia("(prefers-reduced-motion: reduce)").matches) return;

  function init() {
    var section = document.querySelector("section[data-otot-top]");
    if (!section || !window.requestAnimationFrame) return;

    var canvas = document.createElement("canvas");
    canvas.setAttribute("aria-hidden", "true");
    canvas.style.cssText =
      "position:absolute;inset:0;width:100%;height:100%;pointer-events:none;z-index:20;";
    if (getComputedStyle(section).position === "static") section.style.position = "relative";
    section.appendChild(canvas);
    var ctx = canvas.getContext("2d");
    if (!ctx) return;

    var dpr = 1, w = 0, h = 0;
    function fit() {
      dpr = Math.min(window.devicePixelRatio || 1, 2);
      var r = section.getBoundingClientRect();
      w = r.width; h = r.height;
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
    }
    fit();
    if (window.ResizeObserver) new ResizeObserver(fit).observe(section);
    else window.addEventListener("resize", fit);

    var cells = [];   // {gx, gy, born}
    var last = null;  // previous snapped point, for line interpolation
    var raf = 0;

    function snap(v) { return Math.floor(v / GRID) * GRID; }

    function paint(gx, gy, now) {
      var tail = cells[cells.length - 1];
      if (tail && tail.gx === gx && tail.gy === gy) { tail.born = now; return; }
      cells.push({ gx: gx, gy: gy, born: now });
      if (cells.length > MAX_CELLS) cells.splice(0, cells.length - MAX_CELLS);
    }

    section.addEventListener("pointermove", function (e) {
      var r = section.getBoundingClientRect();
      var x = e.clientX - r.left, y = e.clientY - r.top;
      if (x < 0 || y < 0 || x > w || y > h) return;
      var now = performance.now();
      var gx = snap(x), gy = snap(y);
      // fill the gap from the previous point so fast moves stay a line
      if (last) {
        var steps = Math.ceil(Math.max(Math.abs(gx - last.gx), Math.abs(gy - last.gy)) / GRID);
        for (var i = 1; i < Math.min(steps, 24); i++) {
          paint(snap(last.gx + ((gx - last.gx) * i) / steps),
                snap(last.gy + ((gy - last.gy) * i) / steps), now);
        }
      }
      paint(gx, gy, now);
      last = { gx: gx, gy: gy };
      if (!raf) raf = requestAnimationFrame(draw);
    });
    section.addEventListener("pointerleave", function () { last = null; });

    function draw(now) {
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);
      var alive = [];
      var drift = (now / 1000) * HUE_DRIFT;
      for (var i = 0; i < cells.length; i++) {
        var c = cells[i];
        var age = (now - c.born) / LIFE_MS;
        if (age >= 1) continue;
        alive.push(c);
        var hue = (c.gx / Math.max(w, 1)) * HUE_SPAN_X +
                  (c.gy / Math.max(h, 1)) * HUE_SPAN_Y + drift;
        ctx.fillStyle = "hsl(" + (hue % 360) + " 82% 60% / " + (1 - age) + ")";
        ctx.fillRect(c.gx, c.gy, GRID - 1, GRID - 1);
      }
      cells = alive;
      raf = cells.length ? requestAnimationFrame(draw) : 0;
      if (!raf) ctx.clearRect(0, 0, w, h);
    }
  }

  if (document.readyState === "loading") document.addEventListener("DOMContentLoaded", init);
  else init();
})();
