/* Experience globe: canvas orthographic Earth with milestone pins and a
   timeline list. Land outlines come from Natural Earth 110m (same source
   Javier's globe uses), pre-decoded into custom/land-rings.json.
   Edit ENTRIES to change the journey. */
(function () {
  var ENTRIES = [
    { year: '2010 to 2022', title: 'Student', org: 'Colegio Nueva Granada',
      degree: 'American AP diploma + Bachiller Colombiano',
      place: 'Bogota, Colombia', lat: 4.652, lon: -74.055 },
    { year: '2022 to 2023', title: 'Student', org: 'Trinity College Dublin',
      degree: 'Bachelor of Computer Science (transferred to IE University)',
      place: 'Dublin, Ireland', lat: 53.3438, lon: -6.2546 },
    { year: '2023 to present', title: 'Student', org: 'IE University',
      degree: 'Bachelor of Computer Science and Artificial Intelligence',
      place: 'Madrid, Spain', lat: 40.4168, lon: -3.7038 }
  ];

  var canvas = document.getElementById('am-globe');
  var list = document.getElementById('am-exp-timeline');
  if (!canvas || !list) return;
  var ctx = canvas.getContext('2d');
  var RINGS = null;
  var rotLon = 20, rotLat = -18, targetLon = null, targetLat = null;
  var auto = true, dragging = false, lastX = 0, lastY = 0, active = -1;

  fetch('./custom/land-rings.json').then(function (r) { return r.json(); })
    .then(function (d) { RINGS = d; });

  function proj(lat, lon, lift) {
    var la = lat * Math.PI / 180, lo = (lon + rotLon) * Math.PI / 180;
    var x = Math.cos(la) * Math.sin(lo);
    var y = Math.sin(la);
    var z = Math.cos(la) * Math.cos(lo);
    var t = rotLat * Math.PI / 180;
    var y2 = y * Math.cos(t) - z * Math.sin(t);
    var z2 = y * Math.sin(t) + z * Math.cos(t);
    var R = canvas.width * 0.42 * (1 + (lift || 0));
    return { x: canvas.width / 2 + R * x, y: canvas.height / 2 - R * y2, vis: z2 > 0.02 };
  }

  function drawArc(a, b) {
    var pa = { lat: a.lat, lon: a.lon }, pb = { lat: b.lat, lon: b.lon };
    ctx.beginPath();
    var started = false;
    for (var i = 0; i <= 40; i++) {
      var t = i / 40;
      var lat = pa.lat + (pb.lat - pa.lat) * t;
      var lon = pa.lon + (pb.lon - pa.lon) * t;
      var p = proj(lat, lon, 0.09 * Math.sin(t * Math.PI));
      if (p.vis) {
        if (!started) { ctx.moveTo(p.x, p.y); started = true; }
        else ctx.lineTo(p.x, p.y);
      } else started = false;
    }
    ctx.strokeStyle = 'rgba(191,64,255,0.65)';
    ctx.lineWidth = 1.4;
    ctx.stroke();
  }

  function frame(ts) {
    if (auto && !dragging && targetLon === null) rotLon += 0.06;
    if (targetLon !== null) {
      rotLon += (targetLon - rotLon) * 0.08;
      rotLat += (targetLat - rotLat) * 0.08;
      if (Math.abs(targetLon - rotLon) < 0.2) { targetLon = null; targetLat = null; }
    }
    var w = canvas.width, h = canvas.height, R = w * 0.42;
    ctx.clearRect(0, 0, w, h);
    ctx.beginPath();
    ctx.arc(w / 2, h / 2, R, 0, Math.PI * 2);
    ctx.fillStyle = 'rgba(18,16,28,0.85)';
    ctx.fill();
    ctx.strokeStyle = 'rgba(245,240,232,0.35)';
    ctx.lineWidth = 1;
    ctx.stroke();
    if (RINGS) {
      ctx.beginPath();
      for (var r = 0; r < RINGS.length; r++) {
        var ring = RINGS[r], started = false;
        for (var i = 0; i < ring.length; i++) {
          var p = proj(ring[i][1], ring[i][0], 0);
          if (p.vis) {
            if (!started) { ctx.moveTo(p.x, p.y); started = true; }
            else ctx.lineTo(p.x, p.y);
          } else started = false;
        }
      }
      ctx.strokeStyle = 'rgba(245,240,232,0.5)';
      ctx.lineWidth = 0.8;
      ctx.stroke();
    }
    drawArc(ENTRIES[0], ENTRIES[1]);
    drawArc(ENTRIES[1], ENTRIES[2]);
    var pulse = 1 + 0.35 * Math.sin(ts / 400);
    for (var e = 0; e < ENTRIES.length; e++) {
      var p2 = proj(ENTRIES[e].lat, ENTRIES[e].lon, 0);
      if (!p2.vis) continue;
      ctx.beginPath();
      ctx.arc(p2.x, p2.y, (e === active ? 7 : 4.5) * pulse, 0, Math.PI * 2);
      ctx.fillStyle = e === active ? '#bf40ff' : '#f5f0e8';
      ctx.fill();
    }
    requestAnimationFrame(frame);
  }
  requestAnimationFrame(frame);

  function goTo(i) {
    active = i;
    targetLon = -ENTRIES[i].lon;
    targetLat = ENTRIES[i].lat;
    auto = false;
    var items = list.querySelectorAll('li');
    for (var k = 0; k < items.length; k++)
      items[k].classList.toggle('is-active', k === i);
  }

  canvas.addEventListener('pointerdown', function (ev) {
    dragging = true; auto = false; lastX = ev.clientX; lastY = ev.clientY;
    canvas.setPointerCapture(ev.pointerId);
    var rect = canvas.getBoundingClientRect();
    var sx = (ev.clientX - rect.left) * (canvas.width / rect.width);
    var sy = (ev.clientY - rect.top) * (canvas.height / rect.height);
    for (var e = 0; e < ENTRIES.length; e++) {
      var p = proj(ENTRIES[e].lat, ENTRIES[e].lon, 0);
      if (p.vis && (p.x - sx) * (p.x - sx) + (p.y - sy) * (p.y - sy) < 400) { goTo(e); break; }
    }
  });
  canvas.addEventListener('pointermove', function (ev) {
    if (!dragging) return;
    rotLon += (ev.clientX - lastX) * 0.4;
    rotLat -= (ev.clientY - lastY) * 0.3;
    rotLat = Math.max(-70, Math.min(70, rotLat));
    lastX = ev.clientX; lastY = ev.clientY;
    targetLon = null; targetLat = null;
  });
  canvas.addEventListener('pointerup', function () { dragging = false; });

  for (var i = 0; i < ENTRIES.length; i++) {
    (function (i) {
      var en = ENTRIES[i];
      var li = document.createElement('li');
      li.innerHTML = '<span class="am-exp-line1 text-body-reg-mona">' + (en.title + ' · ' + en.org).toUpperCase() + '</span>' +
        '<span class="am-exp-line2 text-body-reg-mona">' + en.degree + '</span>' +
        '<span class="am-exp-line3 text-body-reg-mona">' + en.place + ' · ' + en.year + '</span>';
      li.addEventListener('click', function () { goTo(i); });
      list.appendChild(li);
    })(i);
  }

})();


