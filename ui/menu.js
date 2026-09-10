/* the overlay menu.

   the bar carries no section tabs, so this is the only way through the site
   and it has to behave: focus moves into the panel on open and back to the
   button on close, escape closes, and the page behind cannot scroll while it
   is up. focus is trapped rather than left to wander behind the overlay. */
(function () {
  const btn = document.querySelector('[data-menu-button]');
  const panel = document.querySelector('[data-menu-panel]');
  if (!btn || !panel) return;

  const FOCUSABLE = 'a[href], button:not([disabled])';
  let lastScroll = 0;

  function open() {
    lastScroll = window.scrollY;
    panel.hidden = false;
    // next frame, so the transition has a from-state to animate out of
    requestAnimationFrame(() => panel.classList.add('is-open'));
    btn.setAttribute('aria-expanded', 'true');
    document.body.style.overflow = 'hidden';
    const first = panel.querySelector(FOCUSABLE);
    if (first) first.focus();
  }

  function close() {
    panel.classList.remove('is-open');
    btn.setAttribute('aria-expanded', 'false');
    document.body.style.overflow = '';
    window.scrollTo(0, lastScroll);
    btn.focus();
    // wait out the transition before pulling it from the tree
    setTimeout(() => { panel.hidden = true; }, 320);
  }

  const isOpen = () => btn.getAttribute('aria-expanded') === 'true';

  btn.addEventListener('click', () => (isOpen() ? close() : open()));

  panel.addEventListener('click', (e) => {
    if (e.target === panel || e.target.closest('a')) close();
  });

  document.addEventListener('keydown', (e) => {
    if (!isOpen()) return;

    if (e.key === 'Escape') {
      close();
      return;
    }

    if (e.key !== 'Tab') return;

    const items = [...panel.querySelectorAll(FOCUSABLE)];
    if (!items.length) return;
    const first = items[0];
    const last = items[items.length - 1];

    if (e.shiftKey && document.activeElement === first) {
      e.preventDefault();
      last.focus();
    } else if (!e.shiftKey && document.activeElement === last) {
      e.preventDefault();
      first.focus();
    }
  });
})();
