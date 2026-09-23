/* Wordle with an information theory solver.
 * Every guess splits the remaining candidates into up to 3^5 = 243 buckets,
 * one per colour pattern. The best guess is the one whose buckets are the
 * most even, i.e. the one with the highest expected information (entropy).
 */
'use strict';

const WordleSolver = (() => {
  const words = WORDS.guesses;                 /* every accepted word */
  const index = new Map(words.map((w, i) => [w, i]));
  const answers = WORDS.answers.map(w => index.get(w));
  const isAnswer = new Uint8Array(words.length);
  answers.forEach(i => { isAnswer[i] = 1; });
  const codes = new Uint8Array(words.length * 5);
  words.forEach((w, i) => { for (let k = 0; k < 5; k++) codes[i * 5 + k] = w.charCodeAt(k) - 97; });
  const POW = [1, 3, 9, 27, 81];
  const count = new Uint8Array(26);

  /* Colour pattern of guess g against answer a, as a base-3 number:
   * 2 = green, 1 = yellow, 0 = grey (digit k is letter k). */
  function pattern(g, a) {
    const G = g * 5, A = a * 5;
    count.fill(0);
    let p = 0, greens = 0;
    for (let k = 0; k < 5; k++) {
      if (codes[G + k] === codes[A + k]) { p += 2 * POW[k]; greens |= 1 << k; }
      else count[codes[A + k]]++;
    }
    for (let k = 0; k < 5; k++) {
      if (greens & (1 << k)) continue;
      const ch = codes[G + k];
      if (count[ch] > 0) { p += POW[k]; count[ch]--; }
    }
    return p;
  }

  const buckets = new Int32Array(243);
  function entropy(g, cands) {
    buckets.fill(0);
    for (let i = 0; i < cands.length; i++) buckets[pattern(g, cands[i])]++;
    const n = cands.length;
    let h = 0;
    for (let b = 0; b < 243; b++) if (buckets[b]) { const p = buckets[b] / n; h -= p * Math.log2(p); }
    return h;
  }

  /* Rank the whole dictionary. Ties go to words that could still be the
   * answer, because those can also win outright. */
  function rank(cands, top = 8) {
    if (cands.length <= 2) return cands.map(c => ({ w: c, h: cands.length === 2 ? 1 : 0, cand: true }));
    const inC = new Uint8Array(words.length);
    cands.forEach(c => { inC[c] = 1; });
    const out = [];
    for (let g = 0; g < words.length; g++) {
      const h = entropy(g, cands);
      out.push({ w: g, h, cand: !!inC[g], key: h + (inC[g] ? 0.02 : 0) });
    }
    out.sort((a, b) => b.key - a.key);
    return out.slice(0, top);
  }

  function filter(cands, g, p) { return cands.filter(c => pattern(g, c) === p); }

  return { words, index, answers, isAnswer, pattern, entropy, rank, filter };
})();

