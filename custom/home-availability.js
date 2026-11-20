(() => {
  'use strict';
  const toast = document.getElementById('am-availability-toast');
  if (!toast) return;
  const live = document.getElementById('am-availability-live');
  const close = toast.querySelector('.am-availability-close');
  const title = toast.querySelector('.am-availability-title');
  const season = toast.querySelector('.am-availability-season');
  const key = 'am-home-availability-seen-v1';
  let eligible = true;
  try { eligible = sessionStorage.getItem(key) !== 'true'; } catch (_) {}
  if (!eligible) return;

  function localizeClose() {
    const captions = { en: 'Dismiss availability notice', es: 'Cerrar aviso de disponibilidad', de: 'Verfügbarkeitshinweis schließen' };
    close.setAttribute('aria-label', captions[document.documentElement.lang] || captions.en);
  }
  function dismiss() {
    toast.hidden = true;
    live.textContent = '';
  }
  function show() {
    if (document.hidden) {
      document.addEventListener('visibilitychange', show, { once: true });
      return;
    }
    toast.hidden = false;
    localizeClose();
    live.textContent = title.textContent + '. ' + season.textContent;
    try { sessionStorage.setItem(key, 'true'); } catch (_) {}
  }
  close.addEventListener('click', dismiss);
  document.addEventListener('keydown', event => {
    if (event.key === 'Escape' && !toast.hidden) dismiss();
  });
  document.addEventListener('edge:lang', localizeClose);
  const afterLoad = () => setTimeout(show, 3000);
  if (document.readyState === 'complete') afterLoad();
  else window.addEventListener('load', afterLoad, { once: true });
})();
