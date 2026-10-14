/* A small, on-demand Three.js scene using NASA Earth Observatory imagery.
   City coordinates share the same equirectangular projection as the texture. */
(function () {
  'use strict';
  if (!document.body.classList.contains('experience-page')) return;
  var canvas = document.getElementById('am-globe');
  var list = document.getElementById('am-exp-timeline');
  if (!canvas || !list) return;

  var entries = [
    { id: 'bogota', city: ['Bogotá', 'Bogotá', 'Bogotá'], country: ['Colombia', 'Colombia', 'Kolumbien'],
      detail: ['Colegio Nueva Granada and Top Living Inmobiliaria', 'Colegio Nueva Granada y Top Living Inmobiliaria', 'Colegio Nueva Granada und Top Living Inmobiliaria'],
      when: ['2010 to 2024', '2010 a 2024', '2010 bis 2024'], lat: 4.652, lon: -74.055 },
    { id: 'dublin', city: ['Dublin', 'Dublín', 'Dublin'], country: ['Ireland', 'Irlanda', 'Irland'],
      detail: ['Trinity College Dublin, first year of the degree', 'Trinity College Dublin, primer año del grado', 'Trinity College Dublin, erstes Studienjahr'],
      when: ['2022 to 2023', '2022 a 2023', '2022 bis 2023'], lat: 53.3438, lon: -6.2546 },
    // Approximate old-town coordinates: https://whc.unesco.org/en/list/311/.
    // The first-year campus comes from Andrea; no transition date is inferred.
    { id: 'segovia', city: ['Segovia', 'Segovia', 'Segovia'], country: ['Spain', 'España', 'Spanien'],
      detail: ['IE University · first year', 'IE University · primer año', 'IE University · erstes Jahr'],
      when: ['', '', ''], lat: 40.94847, lon: -4.11675 },
    { id: 'madrid', city: ['Madrid', 'Madrid', 'Madrid'], country: ['Spain', 'España', 'Spanien'],
      detail: ['IE University · subsequent years; IEX Labs and AI Global Markets', 'IE University · años posteriores; IEX Labs y AI Global Markets', 'IE University · Folgejahre; IEX Labs und AI Global Markets'],
      when: ['', '', ''], lat: 40.4168, lon: -3.7038 }
  ];
  var root = window.__SITE_ROOT || '';
  var textureURL = root + '/custom/journey-assets/earth-blue-marble.webp';
  var motion = window.matchMedia('(prefers-reduced-motion: reduce)');
  var caption = document.querySelector('#journey .am-globe-instruction');
  var active = 3, language = 0, visible = true, ready = false, frameID = 0;
  var radians = Math.PI / 180;
  var rotation = { x: entries[active].lat * radians, y: -entries[active].lon * radians };
  var target = { x: rotation.x, y: rotation.y };
  var renderer, scene, camera, world, earth, pins = [], raycaster, pointer;
  var fallbackContext, fallbackPixels, fallbackWidth, fallbackHeight, fallbackHeights;
  var usingFallback = false, graphicUnavailable = false;
  var buttons = [];
  var companion = document.getElementById('journey');
  var region = document.querySelector('.am-career-journey-layout');
  var markers = Array.from(document.querySelectorAll('[data-journey-city], [data-journey-context]'));
  var cityLabel = companion.querySelector('[data-journey-current-city]');
  var detailLabel = companion.querySelector('[data-journey-current-detail]');
  var modeLabel = companion.querySelector('[data-journey-mode]');
  var currentMarker = null, manualScrollY = null, syncFrame = 0, contextOnly = false;
  var journeyCopy = {
    places: ['Places along the way', 'Lugares del recorrido', 'Orte meines Weges'],
    'nav-certificates': ['Certificates', 'Certificados', 'Zertifikate'],
    'ie-cities': ['Segovia → Madrid, Spain', 'Segovia → Madrid, España', 'Segovia → Madrid, Spanien'],
    'first-year': ['IE University · first year', 'IE University · primer año', 'IE University · erstes Jahr'],
    'subsequent-years': ['IE University · subsequent years', 'IE University · años posteriores', 'IE University · Folgejahre']
  };

  function textIfChanged(node, value) {
    if (node && node.textContent !== value) node.textContent = value;
  }
  function describeSelection() {
    if (!cityLabel) return;
    textIfChanged(modeLabel, (manualScrollY === null ? ['In view', 'En pantalla', 'Im Blick'] : ['Exploring', 'Explorando', 'Erkunden'])[language]);
    if (contextOnly) {
      textIfChanged(cityLabel, ['IE University community', 'Comunidad de IE University', 'Gemeinschaft der IE University'][language]);
      textIfChanged(detailLabel, ['Explore the cities in my journey.', 'Explora las ciudades de mi recorrido.', 'Entdecke die Städte meines Weges.'][language]);
      return;
    }
    var entry = entries[active];
    textIfChanged(cityLabel, entry.city[language] + ', ' + entry.country[language]);
    var heading = currentMarker && currentMarker.querySelector('h3, strong, .am-career-lead');
    var campus = currentMarker && currentMarker.querySelector('[data-journey-copy]');
    textIfChanged(detailLabel, manualScrollY === null && heading ?
      heading.textContent + (campus ? ' · ' + campus.textContent : '') :
      entry.detail[language] + (entry.when[language] ? ' · ' + entry.when[language] : ''));
  }
  // Updating labels/pins must never recenter an independently rotated globe.
  function reflectSelection() {
    companion.dataset.activeCity = contextOnly ? '' : entries[active].id;
    buttons.forEach(function (button, i) {
      var selected = !contextOnly && i === active;
      button.setAttribute('aria-pressed', String(selected));
      button.parentNode.classList.toggle('is-active', selected);
    });
    pins.forEach(function (pin, i) {
      var selected = !contextOnly && i === active;
      pin.material.color.set(selected ? 0xf0ca94 : 0xf4f0e9);
      pin.scale.setScalar(selected ? 1.35 : 1);
    });
    describeSelection();
  }
  function beginExploration() {
    manualScrollY = window.scrollY;
    contextOnly = false;
    currentMarker = null;
    reflectSelection();
  }
  function modalOpen() {
    return document.documentElement.classList.contains('am-leadership-details-viewing') ||
      document.documentElement.classList.contains('am-leadership-viewing') ||
      document.querySelector('dialog[open]');
  }
  function syncToEntries() {
    syncFrame = 0;
    if (!region || modalOpen()) return;
    if (manualScrollY !== null) {
      if (Math.abs(window.scrollY - manualScrollY) < 48) return;
      manualScrollY = null;
    }
    var boundary = region.getBoundingClientRect();
    if (boundary.bottom <= 112 || boundary.top >= window.innerHeight) return;
    var focusY = Math.max(140, window.innerHeight * 0.42);
    var nearest = null, distance = Infinity;
    markers.forEach(function (marker) {
      var rect = marker.getBoundingClientRect();
      if (rect.bottom <= 112 || rect.top >= window.innerHeight || !rect.height) return;
      var delta = Math.max(rect.top - focusY, focusY - rect.bottom, 0);
      if (delta < distance) { nearest = marker; distance = delta; }
    });
    if (!nearest || nearest === currentMarker) return;
    currentMarker = nearest;
    if (nearest.dataset.journeyContext) {
      contextOnly = true;
      reflectSelection();
      invalidate();
      return;
    }
    var index = entries.findIndex(function (entry) { return entry.id === nearest.dataset.journeyCity; });
    if (index !== -1) select(index);
  }
  function scheduleSync() {
    if (!syncFrame) syncFrame = requestAnimationFrame(syncToEntries);
  }
  window.addEventListener('scroll', scheduleSync, { passive: true });
  window.addEventListener('resize', scheduleSync);
  window.addEventListener('pageshow', scheduleSync);
  if (region) new ResizeObserver(scheduleSync).observe(region);

  // Let native wheel/touch scrolling reach controls in a short sticky panel.
  // Normal-flow mobile and panels that fit keep the site's smooth scrolling.
  var companionResize = new ResizeObserver(function () {
    companion.toggleAttribute('data-lenis-prevent',
      getComputedStyle(companion).position === 'sticky' &&
      companion.scrollHeight > companion.clientHeight + 1);
  });
  companionResize.observe(companion);
  Array.from(companion.children).forEach(function (child) { companionResize.observe(child); });


  // Keep the satellite geography, with neighboring blue bands for a soft surface.
  // Relief below is derived from image luminance, not an elevation measurement.
  var surfaceLuma = [0.2126, 0.7152, 0.0722];
  var surfaceLighting = { ambient: 0.7, directional: 0.65 };
  function srgbToLinear(value) {
    return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
  }
  function linearToSrgb(value) {
    value = Math.max(0, Math.min(1, value));
    return value <= 0.0031308 ? value * 12.92 : 1.055 * Math.pow(value, 1 / 2.4) - 0.055;
  }
  var surfacePalette = {
    ocean: 0x7ea5d1, oceanLight: 0x9fbee0,
    land: 0x91aec8, landLight: 0xd4e2ed
  };
  Object.keys(surfacePalette).forEach(function (key) {
    var hex = surfacePalette[key];
    surfacePalette[key] = [hex >> 16 & 255, hex >> 8 & 255, hex & 255].map(function (channel) {
      return srgbToLinear(channel / 255);
    });
  });
  var surfaceLinearLookup = Array.from({ length: 256 }, function (_, channel) {
    return srgbToLinear(channel / 255);
  });
  function surfaceSmoothstep(low, high, value) {
    var amount = Math.max(0, Math.min(1, (value - low) / (high - low)));
    return amount * amount * (3 - 2 * amount);
  }
  function blueSurfaceShader() {
    function number(value) { return value.toFixed(9); }
    function color(key) { return 'vec3(' + surfacePalette[key].map(number).join(',') + ')'; }
    return 'uniform vec2 journeyTexelStep;\n' +
      'float journeyHeight(vec3 texel) { return sqrt(clamp(dot(texel, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0)); }\n' +
      'vec3 journeyBlueSurface(vec3 texel, vec3 litSurface, vec4 neighbors) {\n' +
      'float luminance = clamp(dot(texel, vec3(0.2126, 0.7152, 0.0722)), 0.0, 1.0);\n' +
      'float warm = (max(texel.r, texel.g) - texel.b) / max(max(max(texel.r, texel.g), texel.b), 0.0001);\n' +
      // Neutral snow/ice is land; saturated cyan shallows must stay ocean.
      'float neutral = min(min(texel.r, texel.g), texel.b) / max(max(max(texel.r, texel.g), texel.b), 0.0001);\n' +
      'float ice = smoothstep(0.08, 0.30, luminance) * smoothstep(0.25, 0.60, neutral);\n' +
      'float land = max(smoothstep(0.10, 0.35, warm), ice);\n' +
      'float light = clamp((dot(litSurface, vec3(0.2126, 0.7152, 0.0722)) - ' + number(surfaceLighting.ambient) +
        ') / ' + number(surfaceLighting.directional) + ', 0.0, 1.0);\n' +
      'vec3 ocean = mix(' + color('ocean') + ', ' + color('oceanLight') + ', light);\n' +
      // Preserve regional tones and fine detail from the actual NASA image.
      // Four neighboring texels give restrained northwest-lit surface relief;
      // oceans remain smooth, and no procedural noise is added.
      'float height = sqrt(luminance);\n' +
      'float relief = clamp(1.2 * (neighbors.x - neighbors.y + neighbors.z - neighbors.w) + 1.6 * (height - dot(neighbors, vec4(0.25))), -0.16, 0.16);\n' +
      'float detail = clamp(0.16 + 0.22 * light + 0.62 * height + relief, 0.0, 1.0);\n' +
      'vec3 terrain = mix(' + color('land') + ', ' + color('landLight') + ', detail);\n' +
      'return mix(ocean, terrain, land);\n}\n';
  }
  function fallbackBlueSurface(source, index, light, output, pixel) {
    var red = surfaceLinearLookup[source[index]], green = surfaceLinearLookup[source[index + 1]];
    var blue = surfaceLinearLookup[source[index + 2]];
    var luminance = red * surfaceLuma[0] + green * surfaceLuma[1] + blue * surfaceLuma[2];
    var warm = (Math.max(red, green) - blue) / Math.max(red, green, blue, 0.0001);
    var neutral = Math.min(red, green, blue) / Math.max(red, green, blue, 0.0001);
    var ice = surfaceSmoothstep(0.08, 0.30, luminance) * surfaceSmoothstep(0.25, 0.60, neutral);
    var land = Math.max(surfaceSmoothstep(0.10, 0.35, warm), ice);
    light = Math.max(0, Math.min(1, (light - surfaceLighting.ambient) / surfaceLighting.directional));
    var sample = index / 4, row = Math.floor(sample / fallbackWidth), column = sample % fallbackWidth;
    var west = fallbackHeights[row * fallbackWidth + (column - 2 + fallbackWidth) % fallbackWidth];
    var east = fallbackHeights[row * fallbackWidth + (column + 2) % fallbackWidth];
    var north = fallbackHeights[Math.max(0, row - 2) * fallbackWidth + column];
    var south = fallbackHeights[Math.min(fallbackHeight - 1, row + 2) * fallbackWidth + column];
    var height = Math.sqrt(luminance);
    var relief = Math.max(-0.16, Math.min(0.16,
      1.2 * (west - east + north - south) + 1.6 * (height - (west + east + north + south) * 0.25)));
    var detail = Math.max(0, Math.min(1, 0.16 + 0.22 * light + 0.62 * height + relief));
    for (var channel = 0; channel < 3; channel++) {
      var ocean = surfacePalette.ocean[channel] + (surfacePalette.oceanLight[channel] - surfacePalette.ocean[channel]) * light;
      var terrain = surfacePalette.land[channel] + (surfacePalette.landLight[channel] - surfacePalette.land[channel]) * detail;
      output[pixel + channel] = Math.round(linearToSrgb(ocean + (terrain - ocean) * land) * 255);
    }
  }

  entries.forEach(function (entry, index) {
    var item = document.createElement('li');
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'am-journey-button';
    button.innerHTML = '<span class="am-exp-line1"><span class="am-journey-city"></span>' +
      '<span class="am-journey-country"></span></span>';
    button.addEventListener('click', function () { beginExploration(); select(index); });
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
    document.querySelectorAll('[data-journey-copy]').forEach(function (node) {
      var text = journeyCopy[node.dataset.journeyCopy];
      if (text) node.textContent = text[language];
    });
    list.setAttribute('aria-label', ['Journey cities', 'Ciudades del recorrido', 'Städte meines Weges'][language]);
    // Other page-local listeners translate the source heading in this event.
    // Read its final text after all of them have run.
    Promise.resolve().then(describeSelection);
    caption.textContent = graphicUnavailable ?
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
    contextOnly = false;
    reflectSelection();
    target.x = entries[index].lat * radians;
    var longitude = -entries[index].lon * radians;
    target.y = rotation.y + Math.atan2(Math.sin(longitude - rotation.y), Math.cos(longitude - rotation.y));
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
    var amount = motion.matches || usingFallback ? 1 : 0.085;
    rotation.x += (target.x - rotation.x) * amount;
    rotation.y += (target.y - rotation.y) * amount;
    if (difference < 0.001) { rotation.x = target.x; rotation.y = target.y; }
    if (fallbackContext) drawFallback();
    else {
      world.rotation.set(rotation.x, rotation.y, 0, 'XYZ');
      renderer.render(scene, camera);
    }
    if (amount < 1 && difference > 0.001) invalidate();
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
      // Diffuse-only illumination keeps specular highlights out of both palettes.
      earth = new THREE.Mesh(geometry, new THREE.MeshLambertMaterial({ color: 0xffffff }));
      earth.material.onBeforeCompile = function (shader) {
        // Three r149 decodes the sRGB map before map_fragment. Save the unlit
        // texel for geography, and shade white to measure illumination separately.
        var image = earth.material.map && earth.material.map.image;
        shader.uniforms.journeyTexelStep = { value: new THREE.Vector2(
          2 / (image && image.width || 2048), 2 / (image && image.height || 1024)) };
        shader.fragmentShader = blueSurfaceShader() + shader.fragmentShader.replace(
          '#include <map_fragment>',
          '#include <map_fragment>\nvec3 journeyTexel = diffuseColor.rgb;\ndiffuseColor.rgb = vec3(1.0);'
        ).replace(
          '#include <output_fragment>',
          'vec4 journeyNeighbors = vec4(\n' +
          'journeyHeight(texture2D(map, vec2(fract(vUv.x - journeyTexelStep.x), vUv.y)).rgb),\n' +
          'journeyHeight(texture2D(map, vec2(fract(vUv.x + journeyTexelStep.x), vUv.y)).rgb),\n' +
          'journeyHeight(texture2D(map, vec2(vUv.x, clamp(vUv.y + journeyTexelStep.y, 0.0, 1.0))).rgb),\n' +
          'journeyHeight(texture2D(map, vec2(vUv.x, clamp(vUv.y - journeyTexelStep.y, 0.0, 1.0))).rgb));\n' +
          'outgoingLight = journeyBlueSurface(journeyTexel, outgoingLight, journeyNeighbors);\n#include <output_fragment>'
        );
      };
      earth.material.customProgramCacheKey = function () { return 'journey-ocean-land-surface-v4'; };
      world.add(earth);
      scene.add(new THREE.AmbientLight(0xffffff, surfaceLighting.ambient));
      var sun = new THREE.DirectionalLight(0xffffff, surfaceLighting.directional);
      sun.position.set(-3, 4, 5);
      scene.add(sun);
      // A thin blue atmospheric edge, lit with the surface rather than a glow halo.
      var atmosphere = new THREE.Mesh(new THREE.SphereGeometry(1.006, 64, 40),
        new THREE.ShaderMaterial({
          transparent: true, depthWrite: false,
          uniforms: { tint: { value: new THREE.Color(0xc5ddfe) } },
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
        if (usingFallback) { texture.dispose(); return; }
        texture.encoding = THREE.sRGBEncoding;
        texture.anisotropy = Math.min(4, renderer.capabilities.getMaxAnisotropy());
        earth.material.map = texture;
        earth.material.needsUpdate = true;
        ready = true;
        canvas.dataset.renderer = 'webgl';
        resize();
        reflectSelection();
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
  motion.addEventListener('change', function () {
    // Settle the current interaction, retaining its city or neutral context.
    if (motion.matches) { rotation.x = target.x; rotation.y = target.y; }
    invalidate();
  });

  function bindControls() {
    var drag = null;
    canvas.tabIndex = 0;
    canvas.addEventListener('keydown', function (event) {
      var keys = ['ArrowLeft', 'ArrowRight', 'ArrowUp', 'ArrowDown'];
      if (keys.indexOf(event.key) < 0) return;
      event.preventDefault();
      beginExploration();
      target.y += (event.key === 'ArrowRight' ? 0.18 : event.key === 'ArrowLeft' ? -0.18 : 0);
      target.x += (event.key === 'ArrowDown' ? 0.12 : event.key === 'ArrowUp' ? -0.12 : 0);
      target.x = Math.max(-1.3, Math.min(1.3, target.x));
      invalidate();
    });
    canvas.addEventListener('pointerdown', function (event) {
      if (event.button !== 0) return;
      beginExploration();
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
    if (usingFallback) return;
    usingFallback = true;
    ready = false;
    if (frameID) cancelAnimationFrame(frameID);
    frameID = 0;
    if (renderer) renderer.dispose();
    var replacement = canvas.cloneNode(false);
    canvas.replaceWith(replacement);
    canvas = replacement;
    canvas.width = canvas.height = 420;
    try { fallbackContext = canvas.getContext('2d'); }
    catch (error) { showCityOnly(); return; }
    if (!fallbackContext) { showCityOnly(); return; }
    canvas.dataset.renderer = 'canvas';
    bindControls();
    localize(['en', 'es', 'de'][language]);
    var image = new Image();
    image.onload = function () {
      try {
        var buffer = document.createElement('canvas');
        fallbackWidth = buffer.width = image.width;
        fallbackHeight = buffer.height = image.height;
        var context = buffer.getContext('2d', { willReadFrequently: true });
        if (!context) { showCityOnly(); return; }
        context.drawImage(image, 0, 0);
        fallbackPixels = context.getImageData(0, 0, image.width, image.height).data;
        // Cache the source field once; interaction frames only read four values.
        fallbackHeights = new Float32Array(image.width * image.height);
        for (var i = 0; i < fallbackHeights.length; i++) {
          var pixel = i * 4;
          fallbackHeights[i] = Math.sqrt(surfaceLinearLookup[fallbackPixels[pixel]] * surfaceLuma[0] +
            surfaceLinearLookup[fallbackPixels[pixel + 1]] * surfaceLuma[1] +
            surfaceLinearLookup[fallbackPixels[pixel + 2]] * surfaceLuma[2]);
        }
        ready = true;
        reflectSelection();
        invalidate();
      } catch (error) { showCityOnly(); }
    };
    image.onerror = showCityOnly;
    image.src = textureURL;
  }
  function showCityOnly() {
    ready = false;
    graphicUnavailable = true;
    canvas.hidden = true;
    canvas.dataset.renderer = 'unavailable';
    localize(['en', 'es', 'de'][language]);
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
      var light = surfaceLighting.ambient + surfaceLighting.directional * Math.max(0, (-3 * nx + 4 * ny + 5 * nz) / Math.sqrt(50));
      fallbackBlueSurface(fallbackPixels, source, light, output.data, pixel);
      output.data[pixel + 3] = 255;
    }
    fallbackContext.putImageData(output, 0, 0);
    entries.forEach(function (entry, index) {
      var latitude = entry.lat * radians, longitude = entry.lon * radians + rotation.y;
      var x = Math.cos(latitude) * Math.sin(longitude), y = Math.sin(latitude), z = Math.cos(latitude) * Math.cos(longitude);
      var projectedY = y * cx - z * sx, depth = y * sx + z * cx;
      if (depth < 0.03) return;
      fallbackContext.beginPath();
      fallbackContext.arc(half + x * radius, half - projectedY * radius, index === active && !contextOnly ? 4 : 2.5, 0, Math.PI * 2);
      fallbackContext.fillStyle = index === active && !contextOnly ? '#f0ca94' : '#f4f0e9';
      fallbackContext.fill();
    });
  }

  select(active);
  bindControls();
  initialize();
  scheduleSync();
})();
