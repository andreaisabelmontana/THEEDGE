/* Warp starfield behind the quote: coloured streaks flying outward from a
   vanishing point, inspired by the UN Race to Save Space section. Sits in
   a canvas behind the text; pauses when offscreen. */
(function () {
  var canvas = document.querySelector('.am-warp');
  if (!canvas) return;
  var ctx = canvas.getContext('2d');
  if (!ctx) return;
  var reduceMotion = window.matchMedia("(prefers-reduced-motion: reduce)");
  var COLORS = ['#ffffff', '#ffffff', '#9fd8cf', '#e8c27a', '#e0b3d6', '#b9c7e8'];
  var N = 130;
  var stars = [];
  var running = !("IntersectionObserver" in window);
  var raf = 0, started = null;

  function resize() {
    var r = canvas.parentElement.getBoundingClientRect();
    canvas.width = Math.max(2, Math.floor(r.width));
    canvas.height = Math.max(2, Math.floor(r.height));
    draw(false);
  }


  function spawn(s) {
    s.a = Math.random() * Math.PI * 2;
    s.d = 0.02 + Math.random() * 0.25;
    s.v = 0.0016 + Math.random() * 0.004;
    s.c = COLORS[(Math.random() * COLORS.length) | 0];
    s.w = 0.8 + Math.random() * 2.2;
    return s;
  }
  for (var i = 0; i < N; i++) {
    var s = spawn({});
    s.d = Math.random() * 0.9;      /* prefill the field */
    stars.push(s);
  }

  resize();
  if ("ResizeObserver" in window) new ResizeObserver(resize).observe(canvas.parentElement);
  else window.addEventListener("resize", resize);

  if ('IntersectionObserver' in window) {
    new IntersectionObserver(function (en) {
      if (running === en[0].isIntersecting) return;
      running = en[0].isIntersecting;
      reset();
    }).observe(canvas);
  }

  function draw(animate) {
      var w = canvas.width, h = canvas.height;
      var cx = w / 2, cy = h * 0.45;
      var R = Math.max(w, h);
      ctx.clearRect(0, 0, w, h);
      for (var i = 0; i < stars.length; i++) {
        var s = stars[i];
        if (animate) s.d += s.v * (0.4 + s.d * 3.2);   /* accelerate as they approach */
        if (s.d > 1.05) spawn(s);
        var x0 = cx + Math.cos(s.a) * s.d * R;
        var y0 = cy + Math.sin(s.a) * s.d * R * 0.72;
        var tail = 0.05 + s.d * 0.12;
        var x1 = cx + Math.cos(s.a) * Math.max(0, s.d - tail) * R;
        var y1 = cy + Math.sin(s.a) * Math.max(0, s.d - tail) * R * 0.72;
        ctx.strokeStyle = s.c;
        ctx.globalAlpha = Math.min(1, 0.15 + s.d * 1.1);
        ctx.lineWidth = s.w * (0.4 + s.d);
        ctx.lineCap = 'round';
        ctx.beginPath();
        ctx.moveTo(x1, y1);
        ctx.lineTo(x0, y0);
        ctx.stroke();
      }
      ctx.globalAlpha = 1;
  }
  function frame(now) {
    raf = 0;
    if (!running || reduceMotion.matches || document.hidden) return;
    if (started === null) started = now;
    if (now - started >= 4500) return;
    draw(true);
    raf = requestAnimationFrame(frame);
  }
  function reset() {
    cancelAnimationFrame(raf);
    raf = 0;
    started = null;
    if (running && !reduceMotion.matches && !document.hidden) raf = requestAnimationFrame(frame);
  }
  reduceMotion.addEventListener?.("change", reset);
  document.addEventListener("visibilitychange", reset);
  reset();
})();
