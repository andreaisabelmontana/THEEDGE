/* A small, on-demand Three.js scene using NASA Earth Observatory imagery.
   City coordinates share the same equirectangular projection as the texture. */
(function () {
  'use strict';
  if (!document.body.classList.contains('experience-page')) return;
  var canvas = document.getElementById('am-globe');
  var list = document.getElementById('am-exp-timeline');
  if (!canvas || !list) return;

  var entries = [
    { city: ['Bogotá', 'Bogotá', 'Bogotá'], country: ['Colombia', 'Colombia', 'Kolumbien'],
      detail: 'Colegio Nueva Granada, and the digital presence for Top Living Inmobiliaria',
      when: '2010 to 2024', lat: 4.652, lon: -74.055 },
    { city: ['Dublin', 'Dublín', 'Dublin'], country: ['Ireland', 'Irlanda', 'Irland'],
      detail: 'Trinity College Dublin, first year of the degree',
      when: '2022 to 2023', lat: 53.3438, lon: -6.2546 },
    { city: ['Madrid', 'Madrid', 'Madrid'], country: ['Spain', 'España', 'Spanien'],
      detail: 'IE University, IEX Labs research and the Google Developer Group',
      when: '2023 to present', lat: 40.4168, lon: -3.7038 }
  ];
  var root = window.__SITE_ROOT || '';
  var textureURL = root + '/custom/journey-assets/earth-blue-marble.webp';
  var motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  var caption = document.querySelector('#journey .am-globe-instruction');
  var active = 2, language = 0, visible = true, ready = false, frameID = 0;
  var radians = Math.PI / 180;
  var rotation = { x: entries[active].lat * radians, y: -entries[active].lon * radians };
  var target = { x: rotation.x, y: rotation.y };
  var renderer, scene, camera, world, earth, pins = [], raycaster, pointer;
  var fallbackContext, fallbackPixels, fallbackWidth, fallbackHeight;
  var buttons = [];

  entries.forEach(function (entry, index) {
    var item = document.createElement('li');
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'am-journey-button';
    button.innerHTML = '<span class="am-exp-line1"><span class="am-journey-city"></span>' +
      '<span class="am-journey-country"></span></span>' +
      '<span class="am-exp-line2">' + entry.detail + '</span>' +
      '<span class="am-exp-line3">' + entry.when + '</span>';
    button.addEventListener('click', function () { select(index); });
    item.appendChild(button);
    list.appendChild(item);
    buttons.push(button);
  });

  function localize(lang) {
    language = Math.max(0, ['en', 'es', 'de'].indexOf(lang));
    entries.forEach(function (entry, index) {
      buttons[index].querySelector('.am-journey-city').textContent = entry.city[language];
      buttons[index].querySelector('.am-journey-country').textContent = entry.country[language];
    });
    caption.textContent = fallbackContext ?
      ['Select a city to explore.', 'Elige una ciudad para explorar.', 'Wähle eine Stadt zum Erkunden.'][language] :
      ['Drag the Earth or choose a city.', 'Gira la Tierra o elige una ciudad.', 'Drehe die Erde oder wähle eine Stadt.'][language];
    canvas.setAttribute('aria-label', [
      'Earth showing journey locations. Use arrow keys to rotate, or select a city below.',
      'La Tierra con las ciudades del recorrido. Usa las flechas para girar o elige una ciudad.',
      'Erde mit den Stationen meiner Reise. Mit Pfeiltasten drehen oder eine Stadt auswählen.'
    ][language]);
  }
  document.addEventListener('edge:lang', function (event) { localize(event.detail); });
  localize(document.documentElement.lang);

  function select(index) {
    active = index;
    buttons.forEach(function (button, i) {
      button.setAttribute('aria-pressed', String(i === index));
      button.parentNode.classList.toggle('is-active', i === index);
    });
    target.x = entries[index].lat * radians;
    var longitude = -entries[index].lon * radians;
    target.y = rotation.y + Math.atan2(Math.sin(longitude - rotation.y), Math.cos(longitude - rotation.y));
    pins.forEach(function (pin, i) {
      pin.material.color.set(i === active ? 0xf0ca94 : 0xf4f0e9);
      pin.scale.setScalar(i === active ? 1.35 : 1);
    });
    if (motion.matches || fallbackContext) {
      rotation.x = target.x;
      rotation.y = target.y;
    }
    invalidate();
  }

  // Rest between interactions: no perpetual render loop when the view settles.
  function invalidate() {
    if (!ready || !visible || document.hidden || frameID) return;
    frameID = requestAnimationFrame(render);
  }
  function render() {
    frameID = 0;
    if (!ready || !visible || document.hidden) return;
    var difference = Math.abs(target.x - rotation.x) + Math.abs(target.y - rotation.y);
    var amount = motion.matches ? 1 : 0.085;
    rotation.x += (target.x - rotation.x) * amount;
    rotation.y += (target.y - rotation.y) * amount;
    if (difference < 0.001) { rotation.x = target.x; rotation.y = target.y; }
    if (fallbackContext) drawFallback();
    else {
      world.rotation.set(rotation.x, rotation.y, 0, 'XYZ');
      renderer.render(scene, camera);
    }
    if (difference > 0.001) invalidate();
  }

  function point(lat, lon, radius) {
    var latitude = lat * radians, longitude = lon * radians;
    return new THREE.Vector3(
      Math.cos(latitude) * Math.sin(longitude), Math.sin(latitude),
      Math.cos(latitude) * Math.cos(longitude)
    ).multiplyScalar(radius || 1);
  }

  function initialize() {
    if (!window.THREE) { fallback(); return; }
    try {
      renderer = new THREE.WebGLRenderer({ canvas: canvas, alpha: true, antialias: true });
      renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 1.75));
      renderer.outputEncoding = THREE.sRGBEncoding;
      scene = new THREE.Scene();
      camera = new THREE.PerspectiveCamera(34, 1, 0.1, 30);
      camera.position.z = 3.95;
      world = new THREE.Group();
      scene.add(world);
      // Rotate the standard SphereGeometry UV seam to match geographic lon/lat.
      var geometry = new THREE.SphereGeometry(1, 80, 48);
      geometry.rotateY(-Math.PI / 2);
      earth = new THREE.Mesh(geometry, new THREE.MeshPhongMaterial({
        color: 0xffffff, shininess: 12, specular: 0x05080b
      }));
      world.add(earth);
      scene.add(new THREE.AmbientLight(0xd9e2ef, 0.52));
      var sun = new THREE.DirectionalLight(0xfff4e4, 1.6);
      sun.position.set(-3, 4, 5);
      scene.add(sun);
      // A thin blue atmospheric edge, lit with the surface rather than a glow halo.
      var atmosphere = new THREE.Mesh(new THREE.SphereGeometry(1.006, 64, 40),
        new THREE.ShaderMaterial({
          transparent: true, depthWrite: false,
          uniforms: { tint: { value: new THREE.Color(0x759bb8) } },
          vertexShader: 'varying vec3 n; varying vec3 v; void main(){vec4 p=modelViewMatrix*vec4(position,1.0);n=normalize(normalMatrix*normal);v=normalize(-p.xyz);gl_Position=projectionMatrix*p;}',
          fragmentShader: 'uniform vec3 tint; varying vec3 n; varying vec3 v; void main(){float rim=pow(1.0-max(dot(normalize(n),normalize(v)),0.0),4.0);gl_FragColor=vec4(tint,rim*0.32);}'
        }));
      world.add(atmosphere);
      entries.forEach(function (entry, index) {
        var pin = new THREE.Mesh(new THREE.SphereGeometry(0.009, 16, 12),
          new THREE.MeshBasicMaterial({ color: 0xf4f0e9 }));
        pin.position.copy(point(entry.lat, entry.lon, 1.013));
        pin.userData.index = index;
        world.add(pin);
        pins.push(pin);
        var ring = new THREE.Mesh(new THREE.RingGeometry(0.018, 0.021, 40),
          new THREE.MeshBasicMaterial({ color: 0xf0ca94, transparent: true, opacity: 0.8, side: THREE.DoubleSide }));
        ring.position.copy(point(entry.lat, entry.lon, 1.009));
        ring.quaternion.setFromUnitVectors(new THREE.Vector3(0, 0, 1), ring.position.clone().normalize());
        world.add(ring);
      });
      for (var i = 0; i < entries.length - 1; i++) {
        var from = point(entries[i].lat, entries[i].lon), to = point(entries[i + 1].lat, entries[i + 1].lon);
        var angle = from.angleTo(to), path = [];
        for (var step = 0; step <= 80; step++) {
          var t = step / 80;
          var position = from.clone().multiplyScalar(Math.sin((1 - t) * angle))
            .add(to.clone().multiplyScalar(Math.sin(t * angle))).divideScalar(Math.sin(angle));
          path.push(position.multiplyScalar(1.004 + 0.065 * Math.sin(t * Math.PI)));
        }
        world.add(new THREE.Line(new THREE.BufferGeometry().setFromPoints(path),
          new THREE.LineBasicMaterial({ color: 0xdac5a3, transparent: true, opacity: 0.64 })));
      }
      raycaster = new THREE.Raycaster();
      pointer = new THREE.Vector2();
      new THREE.TextureLoader().load(textureURL, function (texture) {
        if (fallbackContext) { texture.dispose(); return; }
        texture.encoding = THREE.sRGBEncoding;
        texture.anisotropy = Math.min(4, renderer.capabilities.getMaxAnisotropy());
        earth.material.map = texture;
        earth.material.needsUpdate = true;
        ready = true;
        canvas.dataset.renderer = 'webgl';
        resize();
        select(active);
      }, undefined, fallback);
      canvas.addEventListener('webglcontextlost', function (event) {
        event.preventDefault();
        fallback();
      }, { once: true });
    } catch (error) { fallback(); }
  }

  function resize() {
    if (renderer && !fallbackContext) {
      var size = Math.max(1, Math.round(canvas.getBoundingClientRect().width));
      renderer.setSize(size, size, false);
    }
    invalidate();
  }
  new ResizeObserver(resize).observe(canvas.parentNode);
  new IntersectionObserver(function (records) {
    visible = records[0].isIntersecting;
    if (visible) invalidate();
  }, { rootMargin: '100px' }).observe(canvas.parentNode);
  document.addEventListener('visibilitychange', invalidate);
  motion.addEventListener('change', function () { select(active); });

  function bindControls() {
    var drag = null;
    canvas.tabIndex = 0;
    canvas.addEventListener('keydown', function (event) {
      var keys = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'];
      if (keys.indexOf(event.key) < 0) return;
      event.preventDefault();
      target.y += (event.key === 'ArrowRight' ? 0.18 : event.key === 'ArrowLeft' ? -0.18 : 0);
      target.x += (event.key === 'ArrowDown' ? 0.12 : event.key === 'ArrowUp' ? -0.12 : 0);
      target.x = Math.max(-1.3, Math.min(1.3, target.x));
      invalidate();
    });
    canvas.addEventListener('pointerdown', function (event) {
      if (event.button !== 0) return;
      drag = { x: event.clientX, y: event.clientY, lastX: event.clientX, lastY: event.clientY, moved: false };
      canvas.setPointerCapture(event.pointerId);
    });
    canvas.addEventListener('pointermove', function (event) {
      if (!drag) return;
      if (Math.hypot(event.clientX - drag.x, event.clientY - drag.y) > 5) drag.moved = true;
      if (!drag.moved) return;
      target.y += (event.clientX - drag.lastX) * 0.006;
      target.x += (event.clientY - drag.lastY) * 0.006;
      target.x = Math.max(-1.3, Math.min(1.3, target.x));
      drag.lastX = event.clientX; drag.lastY = event.clientY;
      invalidate();
    });
    canvas.addEventListener('pointerup', function (event) {
      if (drag && !drag.moved && raycaster && ready && !fallbackContext) {
        var bounds = canvas.getBoundingClientRect();
        pointer.set((event.clientX - bounds.left) / bounds.width * 2 - 1,
          1 - (event.clientY - bounds.top) / bounds.height * 2);
        raycaster.setFromCamera(pointer, camera);
        // Include the solid Earth so pins on its far side cannot be selected.
        var hits = raycaster.intersectObjects([earth].concat(pins));
        if (hits[0] && hits[0].object.userData.index !== undefined) select(hits[0].object.userData.index);
      }
      drag = null;
      if (canvas.hasPointerCapture(event.pointerId)) canvas.releasePointerCapture(event.pointerId);
    });
    canvas.addEventListener('pointercancel', function () { drag = null; });
    canvas.addEventListener('lostpointercapture', function () { drag = null; });
  }

  // The same satellite map rendered as a still sphere if WebGL is unavailable.
  // The list, keyboard and city selection keep working without a GPU context.
  function fallback() {
    if (fallbackContext) return;
    ready = false;
    if (frameID) cancelAnimationFrame(frameID);
    frameID = 0;
    if (renderer) renderer.dispose();
    var replacement = canvas.cloneNode(false);
    canvas.replaceWith(replacement);
    canvas = replacement;
    canvas.width = canvas.height = 420;
    fallbackContext = canvas.getContext('2d');
    canvas.dataset.renderer = 'canvas';
    bindControls();
    localize(['en', 'es', 'de'][language]);
    var image = new Image();
    image.onload = function () {
      var buffer = document.createElement('canvas');
      fallbackWidth = buffer.width = image.width;
      fallbackHeight = buffer.height = image.height;
      var context = buffer.getContext('2d', { willReadFrequently: true });
      context.drawImage(image, 0, 0);
      fallbackPixels = context.getImageData(0, 0, image.width, image.height).data;
      ready = true;
      select(active);
    };
    image.onerror = function () {
      canvas.hidden = true;
      caption.textContent = ['Select a city to explore.', 'Elige una ciudad para explorar.', 'Wähle eine Stadt zum Erkunden.'][language];
    };
    image.src = textureURL;
  }
  function drawFallback() {
    var size = canvas.width, radius = size * 0.43, half = size / 2;
    var output = fallbackContext.createImageData(size, size);
    var cx = Math.cos(rotation.x), sx = Math.sin(rotation.x);
    var cy = Math.cos(rotation.y), sy = Math.sin(rotation.y);
    for (var y = 0; y < size; y++) for (var x = 0; x < size; x++) {
      var nx = (x - half) / radius, ny = (half - y) / radius;
      var distance = nx * nx + ny * ny;
      if (distance > 1) continue;
      var nz = Math.sqrt(1 - distance);
      var ly = ny * cx + nz * sx, lz = -ny * sx + nz * cx;
      var lx = nx * cy - lz * sy; lz = nx * sy + lz * cy;
      var u = (Math.atan2(lx, lz) / (2 * Math.PI) + 0.5) % 1;
      var v = 0.5 - Math.asin(Math.max(-1, Math.min(1, ly))) / Math.PI;
      var source = (Math.min(fallbackHeight - 1, Math.floor(v * fallbackHeight)) * fallbackWidth + Math.floor(u * fallbackWidth)) * 4;
      var pixel = (y * size + x) * 4;
      var light = 0.44 + 0.66 * Math.max(0, -nx * 0.42 + ny * 0.45 + nz * 0.78);
      for (var channel = 0; channel < 3; channel++) output.data[pixel + channel] = fallbackPixels[source + channel] * light;
      output.data[pixel + 3] = 255;
    }
    fallbackContext.putImageData(output, 0, 0);
    entries.forEach(function (entry, index) {
      var latitude = entry.lat * radians, longitude = entry.lon * radians + rotation.y;
      var x = Math.cos(latitude) * Math.sin(longitude), y = Math.sin(latitude), z = Math.cos(latitude) * Math.cos(longitude);
      var projectedY = y * cx - z * sx, depth = y * sx + z * cx;
      if (depth < 0.03) return;
      fallbackContext.beginPath();
      fallbackContext.arc(half + x * radius, half - projectedY * radius, index === active ? 4 : 2.5, 0, Math.PI * 2);
      fallbackContext.fillStyle = index === active ? '#f0ca94' : '#f4f0e9';
      fallbackContext.fill();
    });
  }

  select(active);
  bindControls();
  initialize();
})();