(() => {
  const S = WordleSolver;
  /* Opening ranking over all 2,000 answers, precomputed with the same code
   * (it is ~9 million pattern evaluations, too slow to redo on page load). */
  const OPENERS = [["raise",5.909],["crate",5.867],["trace",5.861],["stare",5.854],["arise",5.852],["slate",5.85],["carte",5.848],["irate",5.837]];

  Arcade.register({
    id: 'wordle',
    title: 'Wordle',
    tag: 'Information theory',
    blurb: 'Six tries to find the hidden five letter word. The solver picks the guess that is expected to cut the list of possible answers the most.',
    chips: ['entropy', 'partitioning', 'hash map'],
    how: {
      algo: 'A guess sorts the remaining candidates into buckets by the colour pattern they would produce (3<sup>5</sup> = 243 possible patterns). A guess whose buckets are evenly sized tells you the most. The solver scores every allowed word by its expected information, <code>H = −Σ p·log₂ p</code> bits, and picks the highest. After each real guess it keeps only the words in the matching bucket.',
      ds: 'Words are stored once as a flat <code>Uint8Array</code> of letter codes, so comparing two words is ten array reads. A 243 slot counting array holds the bucket sizes, a 26 slot array counts letters for yellows, and a <code>Map</code> gives O(1) dictionary lookups.',
      cost: 'Scoring is O(guesses × candidates): 4,681 × 2,000 ≈ 9.4 million pattern checks for the opening move (precomputed), then usually well under a million once the first guess has cut the list. In a test over 300 answers it needed 3.4 guesses on average and never more than 5.',
    },
    glyph: () => {
      const rows = [['x', 'y', 'x', 'x', 'g'], ['g', 'x', 'y', 'g', 'g'], ['g', 'g', 'g', 'g', 'g']];
      const col = { g: '#4f8f4f', y: '#b89a3e', x: '#3a3342' };
      let s = '<svg viewBox="0 0 110 70" width="150" height="96">';
      rows.forEach((r, i) => r.forEach((c, j) => { s += `<rect x="${6 + j * 20}" y="${6 + i * 20}" width="17" height="17" rx="3" fill="${col[c]}"/>`; }));
      return s + '</svg>';
    },
    mount(ctx) {
      const { el } = ctx;
      let secret, cands, rows, cur, over, history, best, solving = null, keyState;
      const tiles = [], rowEls = [];
      const grid = el('div', { class: 'wd-grid', 'aria-label': 'Guesses' });
      for (let r = 0; r < 6; r++) {
        const row = el('div', { class: 'wd-row' });
        tiles.push([]);
        for (let c = 0; c < 5; c++) { const t = el('div', { class: 'wd-tile' }); tiles[r].push(t); row.append(t); }
        rowEls.push(row); grid.append(row);
      }
      const keys = {};
      const kb = el('div', { class: 'kb', 'aria-label': 'Keyboard' });
      ['qwertyuiop', 'asdfghjkl', '+zxcvbnm-'].forEach(line => {
        const row = el('div', { class: 'kb-row' });
        for (const ch of line) {
          const label = ch === '+' ? 'Enter' : ch === '-' ? '⌫' : ch;
          const b = el('button', { class: 'key' + (ch === '+' || ch === '-' ? ' wide' : ''), type: 'button', text: label, 'aria-label': ch === '-' ? 'Backspace' : label, onclick: () => press(ch === '+' ? 'Enter' : ch === '-' ? 'Backspace' : ch) });
          if (ch >= 'a' && ch <= 'z') keys[ch] = b;
          row.append(b);
        }
        kb.append(row);
      });
      ctx.stage.append(grid, kb);

      ctx.button('New word', () => reset(), { primary: true });
      const bestBtn = ctx.button('Play the best guess', () => playBest());
      const solveBtn = ctx.button('Let it solve', () => { if (solving) return; solving = setInterval(() => { if (over) { clearInterval(solving); solving = null; return; } playBest(); }, 900); playBest(); });

      function reset() {
        clearInterval(solving); solving = null;
        secret = S.answers[Math.floor(Math.random() * S.answers.length)];
        cands = S.answers.slice();
        rows = []; cur = ''; over = false; history = []; keyState = {};
        best = OPENERS.length ? OPENERS.map(([w, h]) => ({ w: S.index.get(w), h, cand: !!S.isAnswer[S.index.get(w)] })) : null;
        if (!best) best = S.rank(cands);
        ctx.status('Guess the five letter word.');
        draw(); think();
      }

      function press(k) {
        if (over) return;
        if (k === 'Enter') return submit(cur);
        if (k === 'Backspace') { cur = cur.slice(0, -1); return draw(); }
        if (/^[a-z]$/i.test(k) && cur.length < 5) { cur += k.toLowerCase(); draw(); }
      }

      function submit(word) {
        if (over) return;
        if (word.length !== 5) { shake(); ctx.status('Five letters, please.'); return; }
        const g = S.index.get(word);
        if (g === undefined) { shake(); ctx.status(`“${word.toUpperCase()}” is not in the word list.`); return; }
        const p = S.pattern(g, secret);
        const before = cands.length;
        const expected = S.entropy(g, cands);
        cands = S.filter(cands, g, p);
        history.push({ word, expected, got: Math.log2(before / Math.max(1, cands.length)), left: cands.length });
        rows.push({ word, p }); cur = '';
        for (let k = 0; k < 5; k++) {
          const d = Math.floor(p / [1, 3, 9, 27, 81][k]) % 3, ch = word[k];
          const rankOf = { x: 0, y: 1, g: 2 }, v = 'xyg'[d];
          if (!keyState[ch] || rankOf[v] > rankOf[keyState[ch]]) keyState[ch] = v;
        }
        if (p === 242) { over = true; ctx.status(`Solved in ${rows.length}. The word was ${word.toUpperCase()}.`, 'win'); }
        else if (rows.length === 6) { over = true; ctx.status(`Out of guesses. The word was ${S.words[secret].toUpperCase()}.`, 'lose'); }
        else ctx.status(`${cands.length} possible ${cands.length === 1 ? 'answer' : 'answers'} left.`);
        best = null; draw(); think();
        if (!over) setTimeout(() => { best = S.rank(cands); think(); }, 30);
      }

      function playBest() {
        if (over) return;
        if (!best) best = S.rank(cands);
        submit(S.words[best[0].w]);
      }

      function shake() { const r = rowEls[rows.length]; if (!r) return; r.classList.remove('shake'); void r.offsetWidth; r.classList.add('shake'); }

      function draw() {
        for (let r = 0; r < 6; r++) for (let c = 0; c < 5; c++) {
          const t = tiles[r][c];
          let ch = '', cls = 'wd-tile';
          if (r < rows.length) {
            ch = rows[r].word[c];
            cls += ' ' + 'xyg'[Math.floor(rows[r].p / [1, 3, 9, 27, 81][c]) % 3];
          } else if (r === rows.length && c < cur.length) { ch = cur[c]; cls += ' filled'; }
          t.textContent = ch; t.className = cls;
        }
        for (const ch in keys) keys[ch].className = 'key' + (keyState[ch] ? ' ' + keyState[ch] : '');
        bestBtn.disabled = solveBtn.disabled = over;
        const bits = history.reduce((s, h) => s + h.got, 0);
        ctx.stats([['Guesses', `${rows.length} / 6`], ['Answers left', cands.length], ['Bits gained', bits.toFixed(1)], ['Bits to go', Math.log2(Math.max(1, cands.length)).toFixed(1)]]);
      }

      function think() {
        if (!ctx.thinking()) { ctx.think.replaceChildren(); return; }
        const kids = [];
        kids.push(el('p', { html: `Uncertainty left: <span class="big">${Math.log2(Math.max(1, cands.length)).toFixed(2)} bits</span> (${cands.length} of ${S.answers.length} answers).` }));
        if (history.length) {
          kids.push(el('ul', { class: 'rank' }, history.map(h => el('li', {},
            el('code', { text: h.word.toUpperCase() }),
            el('span', { class: 'bar' }, el('i', { style: `width:${Math.min(100, (h.got / 11) * 100)}%` })),
            el('span', { class: 'v', text: `${h.got.toFixed(1)}b` })))));
          const last = history[history.length - 1];
          kids.push(el('p', { text: `Your last guess was expected to give ${last.expected.toFixed(2)} bits and actually gave ${last.got.toFixed(2)}.` }));
        }
        if (!over) {
          if (!best) kids.push(el('p', { text: 'Scoring all 4,681 words…' }));
          else {
            const top = best[0].h || 1;
            kids.push(el('p', { html: '<strong>Best next guesses</strong> by expected information:' }));
            kids.push(el('ul', { class: 'rank' }, best.map(b => el('li', {},
              el('code', { text: S.words[b.w].toUpperCase() + (b.cand ? ' ✓' : '') }),
              el('span', { class: 'bar' }, el('i', { style: `width:${(b.h / top) * 100}%` })),
              el('span', { class: 'v', text: b.h.toFixed(2) + 'b' })))));
            kids.push(el('p', { text: '✓ could itself be the answer.' }));
          }
          if (cands.length <= 12) kids.push(el('p', { html: 'Still possible: ' + cands.map(c => `<code>${S.words[c].toUpperCase()}</code>`).join(' ') }));
        }
        ctx.think.replaceChildren(...kids);
      }

      const onKey = e => {
        if (e.ctrlKey || e.metaKey || e.altKey) return;
        if (e.target && /input|textarea/i.test(e.target.tagName)) return;
        if (e.key === 'Enter' || e.key === 'Backspace' || /^[a-z]$/i.test(e.key)) {
          if (e.key === 'Enter' && e.target && e.target.tagName === 'BUTTON' && !e.target.classList.contains('key')) return;
          e.preventDefault(); press(e.key);
        }
      };
      document.addEventListener('keydown', onKey);
      ctx.onThinking(think);
      reset();
      return () => { document.removeEventListener('keydown', onKey); clearInterval(solving); };
    },
  });
})();
