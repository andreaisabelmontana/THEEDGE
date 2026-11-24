/* Arcade shell: game registry, hash routing, shared layout and the global
 * "show the thinking" switch. Each game registers an object with
 *   id, title, tag, blurb, chips[], how { algo, ds, cost }, glyph(), mount(ctx)
 * and mount() returns an optional cleanup function.
 */
'use strict';

const Arcade = (() => {
  const games = [];
  let cleanup = null;
  let thinking = false;
  const listeners = new Set();

  function el(tag, attrs, ...kids) {
    const n = document.createElement(tag);
    if (attrs) for (const k in attrs) {
      const v = attrs[k];
      if (v == null || v === false) continue;
      if (k === 'class') n.className = v;
      else if (k === 'text') n.textContent = v;
      else if (k === 'html') n.innerHTML = v;
      else if (k.startsWith('on')) n.addEventListener(k.slice(2), v);
      else n.setAttribute(k, v === true ? '' : v);
    }
    for (const kid of kids.flat()) if (kid != null) n.append(kid);
    return n;
  }

  /* Sequential ramp for heat maps: dark plum -> lilac -> warm gold. */
  const STOPS = [[36, 27, 45], [117, 67, 154], [199, 160, 238], [255, 209, 102]];
  function heat(t) {
    t = Math.max(0, Math.min(1, t));
    const x = t * (STOPS.length - 1), i = Math.min(STOPS.length - 2, Math.floor(x)), f = x - i;
    const a = STOPS[i], b = STOPS[i + 1];
    return `rgb(${a.map((v, k) => Math.round(v + (b[k] - v) * f)).join(',')})`;
  }

  function store(key, val) {
    try {
      if (val === undefined) return localStorage.getItem('arcade:' + key);
      localStorage.setItem('arcade:' + key, val);
    } catch (_) { return null; }
  }

  function setThinking(on) {
    thinking = on;
    document.body.classList.toggle('thinking', on);
    const box = document.getElementById('think-global');
    if (box) box.checked = on;
    store('thinking', on ? '1' : '0');
    listeners.forEach(fn => { try { fn(on); } catch (e) { console.error(e); } });
  }

  function register(game) { games.push(game); }

  const isolation = { id: 'isolation', title: 'Isolation', tag: 'Knight strategy' };
  const compactIndex = window.matchMedia('(max-width: 640px)');

  function renderTabs() {
    const nav = document.getElementById('tabs');
    const entries = [...games, isolation];
    document.getElementById('index-count').textContent = entries.length;
    nav.replaceChildren(...entries.map((g, index) => el('a', { class: 'tab', href: '#' + g.id },
      el('span', { class: 'tab-order', 'aria-hidden': 'true', text: String(index + 1).padStart(2, '0') }),
      el('span', { text: g.title })
    )));
    nav.addEventListener('click', event => {
      const link = event.target.closest('.tab');
      if (!link || event.button || event.altKey || event.ctrlKey || event.metaKey || event.shiftKey) return;
      if (compactIndex.matches) document.getElementById('game-index').open = false;
      // Keep keyboard focus out of the now-closed disclosure and put it just before play.
      document.getElementById('app').focus({ preventScroll: true });
    });
  }

  function renderIsolation(app) {
    const frame = el('iframe', {
      class: 'isolation-frame',
      src: '../../isolation/?embed=arcade',
      title: 'Isolation game'
    });
    document.title = 'Isolation · Arcade · Andrea Montaña';
    app.append(frame, el('p', { class: 'isolation-fallback' }, el('a', {
      href: '../../isolation/?view=full', text: 'Open the full-size Isolation board'
    })));
    let observer;
    frame.addEventListener('load', () => {
      const content = frame.contentDocument?.querySelector('main');
      if (!content) return;
      const resize = () => { frame.style.height = Math.ceil(content.getBoundingClientRect().height) + 2 + 'px'; };
      observer?.disconnect();
      observer = new ResizeObserver(resize);
      observer.observe(content);
      resize();
    });
    return () => observer?.disconnect();
  }

  function renderGame(app, g) {
    const stage = el('div', { class: 'stage' });
    const controls = el('div', { class: 'controls' });
    const status = el('p', { class: 'status', role: 'status', 'aria-live': 'polite' });
    const stats = el('dl', { class: 'stats' });
    const think = el('div', { class: 'think' });
    app.append(
      el('header', { class: 'game-head' },
        el('p', { class: 'eyebrow', text: g.tag }),
        el('h1', { text: g.title }),
        el('p', { class: 'lede', text: g.blurb })
      ),
      el('div', { class: 'game-grid' },
        stage,
        el('aside', { class: 'panel' },
          el('div', { class: 'box' }, el('h3', { text: 'Game' }), status, controls),
          el('div', { class: 'box' }, el('h3', { text: 'Stats' }), stats),
          el('div', { class: 'box' },
            el('h3', { text: 'The thinking' }),
            el('p', { class: 'think-off-note', html: 'Switch on <strong>Show the thinking</strong> at the top to see what the algorithm is computing.' }),
            think)
        )
      ),
      el('details', { class: 'how' },
        el('summary', { text: 'How it works' }),
        el('div', { class: 'how-grid' },
          el('div', {}, el('h4', { text: 'Algorithm' }), el('p', { html: g.how.algo })),
          el('div', {}, el('h4', { text: 'Data structures' }), el('p', { html: g.how.ds })),
          el('div', {}, el('h4', { text: 'Cost' }), el('p', { html: g.how.cost }))
        ))
    );
    const ctx = {
      stage, controls, think, el, heat, store,
      thinking: () => thinking,
      onThinking(fn) { listeners.add(fn); },
      status(text, kind) { status.textContent = text; status.className = 'status' + (kind ? ' ' + kind : ''); },
      stats(pairs) {
        stats.replaceChildren(...pairs.map(([k, v]) => el('div', {}, el('dt', { text: k }), el('dd', { text: String(v) }))));
      },
      button(label, fn, opts = {}) {
        const b = el('button', { class: 'btn' + (opts.primary ? ' primary' : ''), type: 'button', text: label, onclick: fn, title: opts.title });
        (opts.parent || controls).append(b);
        return b;
      },
      segmented(options, value, fn) {
        const wrap = el('div', { class: 'seg', role: 'group' });
        const btns = options.map(([v, label]) => el('button', {
          type: 'button', text: label, 'aria-pressed': String(v === value),
          onclick: () => { btns.forEach(b => b.setAttribute('aria-pressed', 'false')); btns[options.findIndex(o => o[0] === v)].setAttribute('aria-pressed', 'true'); fn(v); }
        }));
        wrap.append(...btns);
        controls.append(wrap);
        return wrap;
      },
    };
    document.title = g.title + ' · Arcade · Andrea Montaña';
    return ctx;
  }

  function route() {
    if (cleanup) { try { cleanup(); } catch (e) { console.error(e); } cleanup = null; }
    listeners.clear();
    const id = location.hash.slice(1);
    const g = id === isolation.id ? isolation : (games.find(x => x.id === id) || games[0]);
    const app = document.getElementById('app');
    app.replaceChildren();
    const isIsolation = g === isolation;
    document.getElementById('workspace').classList.toggle('shows-isolation', isIsolation);
    document.querySelectorAll('#tabs .tab').forEach(link => {
      if (link.hash === '#' + g.id) link.setAttribute('aria-current', 'page');
      else link.removeAttribute('aria-current');
    });
    if (isIsolation) { cleanup = renderIsolation(app); return; }
    const ctx = renderGame(app, g);
    cleanup = g.mount(ctx) || null;
  }

  function start() {
    const box = document.getElementById('think-global');
    renderTabs();
    document.querySelector('.skip').addEventListener('click', event => {
      event.preventDefault();
      document.getElementById('app').focus();
    });
    const index = document.getElementById('game-index');
    // Game shortcuts listen on document; navigation controls must keep native keys.
    for (const controls of [index, document.querySelector('.top')]) {
      controls.addEventListener('keydown', event => event.stopPropagation());
    }
    if (compactIndex.matches) index.open = false;
    index.addEventListener('toggle', () => document.getElementById('workspace').classList.toggle('index-collapsed', !index.open));
    box.addEventListener('change', () => setThinking(box.checked));
    setThinking(store('thinking') !== '0'); /* on by default: it is the point of the page */
    window.addEventListener('hashchange', () => { route(); window.scrollTo(0, 0); });
    route();
  }

  return { register, start, el, heat, games };
})();

