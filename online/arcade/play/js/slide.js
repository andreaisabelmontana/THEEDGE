/* Sliding puzzle (8 and 15 puzzle) with an optimal IDA* solver running in a
 * Web Worker. Heuristic: Manhattan distance plus linear conflicts, which is
 * admissible, so the first solution IDA* finds is the shortest.
 */
'use strict';

const SlideSolver = (() => {
  /* This function is stringified into a worker, so it must be self contained. */
  function core() {
    const seq = new Int32Array(8), lis = new Int32Array(8);
    /* Longest increasing subsequence of seq[0..k): tiles outside it are the
     * fewest that must leave the line so the rest can pass each other. */
    function keep(k) {
      let best = 0;
      for (let i = 0; i < k; i++) {
        lis[i] = 1;
        for (let j = 0; j < i; j++) if (seq[j] < seq[i] && lis[j] + 1 > lis[i]) lis[i] = lis[j] + 1;
        if (lis[i] > best) best = lis[i];
      }
      return best;
    }
    /* Manhattan distance, plus 2 for every tile that has to step out of its
     * goal row or column to let another tile in the same line pass. */
    function rowLC(t, n, line) {
      let k = 0;
      for (let a = 0; a < n; a++) { const v = t[line * n + a]; if (v && ((v - 1) / n | 0) === line) seq[k++] = v; }
      return k - keep(k);
    }
    function colLC(t, n, line) {
      let k = 0;
      for (let a = 0; a < n; a++) { const v = t[a * n + line]; if (v && (v - 1) % n === line) seq[k++] = v; }
      return k - keep(k);
    }
    function dist(v, i, n) { return Math.abs(((v - 1) / n | 0) - (i / n | 0)) + Math.abs(((v - 1) % n) - (i % n)); }
    function heuristic(t, n) {
      let h = 0;
      for (let i = 0; i < t.length; i++) if (t[i]) h += dist(t[i], i, n);
      for (let line = 0; line < n; line++) h += 2 * (rowLC(t, n, line) + colLC(t, n, line));
      return h;
    }
    /* for display: the tiles involved in at least one reversed pair, and the count */
    function conflicts(t, n) {
      const tilesIn = [];
      let extra = 0;
      for (let line = 0; line < n; line++) for (const col of [false, true]) {
        const idx = [];
        for (let a = 0; a < n; a++) {
          const i = col ? a * n + line : line * n + a, v = t[i];
          if (v && (col ? (v - 1) % n : (v - 1) / n | 0) === line) idx.push(i);
        }
        idx.forEach((i, x) => { seq[x] = t[i]; });
        extra += idx.length - keep(idx.length);
        for (let x = 0; x < idx.length; x++) for (let y = x + 1; y < idx.length; y++)
          if (t[idx[x]] > t[idx[y]]) tilesIn.push(idx[x], idx[y]);
      }
      return { tiles: tilesIn, count: extra };
    }
    function solve(start, n, maxNodes, report) {
      const t = Uint8Array.from(start);
      let blank = t.indexOf(0), nodes = 0;
      const path = [];
      const D = [-n, n, -1, 1];
      let bound = heuristic(t, n);
      const iterations = [];
      const FOUND = -1;
      /* The heuristic is updated incrementally: a move changes one tile's
       * Manhattan distance and the conflicts of only the two lines it crosses. */
      function search(g, prev, h) {
        const f = g + h;
        if (f > bound) return f;
        if (h === 0) return FOUND;
        if (++nodes > maxNodes) throw new Error('budget');
        let min = Infinity;
        const r = blank / n | 0, c = blank % n;
        for (let d = 0; d < 4; d++) {
          if (d === 0 && r === 0 || d === 1 && r === n - 1 || d === 2 && c === 0 || d === 3 && c === n - 1) continue;
          const nb = blank + D[d];
          if (nb === prev) continue;
          const v = t[nb], vertical = d < 2;
          const l1 = vertical ? (nb / n | 0) : nb % n, l2 = vertical ? r : c;
          const before = vertical ? rowLC(t, n, l1) + rowLC(t, n, l2) : colLC(t, n, l1) + colLC(t, n, l2);
          const dm = dist(v, blank, n) - dist(v, nb, n);
          t[blank] = v; t[nb] = 0;
          const after = vertical ? rowLC(t, n, l1) + rowLC(t, n, l2) : colLC(t, n, l1) + colLC(t, n, l2);
          const old = blank; blank = nb; path.push(nb);
          const res = search(g + 1, old, h + dm + 2 * (after - before));
          if (res === FOUND) return FOUND;
          path.pop(); blank = old; t[nb] = v; t[old] = 0;
          if (res < min) min = res;
        }
        return min;
      }
      const t0 = Date.now();
      try {
        for (;;) {
          const before = nodes;
          const v = search(0, -1, heuristic(t, n));
          iterations.push({ bound, nodes: nodes - before });
          if (report) report({ type: 'progress', bound, nodes });
          if (v === FOUND) return { ok: true, path: path.slice(), nodes, iterations, ms: Date.now() - t0 };
          if (v === Infinity) return { ok: false, nodes, iterations };
          bound = v;
        }
      } catch (e) {
        return { ok: false, nodes, iterations, budget: true, ms: Date.now() - t0 };
      }
    }
    return { heuristic, conflicts, solve };
  }

  const local = core();
  let worker = null;
  function run(tiles, n, onProgress) {
    return new Promise(resolve => {
      try {
        if (!worker) {
          const src = `const S=(${core.toString()})();onmessage=e=>{const r=S.solve(e.data.t,e.data.n,e.data.max,m=>postMessage(m));postMessage(Object.assign({type:'done'},r));};`;
          worker = new Worker(URL.createObjectURL(new Blob([src], { type: 'text/javascript' })));
        }
        worker.onmessage = e => { if (e.data.type === 'progress') onProgress && onProgress(e.data); else resolve(e.data); };
        worker.postMessage({ t: Array.from(tiles), n, max: 3e7 });
      } catch (_) {
        resolve(local.solve(tiles, n, 2e6)); /* no workers: solve inline with a small budget */
      }
    });
  }
  function cancel() { if (worker) { worker.terminate(); worker = null; } }
  return { heuristic: local.heuristic, conflicts: local.conflicts, solveSync: local.solve, run, cancel };
})();

