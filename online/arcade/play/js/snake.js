/* Snake with a graph search autopilot.
 * BFS finds the shortest path to the food, but the autopilot only takes it
 * if, after eating, the snake could still reach its own tail (the tail is a
 * guaranteed escape route). Otherwise it follows its tail and waits.
 */
'use strict';

(() => {
  const G = 20, N = G * G;

  function bfs(from, to, blocked) {
    const prev = new Int32Array(N).fill(-1), q = new Int32Array(N);
    let h = 0, t = 0;
    q[t++] = from; prev[from] = from;
    while (h < t) {
      const x = q[h++];
      if (x === to) break;
      const r = (x / G) | 0, c = x % G;
      const nb = [r > 0 ? x - G : -1, r < G - 1 ? x + G : -1, c > 0 ? x - 1 : -1, c < G - 1 ? x + 1 : -1];
      for (const y of nb) if (y >= 0 && prev[y] < 0 && (!blocked[y] || y === to)) { prev[y] = x; q[t++] = y; }
    }
    if (prev[to] < 0) return { path: null, visited: t, order: q.subarray(0, t) };
    const path = [];
    for (let x = to; x !== from; x = prev[x]) path.push(x);
    path.reverse();
    return { path, visited: t, order: q.subarray(0, t) };
  }

  Arcade.register({
    id: 'snake',
    title: 'Snake',
    tag: 'Graph search',
    blurb: 'Eat, grow, and do not bite yourself. The autopilot plans with breadth first search and checks every plan for a way out before it commits.',
    chips: ['BFS', 'ring buffer deque', 'safety check'],
    how: {
      algo: 'The board is a grid graph. <em>Breadth first search</em> gives the shortest path from the head to the food. Shortest is not always safe, so the autopilot simulates eating along that path and runs a second BFS from the new head to the new tail. If the tail is reachable the snake can always escape by following it, so the plan is taken. If not, it follows its tail, choosing the move that keeps the tail farthest away, until a safe path to the food opens.',
      ds: 'The body is a <em>deque</em> stored as a ring buffer (<code>Int32Array</code> plus head index and length), so moving is O(1) at both ends. An occupancy grid gives O(1) collision checks, and BFS uses a flat typed array as its queue and a predecessor array to rebuild the path.',
      cost: 'Each BFS is O(V + E) = O(400) on a 20 × 20 board, and a move runs at most a handful of them, so planning takes microseconds per step.',
    },
    glyph: () => {
      const body = [[2, 5], [3, 5], [4, 5], [4, 4], [4, 3], [5, 3], [6, 3]];
      let s = '<svg viewBox="0 0 100 80" width="120" height="96"><path d="M70 30 H80 V50" stroke="#c7a0ee" stroke-width="2" stroke-dasharray="3 3" fill="none"/>';
      body.forEach(([x, y], i) => { s += `<rect x="${x * 10}" y="${y * 10}" width="9" height="9" rx="2" fill="${i === body.length - 1 ? '#f4f1f8' : '#8fd17f'}"/>`; });
      return s + '<circle cx="84.5" cy="54.5" r="4" fill="#ef6f6c"/></svg>';
    },
    mount(ctx) {
      const { el } = ctx;
      const canvas = el('canvas', { class: 'sn-canvas', width: 480, height: 480, 'aria-label': 'Snake board' });
      const pad = el('div', { class: 'dpad' });
      [['u', '↑', -G], ['l', '←', -1], ['d', '↓', G], ['r', '→', 1]].forEach(([k, t, d]) => pad.append(el('button', { class: k, type: 'button', text: t, 'aria-label': { u: 'Up', l: 'Left', d: 'Down', r: 'Right' }[k], onclick: () => turn(d) })));
      ctx.stage.append(canvas, pad, el('p', { class: 'hint', text: 'Arrow keys or WASD to steer, space to pause. Or let the autopilot drive.' }));
      const g2 = canvas.getContext('2d');

      let body, head, len, occ, dir, queue, food, alive, running, timer = null, auto = ctx.store('sn-auto') === '1', score, best = +(ctx.store('sn-best') || 0), plan = null;
      const playBtn = ctx.button('Start', () => { if (!alive) reset(); running = !running; tick(); updateButtons(); }, { primary: true });
      ctx.button('Restart', () => { reset(); running = true; tick(); updateButtons(); });
      const autoBtn = ctx.button('Autopilot', () => { auto = !auto; ctx.store('sn-auto', auto ? '1' : '0'); updateButtons(); if (auto && !running && alive) { running = true; tick(); updateButtons(); } });
      const speedSeg = ctx.segmented([[140, 'Slow'], [90, 'Normal'], [40, 'Fast']], 90, v => { speed = v; });
      let speed = 90;

      const at = k => body[(head - k + N) % N]; /* k-th segment from the head */
      const tail = () => at(len - 1);

      function reset() {
        clearTimeout(timer);
        body = new Int32Array(N); occ = new Uint8Array(N);
        head = 2; len = 3;
        const mid = (G >> 1) * G;
        body[0] = mid + 3; body[1] = mid + 4; body[2] = mid + 5;
        for (let k = 0; k < len; k++) occ[at(k)] = 1;
        dir = 1; queue = []; alive = true; running = false; score = 0; plan = null;
        placeFood();
        ctx.status('Press Start, or any arrow key.');
        draw(); updateButtons();
      }

      function placeFood() {
        const free = [];
        for (let i = 0; i < N; i++) if (!occ[i]) free.push(i);
        food = free.length ? free[Math.floor(Math.random() * free.length)] : -1;
      }

      function turn(d) {
        const last = queue.length ? queue[queue.length - 1] : dir;
        if (d === -last || d === last || queue.length > 2) return;
        queue.push(d);
        if (auto) { auto = false; ctx.store('sn-auto', '0'); }
        if (!running && alive) { running = true; tick(); }
        updateButtons();
      }

      function neighbours(x) {
        const r = (x / G) | 0, c = x % G, out = [];
        if (r > 0) out.push(x - G); if (r < G - 1) out.push(x + G);
        if (c > 0) out.push(x - 1); if (c < G - 1) out.push(x + 1);
        return out;
      }

      /* Autopilot: returns the next cell and a record of its reasoning. */
      function think() {
        const h = at(0), tl = tail();
        const blocked = occ.slice(); blocked[tl] = 0; /* the tail moves away this step */
        const toFood = bfs(h, food, blocked);
        if (toFood.path) {
          /* simulate the snake walking the path and eating */
          const sim = [];
          for (let k = 0; k < len; k++) sim.push(at(k));
          for (const x of toFood.path) { sim.unshift(x); if (x !== food) sim.pop(); }
          const vocc = new Uint8Array(N);
          sim.forEach(x => { vocc[x] = 1; });
          const vt = sim[sim.length - 1];
          vocc[vt] = 0;
          const escape = bfs(sim[0], vt, vocc);
          if (escape.path || sim.length >= N) return { next: toFood.path[0], mode: 'food', path: toFood.path, visited: toFood.visited, order: toFood.order, escape: escape.path };
        }
        /* follow the tail: prefer the safe move that keeps the tail farthest */
        let bestMove = -1, bestLen = -1, bestPath = null;
        for (const x of neighbours(h)) {
          if ((occ[x] && x !== tl) || x === food) continue;
          const vocc = occ.slice(); vocc[tl] = 0; vocc[x] = 1;
          const newTail = at(len - 2);
          vocc[newTail] = 0;
          const r = bfs(x, newTail, vocc);
          if (r.path && r.path.length > bestLen) { bestLen = r.path.length; bestMove = x; bestPath = r.path; }
        }
        if (bestMove >= 0) return { next: bestMove, mode: 'tail', path: bestPath, visited: toFood.visited, order: toFood.order };
        for (const x of neighbours(h)) if (!occ[x] || x === tl) return { next: x, mode: 'survive', path: null, visited: toFood.visited, order: toFood.order };
        return { next: neighbours(h)[0], mode: 'trapped', path: null, visited: 0, order: [] };
      }

      function step() {
        const h = at(0);
        let next;
        if (auto) { plan = think(); next = plan.next; dir = next - h; }
        else {
          if (queue.length) dir = queue.shift();
          const r = (h / G) | 0, c = h % G;
          if ((dir === -1 && c === 0) || (dir === 1 && c === G - 1) || (dir === -G && r === 0) || (dir === G && r === G - 1)) return die('You hit the wall.');
          next = h + dir;
          plan = ctx.thinking() ? think() : null;
        }
        const eating = next === food;
        if (!eating) { occ[tail()] = 0; len--; }
        if (occ[next]) { if (!eating) { len++; occ[tail()] = 1; } return die(auto ? 'The autopilot boxed itself in.' : 'You bit yourself.'); }
        head = (head + 1) % N; body[head] = next; len++; occ[next] = 1;
        if (eating) {
          score++;
          if (score > best) { best = score; ctx.store('sn-best', String(best)); }
          if (len === N) { alive = false; running = false; ctx.status('The board is full. Perfect game.', 'win'); return; }
          placeFood();
        }
        return true;
      }

      function die(msg) { alive = false; running = false; ctx.status(`${msg} Length ${len}.`, 'lose'); updateButtons(); return false; }

      function tick() {
        clearTimeout(timer);
        if (!running || !alive) { draw(); return; }
        step();
        draw();
        if (running && alive) timer = setTimeout(tick, auto ? Math.min(speed, 60) : speed);
      }

      function updateButtons() {
        playBtn.textContent = !alive ? 'Play again' : running ? 'Pause' : 'Start';
        autoBtn.setAttribute('aria-pressed', String(auto));
        if (alive && running) ctx.status(auto ? 'Autopilot is driving.' : 'Go.');
      }

      function draw() {
        const S = canvas.width / G;
        g2.fillStyle = '#141019'; g2.fillRect(0, 0, canvas.width, canvas.height);
        const on = ctx.thinking() && alive;
        if (on && plan && plan.order) { /* BFS frontier, in visiting order */
          const o = plan.order;
          for (let i = 0; i < o.length; i++) { g2.fillStyle = `rgba(199,160,238,${0.05 + 0.12 * (1 - i / o.length)})`; g2.fillRect((o[i] % G) * S, ((o[i] / G) | 0) * S, S, S); }
        }
        g2.strokeStyle = 'rgba(255,255,255,.035)'; g2.lineWidth = 1;
        for (let i = 1; i < G; i++) { g2.beginPath(); g2.moveTo(i * S, 0); g2.lineTo(i * S, canvas.height); g2.moveTo(0, i * S); g2.lineTo(canvas.width, i * S); g2.stroke(); }
        if (food >= 0) { g2.fillStyle = '#ef6f6c'; g2.beginPath(); g2.arc((food % G + 0.5) * S, (((food / G) | 0) + 0.5) * S, S * 0.36, 0, 7); g2.fill(); }
        for (let k = len - 1; k >= 0; k--) {
          const x = at(k);
          g2.fillStyle = k === 0 ? '#f4f1f8' : `hsl(${110 - 40 * (k / Math.max(1, len))},45%,${58 - 18 * (k / Math.max(1, len))}%)`;
          g2.beginPath(); g2.roundRect ? g2.roundRect((x % G) * S + 1, ((x / G) | 0) * S + 1, S - 2, S - 2, 4) : g2.rect((x % G) * S + 1, ((x / G) | 0) * S + 1, S - 2, S - 2); g2.fill();
        }
        if (on && plan && plan.path) {
          g2.strokeStyle = plan.mode === 'food' ? '#c7a0ee' : '#ffd166'; g2.lineWidth = 3; g2.setLineDash([6, 5]);
          g2.beginPath();
          const h = at(0);
          g2.moveTo((h % G + 0.5) * S, (((h / G) | 0) + 0.5) * S);
          for (const x of plan.path) g2.lineTo((x % G + 0.5) * S, (((x / G) | 0) + 0.5) * S);
          g2.stroke(); g2.setLineDash([]);
        }
        if (!running && alive) {
          g2.fillStyle = 'rgba(16,16,22,.55)'; g2.fillRect(0, 0, canvas.width, canvas.height);
          g2.fillStyle = '#f4f1f8'; g2.font = '600 20px "Mona Sans Variable", Arial'; g2.textAlign = 'center';
          g2.fillText(score ? 'Paused' : 'Press Start', canvas.width / 2, canvas.height / 2);
        }
        ctx.stats([['Length', len], ['Food eaten', score], ['Best', best], ['Driver', auto ? 'autopilot' : 'you']]);
        if (!ctx.thinking()) { ctx.think.replaceChildren(); return; }
        if (!plan) plan = alive ? think() : null;
        if (!plan) { ctx.think.replaceChildren(el('p', { text: 'Game over.' })); return; }
        const words = { food: 'Heading for the food: the path is safe because the tail stays reachable after eating.', tail: 'No safe path to the food right now, so it follows its own tail (gold) and waits for one to open.', survive: 'Cornered: taking any move that does not crash.', trapped: 'No moves left.' };
        ctx.think.replaceChildren(
          el('p', { html: `Mode: <span class="big">${plan.mode}</span>` }),
          el('p', { text: words[plan.mode] }),
          el('p', { html: plan.mode === 'food' ? `BFS visited <strong>${plan.visited}</strong> of ${N} squares (shaded) to find this ${plan.path.length} step route, then checked the tail is still reachable.` : `The search for the food visited <strong>${plan.visited}</strong> squares (shaded) without finding a route it could trust.` })
        );
      }

      const keys = { ArrowUp: -G, KeyW: -G, ArrowDown: G, KeyS: G, ArrowLeft: -1, KeyA: -1, ArrowRight: 1, KeyD: 1 };
      const onKey = e => {
        if (e.ctrlKey || e.metaKey || e.altKey) return;
        if (e.code === 'Space' && e.target === document.body) { e.preventDefault(); if (alive) { running = !running; tick(); updateButtons(); } return; }
        if (e.code in keys) { e.preventDefault(); if (!alive) { reset(); } turn(keys[e.code]); }
      };
      let touch = null;
      canvas.addEventListener('touchstart', e => { const t = e.touches[0]; touch = [t.clientX, t.clientY]; }, { passive: true });
      canvas.addEventListener('touchend', e => {
        if (!touch) return;
        const t = e.changedTouches[0], dx = t.clientX - touch[0], dy = t.clientY - touch[1];
        if (Math.max(Math.abs(dx), Math.abs(dy)) > 24) turn(Math.abs(dx) > Math.abs(dy) ? (dx > 0 ? 1 : -1) : (dy > 0 ? G : -G));
        touch = null;
      });
      document.addEventListener('keydown', onKey);
      ctx.onThinking(() => { plan = null; draw(); });
      reset();
      void speedSeg;
      return () => { clearTimeout(timer); document.removeEventListener('keydown', onKey); };
    },
  });
})();
