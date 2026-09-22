(function () {
  'use strict';
  var reducedMotion = window.matchMedia('(prefers-reduced-motion: reduce)');
  var labels = {
    en: { pause: 'Pause preview', play: 'Play preview', pauseLabel: 'Pause Top Living website preview', playLabel: 'Play Top Living website preview' },
    es: { pause: 'Pausar vista previa', play: 'Reproducir vista previa', pauseLabel: 'Pausar la vista previa del sitio Top Living', playLabel: 'Reproducir la vista previa del sitio Top Living' },
    de: { pause: 'Vorschau pausieren', play: 'Vorschau abspielen', pauseLabel: 'Vorschau der Top Living Website pausieren', playLabel: 'Vorschau der Top Living Website abspielen' }
  };
  var previews = Array.from(document.querySelectorAll('[data-project-preview]')).map(function (host) {
    var media = host.querySelector('.project-preview-media') || host;
    var video = media.querySelector('.project-preview-video');
    var button = host.querySelector('.project-preview-toggle');
    if (!video || !button) return null;
    var visible = false, userPaused = false, loaded = false, failed = false;
    var pending = false, requestId = 0;
    video.muted = true;
    video.defaultMuted = true;

    function shouldPlay() {
      return visible && !document.hidden && !host.closest('[hidden]') && !reducedMotion.matches && !userPaused && !failed;
    }
    function render() {
      var copy = labels[document.documentElement.lang] || labels.en;
      button.hidden = !loaded || failed || reducedMotion.matches;
      button.textContent = userPaused ? copy.play : copy.pause;
      button.setAttribute('aria-label', userPaused ? copy.playLabel : copy.pauseLabel);
    }
    function pause() {
      requestId++;
      pending = false;
      video.pause();
    }
    function sync() {
      if (!shouldPlay()) {
        pause();
        if (reducedMotion.matches) media.classList.remove('project-preview-has-frame');
        render();
        return;
      }
      if (!loaded) {
        video.src = video.getAttribute('data-preview-src');
        loaded = true;
        video.load();
      }
      render();
      if (!video.paused || pending) return;
      var currentRequest = ++requestId;
      pending = true;
      var playback = video.play();
      if (playback && typeof playback.then === 'function') {
        playback.then(function () {
          if (currentRequest !== requestId) return;
          pending = false;
          if (!shouldPlay()) pause();
        }).catch(function (error) {
          if (currentRequest !== requestId) return;
          pending = false;
          if (error.name !== 'AbortError') {
            userPaused = true;
            media.classList.remove('project-preview-has-frame');
            render();
          }
        });
      } else {
        pending = false;
      }
    }
    video.addEventListener('playing', function () {
      if (shouldPlay()) media.classList.add('project-preview-has-frame');
      else pause();
    });
    video.addEventListener('error', function () {
      failed = true;
      pause();
      media.classList.remove('project-preview-has-frame');
      render();
    });
    button.addEventListener('click', function () {
      userPaused = !userPaused;
      sync();
    });
    // Filtering sets hidden on the card; stop immediately even before an intersection callback.
    if ('MutationObserver' in window) {
      new MutationObserver(sync).observe(host, { attributes: true, attributeFilter: ['hidden'] });
    }
    if ('IntersectionObserver' in window) {
      new IntersectionObserver(function (entries) {
        visible = entries[0].isIntersecting && entries[0].intersectionRatio >= 0.15;
        sync();
      }, { threshold: [0, 0.15] }).observe(media);
    } else {
      function checkVisibility() {
        var rect = media.getBoundingClientRect();
        visible = rect.width > 0 && rect.height > 0 && rect.bottom > 0 && rect.top < window.innerHeight;
        sync();
      }
      window.addEventListener('scroll', checkVisibility, { passive: true });
      window.addEventListener('resize', checkVisibility);
      checkVisibility();
    }
    return { sync: sync, pause: pause, render: render };
  }).filter(Boolean);

  function syncAll() { previews.forEach(function (preview) { preview.sync(); }); }
  document.addEventListener('visibilitychange', syncAll);
  window.addEventListener('pageshow', syncAll);
  window.addEventListener('pagehide', function () { previews.forEach(function (preview) { preview.pause(); }); });
  document.addEventListener('edge:lang', function () { previews.forEach(function (preview) { preview.render(); }); });
  if (reducedMotion.addEventListener) reducedMotion.addEventListener('change', syncAll);
  else reducedMotion.addListener(syncAll);
})();