/* Orbiting Earth before the footer: photoreal when three.js loads,
   line-art fallback otherwise. */
(function () {
  var c = document.getElementById('am-earth');
  if (!c) return;

  function vector() {
    var x = c.getContext('2d');
    var rot = 0, R2 = null;
    fetch('./custom/land-rings.json').then(function (r) { return r.json(); })
      .then(function (d) { R2 = d; });
    function pr(lat, lon, cx, cy, R) {
      var la = lat * Math.PI / 180, lo = (lon + rot) * Math.PI / 180;
      var px = Math.cos(la) * Math.sin(lo), py = Math.sin(la), pz = Math.cos(la) * Math.cos(lo);
      var t = -0.38;
      var y2 = py * Math.cos(t) - pz * Math.sin(t), z2 = py * Math.sin(t) + pz * Math.cos(t);
      return { x: cx + R * px, y: cy - R * y2, vis: z2 > 0.02 };
    }
    (function frame() {
      rot += 0.03;
      var w = c.width, h = c.height, cx = w / 2, cy = h * 1.22, R = w * 0.37;
      x.clearRect(0, 0, w, h);
      x.fillStyle = '#211d3a';
      x.beginPath(); x.arc(cx, cy, R, 0, Math.PI * 2); x.fill();
      if (R2) {
        x.beginPath();
        for (var r = 0; r < R2.length; r++) {
          var ring = R2[r], st = false;
          for (var i = 0; i < ring.length; i++) {
            var p = pr(ring[i][1], ring[i][0], cx, cy, R);
            if (p.vis) { if (!st) { x.moveTo(p.x, p.y); st = true; } else x.lineTo(p.x, p.y); }
            else st = false;
          }
        }
        x.strokeStyle = 'rgba(228,222,255,0.85)'; x.lineWidth = 0.9; x.stroke();
      }
      requestAnimationFrame(frame);
    })();
  }

  if (!window.THREE) return vector();
  try {
    var renderer = new THREE.WebGLRenderer({ canvas: c, alpha: true, antialias: true });
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    var scene = new THREE.Scene();
    var cam = new THREE.PerspectiveCamera(38, 2.5, 0.1, 100);
    var failed = false;
    var tex = new THREE.TextureLoader().load('./custom/vendor/earth-atmos.jpg',
      null, undefined, function () { failed = true; vector(); });
    if (tex.colorSpace !== undefined && THREE.SRGBColorSpace) tex.colorSpace = THREE.SRGBColorSpace;
    var globe = new THREE.Mesh(new THREE.SphereGeometry(1.55, 96, 96),
      new THREE.MeshStandardMaterial({ map: tex, roughness: 1, metalness: 0 }));
    globe.position.y = -1.05;
    globe.rotation.z = 0.1;
    scene.add(globe);
    scene.add(new THREE.AmbientLight(0x8888aa, 0.6));
    var sun = new THREE.DirectionalLight(0xffffff, 2.4);
    sun.position.set(-2.5, 2, 2.5);
    scene.add(sun);
    var glow = new THREE.Mesh(new THREE.SphereGeometry(1.63, 64, 64),
      new THREE.MeshBasicMaterial({ color: 0x9a5cff, transparent: true, opacity: 0.16,
        side: THREE.BackSide, blending: THREE.AdditiveBlending }));
    glow.position.y = -1.05;
    scene.add(glow);
    cam.position.set(0, 0.35, 3.1);
    function size() {
      var w = c.clientWidth || (c.parentElement && c.parentElement.clientWidth) || 1200;
      var h2 = Math.max(1, Math.round(w * 0.40));
      renderer.setSize(w, h2, false);
      cam.aspect = w / h2;
      cam.updateProjectionMatrix();
    }
    size();
    window.addEventListener('resize', size);
    (function tick() {
      if (failed) return;
      globe.rotation.y += 0.0011;
      renderer.render(scene, cam);
      requestAnimationFrame(tick);
    })();
  } catch (e) { vector(); }
})();
