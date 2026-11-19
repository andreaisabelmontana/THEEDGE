/* Contact-only NASA/Ames TDRS model. No changes to shared Three.js scenes.
   NASA source: https://science.nasa.gov/3d-resources/tracking-and-data-relay-satellites-tdrs-c/
   Model-viewer 4.1.0 (Google, Apache-2.0), isolated custom element. */
(function () {
  'use strict';
  function init() {
    var host = document.querySelector('body.contact-page .c.is-on-track-home');
    if (!host || host.querySelector('.contact-satellite')) return;
    var base = 'https://assets.science.nasa.gov/';
    var file = 'assets/science/cds/3d/resources/model/tracking-and-data-relay-satellites-(tdrs)-(c)/Tracking%20and%20Data%20Relay%20Satellites%20(TDRS)%20(C)';
    var poster = base + 'dynamicimage/' + file + '.png?w=640&fit=clip';
    var stage = document.createElement('div');
    stage.className = 'contact-satellite';
    stage.innerHTML = '<div class="contact-satellite-flight" aria-hidden="true"><img class="contact-satellite-poster" alt="" width="640" height="370" src="' + poster + '"></div><div class="contact-satellite-credit"><a href="https://science.nasa.gov/3d-resources/tracking-and-data-relay-satellites-tdrs-c/" target="_blank" rel="noopener noreferrer">NASA/Ames · TDRS</a><button type="button" aria-label="Pause satellite animation" aria-pressed="false" hidden>Ⅱ</button></div>';
    host.appendChild(stage);
    var flight = stage.querySelector('.contact-satellite-flight');
    var button = stage.querySelector('button');
    var reduced = matchMedia('(prefers-reduced-motion: reduce)');
    var userPaused = false, visible = true, loaded = false, viewer;
    function sync() {
      var moving = loaded && !reduced.matches && !userPaused && visible && !document.hidden;
      stage.classList.toggle('is-moving', moving);
      if (viewer) viewer.autoRotate = moving;
      button.hidden = !loaded || reduced.matches;
      button.textContent = userPaused ? '▷' : 'Ⅱ';
      button.setAttribute('aria-label', userPaused ? 'Resume satellite animation' : 'Pause satellite animation');
      button.setAttribute('aria-pressed', String(userPaused));
    }
    button.addEventListener('click', function () { userPaused = !userPaused; sync(); });
    reduced.addEventListener('change', sync);
    document.addEventListener('visibilitychange', sync);
    new ResizeObserver(function () {
      stage.style.setProperty('--satellite-travel', Math.max(0, stage.clientWidth - flight.clientWidth) + 'px');
    }).observe(stage);
    new IntersectionObserver(function (entries) { visible = entries[0].isIntersecting; sync(); }).observe(stage);
    // Static official preview is always available, including without WebGL.
    var probe = document.createElement('canvas');
    var gl;
    try { gl = probe.getContext('webgl2'); } catch (_) {}
    if (!gl) return;
    var lose = gl.getExtension('WEBGL_lose_context'); if (lose) lose.loseContext();
    var script = document.createElement('script');
    script.type = 'module';
    script.src = 'https://unpkg.com/@google/model-viewer@4.1.0/dist/model-viewer.min.js';
    script.onerror = function () { stage.dataset.renderer = 'static'; };
    document.head.appendChild(script);
    customElements.whenDefined('model-viewer').then(function () {
      if (!host.isConnected) return;
      viewer = document.createElement('model-viewer');
      viewer.setAttribute('src', base + 'content/dam/science/cds/3d/resources/model/tracking-and-data-relay-satellites-(tdrs)-(c)/Tracking%20and%20Data%20Relay%20Satellites%20(TDRS)%20(C).glb');
      viewer.setAttribute('alt', 'Textured NASA tracking and data relay satellite');
      viewer.setAttribute('aria-hidden', 'true');
      viewer.setAttribute('tabindex', '-1');
      viewer.setAttribute('loading', 'eager');
      viewer.setAttribute('reveal', 'auto');
      viewer.setAttribute('interaction-prompt', 'none');
      viewer.setAttribute('camera-orbit', '35deg 65deg 112%');
      viewer.setAttribute('field-of-view', '28deg');
      viewer.setAttribute('orientation', '0deg 0deg -15deg');
      viewer.setAttribute('rotation-per-second', '10deg');
      viewer.setAttribute('auto-rotate-delay', '0');
      viewer.setAttribute('shadow-intensity', '0');
      viewer.setAttribute('exposure', '1');
      viewer.addEventListener('load', function () { loaded = true; stage.classList.add('is-rendered'); stage.dataset.renderer = 'webgl'; sync(); });
      viewer.addEventListener('error', function () { loaded = false; stage.classList.remove('is-rendered'); stage.dataset.renderer = 'static'; sync(); });
      flight.appendChild(viewer);
      sync();
    });
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init, { once: true });
  else init();
})();