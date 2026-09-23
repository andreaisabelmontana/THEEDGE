/* Minesweeper with an exact probability solver.
 * The revealed numbers are linear constraints over the hidden cells next to
 * them (the frontier). The solver splits the frontier into independent
 * components, enumerates every mine layout of each component by
 * backtracking, and then weights layouts by how many ways the remaining
 * mines fit in the unconstrained interior. The result is the exact chance
 * that each hidden cell is a mine, including the global mine count.
 */
'use strict';

const MineSolver = (() => {
  const lf = [0];
  function logFact(n) { while (lf.length <= n) lf.push(lf[lf.length - 1] + Math.log(lf.length)); return lf[n]; }
  function logC(n, k) { return k < 0 || k > n ? -Infinity : logFact(n) - logFact(k) - logFact(n - k); }
  function conv(a, b) {
    const out = new Float64Array(a.length + b.length - 1);
    for (let i = 0; i < a.length; i++) if (a[i]) for (let j = 0; j < b.length; j++) out[i + j] += a[i] * b[j];
    return out;
  }

  /* board: { W, H, M, open: Uint8Array, adj: Uint8Array, nbrs: Int32Array[] } */
  function solve(bd, budget = 2e6) {
    const { W, H, M, open, adj, nbrs } = bd, N = W * H;
    const prob = new Float64Array(N).fill(NaN);
    /* 1. constraints from revealed numbers */
    const cons = [];
    const isFront = new Int32Array(N).fill(-1);
    const front = [];
    for (let i = 0; i < N; i++) {
      if (!open[i]) continue;
      const cells = nbrs[i].filter(j => !open[j]);
      if (!cells.length) continue;
      cons.push({ cells, target: adj[i] });
      for (const j of cells) if (isFront[j] < 0) { isFront[j] = front.length; front.push(j); }
    }
    /* 2. union find over frontier cells that share a constraint */
    const parent = front.map((_, i) => i);
    const find = x => { while (parent[x] !== x) { parent[x] = parent[parent[x]]; x = parent[x]; } return x; };
    for (const c of cons) for (let k = 1; k < c.cells.length; k++) {
      const a = find(isFront[c.cells[0]]), b = find(isFront[c.cells[k]]);
      if (a !== b) parent[a] = b;
    }
    const compOf = new Map();
    front.forEach((cell, i) => { const r = find(i); if (!compOf.has(r)) compOf.set(r, { cells: [], cons: [] }); compOf.get(r).cells.push(cell); });
    for (const c of cons) compOf.get(find(isFront[c.cells[0]])).cons.push(c);
    const comps = [...compOf.values()];

    /* 3. enumerate each component by backtracking with constraint pruning */
    let interior = 0, nodes = 0, approx = false;
    for (let i = 0; i < N; i++) if (!open[i] && isFront[i] < 0) interior++;
    const solved = [];
    for (const comp of comps) {
      /* order cells so each new cell touches constraints already in play */
      const order = [], seen = new Set();
      const byCell = new Map(comp.cells.map(c => [c, []]));
      comp.cons.forEach((c, ci) => c.cells.forEach(x => byCell.get(x).push(ci)));
      const queue = [comp.cells[0]]; seen.add(comp.cells[0]);
      while (queue.length) {
        const x = queue.shift(); order.push(x);
        for (const ci of byCell.get(x)) for (const y of comp.cons[ci].cells) if (!seen.has(y)) { seen.add(y); queue.push(y); }
      }
      const n = order.length;
      const local = new Map(order.map((c, i) => [c, i]));
      const cellCons = order.map(c => byCell.get(c));
      const sum = new Int32Array(comp.cons.length);
      const left = Int32Array.from(comp.cons, c => c.cells.length);
      const target = Int32Array.from(comp.cons, c => c.target);
      const assign = new Uint8Array(n);
      const counts = new Float64Array(n + 1);
      const cellCounts = new Float64Array((n + 1) * n);
      let over = false;
      (function go(i, k) {
        if (over) return;
        if (++nodes > budget) { over = true; return; }
        if (i === n) {
          counts[k]++;
          const base = k * n;
          for (let j = 0; j < n; j++) if (assign[j]) cellCounts[base + j]++;
          return;
        }
        for (let v = 0; v <= 1; v++) {
          if (k + v > M) break;
          let ok = true;
          const cs = cellCons[i];
          for (let q = 0; q < cs.length; q++) {
            const c = cs[q];
            sum[c] += v; left[c]--;
            if (sum[c] > target[c] || sum[c] + left[c] < target[c]) ok = false;
          }
          assign[i] = v;
          if (ok) go(i + 1, k + v);
          for (let q = 0; q < cs.length; q++) { const c = cs[q]; sum[c] -= v; left[c]++; }
        }
        assign[i] = 0;
      })(0, 0);
      if (over) { approx = true; interior += n; for (const c of order) isFront[c] = -2; continue; }
      solved.push({ order, local, counts, cellCounts, n });
    }

    /* 4. combine components with the interior using the global mine count */
    const dists = solved.map(s => s.counts);
    const all = dists.reduce((acc, d) => conv(acc, d), Float64Array.of(1));
    const lw = Array.from({ length: all.length }, (_, k) => logC(interior, M - k));
    const maxLw = Math.max(...lw.filter(isFinite), -1e300);
    const w = lw.map(v => isFinite(v) ? Math.exp(v - maxLw) : 0);
    let Z = 0, eInterior = 0;
    for (let k = 0; k < all.length; k++) { Z += all[k] * w[k]; eInterior += all[k] * w[k] * (M - k); }
    if (Z <= 0) return { prob, ok: false };
    solved.forEach((s, j) => {
      const others = dists.reduce((acc, d, q) => q === j ? acc : conv(acc, d), Float64Array.of(1));
      /* weight for this component having kj mines */
      const wk = new Float64Array(s.n + 1);
      for (let kj = 0; kj <= s.n; kj++) {
        if (!s.counts[kj]) continue;
        let t = 0;
        for (let ko = 0; ko < others.length; ko++) if (others[ko] && kj + ko < w.length) t += others[ko] * w[kj + ko];
        wk[kj] = t;
      }
      for (let c = 0; c < s.n; c++) {
        let p = 0;
        for (let kj = 0; kj <= s.n; kj++) if (wk[kj]) p += s.cellCounts[kj * s.n + c] * wk[kj];
        prob[s.order[c]] = p / Z;
      }
    });
    const pInterior = interior ? eInterior / Z / interior : 0;
    for (let i = 0; i < N; i++) if (!open[i] && isFront[i] < 0) prob[i] = pInterior;
    let configs = 1;
    solved.forEach(s => { configs *= s.counts.reduce((a, b) => a + b, 0); });
    return { prob, ok: true, approx, frontier: front.length, components: comps.length, interior, pInterior, configs, nodes };
  }

  return { solve };
})();

