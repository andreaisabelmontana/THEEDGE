/* Connect Four against negamax search with alpha beta pruning,
 * iterative deepening, move ordering and a Zobrist hashed transposition table.
 */
'use strict';

const C4 = (() => {
  const W = 7, H = 6, SIZE = W * H;
  const WIN = 100000, INF = 1e9;
  const ORDER = [3, 2, 4, 1, 5, 0, 6];

  /* Zobrist keys: two independent 32 bit keys per (cell, player); the first
   * indexes the table, the second verifies the entry (cheap collision check). */
  const Z1 = new Uint32Array(SIZE * 2), Z2 = new Uint32Array(SIZE * 2);
  let seed = 0x9e3779b9;
  const rnd = () => { seed ^= seed << 13; seed ^= seed >>> 17; seed ^= seed << 5; return seed >>> 0; };
  for (let i = 0; i < SIZE * 2; i++) { Z1[i] = rnd(); Z2[i] = rnd(); }

  /* all 69 windows of four cells, for the heuristic */
  const WINDOWS = [];
  for (let c = 0; c < W; c++) for (let r = 0; r < H; r++)
    for (const [dc, dr] of [[1, 0], [0, 1], [1, 1], [1, -1]]) {
      const w = [];
      for (let k = 0; k < 4; k++) { const cc = c + dc * k, rr = r + dr * k; if (cc < 0 || cc >= W || rr < 0 || rr >= H) break; w.push(cc * H + rr); }
      if (w.length === 4) WINDOWS.push(w);
    }

  function create() { return { cells: new Int8Array(SIZE), height: new Int8Array(W), moves: 0, h1: 0, h2: 0 }; }
  function canPlay(b, c) { return b.height[c] < H; }
  function play(b, c, p) {
    const i = c * H + b.height[c]++;
    b.cells[i] = p; b.moves++;
    b.h1 ^= Z1[i * 2 + p - 1]; b.h2 ^= Z2[i * 2 + p - 1];
    return i;
  }
  function undo(b, c) {
    const i = c * H + --b.height[c], p = b.cells[i];
    b.cells[i] = 0; b.moves--;
    b.h1 ^= Z1[i * 2 + p - 1]; b.h2 ^= Z2[i * 2 + p - 1];
  }
  /* does the disc just played at index i complete four? returns the line or null */
  function winLine(b, i) {
    const p = b.cells[i], c = Math.floor(i / H), r = i % H;
    for (const [dc, dr] of [[1, 0], [0, 1], [1, 1], [1, -1]]) {
      const line = [i];
      for (const s of [1, -1]) {
        let cc = c + dc * s, rr = r + dr * s;
        while (cc >= 0 && cc < W && rr >= 0 && rr < H && b.cells[cc * H + rr] === p) { line.push(cc * H + rr); cc += dc * s; rr += dr * s; }
      }
      if (line.length >= 4) return line;
    }
    return null;
  }
  function evaluate(b, p) {
    const o = 3 - p;
    let s = 0;
    for (let r = 0; r < H; r++) { const v = b.cells[3 * H + r]; if (v === p) s += 3; else if (v === o) s -= 3; }
    for (let k = 0; k < WINDOWS.length; k++) {
      const w = WINDOWS[k];
      let mine = 0, theirs = 0;
      for (let j = 0; j < 4; j++) { const v = b.cells[w[j]]; if (v === p) mine++; else if (v === o) theirs++; }
      if (theirs === 0) s += mine === 3 ? 5 : mine === 2 ? 2 : 0;
      else if (mine === 0) s -= theirs === 3 ? 4 : theirs === 2 ? 1 : 0;
    }
    return s;
  }

  /* search(b, p, { maxDepth, ms }) -> { scores[7], best, depth, nodes, ttHits, ms } */
  function search(b, p, opts) {
    const table = new Map();
    const deadline = performance.now() + (opts.ms || 1e9);
    let nodes = 0, ttHits = 0;
    const ABORT = {};

    function negamax(depth, alpha, beta, pl, ply) {
      if ((++nodes & 2047) === 0 && performance.now() > deadline) throw ABORT;
      const a0 = alpha;
      const e = table.get(b.h1);
      let first = -1;
      if (e && e.h2 === b.h2) {
        first = e.move;
        if (e.depth >= depth) {
          ttHits++;
          if (e.flag === 0) return e.value;
          if (e.flag === 1 && e.value > alpha) alpha = e.value;
          else if (e.flag === 2 && e.value < beta) beta = e.value;
          if (alpha >= beta) return e.value;
        }
      }
      if (b.moves === SIZE) return 0;
      if (depth === 0) return evaluate(b, pl);
      let best = -INF, bestMove = -1;
      const order = first >= 0 ? [first, ...ORDER.filter(c => c !== first)] : ORDER;
      for (const c of order) {
        if (!canPlay(b, c)) continue;
        const i = play(b, c, pl);
        let v;
        if (winLine(b, i)) v = WIN - ply;
        else v = -negamax(depth - 1, -beta, -alpha, 3 - pl, ply + 1);
        undo(b, c);
        if (v > best) { best = v; bestMove = c; }
        if (v > alpha) alpha = v;
        if (alpha >= beta) break;
      }
      table.set(b.h1, { h2: b.h2, depth, value: best, flag: best <= a0 ? 2 : best >= beta ? 1 : 0, move: bestMove });
      return best;
    }

    const t0 = performance.now();
    const snap = { cells: b.cells.slice(), height: b.height.slice(), moves: b.moves, h1: b.h1, h2: b.h2 };
    let result = null;
    for (let d = 1; d <= opts.maxDepth && d <= SIZE - b.moves; d++) {
      const scores = new Array(W).fill(null);
      try {
        for (const c of ORDER) {
          if (!canPlay(b, c)) continue;
          const i = play(b, c, p);
          scores[c] = winLine(b, i) ? WIN : -negamax(d - 1, -INF, INF, 3 - p, 1);
          undo(b, c);
        }
      } catch (err) {
        if (err !== ABORT) throw err;
        /* the aborted search left discs on the board: restore the snapshot */
        b.cells.set(snap.cells); b.height.set(snap.height);
        b.moves = snap.moves; b.h1 = snap.h1; b.h2 = snap.h2;
        break;
      }
      let best = -1;
      for (const c of ORDER) if (scores[c] !== null && (best < 0 || scores[c] > scores[best])) best = c;
      result = { scores, best, depth: d };
      if (Math.abs(scores[best]) > WIN - 100) break; /* forced result found */
    }
    return Object.assign(result || { scores: new Array(W).fill(null), best: ORDER.find(c => canPlay(b, c)), depth: 0 }, { nodes, ttHits, ms: Math.round(performance.now() - t0), tt: table.size });
  }

  return { W, H, WIN, create, canPlay, play, undo, winLine, search };
})();

