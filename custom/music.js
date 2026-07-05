/* Site music: starts on load where the browser allows it, otherwise on the
   first interaction (browsers block un-gestured audio). The audio element
   lives outside the taxi.js view, so it keeps playing across page swaps.
   A fixed toggle lets the visitor pause; that choice is remembered. */
(function () {
  if (window.__edgeMusic) return; window.__edgeMusic = true;

  var src = document.currentScript && document.currentScript.src
    ? document.currentScript.src.replace(/music\.js.*$/, 'voyager.mp3')
    : 'custom/voyager.mp3';
  var audio = new Audio(src);
  audio.loop = true;
  audio.volume = 0.35;
  audio.preload = 'auto';

  var btn = document.createElement('button');
  btn.id = 'music-toggle';
  btn.type = 'button';
  btn.setAttribute('aria-label', 'Toggle music');
  btn.style.cssText = 'position:fixed;bottom:1.2rem;left:1.2rem;z-index:99999;' +
    'width:46px;height:46px;border-radius:50%;border:2px solid #BC13FE;' +
    'background:rgba(5,5,5,.72);color:#f4f4ed;font-size:20px;line-height:1;' +
    'cursor:pointer;transition:background .25s,opacity .25s;';

  function paint() {
    var on = !audio.paused;
    btn.textContent = '♪';
    btn.style.background = on ? '#BC13FE' : 'rgba(5,5,5,.72)';
    btn.style.opacity = on ? '1' : '.7';
    btn.title = on ? 'Pause music' : 'Play music';
  }
  audio.addEventListener('play', paint);
  audio.addEventListener('pause', paint);

  btn.addEventListener('click', function (e) {
    e.stopPropagation();
    if (audio.paused) {
      audio.play().catch(function () {});
      try { localStorage.removeItem('edge-music-off'); } catch (e2) {}
    } else {
      audio.pause();
      try { localStorage.setItem('edge-music-off', '1'); } catch (e2) {}
    }
  });

  function armGestureFallback() {
    var types = ['pointerdown', 'keydown', 'touchstart', 'wheel'];
    var kick = function () {
      audio.play().then(function () {
        types.forEach(function (t) { window.removeEventListener(t, kick); });
      }).catch(function () {});
    };
    types.forEach(function (t) { window.addEventListener(t, kick, { passive: true }); });
  }

  function boot() {
    document.body.appendChild(btn);
    paint();
    var off = false;
    try { off = !!localStorage.getItem('edge-music-off'); } catch (e2) {}
    if (off) return;
    audio.play().then(paint).catch(armGestureFallback);
  }

  if (document.body) boot();
  else document.addEventListener('DOMContentLoaded', boot);
})();
