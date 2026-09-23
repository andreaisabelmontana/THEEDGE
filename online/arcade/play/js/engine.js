/* Battleship engine — a line-for-line port of src/board.c and src/ai.c.
 *
 * Parity with the C engine is enforced by `make parity`, which diffs AI
 * self-play traces from both implementations byte-for-byte (same splitmix64
 * PRNG, same iteration order, same tie-breaking). Change one side, change
 * both.
 */
'use strict';

const Battleship = (() => {
  const N = 10;
  const SHIP_NAMES = ['Carrier', 'Battleship', 'Cruiser', 'Submarine', 'Destroyer'];
  const SHIP_LENS = [5, 4, 3, 3, 2];

  /* ---- splitmix64 (BigInt, matches the C uint64_t exactly) ---- */
  const MASK = (1n << 64n) - 1n;
  const GAMMA = 0x9E3779B97F4A7C15n;

  function rngNew(seed) {
    let s = BigInt(seed) & MASK;
    if (s === 0n) s = GAMMA;
    return { s };
  }

  function rngNext(r) {
    r.s = (r.s + GAMMA) & MASK;
    let z = r.s;
    z = ((z ^ (z >> 30n)) * 0xBF58476D1CE4E5B9n) & MASK;
    z = ((z ^ (z >> 27n)) * 0x94D049BB133111EBn) & MASK;
    return (z ^ (z >> 31n)) & MASK;
  }

  function rngBelow(r, n) {
    return Number(rngNext(r) % BigInt(n));
  }

  /* ---- board ---- */
  function boardNew() {
    const shipAt = [], shot = [];
    for (let r = 0; r < N; r++) {
      shipAt.push(new Array(N).fill(-1));
      shot.push(new Array(N).fill(false));
    }
    const ships = SHIP_LENS.map((len, i) => ({
      name: SHIP_NAMES[i], len, row: -1, col: -1, horiz: true, hits: 0,
    }));
    return { shipAt, shot, ships, alive: 0 };
  }

  function canPlace(b, len, row, col, horiz) {
    if (row < 0 || col < 0) return false;
    if ((horiz ? col + len : col + 1) > N) return false;
    if ((horiz ? row + 1 : row + len) > N) return false;
    for (let k = 0; k < len; k++) {
      const r = horiz ? row : row + k;
      const c = horiz ? col + k : col;
      if (b.shipAt[r][c] !== -1) return false;
    }
    return true;
  }

  function place(b, idx, row, col, horiz) {
    const s = b.ships[idx];
    if (s.row !== -1) return false;
    if (!canPlace(b, s.len, row, col, horiz)) return false;
    s.row = row; s.col = col; s.horiz = horiz;
    for (let k = 0; k < s.len; k++) {
      const r = horiz ? row : row + k;
      const c = horiz ? col + k : col;
      b.shipAt[r][c] = idx;
    }
    b.alive++;
    return true;
  }

  function unplace(b, idx) { /* web-only helper for the placement UI */
    const s = b.ships[idx];
    if (s.row === -1) return;
    for (let k = 0; k < s.len; k++) {
      const r = s.horiz ? s.row : s.row + k;
      const c = s.horiz ? s.col + k : s.col;
      b.shipAt[r][c] = -1;
    }
    s.row = -1; s.col = -1; s.hits = 0;
    b.alive--;
  }

  function placeShipRandom(b, idx, rng) {
    for (let tries = 0; tries < 100000; tries++) {
      const horiz = rngBelow(rng, 2) === 0;
      const row = rngBelow(rng, N);
      const col = rngBelow(rng, N);
      if (place(b, idx, row, col, horiz)) return true;
    }
    return false;
  }

  function placeAllRandom(b, rng) {
    for (let i = 0; i < SHIP_LENS.length; i++)
      if (b.ships[i].row === -1) placeShipRandom(b, i, rng);
  }

  /* fire -> { res: 'invalid'|'repeat'|'miss'|'hit'|'sunk', sunk: idx|-1 } */
  function fire(b, row, col) {
    if (row < 0 || row >= N || col < 0 || col >= N) return { res: 'invalid', sunk: -1 };
    if (b.shot[row][col]) return { res: 'repeat', sunk: -1 };
    b.shot[row][col] = true;
    const idx = b.shipAt[row][col];
    if (idx < 0) return { res: 'miss', sunk: -1 };
    const s = b.ships[idx];
    s.hits++;
    if (s.hits === s.len) {
      b.alive--;
      return { res: 'sunk', sunk: idx };
    }
    return { res: 'hit', sunk: -1 };
  }

  function allSunk(b) { return b.alive === 0; }

  /* ---- probability-density AI (see src/ai.c for the full commentary) ---- */
  const AI_UNKNOWN = 0, AI_MISS = 1, AI_HIT = 2, AI_SUNK = 3;

  function aiNew(rng) {
    const cell = [];
    for (let r = 0; r < N; r++) cell.push(new Array(N).fill(AI_UNKNOWN));
    return { cell, enemyAlive: new Array(SHIP_LENS.length).fill(true), rng };
  }

  function aiPick(ai) {
    const density = [];
    for (let r = 0; r < N; r++) density.push(new Array(N).fill(0));

    let targeting = false;
    for (let r = 0; r < N && !targeting; r++)
      for (let c = 0; c < N && !targeting; c++)
        if (ai.cell[r][c] === AI_HIT) targeting = true;

    for (let i = 0; i < SHIP_LENS.length; i++) {
      if (!ai.enemyAlive[i]) continue;
      const len = SHIP_LENS[i];
      for (let horiz = 0; horiz < 2; horiz++) {
        const rmax = horiz ? N : N - len + 1;
        const cmax = horiz ? N - len + 1 : N;
        for (let r = 0; r < rmax; r++) {
          for (let c = 0; c < cmax; c++) {
            let ok = true, hitsCovered = 0;
            for (let k = 0; k < len; k++) {
              const cell = horiz ? ai.cell[r][c + k] : ai.cell[r + k][c];
              if (cell === AI_MISS || cell === AI_SUNK) { ok = false; break; }
              if (cell === AI_HIT) hitsCovered++;
            }
            if (!ok) continue;
            if (targeting && hitsCovered === 0) continue;
            const w = targeting ? hitsCovered * hitsCovered : 1;
            for (let k = 0; k < len; k++) {
              const rr = horiz ? r : r + k;
              const cc = horiz ? c + k : c;
              if (ai.cell[rr][cc] === AI_UNKNOWN) density[rr][cc] += w;
            }
          }
        }
      }
    }

    let best = -1, ties = 0, br = 0, bc = 0;
    for (let r = 0; r < N; r++) {
      for (let c = 0; c < N; c++) {
        if (ai.cell[r][c] !== AI_UNKNOWN) continue;
        const d = density[r][c];
        if (d > best) {
          best = d; ties = 1; br = r; bc = c;
        } else if (d === best) {
          ties++;
          if (rngBelow(ai.rng, ties) === 0) { br = r; bc = c; }
        }
      }
    }
    return [br, bc];
  }

  function aiNotify(ai, row, col, res, enemy, sunkIdx) {
    if (res === 'miss') {
      ai.cell[row][col] = AI_MISS;
    } else if (res === 'hit') {
      ai.cell[row][col] = AI_HIT;
    } else if (res === 'sunk') {
      const s = enemy.ships[sunkIdx];
      for (let k = 0; k < s.len; k++) {
        const r = s.horiz ? s.row : s.row + k;
        const c = s.horiz ? s.col + k : s.col;
        ai.cell[r][c] = AI_SUNK;
      }
      ai.enemyAlive[sunkIdx] = false;
    }
  }

  return {
    N, SHIP_NAMES, SHIP_LENS,
    rngNew, rngNext, rngBelow,
    boardNew, canPlace, place, unplace, placeShipRandom, placeAllRandom,
    fire, allSunk,
    aiNew, aiPick, aiNotify,
  };
})();

if (typeof module !== 'undefined') module.exports = Battleship;