(() => {
  const LEVELS = { easy: { maxDepth: 2, ms: 200 }, medium: { maxDepth: 5, ms: 500 }, hard: { maxDepth: 14, ms: 900 } };

  Arcade.register({
    id: 'connect-four',
    title: 'Connect Four',
    tag: 'Adversarial search',
    blurb: 'Line up four before the computer does. It looks ahead through every reply it can afford, assuming you always play your best.',
    chips: ['negamax', 'alpha beta', 'transposition table'],
    how: {
      algo: '<em>Negamax</em> is minimax written once: a position is worth the negative of the best position the opponent can reach from it. <em>Alpha beta pruning</em> stops exploring a move as soon as it is proven worse than one already found. <em>Iterative deepening</em> searches 1, 2, 3… moves ahead until the time budget runs out, and each pass orders the moves for the next. At the horizon a heuristic scores every open line of four.',
      ds: 'The board is a flat <code>Int8Array</code> of 42 cells plus a column height array, so a move and its undo are O(1). A <em>transposition table</em> (a hash map keyed by <em>Zobrist hashing</em>: XOR of a random key per disc) remembers positions reached by different move orders, with a second key to catch collisions.',
      cost: 'Plain minimax costs O(7<sup>d</sup>). With good move ordering alpha beta approaches O(7<sup>d/2</sup>), which is why the hard level reaches 10 to 14 moves deep within a second. Each score shown above a column is from the deepest finished pass.',
    },
    glyph: () => {
      const g = ['0000000', '0000000', '0002000', '0012100', '0121200', '1212110'];
      let s = '<svg viewBox="0 0 150 130" width="110" height="96"><rect width="150" height="130" rx="12" fill="#2b2458"/>';
      g.forEach((row, r) => [...row].forEach((v, c) => { s += `<circle cx="${14 + c * 20.3}" cy="${14 + r * 20.3}" r="8" fill="${v === '1' ? '#ef6f6c' : v === '2' ? '#e4c35a' : '#101016'}"/>`; }));
      return s + '</svg>';
    },
    mount(ctx) {
      const { el } = ctx;
      const { W, H, WIN } = C4;
      let level = ctx.store('c4-level') || 'hard';
      let b, turn, over, you = 1, aiStarts = false, last = -1, winCells = null, analysis = null, timer = null;
      let nodesTotal = 0;
      const scoresRow = el('div', { class: 'c4-scores', 'aria-hidden': 'true' });
      const boardEl = el('div', { class: 'c4-board', role: 'grid', 'aria-label': 'Connect Four board' });
      const cols = [], discs = [];
      for (let c = 0; c < W; c++) {
        const col = el('button', { class: 'c4-col', type: 'button', 'aria-label': `Drop in column ${c + 1}`, onclick: () => human(c) });
        discs.push([]);
        for (let r = H - 1; r >= 0; r--) { const d = el('div', { class: 'c4-disc' }); discs[c][r] = d; col.append(d); }
        cols.push(col); boardEl.append(col);
      }
      ctx.stage.append(el('div', { class: 'c4' }, scoresRow, boardEl), el('p', { class: 'hint', text: 'You are red. Click a column, or press 1 to 7.' }));

      ctx.button('New game', () => reset(), { primary: true });
      ctx.segmented([['easy', 'Easy'], ['medium', 'Medium'], ['hard', 'Hard']], level, v => { level = v; ctx.store('c4-level', v); });
      const startBtn = ctx.button('Computer starts', () => { aiStarts = !aiStarts; startBtn.setAttribute('aria-pressed', String(aiStarts)); reset(); });
      startBtn.setAttribute('aria-pressed', 'false');

      function reset() {
        clearTimeout(timer);
        b = C4.create(); over = false; last = -1; winCells = null; analysis = null; nodesTotal = 0;
        you = aiStarts ? 2 : 1;
        turn = 1;
        ctx.status(aiStarts ? 'The computer opens.' : 'Your move.');
        render();
        if (turn !== you) timer = setTimeout(aiMove, 350); else analyse();
      }

      function drop(c, p) {
        const i = C4.play(b, c, p);
        last = i;
        const d = discs[c][i % H];
        d.style.setProperty('--rows', String(H - (i % H)));
        d.classList.remove('drop'); void d.offsetWidth; d.classList.add('drop');
        const line = C4.winLine(b, i);
        if (line) { over = true; winCells = line; ctx.status(p === you ? 'Four in a row. You win.' : 'The computer connects four.', p === you ? 'win' : 'lose'); }
        else if (b.moves === W * H) { over = true; ctx.status('Board full. A draw.'); }
        turn = 3 - p;
      }

      function human(c) {
        if (over || turn !== you || !C4.canPlay(b, c)) return;
        drop(c, you); analysis = null; render();
        if (!over) { ctx.status('Thinking…'); timer = setTimeout(aiMove, 60); }
      }

      function aiMove() {
        if (over) return;
        const a = C4.search(b, 3 - you, LEVELS[level]);
        nodesTotal += a.nodes;
        let pick = a.best;
        if (level === 'easy') { /* easy: pick among moves close to the best */
          const ok = a.scores.map((s, c) => s !== null && s >= a.scores[a.best] - 6 ? c : -1).filter(c => c >= 0);
          pick = ok[Math.floor(Math.random() * ok.length)];
        }
        analysis = Object.assign({ who: 'computer' }, a);
        drop(pick, 3 - you);
        if (!over) ctx.status(`It played column ${pick + 1}. Your move.`);
        render();
        if (!over) timer = setTimeout(analyse, 30);
      }

      function analyse() { /* when thinking is on, show the same search from your side */
        if (over || turn !== you || !ctx.thinking()) return;
        analysis = Object.assign({ who: 'you' }, C4.search(b, you, { maxDepth: 12, ms: 400 }));
        render();
      }

      const fmt = s => {
        if (s === null) return '';
        if (s > WIN - 100) return 'W' + Math.ceil((WIN - s + 1) / 2);
        if (s < -WIN + 100) return 'L' + Math.ceil((WIN + s) / 2);
        return String(s);
      };

      function render() {
        for (let c = 0; c < W; c++) {
          for (let r = 0; r < H; r++) {
            const i = c * H + r, v = b.cells[i];
            const d = discs[c][r];
            const drop = d.classList.contains('drop') && i === last;
            d.className = 'c4-disc' + (v ? ' p' + (v === 1 ? 1 : 2) : '') + (i === last ? ' last' : '') + (winCells && winCells.includes(i) ? ' win' : '') + (drop ? ' drop' : '');
          }
          cols[c].disabled = over || turn !== you || !C4.canPlay(b, c);
        }
        const on = ctx.thinking();
        scoresRow.replaceChildren(...Array.from({ length: W }, (_, c) => {
          if (!on || !analysis || over) return el('span');
          const s = analysis.scores[c];
          return el('span', { class: c === analysis.best ? 'best' : '', text: fmt(s) });
        }));
        ctx.stats([['Moves', b.moves], ['Level', level], ['Nodes searched', nodesTotal.toLocaleString()], ['You are', you === 1 ? 'red' : 'yellow']]);
        if (!on) { ctx.think.replaceChildren(); return; }
        if (!analysis) { ctx.think.replaceChildren(el('p', { text: over ? 'Game over.' : 'Searching…' })); return; }
        const a = analysis;
        ctx.think.replaceChildren(
          el('p', { html: `${a.who === 'computer' ? '<strong>Computer’s last search</strong>' : '<strong>Your options</strong>, searched from your side'}: <span class="big">${a.depth}</span> moves deep.` }),
          el('p', { html: `${a.nodes.toLocaleString()} positions in ${a.ms} ms, ${a.ttHits.toLocaleString()} answered from the transposition table (${a.tt.toLocaleString()} stored).` }),
          el('p', { html: `Numbers above the columns are the score of each move for ${a.who === 'computer' ? 'the computer' : 'you'}. Higher is better. <code>W3</code> means a forced win within 3 moves, <code>L2</code> a forced loss.` })
        );
      }

      const onKey = e => {
        if (e.ctrlKey || e.metaKey || e.altKey) return;
        const n = parseInt(e.key, 10);
        if (n >= 1 && n <= 7) { e.preventDefault(); human(n - 1); }
      };
      document.addEventListener('keydown', onKey);
      ctx.onThinking(on => { if (on) analyse(); render(); });
      reset();
      return () => { clearTimeout(timer); document.removeEventListener('keydown', onKey); };
    },
  });
})();
