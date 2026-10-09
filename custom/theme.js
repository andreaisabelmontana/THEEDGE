/* Load synchronously in <head>, before stylesheets. The portfolio always uses
   its light palette, including visits with an older saved dark preference. */
(function () {
  'use strict';

  var root = document.documentElement;
  root.setAttribute('data-theme', 'light');
  root.style.colorScheme = 'light';

  try {
    window.localStorage.removeItem('andrea-portfolio-theme');
  } catch (error) { /* Storage is optional; the light palette is already set. */ }

  var meta = document.querySelector('meta[name="theme-color"]');
  if (!meta) {
    meta = document.createElement('meta');
    meta.name = 'theme-color';
    document.head.appendChild(meta);
  }
  meta.content = '#ffffff';
})();
