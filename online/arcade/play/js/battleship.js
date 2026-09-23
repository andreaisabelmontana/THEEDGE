/* Battleship: the original probability-density opponent (engine.js, ported
 * line for line from the C11 engine), now with its density map drawn live.
 * A second copy of the same AI tracks what YOU know about the enemy fleet,
 * so the thinking view can show where you should shoot too.
 */
'use strict';

(() => {
  const B = Battleship, N = B.N;
  const UNKNOWN = 0, MISS = 1, HIT = 2, SUNK = 3;

  /* Same counting as aiPick in engine.js, but returns the whole density
   * grid so it can be drawn. Kept separate so the engine stays byte-for-byte
   * in parity with the C version. */
  function density(ai) {
    const d = Array.from({ length: N }, () => new Array(N).fill(0));
    let targeting = false, placements = 0, max = 0;
    for (let r = 0; r < N && !targeting; r++)
      for (let c = 0; c < N && !targeting; c++) if (ai.cell[r][c] === HIT) targeting = true;
    for (let i = 0; i < B.SHIP_LENS.length; i++) {
      if (!ai.enemyAlive[i]) continue;
      const len = B.SHIP_LENS[i];
      for (let horiz = 0; horiz < 2; horiz++) {
        const rmax = horiz ? N : N - len + 1, cmax = horiz ? N - len + 1 : N;
        for (let r = 0; r < rmax; r++) for (let c = 0; c < cmax; c++) {
          let ok = true, hits = 0;
          for (let k = 0; k < len; k++) {
            const v = horiz ? ai.cell[r][c + k] : ai.cell[r + k][c];
            if (v === MISS || v === SUNK) { ok = false; break; }
            if (v === HIT) hits++;
          }
          if (!ok || (targeting && hits === 0)) continue;
          placements++;
          const w = targeting ? hits * hits : 1;
          for (let k = 0; k < len; k++) {
            const rr = horiz ? r : r + k, cc = horiz ? c + k : c;
            if (ai.cell[rr][cc] === UNKNOWN) d[rr][cc] += w;
          }
        }
      }
    }
    let br = -1, bc = -1;
    for (let r = 0; r < N; r++) for (let c = 0; c < N; c++)
      if (ai.cell[r][c] === UNKNOWN && d[r][c] > max) { max = d[r][c]; br = r; bc = c; }
    return { d, max, targeting, placements, best: [br, bc] };
  }

  Arcade.register({
    id: 'battleship',
    title: 'Battleship',
    tag: 'Probability',
    blurb: 'Sink the fleet before the computer sinks yours. Its opponent counts every way the remaining ships could still fit and fires at the likeliest square.',
    chips: ['probability density', 'enumeration', 'C11 ⇄ JS parity'],
    how: {
      algo: 'For every ship still afloat, try every horizontal and vertical placement that does not cross a miss or a sunk ship. Each legal placement adds one vote to the squares it covers. The square with the most votes is the most likely to hold a ship. Once something is hit, the AI switches to <em>targeting</em> and only counts placements through the hits, weighted by hits².',
      ds: 'Two 10×10 grids per side: <code>shipAt</code> (which ship sits where) and the AI\'s knowledge grid (unknown, miss, hit, sunk). Ties are broken with a seeded <code>splitmix64</code> generator so the C and JavaScript engines replay identically.',
      cost: 'Each turn scans at most 5 ships × 2 orientations × 100 anchors × 5 cells, about 5,000 checks, so it answers instantly. The engine is the same code as the C11 version; a parity test diffs full self-play traces between the two.',
    },
    glyph: () => {
      let s = '<svg viewBox="0 0 100 100" width="96" height="96">';
      for (let r = 0; r < 6; r++) for (let c = 0; c < 6; c++) {
        const t = Math.max(0, 1 - Math.hypot(r - 2.4, c - 3.1) / 3.2);
        s += `<rect x="${8 + c * 14}" y="${8 + r * 14}" width="12" height="12" rx="2" fill="${Arcade.heat(t)}"/>`;
      }
      return s + '</svg>';
    },
    mount(ctx) {
      const { el } = ctx;
      let rng, me, foe, ai, hint, turn, over, timer = null, auto = false;
      let yourShots = 0, yourHits = 0, aiShots = 0, aiHits = 0, lastAi = null;

      const mkGrid = (enemy) => {
        const cells = [];
        const g = el('div', { class: 'grid', role: 'grid', 'aria-label': enemy ? 'Enemy waters' : 'Your fleet' });
        for (let r = 0; r < N; r++) for (let c = 0; c < N; c++) {
          const b = el('button', { class: 'cell', type: 'button', 'aria-label': `${'ABCDEFGHIJ'[r]}${c + 1}` });
          if (enemy) b.addEventListener('click', () => shoot(r, c));
          else b.tabIndex = -1;
          cells.push(b); g.append(b);
        }
        return { g, cells };
      };
      const foeView = mkGrid(true), meView = mkGrid(false);
      const foeFleet = el('div', { class: 'fleet' }), meFleet = el('div', { class: 'fleet' });
      ctx.stage.append(
        el('div', { class: 'bs-boards' },
          el('div', { class: 'bs-board enemy' }, el('h2', { text: 'Enemy waters' }), foeView.g, foeFleet),
          el('div', { class: 'bs-board' }, el('h2', { text: 'Your fleet' }), meView.g, meFleet)),
        el('p', { class: 'hint', text: 'Click a square in enemy waters to fire.' })
      );

      ctx.button('New game', () => reset(), { primary: true });
      const shuffleBtn = ctx.button('Shuffle my fleet', () => { if (yourShots === 0) { me = B.boardNew(); B.placeAllRandom(me, rng); render(); } });
      ctx.button('Take the best shot', () => { if (!over && turn === 'you') { const [r, c] = density(hint).best; shoot(r, c); } }, { title: 'Fire where the probability map points' });
      const autoBtn = ctx.button('Autoplay', () => { auto = !auto; autoBtn.setAttribute('aria-pressed', String(auto)); if (auto) step(); });
      autoBtn.setAttribute('aria-pressed', 'false');

      function reset() {
        clearTimeout(timer);
        const seed = Date.now() >>> 0;
        rng = B.rngNew(seed);
        me = B.boardNew(); foe = B.boardNew();
        B.placeAllRandom(me, rng); B.placeAllRandom(foe, rng);
        ai = B.aiNew(B.rngNew(seed ^ 0x5bd1e995));
        hint = B.aiNew(B.rngNew(seed ^ 0x27d4eb2f));
        turn = 'you'; over = false; lastAi = null;
        yourShots = yourHits = aiShots = aiHits = 0;
        ctx.status('Your move. Pick a square in enemy waters.');
        render();
        if (auto) step();
      }

      function step() {
        clearTimeout(timer);
        if (!auto || over) return;
        timer = setTimeout(() => { if (turn === 'you') { const [r, c] = density(hint).best; shoot(r, c); } }, 380);
      }

      function shoot(r, c) {
        if (over || turn !== 'you' || foe.shot[r][c]) return;
        const res = B.fire(foe, r, c);
        B.aiNotify(hint, r, c, res.res, foe, res.sunk);
        yourShots++; if (res.res !== 'miss') yourHits++;
        if (res.res === 'sunk') ctx.status(`You sank their ${B.SHIP_NAMES[res.sunk]}.`);
        else ctx.status(res.res === 'hit' ? 'Hit.' : 'Miss.');
        if (B.allSunk(foe)) { over = true; ctx.status(`You win in ${yourShots} shots.`, 'win'); render(); return; }
        turn = 'ai'; render();
        timer = setTimeout(aiTurn, 420);
      }

      function aiTurn() {
        const [r, c] = B.aiPick(ai);
        const res = B.fire(me, r, c);
        B.aiNotify(ai, r, c, res.res, me, res.sunk);
        aiShots++; if (res.res !== 'miss') aiHits++;
        lastAi = [r, c];
        const where = `${'ABCDEFGHIJ'[r]}${c + 1}`;
        if (B.allSunk(me)) { over = true; ctx.status(`The computer sank your whole fleet in ${aiShots} shots.`, 'lose'); render(); return; }
        ctx.status(res.res === 'sunk' ? `They fired at ${where} and sank your ${B.SHIP_NAMES[res.sunk]}. Your move.` : `They fired at ${where}: ${res.res}. Your move.`);
        turn = 'you'; render(); step();
      }

      function paint(view, board, know, showShips, dens) {
        for (let r = 0; r < N; r++) for (let c = 0; c < N; c++) {
          const b = view.cells[r * N + c];
          const k = know.cell[r][c];
          let cls = 'cell';
          if (k === MISS) cls += ' miss';
          else if (k === HIT) cls += ' hit';
          else if (k === SUNK) cls += ' sunk';
          else if (showShips && board.shipAt[r][c] >= 0) cls += ' ship';
          b.style.background = '';
          b.style.boxShadow = '';
          b.textContent = '';
          if (dens && k === UNKNOWN) {
            const t = dens.max ? dens.d[r][c] / dens.max : 0;
            b.style.background = Arcade.heat(t);
            if (showShips && board.shipAt[r][c] >= 0) b.style.boxShadow = 'inset 0 0 0 2px #9fb3cc';
            if (dens.best[0] === r && dens.best[1] === c) cls += ' target';
          }
          if (!dens && showShips && lastAi && lastAi[0] === r && lastAi[1] === c) cls += ' target';
          b.className = cls;
        }
      }

      function fleet(node, board, reveal) {
        node.replaceChildren(...board.ships.map(s => s.hits === s.len ? el('s', { text: `${s.name} ${s.len}` }) : el('span', { text: reveal ? `${s.name} ${s.len}` : `${s.name} ${s.len}` })));
      }

      function render() {
        const on = ctx.thinking();
        const hd = on && !over ? density(hint) : null;
        const ad = on && !over ? density(ai) : null;
        paint(foeView, foe, hint, over, hd);
        paint(meView, me, ai, true, ad);
        fleet(foeFleet, foe); fleet(meFleet, me);
        shuffleBtn.disabled = yourShots > 0;
        const pct = (h, s) => s ? Math.round(100 * h / s) + '%' : '·';
        ctx.stats([['Your shots', yourShots], ['Your accuracy', pct(yourHits, yourShots)], ['AI shots', aiShots], ['AI accuracy', pct(aiHits, aiShots)]]);
        if (!on) { ctx.think.replaceChildren(); return; }
        if (over) { ctx.think.replaceChildren(el('p', { text: 'Game over. Start a new game to watch the maps again.' })); return; }
        const cellName = ([r, c]) => `${'ABCDEFGHIJ'[r]}${c + 1}`;
        ctx.think.replaceChildren(
          el('p', { html: `<strong>Computer</strong> is <em>${ad.targeting ? 'targeting' : 'hunting'}</em>, weighing <span class="big">${ad.placements.toLocaleString()}</span> legal ship placements on your board. Its next shot: <code>${cellName(ad.best)}</code>.` }),
          el('p', { html: `<strong>For you</strong>: ${hd.placements.toLocaleString()} placements still fit in enemy waters. The likeliest square is <code>${cellName(hd.best)}</code> (outlined).` }),
          el('div', { class: 'legend' }, el('span', { text: 'unlikely' }), el('span', { class: 'ramp' }), el('span', { text: 'likely' }))
        );
      }

      ctx.onThinking(render);
      reset();
      return () => { clearTimeout(timer); auto = false; };
    },
  });
})();
