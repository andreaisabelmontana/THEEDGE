/* A small original orbital sculpture. Uses the page's existing Three.js r149;
   all surfaces are geometry, with no textures or additional dependencies. */
(function () {
  'use strict';

  var host = document.querySelector('[data-space-orbit]');
  if (!host || host.dataset.spaceInitialized) return;
  var canvas = host.querySelector('[data-space-canvas]');
  var fallback = host.querySelector('[data-space-fallback]');
  var toggle = host.querySelector('[data-space-toggle]');
  if (!canvas || !fallback) return;
  host.dataset.spaceInitialized = 'true';
  canvas.setAttribute('aria-hidden', 'true');
  canvas.removeAttribute('tabindex');

  var reduced = window.matchMedia('(prefers-reduced-motion: reduce)');
  var finePointer = window.matchMedia('(hover: hover) and (pointer: fine)');
  var language = 'en';
  var renderer, scene, camera, sculpture, planet, moon;
  var appliedSize = null;
  var geometries = [], materials = [];
  var resizeObserver, intersectionObserver;
  var ready = false, disposed = false, visible = true, manuallyPaused = false;
  var frameID = 0, lastFrame = 0, time = 0, renderCount = 0;
  var tilt = { x: 0, y: 0, targetX: 0, targetY: 0 };
  var labels = {
    en: ['Pause orbital animation', 'Resume orbital animation', 'Animation paused: reduced motion'],
    es: ['Pausar la animación orbital', 'Reanudar la animación orbital', 'Animación pausada: movimiento reducido'],
    de: ['Orbitanimation pausieren', 'Orbitanimation fortsetzen', 'Animation pausiert: reduzierte Bewegung']
  };

  function canAnimate() {
    return ready && !disposed && visible && !document.hidden && !manuallyPaused && !reduced.matches;
  }

  function state() {
    var value = disposed ? 'disposed' : !ready ? 'fallback' : reduced.matches ? 'reduced' :
      manuallyPaused ? 'paused' : document.hidden ? 'hidden' : !visible ? 'offscreen' : 'running';
    host.dataset.motion = value;
    if (!toggle) return;
    var label = labels[language][reduced.matches ? 2 : manuallyPaused ? 1 : 0];
    toggle.setAttribute('aria-label', label);
    toggle.title = label;
    toggle.dataset.action = manuallyPaused || reduced.matches ? 'resume' : 'pause';
    toggle.disabled = !ready || reduced.matches;
    toggle.hidden = !ready;
  }

  function localize(event) {
    var lang = event && typeof event.detail === 'string' ? event.detail : document.documentElement.lang;
    language = Object.prototype.hasOwnProperty.call(labels, lang) ? lang : 'en';
    state();
  }

  function stop() {
    if (frameID) cancelAnimationFrame(frameID);
    frameID = 0;
    lastFrame = 0;
  }

  function synchronize() {
    stop();
    state();
    if (canAnimate()) frameID = requestAnimationFrame(frame);
  }

  function releaseGPU(loseContext) {
    stop();
    appliedSize = null;
    geometries.forEach(function (geometry) { geometry.dispose(); });
    materials.forEach(function (material) { material.dispose(); });
    geometries = [];
    materials = [];
    if (renderer) {
      var oldRenderer = renderer;
      renderer = null;
      oldRenderer.dispose();
      if (loseContext && !oldRenderer.getContext().isContextLost()) oldRenderer.forceContextLoss();
    }
    scene = camera = sculpture = planet = moon = null;
  }

  function showFallback() {
    ready = false;
    host.dataset.renderer = canvas.dataset.renderer = 'fallback';
    host.dataset.spaceReady = 'false';
    canvas.hidden = true;
    fallback.removeAttribute('hidden');
    releaseGPU(false);
    state();
  }

  function geometry(value) { geometries.push(value); return value; }
  function material(value) { materials.push(value); return value; }

  function draw() {
    if (!renderer || disposed) return false;
    try {
      renderer.render(scene, camera);
      host.dataset.frameCount = String(++renderCount);
      return true;
    } catch (error) {
      showFallback();
      return false;
    }
  }

  function resize() {
    if (!renderer || disposed) return;
    var rect = host.getBoundingClientRect();
    var width = Math.max(1, Math.round(rect.width));
    var height = Math.max(1, Math.round(rect.height));
    var pixelRatio = Math.min(window.devicePixelRatio || 1, 1.5);
    if (appliedSize && appliedSize.width === width && appliedSize.height === height &&
        appliedSize.pixelRatio === pixelRatio) return;
    var aspect = width / height;
    // Keep the complete rings and moon in view, including the narrow mobile tile.
    var halfHeight = Math.max(1.69, 1.69 / aspect);
    camera.left = -halfHeight * aspect;
    camera.right = halfHeight * aspect;
    camera.top = halfHeight;
    camera.bottom = -halfHeight;
    camera.updateProjectionMatrix();
    renderer.setPixelRatio(pixelRatio);
    renderer.setSize(width, height, false);
    appliedSize = { width: width, height: height, pixelRatio: pixelRatio };
    draw();
  }

  function frame(now) {
    frameID = 0;
    if (!canAnimate()) { state(); return; }
    if (!lastFrame) lastFrame = now;
    var elapsed = now - lastFrame;
    if (elapsed >= 1000 / 30) {
      time += Math.min(elapsed / 1000, 0.1);
      lastFrame = now;
      planet.rotation.y = time * Math.PI * 2 / 40;
      var orbit = time * Math.PI * 2 / 20 + 0.6;
      moon.position.set(Math.cos(orbit) * 1.49, Math.sin(orbit) * 1.49, 0);
      tilt.x += (tilt.targetX - tilt.x) * 0.09;
      tilt.y += (tilt.targetY - tilt.y) * 0.09;
      sculpture.rotation.x = tilt.x;
      sculpture.rotation.y = tilt.y;
      if (!draw()) return;
    }
    if (canAnimate()) frameID = requestAnimationFrame(frame);
  }

  function initialize() {
    if (disposed || ready) return;
    showFallback();
    var THREE = window.THREE;
    if (!THREE) return;
    try {
      var attributes = { alpha: true, antialias: true, powerPreference: 'low-power' };
      var context = canvas.getContext('webgl2', attributes) || canvas.getContext('webgl', attributes);
      if (!context || context.isContextLost()) return;
      renderer = new THREE.WebGLRenderer({ canvas: canvas, context: context, alpha: true, antialias: true });
      renderer.setClearColor(0xffffff, 0);
      renderer.outputEncoding = THREE.sRGBEncoding;
      renderer.toneMapping = THREE.ACESFilmicToneMapping;
      renderer.toneMappingExposure = 1.08;
      scene = new THREE.Scene();
      camera = new THREE.OrthographicCamera(-2.2, 2.2, 1.69, -1.69, 0.1, 20);
      camera.position.set(0, 0.12, 6);
      camera.lookAt(0, 0, 0);
      sculpture = new THREE.Group();
      scene.add(sculpture);
      planet = new THREE.Group();
      sculpture.add(planet);

      var cobalt = material(new THREE.MeshPhysicalMaterial({
        color: 0x3369e8, metalness: 0.3, roughness: 0.3, clearcoat: 1, clearcoatRoughness: 0.16
      }));
      var pearl = material(new THREE.MeshPhysicalMaterial({
        color: 0xe9eef8, metalness: 0.42, roughness: 0.32, clearcoat: 0.65,
        side: THREE.DoubleSide
      }));
      var silver = material(new THREE.MeshStandardMaterial({ color: 0xc4d3ee, metalness: 0.35, roughness: 0.36 }));
      var edgeBlue = material(new THREE.MeshStandardMaterial({ color: 0x3369e8, metalness: 0.2, roughness: 0.4 }));
      planet.add(new THREE.Mesh(geometry(new THREE.SphereGeometry(0.82, 48, 32)), cobalt));

      // Raised meridians follow the globe; the solid sphere naturally hides their far side.
      [0.28, 1.38].forEach(function (angle) {
        var meridian = new THREE.Mesh(geometry(new THREE.TorusGeometry(0.824, 0.0045, 5, 96)), silver);
        meridian.rotation.y = angle;
        planet.add(meridian);
      });

      var orbitPlane = new THREE.Group();
      orbitPlane.rotation.set(1.12, -0.1, -0.4);
      sculpture.add(orbitPlane);
      orbitPlane.add(new THREE.Mesh(geometry(new THREE.RingGeometry(1.06, 1.31, 96)), pearl));
      orbitPlane.add(new THREE.Mesh(geometry(new THREE.TorusGeometry(1.315, 0.009, 6, 96)), edgeBlue));
      orbitPlane.add(new THREE.Mesh(geometry(new THREE.TorusGeometry(1.06, 0.006, 6, 96)), silver));
      moon = new THREE.Mesh(geometry(new THREE.SphereGeometry(0.105, 20, 16)), pearl);
      moon.position.set(Math.cos(0.6) * 1.49, Math.sin(0.6) * 1.49, 0);
      orbitPlane.add(moon);

      scene.add(new THREE.HemisphereLight(0xffffff, 0xadc4f2, 1.2));
      var key = new THREE.DirectionalLight(0xffffff, 2.7);
      key.position.set(-3, 4, 5);
      scene.add(key);
      var rim = new THREE.DirectionalLight(0xc9dcff, 1.8);
      rim.position.set(4, 1, -2);
      scene.add(rim);
      resize();
      if (!renderer || !draw()) return;
      ready = true;
      host.dataset.renderer = canvas.dataset.renderer = 'webgl';
      host.dataset.spaceReady = 'true';
      canvas.hidden = false;
      fallback.setAttribute('hidden', '');
      synchronize();
    } catch (error) {
      showFallback();
    }
  }

  function pointerMove(event) {
    if (!canAnimate() || !finePointer.matches || event.pointerType === 'touch') return;
    var rect = host.getBoundingClientRect();
    tilt.targetX = ((event.clientY - rect.top) / Math.max(1, rect.height) - 0.5) * 0.1;
    tilt.targetY = ((event.clientX - rect.left) / Math.max(1, rect.width) - 0.5) * 0.14;
  }

  function pointerLeave() {
    if (!canAnimate()) return;
    tilt.targetX = tilt.targetY = 0;
  }

  function pauseToggle() {
    if (!ready || reduced.matches) return;
    manuallyPaused = !manuallyPaused;
    synchronize();
  }

  function contextLost(event) {
    event.preventDefault();
    showFallback();
  }

  function pageHide(event) {
    disposed = true;
    ready = false;
    if (resizeObserver) resizeObserver.disconnect();
    if (intersectionObserver) intersectionObserver.disconnect();
    // A bfcache entry keeps its context reusable, but releases every GPU resource.
    releaseGPU(!event.persisted);
    canvas.hidden = true;
    fallback.removeAttribute('hidden');
    host.dataset.renderer = canvas.dataset.renderer = 'fallback';
    host.dataset.spaceReady = 'false';
    state();
  }

  function observe() {
    if (window.ResizeObserver) {
      resizeObserver = new ResizeObserver(resize);
      resizeObserver.observe(host);
    }
    if (window.IntersectionObserver) {
      intersectionObserver = new IntersectionObserver(function (entries) {
        visible = entries[0].isIntersecting;
        synchronize();
      }, { threshold: 0.01 });
      intersectionObserver.observe(host);
    }
  }

  host.addEventListener('pointermove', pointerMove);
  host.addEventListener('pointerleave', pointerLeave);
  if (toggle) toggle.addEventListener('click', pauseToggle);
  canvas.addEventListener('webglcontextlost', contextLost);
  document.addEventListener('edge:lang', localize);
  document.addEventListener('visibilitychange', synchronize);
  window.addEventListener('resize', resize);
  window.addEventListener('pagehide', pageHide);
  window.addEventListener('pageshow', function (event) {
    if (!event.persisted || !disposed) return;
    disposed = false;
    observe();
    initialize();
  });
  if (reduced.addEventListener) reduced.addEventListener('change', synchronize);
  else reduced.addListener(synchronize);
  localize();
  observe();
  initialize();
})();