(() => {
  Arcade.register({
    id: 'sliding-puzzle',
    title: 'Sliding Puzzle',
    tag: 'Heuristic search',
    blurb: 'Slide the tiles back into order. The solver finds the shortest possible solution, not just a solution, and shows the estimate that guides it.',
    chips: ['IDA*', 'admissible heuristic', 'web worker'],
    how: {
      algo: '<em>IDA*</em> (iterative deepening A*) runs depth first searches with a growing cost bound. A branch is cut once <code>moves so far + estimate</code> exceeds the bound. Because the estimate never overestimates (it is <em>admissible</em>), the first solution found is optimal. The estimate is the sum of each tile\'s Manhattan distance to home, plus 2 for every tile that must step out of its goal row or column to let another pass (<em>linear conflict</em>, counted exactly with a longest increasing subsequence).',
      ds: 'The board is a single <code>Uint8Array</code> that is mutated and undone in place, so the search uses O(depth) memory, unlike A* which keeps every frontier state in a priority queue. The solver runs in a <em>Web Worker</em> built from the same function, so the page never freezes.',
      cost: 'Time grows roughly as b<sup>d</sup> with an effective branching factor near 2 for the 15 puzzle; a better heuristic shrinks it. The 8 puzzle solves in milliseconds. Shuffles of the 15 puzzle here usually need 25 to 40 moves and take from a few milliseconds to a few seconds; a fully random 15 puzzle can need 80 moves and billions of nodes, which is why pattern databases exist.',
    },
    glyph: () => {
      const g = [1, 2, 3, 4, 5, 6, 0, 7, 8];
      let s = '<svg viewBox="0 0 70 70" width="96" height="96" font-family="sans-serif" font-weight="700" font-size="10">';
      g.forEach((v, i) => { if (!v) return; const x = 4 + (i % 3) * 21, y = 4 + Math.floor(i / 3) * 21; s += `<rect x="${x}" y="${y}" width="19" height="19" rx="3" fill="${v === i + 1 ? '#5b3b7a' : '#3a2c4a'}"/><text x="${x + 9.5}" y="${y + 13.5}" text-anchor="middle" fill="#f4f1f8">${v}</text>`; });
      return s + '</svg>';
    },
    mount(ctx) {
      const { el } = ctx;
      let n = +(ctx.store('sp-size') || 4), tiles, moves = 0, solved = false, busy = false, solution = null, info = null, anim = null, progress = null;
      const boardEl = el('div', { class: 'sp-board' });
      ctx.stage.append(boardEl, el('p', { class: 'hint', text: 'Click a tile next to the gap, or use the arrow keys.' }));

      ctx.button('Shuffle', () => shuffle(), { primary: true });
      ctx.segmented([[3, '3 × 3'], [4, '4 × 4']], n, v => { n = v; ctx.store('sp-size', v); shuffle(); });
      const hintBtn = ctx.button('Hint', () => solveThen(1));
      const solveBtn = ctx.button('Solve optimally', () => solveThen(Infinity));

      function shuffle() {
        stop();
        tiles = new Uint8Array(n * n);
        for (let i = 0; i < n * n - 1; i++) tiles[i] = i + 1;
        let blank = n * n - 1, prev = -1;
        const steps = n === 3 ? 300 : 40 + Math.floor(Math.random() * 8);
        for (let s = 0; s < steps; s++) { /* random walk from the goal: always solvable */
          const opts = neighbours(blank).filter(x => x !== prev);
          const nb = opts[Math.floor(Math.random() * opts.length)];
          tiles[blank] = tiles[nb]; tiles[nb] = 0; prev = blank; blank = nb;
        }
        if (SlideSolver.heuristic(tiles, n) === 0) return shuffle();
        moves = 0; solved = false; solution = null; info = null;
        ctx.status('Put the tiles back in order, 1 in the top left.');
        render();
      }

      function neighbours(i) {
        const r = Math.floor(i / n), c = i % n, out = [];
        if (r > 0) out.push(i - n); if (r < n - 1) out.push(i + n);
        if (c > 0) out.push(i - 1); if (c < n - 1) out.push(i + 1);
        return out;
      }

      function slideTile(i) { /* move tile i (and any between it and the gap) into the gap */
        if (solved || busy) return false;
        const blank = tiles.indexOf(0);
        const br = Math.floor(blank / n), bc = blank % n, r = Math.floor(i / n), c = i % n;
        if (br !== r && bc !== c) return false;
        const step = br === r ? (c > bc ? 1 : -1) : (r > br ? n : -n);
        let b = blank;
        while (b !== i) { tiles[b] = tiles[b + step]; tiles[b + step] = 0; b += step; moves++; }
        solution = null; info = null;
        check(); render();
        return true;
      }

      function moveBlankTo(nb) {
        const b = tiles.indexOf(0);
        tiles[b] = tiles[nb]; tiles[nb] = 0; moves++;
      }

      function check() {
        if (SlideSolver.heuristic(tiles, n) === 0) { solved = true; ctx.status(`Solved in ${moves} moves.`, 'win'); }
      }

      function stop() { clearInterval(anim); anim = null; SlideSolver.cancel(); busy = false; progress = null; }

      async function solveThen(limit) {
        if (solved || busy) return;
        if (!solution) {
          busy = true; progress = { bound: 0, nodes: 0 };
          ctx.status('Searching for the shortest solution…'); render();
          const snapshot = tiles.slice();
          const res = await SlideSolver.run(snapshot, n, p => { progress = p; renderThink(); });
          busy = false; progress = null;
          if (!res.ok) { ctx.status(res.budget ? 'That one is too deep for the node budget. Try another shuffle.' : 'No solution found.', 'lose'); info = res; render(); return; }
          solution = res.path; info = res;
        }
        ctx.status(`Optimal: ${solution.length} moves from here.`);
        let left = Math.min(limit, solution.length);
        const stepOnce = () => {
          if (!left || !solution.length) { clearInterval(anim); anim = null; check(); render(); return; }
          moveBlankTo(solution.shift()); left--;
          if (info) info.remaining = solution.length;
          render();
          if (!solution.length) { clearInterval(anim); anim = null; check(); render(); }
        };
        if (limit === 1) stepOnce(); else { anim = setInterval(stepOnce, n === 3 ? 180 : 140); }
      }

      function render() {
        boardEl.style.gridTemplateColumns = `repeat(${n},1fr)`;
        const lc = new Set(SlideSolver.conflicts(tiles, n).tiles);
        const kids = [];
        for (let i = 0; i < n * n; i++) {
          const v = tiles[i];
          if (!v) { kids.push(el('div', { class: 'sp-tile blank', 'aria-hidden': 'true' })); continue; }
          const dist = Math.abs(Math.floor((v - 1) / n) - Math.floor(i / n)) + Math.abs((v - 1) % n - i % n);
          kids.push(el('button', { class: 'sp-tile' + (v === i + 1 ? ' home' : '') + (lc.has(i) && ctx.thinking() ? ' lc' : ''), type: 'button', 'aria-label': `Tile ${v}`, onclick: () => slideTile(i) },
            String(v), el('span', { class: 'd', text: dist ? String(dist) : '' })));
        }
        boardEl.replaceChildren(...kids);
        hintBtn.disabled = solveBtn.disabled = solved || busy || !!anim;
        const h = SlideSolver.heuristic(tiles, n);
        ctx.stats([['Moves', moves], ['Size', `${n} × ${n}`], ['Estimate', h], ['Optimal from here', solution ? solution.length : info && info.ok ? 0 : '?']]);
        renderThink();
      }

      function renderThink() {
        if (!ctx.thinking()) { ctx.think.replaceChildren(); return; }
        let man = 0;
        for (let i = 0; i < n * n; i++) { const v = tiles[i]; if (v) man += Math.abs(Math.floor((v - 1) / n) - Math.floor(i / n)) + Math.abs((v - 1) % n - i % n); }
        const lc = SlideSolver.conflicts(tiles, n).count;
        const kids = [
          el('p', { html: `Lower bound: <span class="big">${man + 2 * lc}</span> moves = ${man} Manhattan + 2 × ${lc} for tiles that must step out of their line to let another pass.` }),
          el('p', { text: 'Small gold numbers are each tile’s distance from home. Gold outlines mark tiles in a linear conflict.' }),
        ];
        if (progress) kids.push(el('p', { html: `Searching with bound <strong>${progress.bound}</strong>… ${progress.nodes.toLocaleString()} nodes so far.` }));
        if (info && info.iterations && info.iterations.length) {
          const top = Math.max(...info.iterations.map(it => it.nodes), 1);
          kids.push(el('p', { html: `${info.ok ? 'Found an optimal path' : 'Stopped'} after ${info.nodes.toLocaleString()} nodes${info.ms != null ? ` in ${info.ms} ms` : ''}. Nodes per bound:` }));
          kids.push(el('ul', { class: 'rank' }, info.iterations.map(it => el('li', {},
            el('code', { text: 'f ≤ ' + it.bound }),
            el('span', { class: 'bar' }, el('i', { style: `width:${Math.max(2, (it.nodes / top) * 100)}%` })),
            el('span', { class: 'v', text: it.nodes >= 1000 ? Math.round(it.nodes / 1000) + 'k' : String(it.nodes) })))));
        }
        ctx.think.replaceChildren(...kids);
      }

      const onKey = e => {
        const map = { ArrowUp: n, ArrowDown: -n, ArrowLeft: 1, ArrowRight: -1 };
        if (!(e.key in map)) return;
        const b = tiles.indexOf(0), t = b + map[e.key];
        if (t < 0 || t >= n * n) return;
        if ((e.key === 'ArrowLeft' || e.key === 'ArrowRight') && Math.floor(t / n) !== Math.floor(b / n)) return;
        e.preventDefault(); slideTile(t);
      };
      document.addEventListener('keydown', onKey);
      ctx.onThinking(render);
      shuffle();
      return () => { stop(); document.removeEventListener('keydown', onKey); };
    },
  });
})();