(() => {
  const LEVELS = { easy: [9, 9, 10], medium: [16, 16, 40] };

  Arcade.register({
    id: 'minesweeper',
    title: 'Minesweeper',
    tag: 'Constraint satisfaction',
    blurb: 'Clear the field without touching a mine. The solver turns every number into an equation and works out the exact chance that each hidden square is a mine.',
    chips: ['backtracking', 'union find', 'combinatorics'],
    how: {
      algo: 'Each revealed number says how many of its hidden neighbours are mines. The solver groups the hidden cells touching numbers into independent components, lists every mine layout for each component by <em>backtracking</em> (pruning a branch as soon as any number is over or under satisfied), then weights each layout by <code>C(interior, mines left)</code>, the number of ways to place the rest of the mines where no number reaches. That gives exact probabilities, not guesses.',
      ds: '<em>Union find</em> with path halving splits the frontier into components. Each component keeps a count of layouts per number of mines, plus per cell counts, so components can be combined with a convolution instead of enumerating the cross product. Flood fill uses a BFS queue.',
      cost: 'Backtracking is exponential in the size of a component in the worst case, but pruning keeps real boards fast (thousands of nodes). Combining components is polynomial. Log factorials keep the binomials from overflowing.',
    },
    glyph: () => {
      const g = [[0, 1, '1', 0.05, 0.9], [0, '1', '2', 0.5, 0.5], [0, '1', 0.2, 0.2, 0.33]];
      let s = '<svg viewBox="0 0 110 70" width="150" height="96" font-family="monospace" font-weight="700" font-size="11">';
      g.forEach((row, r) => row.forEach((v, c) => {
        const x = 6 + c * 20, y = 6 + r * 20;
        if (typeof v === 'string' || v === 0) {
          s += `<rect x="${x}" y="${y}" width="18" height="18" rx="2" fill="#17131c"/>`;
          if (v) s += `<text x="${x + 9}" y="${y + 13}" text-anchor="middle" fill="${v === '1' ? '#7cc4f0' : '#6fbf73'}">${v}</text>`;
        } else s += `<rect x="${x}" y="${y}" width="18" height="18" rx="2" fill="${Arcade.heat(v)}"/>`;
      }));
      return s + '</svg>';
    },
    mount(ctx) {
      const { el } = ctx;
      let level = ctx.store('ms-level') || (window.innerWidth < 600 ? 'easy' : 'medium');
      let W, H, M, N, mine, open, flag, adj, nbrs, started, over, cells, flagMode = false, timer = null, auto = null;
      let t0 = 0, tEnd = 0, guesses = 0, lastSol = null, boom = -1;
      const wrap = el('div', { class: 'ms-wrap' });
      const board = el('div', { class: 'ms' });
      wrap.append(board);
      ctx.stage.append(wrap, el('p', { class: 'hint', text: 'Click to reveal. Right click, or switch on flag mode, to place a flag. Click a number to clear around it.' }));

      ctx.button('New game', () => reset(), { primary: true });
      ctx.segmented([['easy', '9 × 9'], ['medium', '16 × 16']], level, v => { level = v; ctx.store('ms-level', v); reset(); });
      const flagBtn = ctx.button('Flag mode', () => { flagMode = !flagMode; flagBtn.setAttribute('aria-pressed', String(flagMode)); });
      flagBtn.setAttribute('aria-pressed', 'false');
      ctx.button('Solver step', () => step(), { title: 'Reveal every certain safe square, or take the safest guess' });
      const autoBtn = ctx.button('Let it solve', () => {
        if (auto) { clearInterval(auto); auto = null; autoBtn.setAttribute('aria-pressed', 'false'); return; }
        autoBtn.setAttribute('aria-pressed', 'true');
        auto = setInterval(() => { if (over) { clearInterval(auto); auto = null; autoBtn.setAttribute('aria-pressed', 'false'); return; } step(); }, 260);
      });

      function sizeCells() {
        const avail = Math.min(wrap.clientWidth || 600, 640);
        const sz = Math.max(20, Math.min(36, Math.floor((avail - (W - 1) * 2) / W)));
        board.style.setProperty('--sz', sz + 'px');
        board.classList.toggle('small', sz < 27);
      }

      function reset() {
        clearInterval(timer); timer = null;
        if (auto) { clearInterval(auto); auto = null; autoBtn.setAttribute('aria-pressed', 'false'); }
        [W, H, M] = LEVELS[level]; N = W * H;
        mine = new Uint8Array(N); open = new Uint8Array(N); flag = new Uint8Array(N); adj = new Uint8Array(N);
        nbrs = [];
        for (let i = 0; i < N; i++) {
          const r = Math.floor(i / W), c = i % W, list = [];
          for (let dr = -1; dr <= 1; dr++) for (let dc = -1; dc <= 1; dc++) {
            if (!dr && !dc) continue;
            const rr = r + dr, cc = c + dc;
            if (rr >= 0 && rr < H && cc >= 0 && cc < W) list.push(rr * W + cc);
          }
          nbrs.push(Int32Array.from(list));
        }
        started = false; over = false; tEnd = 0; guesses = 0; boom = -1; lastSol = null;
        const grid = el('div', { class: 'grid', role: 'grid', 'aria-label': 'Minefield', style: `grid-template-columns:repeat(${W},var(--sz))` });
        cells = [];
        for (let i = 0; i < N; i++) {
          const b = el('button', { class: 'cell', type: 'button', 'aria-label': `Row ${Math.floor(i / W) + 1}, column ${i % W + 1}` });
          b.addEventListener('click', () => click(i));
          b.addEventListener('contextmenu', e => { e.preventDefault(); toggleFlag(i); });
          cells.push(b); grid.append(b);
        }
        board.replaceChildren(grid);
        sizeCells();
        ctx.status(`Find the ${M} mines. Your first click is always safe.`);
        render();
      }

      function layMines(safe) {
        const banned = new Set([safe, ...nbrs[safe]]);
        const pool = [];
        for (let i = 0; i < N; i++) if (!banned.has(i)) pool.push(i);
        for (let k = 0; k < M; k++) { /* partial Fisher Yates */
          const j = k + Math.floor(Math.random() * (pool.length - k));
          [pool[k], pool[j]] = [pool[j], pool[k]];
          mine[pool[k]] = 1;
        }
        for (let i = 0; i < N; i++) adj[i] = nbrs[i].reduce((s, j) => s + mine[j], 0);
        started = true; t0 = Date.now();
        timer = setInterval(stats, 1000);
      }

      function reveal(start) {
        const q = new Int32Array(N); let h = 0, t = 0; /* BFS flood fill */
        q[t++] = start; open[start] = 1; flag[start] = 0;
        while (h < t) {
          const i = q[h++];
          if (adj[i] !== 0 || mine[i]) continue;
          for (const j of nbrs[i]) if (!open[j] && !mine[j]) { open[j] = 1; flag[j] = 0; q[t++] = j; }
        }
      }

      function click(i) {
        if (over) return;
        if (flagMode && !open[i]) return toggleFlag(i);
        if (!started) layMines(i);
        if (open[i]) { /* chord */
          const f = nbrs[i].reduce((s, j) => s + flag[j], 0);
          if (f !== adj[i] || !adj[i]) return;
          for (const j of nbrs[i]) if (!open[j] && !flag[j]) { if (mine[j]) return lose(j); reveal(j); }
          return after();
        }
        if (flag[i]) return;
        if (mine[i]) return lose(i);
        reveal(i); after();
      }

      function toggleFlag(i) { if (over || open[i]) return; flag[i] ^= 1; lastSol = null; render(); }

      function lose(i) {
        over = true; boom = i; open[i] = 1; clearInterval(timer); tEnd = Date.now();
        ctx.status('Boom. That square was a mine.', 'lose'); lastSol = null; render();
      }

      function after() {
        lastSol = null;
        let closed = 0;
        for (let i = 0; i < N; i++) if (!open[i]) closed++;
        if (closed === M) {
          over = true; clearInterval(timer); tEnd = Date.now();
          for (let i = 0; i < N; i++) if (mine[i]) flag[i] = 1;
          ctx.status(`Cleared in ${Math.round((Date.now() - t0) / 1000)} s.`, 'win');
        }
        render();
      }

      function solution() {
        if (!lastSol) lastSol = started ? MineSolver.solve({ W, H, M, open, adj, nbrs }) : null;
        return lastSol;
      }

      function step() {
        if (over) return;
        if (!started) { click(Math.floor(H / 2) * W + Math.floor(W / 2)); return; }
        const s = solution();
        if (!s || !s.ok) return;
        let any = false, best = -1, bp = 2;
        for (let i = 0; i < N; i++) {
          if (open[i]) continue;
          const p = s.prob[i];
          if (p < 1e-9) { any = true; if (!mine[i]) reveal(i); }
          else if (p > 1 - 1e-9) flag[i] = 1;
          else if (p < bp) { bp = p; best = i; }
        }
        if (any) { ctx.status('Revealed every square that is certainly safe.'); return after(); }
        if (best < 0) return;
        guesses++;
        ctx.status(`No safe square left, so it guesses: ${Math.round(bp * 100)}% risk.`);
        if (mine[best]) return lose(best);
        reveal(best); after();
      }

      function stats() {
        const flags = flag.reduce((a, b) => a + b, 0);
        let opened = 0; for (let i = 0; i < N; i++) if (open[i] && !mine[i]) opened++;
        ctx.stats([['Mines left', M - flags], ['Time', started ? Math.round(((over && tEnd ? tEnd : Date.now()) - t0) / 1000) + ' s' : '0 s'], ['Cleared', Math.round(100 * opened / (N - M)) + '%'], ['Solver guesses', guesses]]);
      }

      function render() {
        const on = ctx.thinking() && !over;
        const s = on ? solution() : null;
        let bestI = -1, bp = 2;
        if (s && s.ok) for (let i = 0; i < N; i++) if (!open[i] && s.prob[i] < bp) { bp = s.prob[i]; bestI = i; }
        const small = board.classList.contains('small');
        for (let i = 0; i < N; i++) {
          const b = cells[i];
          let cls = 'cell', txt = '';
          b.style.background = '';
          if (open[i]) {
            cls += ' open';
            if (mine[i]) cls += i === boom ? ' mine boom' : ' mine';
            else if (adj[i]) { txt = adj[i]; cls += ' n' + adj[i]; }
          } else if (over && mine[i] && !flag[i]) cls += ' mine';
          else if (flag[i]) cls += ' flag';
          else if (s && s.ok) {
            const p = s.prob[i];
            b.style.background = Arcade.heat(p);
            if (i === bestI || p < 1e-9) cls += ' safe-pick';
          }
          b.className = cls;
          b.textContent = txt;
          if (s && s.ok && !open[i] && !flag[i] && !small) {
            const p = s.prob[i];
            b.append(el('span', { class: 'p', text: p < 1e-9 ? '0' : p > 1 - 1e-9 ? '100' : String(Math.max(1, Math.round(p * 100))) }));
          }
        }
        stats();
        if (!ctx.thinking()) { ctx.think.replaceChildren(); return; }
        if (!started) { ctx.think.replaceChildren(el('p', { text: `Before the first click every square has the same ${Math.round(100 * M / N)}% chance. The first click is always safe here.` })); return; }
        if (over || !s || !s.ok) { ctx.think.replaceChildren(el('p', { text: over ? 'Game over. Start a new game to watch the solver again.' : 'No consistent layout.' })); return; }
        let safe = 0, sure = 0;
        for (let i = 0; i < N; i++) if (!open[i]) { if (s.prob[i] < 1e-9) safe++; else if (s.prob[i] > 1 - 1e-9) sure++; }
        ctx.think.replaceChildren(
          el('p', { html: `Frontier: <strong>${s.frontier}</strong> hidden squares touching numbers, in <strong>${s.components}</strong> independent ${s.components === 1 ? 'group' : 'groups'}.` }),
          el('p', { html: `Layouts counted: <span class="big">${s.configs < 1e6 ? s.configs.toLocaleString() : s.configs.toExponential(2)}</span> (${s.nodes.toLocaleString()} search nodes${s.approx ? ', one group too large so treated as open ground' : ''}).` }),
          el('p', { html: `Certain safe: <strong>${safe}</strong>. Certain mines: <strong>${sure}</strong>. Squares far from any number: ${Math.round(s.pInterior * 100)}% each.` }),
          el('p', { html: safe ? 'Green outlines are provably safe.' : `Nothing is provably safe. The best guess carries <strong>${Math.round(bp * 100)}%</strong> risk (green outline).` }),
          el('div', { class: 'legend' }, el('span', { text: 'safe' }), el('span', { class: 'ramp' }), el('span', { text: 'mine' }))
        );
      }

      const onResize = () => { sizeCells(); render(); };
      window.addEventListener('resize', onResize);
      ctx.onThinking(render);
      reset();
      return () => { clearInterval(timer); clearInterval(auto); window.removeEventListener('resize', onResize); };
    },
  });
})();
