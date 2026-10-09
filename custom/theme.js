/* Load synchronously in <head>, before stylesheets. This small bootstrap makes
   the saved choice available before first paint; first visits always use light. */
(function () {
  'use strict';
  if (window.__portfolioThemeLoaded) return;
  window.__portfolioThemeLoaded = true;

  var root = document.documentElement;
  var storageKey = 'andrea-portfolio-theme';
  var saved = null;
  try { saved = window.localStorage.getItem(storageKey); } catch (error) { /* Storage is optional. */ }
  root.setAttribute('data-theme', saved === 'dark' ? 'dark' : 'light');
  root.style.colorScheme = saved === 'dark' ? 'dark' : 'light';

  var labels = {
    en: { light: 'Light', dark: 'Dark', name: 'Dark theme', toLight: 'Switch to light theme', toDark: 'Switch to dark theme' },
    es: { light: 'Claro', dark: 'Oscuro', name: 'Tema oscuro', toLight: 'Cambiar al tema claro', toDark: 'Cambiar al tema oscuro' },
    de: { light: 'Hell', dark: 'Dunkel', name: 'Dunkles Design', toLight: 'Zum hellen Design wechseln', toDark: 'Zum dunklen Design wechseln' }
  };
  var icons = '<svg class="am-theme-sun" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" aria-hidden="true"><circle cx="12" cy="12" r="3.75"/><path d="M12 2v2m0 16v2M2 12h2m16 0h2M4.93 4.93l1.42 1.42m11.3 11.3 1.42 1.42M4.93 19.07l1.42-1.42m11.3-11.3 1.42-1.42"/></svg>' +
    '<svg class="am-theme-moon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M20.4 14.5A8.7 8.7 0 0 1 9.5 3.6a8.7 8.7 0 1 0 10.9 10.9Z"/></svg>';

  function updateButtons() {
    var dark = root.getAttribute('data-theme') === 'dark';
    var copy = labels[root.lang] || labels.en;
    document.querySelectorAll('[data-theme-toggle]').forEach(function (button) {
      button.setAttribute('aria-label', copy.name);
      button.setAttribute('aria-pressed', String(dark));
      button.title = dark ? copy.toLight : copy.toDark;
      var text = button.querySelector('.am-theme-label');
      if (text && text.textContent !== (dark ? copy.dark : copy.light)) text.textContent = dark ? copy.dark : copy.light;
    });
    var meta = document.querySelector('meta[name="theme-color"]');
    if (!meta) {
      meta = document.createElement('meta');
      meta.name = 'theme-color';
      document.head.appendChild(meta);
    }
    meta.content = dark ? '#100e14' : '#f7f5f9';
  }

  function applyTheme(theme, remember) {
    theme = theme === 'dark' ? 'dark' : 'light';
    root.setAttribute('data-theme', theme);
    root.style.colorScheme = theme;
    if (remember) {
      try { window.localStorage.setItem(storageKey, theme); } catch (error) { /* The control still works for this visit. */ }
    }
    updateButtons();
    document.dispatchEvent(new CustomEvent('portfolio:theme', { detail: theme }));
  }

  function ensureToggle() {
    var inner = document.querySelector('.nav .nav-inner');
    var button = document.querySelector('[data-theme-toggle]');
    if (!button) {
      button = document.createElement('button');
      button.type = 'button';
      button.className = 'am-theme-toggle';
      button.setAttribute('data-theme-toggle', '');
      button.innerHTML = icons + '<span class="am-theme-label"></span>';
    }
    if (inner) {
      if (button.parentElement !== inner) inner.appendChild(button);
      var oldTools = document.querySelector('.am-theme-tools');
      if (oldTools) oldTools.remove();
    } else if (!button.isConnected) {
      // Coursework is intentionally a simple document with no shared navbar.
      var tools = document.createElement('div');
      tools.className = 'am-theme-tools';
      tools.appendChild(button);
      document.body.prepend(tools);
    }
    updateButtons();
  }

  function boot() {
    ensureToggle();
    document.addEventListener('click', function (event) {
      var button = event.target.closest && event.target.closest('[data-theme-toggle]');
      if (button) applyTheme(root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark', true);
    });
    document.addEventListener('edge:lang', updateButtons);
    window.addEventListener('storage', function (event) {
      if (event.key === storageKey || event.key === null) applyTheme(event.newValue, false);
    });
    // The existing page runtime may replace the nav. Observe only structural
    // additions, without polling or following every animated inline style.
    new MutationObserver(function (records) {
      var navChanged = records.some(function (record) {
        return Array.prototype.some.call(record.addedNodes, function (node) {
          return node.nodeType === 1 && (node.matches('.nav, .nav-inner') || node.querySelector('.nav, .nav-inner'));
        });
      });
      if (navChanged || !document.querySelector('[data-theme-toggle]')) ensureToggle();
    }).observe(document.body, { childList: true, subtree: true });
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot, { once: true });
  else boot();
})();
