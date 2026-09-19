/* Everything on the page that moves.

   Five independent pieces, each set up only if its markup is present, so a
   section can be removed from the HTML without breaking the rest:

     the loop      an eight step walk through the task lifecycle
     the station   the bin's state machine, driven by clicks instead of sensors
     the proof     the verification service, run live against a tampered bundle
     the yard      the deployment planner, three simulated days on a canvas
     the frontier  the solvency sweep, run here rather than pasted in

   The last three are ports of code in the robopreneur-andes repository. The yard
   is checked against the Python model field by field in CI; the proof checker is
   a direct translation of the oracle's checks. */

import { Simulation } from './sim.js';
import * as oracle from './oracle.js';

const $ = (selector, root = document) => root.querySelector(selector);
const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));

/* ------------------------------------------------------------------ chrome */

function entrances() {
  const targets = $$('.section, .headline-figures, .panel');
  if (!('IntersectionObserver' in window)) return;
  const io = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (entry.isIntersecting) {
          entry.target.classList.add('in');
          io.unobserve(entry.target);
        }
      }
    },
    { rootMargin: '0px 0px -8% 0px', threshold: 0.05 }
  );
  for (const target of targets) {
    target.setAttribute('data-reveal', '');
    io.observe(target);
  }
}

function navHighlight() {
  const links = $$('.topbar nav a');
  const sections = links.map((a) => document.getElementById(a.getAttribute('href').slice(1))).filter(Boolean);
  if (!sections.length || !('IntersectionObserver' in window)) return;

  const io = new IntersectionObserver(
    (entries) => {
      for (const entry of entries) {
        if (!entry.isIntersecting) continue;
        for (const link of links) {
          link.setAttribute('aria-current', String(link.getAttribute('href') === `#${entry.target.id}`));
        }
      }
    },
    { rootMargin: '-45% 0px -50% 0px' }
  );
  for (const section of sections) io.observe(section);
}

/* -------------------------------------------------------------- the loop */

const LOOP = [
  {
    title: 'A member posts work',
    body: 'The escrow is funded in the same transaction that advertises the job, so nobody can post work they cannot pay for. The reward sits in the contract from that moment until an oracle verdict, a cancellation, or the deadline.',
  },
  {
    title: 'The market matches it',
    body: 'The cheapest idle provider of that kind whose price clears the reward takes the job, and ties break on the lower id so a run is reproducible from the event log alone. Robots and people compete on the same board for the same work.',
  },
  {
    title: 'The robot works',
    body: 'It drives out to the site, picks the item up, and carries it back. Nothing about the job is special to a robot: the contract knows a provider, a price and a deadline, and not what the provider is made of.',
  },
  {
    title: 'The bin station records',
    body: 'The camera names the material, the ring lights the bin it belongs in, and a sensor in each bin records where the item actually landed. That row is the study’s data and the robot’s evidence at the same time.',
  },
  {
    title: 'The robot publishes its evidence',
    body: 'One JSON Lines file: its odometry, its joint positions, the disposal row from the station. It commits that file’s sha256 on chain in the same transaction that submits it, so the bytes cannot be changed afterwards.',
  },
  {
    title: 'The oracle replays it',
    body: 'Route length, the stop and start shape of a real collection, how far the arm left its rest pose, whether every logged position was inside the declared yard, and which bin the item went in. Each check reports its own numbers.',
  },
  {
    title: 'The escrow settles',
    body: 'Approved pays the provider its price and returns the surplus to the poster. Refused returns the whole escrow and writes the reason on chain, truncated to fit, so a member can read why their money did not move.',
  },
  {
    title: 'The robot buys a charge',
    body: 'Below its threshold it posts a charging job of its own and pays a person to plug it in. This is the step the whole experiment is about: if it cannot afford that, it stands in the yard and stops being a robot.',
  },
];

function loopWalker() {
  const stage = $('.loop-stage');
  if (!stage) return;

  const steps = $$('.loop-step', stage);
  const titleEl = $('[data-loop-title]');
  const bodyEl = $('[data-loop-body]');
  const dotsEl = $('[data-loop-dots]');
  const playBtn = $('[data-loop-play]');

  let index = 0;
  let timer = null;
  let playing = !window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  const dots = LOOP.map((step, i) => {
    const dot = document.createElement('button');
    dot.type = 'button';
    dot.setAttribute('role', 'tab');
    dot.setAttribute('aria-label', `Step ${i + 1}: ${step.title}`);
    dot.addEventListener('click', () => {
      stop();
      show(i);
    });
    dotsEl.append(dot);
    return dot;
  });

  function show(next) {
    index = (next + LOOP.length) % LOOP.length;
    steps.forEach((step, i) => step.classList.toggle('on', i === index));
    dots.forEach((dot, i) => dot.setAttribute('aria-current', String(i === index)));
    titleEl.textContent = `${String(index + 1).padStart(2, '0')} · ${LOOP[index].title}`;
    bodyEl.textContent = LOOP[index].body;
  }

  function start() {
    playing = true;
    playBtn.textContent = 'Pause';
    clearInterval(timer);
    timer = setInterval(() => show(index + 1), 4200);
  }

  function stop() {
    playing = false;
    playBtn.textContent = 'Play';
    clearInterval(timer);
  }

  playBtn.addEventListener('click', () => (playing ? stop() : start()));
  $('[data-loop-next]').addEventListener('click', () => {
    stop();
    show(index + 1);
  });
  $('[data-loop-prev]').addEventListener('click', () => {
    stop();
    show(index - 1);
  });

  show(0);
  if (playing) start();

  // Do not animate a diagram nobody is looking at.
  if ('IntersectionObserver' in window) {
    new IntersectionObserver(
      ([entry]) => {
        if (!entry.isIntersecting) clearInterval(timer);
        else if (playing) start();
      },
      { threshold: 0.2 }
    ).observe(stage);
  }
}

/* ----------------------------------------------------------- the station */

const ITEMS = [
  { glyph: '\u{1F9F4}', name: 'plastic bottle', material: 'plastic', confidence: 0.94 },
  { glyph: '\u{1F34C}', name: 'banana peel', material: 'food', confidence: 0.91 },
  { glyph: '\u{1F4E6}', name: 'cardboard box', material: 'cardboard', confidence: 0.96 },
  { glyph: '\u{1FAD9}', name: 'glass jar', material: 'glass', confidence: 0.88 },
  { glyph: '☕', name: 'coffee grounds', material: 'coffee_grounds', confidence: 0.83 },
  { glyph: '\u{1F9FB}', name: 'used napkin', material: 'napkin', confidence: 0.74 },
  { glyph: '\u{1F35E}', name: 'styrofoam tray', material: 'styrofoam', confidence: 0.69 },
  { glyph: '\u{1F36C}', name: 'snack wrapper', material: 'multilayer_wrapper', confidence: 0.62 },
  { glyph: '❓', name: 'something in a bag', material: 'unknown', confidence: 0.31 },
];

const BIN_OF = {
  plastic: 'blanco',
  glass: 'blanco',
  metal: 'blanco',
  paper: 'blanco',
  cardboard: 'blanco',
  tetrapak: 'blanco',
  food: 'verde',
  garden: 'verde',
  coffee_grounds: 'verde',
  soiled_paper: 'negro',
  napkin: 'negro',
  styrofoam: 'negro',
  multilayer_wrapper: 'negro',
  ceramic: 'negro',
  cigarette: 'negro',
  diaper: 'negro',
};

const BIN_LABEL = { blanco: 'blanco', verde: 'verde', negro: 'negro' };
const MIN_CONFIDENCE = 0.55;

function station() {
  const itemsEl = $('[data-items]');
  if (!itemsEl) return;

  const binButtons = $$('[data-bins] .bin');
  const screen = $('[data-screen]');
  const big = $('[data-screen-big]');
  const small = $('[data-screen-small]');
  const rowEl = $('[data-station-row]');

  const tally = { n: 0, ok: 0, paid: 0, credit: 0 };
  let phase = 'idle';
  let expected = null;
  let current = null;
  let paidArm = true;
  let resetTimer = null;

  for (const item of ITEMS) {
    const button = document.createElement('button');
    button.type = 'button';
    button.className = 'item';
    button.innerHTML = `<span class="glyph" aria-hidden="true">${item.glyph}</span>${item.name}`;
    button.addEventListener('click', () => present(item));
    itemsEl.append(button);
  }

  function setScreen(tone, headline, sub) {
    screen.className = `screen ${tone}`;
    big.textContent = headline;
    small.textContent = sub;
  }

  function light(bin) {
    for (const button of binButtons) button.classList.toggle('lit', button.dataset.bin === bin);
  }

  function enableBins(on) {
    for (const button of binButtons) button.disabled = !on;
  }

  function present(item) {
    if (phase !== 'idle') return;
    clearTimeout(resetTimer);
    phase = 'inspecting';
    current = item;

    // The arm is decided before the item is seen, which is the whole point.
    paidArm = Math.random() < 0.5;

    light(null);
    enableBins(false);
    setScreen('wait', 'Looking…', `The camera has the ${item.name}.`);

    setTimeout(() => {
      const bin = item.confidence >= MIN_CONFIDENCE ? BIN_OF[item.material] : undefined;
      if (!bin) {
        phase = 'unsure';
        setScreen(
          'bad',
          'Not sure',
          item.material === 'unknown'
            ? 'Nothing the taxonomy covers. The station lights no bin rather than guessing.'
            : `Only ${Math.round(item.confidence * 100)}% sure, below the threshold. It lights nothing.`
        );
        writeRow(null);
        resetTimer = setTimeout(reset, 3400);
        return;
      }
      expected = bin;
      phase = 'guiding';
      light(bin);
      enableBins(true);
      setScreen(
        'wait',
        `Throw it in ${BIN_LABEL[bin]}`,
        `Read as ${item.material.replace(/_/g, ' ')}, ${Math.round(item.confidence * 100)}% confident. ` +
          (paidArm ? 'This session pays.' : 'This session does not pay.')
      );
    }, 620);
  }

  function threw(bin) {
    if (phase !== 'guiding') return;
    const correct = bin === expected;
    phase = 'done';
    enableBins(false);
    light(expected);

    tally.n += 1;
    if (correct) tally.ok += 1;
    if (paidArm) tally.paid += 1;
    if (correct && paidArm) tally.credit += 1;

    if (correct) {
      setScreen(
        'good',
        paidArm ? '¡Bien hecho! +1 ANDE' : '¡Bien hecho!',
        paidArm ? 'Right bin, and this session was in the paid arm.' : 'Right bin. This session was in the unpaid arm, so nothing is credited.'
      );
    } else {
      setScreen('bad', 'Wrong bin', `That went in ${BIN_LABEL[bin]}. The ring is still showing ${BIN_LABEL[expected]}.`);
    }

    writeRow(bin);
    updateTally();
    resetTimer = setTimeout(reset, 3600);
  }

  function reset() {
    phase = 'idle';
    expected = null;
    current = null;
    light(null);
    enableBins(false);
    setScreen('wait', 'Ready', 'Pick an item above to start a session.');
  }

  function writeRow(bin) {
    if (!current) return;
    if (bin === null) {
      rowEl.textContent = '# the station lit nothing, so no session row is written';
      return;
    }
    const row = {
      topic: 'disposal',
      t: Number((Date.now() / 1000).toFixed(2)),
      bin,
      expected,
      material: current.material,
      confidence: current.confidence,
      reward_arm: paidArm,
    };
    rowEl.innerHTML = JSON.stringify(row, null, 2)
      .replace(/"([^"]+)":/g, '<span class="k">"$1"</span>:')
      .replace(/: ("(?:[^"]*)")/g, ': <span class="s">$1</span>')
      .replace(/: (true|false|[\d.]+)/g, ': <span class="n">$1</span>');
  }

  function updateTally() {
    $('[data-tally-n]').textContent = String(tally.n);
    $('[data-tally-ok]').textContent = String(tally.ok);
    $('[data-tally-paid]').textContent = String(tally.paid);
    $('[data-tally-credit]').textContent = tally.credit.toFixed(1);
  }

  for (const button of binButtons) {
    button.addEventListener('click', () => threw(button.dataset.bin));
  }

  reset();
}

/* ------------------------------------------------------------- the proof */

function proofChecker() {
  const togglesEl = $('[data-proof-toggles]');
  if (!togglesEl) return;

  const verdictEl = $('[data-verdict]');
  const bundleEl = $('[data-proof-bundle]');
  const hashLine = $('[data-hashline]');
  const committedEl = $('[data-hash-committed]');
  const actualEl = $('[data-hash-actual]');

  const inputs = $$('input[type="checkbox"]', togglesEl);
  for (const input of inputs) input.addEventListener('change', render);

  async function render() {
    const on = Object.fromEntries(inputs.map((i) => [i.dataset.break, i.checked]));

    const records = oracle.collectionRun({
      metres: on.route ? 1.0 : 12.0,
      armDrift: on.arm ? 0.02 : 3.1,
      thrown: on.bin ? 'negro' : 'verde',
      expected: 'verde',
      stray: !!on.area,
      pauseSamples: on.pauses ? 0 : 6,
    });

    // The commitment is made at submission. Normally it is the digest of the
    // file the robot then publishes. With the swap on, the robot commits to one
    // run and publishes an edited copy, which is caught before any check runs.
    const pristine = oracle.serialise(oracle.collectionRun());
    let text = oracle.serialise(records);
    let committed;
    if (on.swap) {
      committed = await oracle.digest(pristine);
      const edited = structuredClone(records);
      edited[0].recorded_at = '2026-09-05T11:42:00-05:00';
      text = oracle.serialise(edited);
    } else {
      committed = await oracle.digest(text);
    }

    const actual = await oracle.digest(text);
    committedEl.textContent = `${committed.slice(0, 22)}…`;
    actualEl.textContent = `${actual.slice(0, 22)}…`;
    hashLine.classList.toggle('mismatch', committed !== actual);

    const shown = text.split('\n').filter(Boolean);
    const head = shown.slice(0, 3);
    const tail = shown.slice(-3);
    bundleEl.textContent = [...head, `… ${shown.length - 6} more records …`, ...tail].join('\n');

    if (committed !== actual) {
      verdictEl.innerHTML = `
        <div class="line fail"><span class="tag">FAIL</span><span class="detail">digest: committed ${committed.slice(
          0,
          18
        )}, file hashes to ${actual.slice(0, 18)}</span></div>
        <div class="stamp no">REJECTED</div>
        <div class="why">the file is not the one the robot committed to, so no check is run on it at all</div>`;
      return;
    }

    const bundle = oracle.parse(text.split('\n'));
    const verdict = oracle.judge(bundle);
    const lines = verdict.results
      .map(
        (r) =>
          `<div class="line ${r.passed ? 'pass' : 'fail'}"><span class="tag">${
            r.passed ? 'pass' : 'FAIL'
          }</span><span class="detail">${r.name}: ${escapeHtml(r.detail)}</span></div>`
      )
      .join('');

    verdictEl.innerHTML = `
      ${lines}
      <div class="stamp ${verdict.approved ? 'ok' : 'no'}">${verdict.approved ? 'APPROVED' : 'REJECTED'}</div>
      <div class="why">${
        verdict.approved
          ? 'the escrow releases: 110 ANDE to the robot, 40 back to the poster'
          : escapeHtml(verdict.reason)
      }</div>`;
  }

  render();
}

const escapeHtml = (s) => s.replace(/[&<>]/g, (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;' })[c]);

/* -------------------------------------------------------------- the yard */

const YARD_COLOURS = {
  ground: '#0c0c0f',
  grid: 'rgba(244,244,237,0.055)',
  robot: '#bc13fe',
  robotFlat: '#ff5d5d',
  human: '#f4f4ed',
  task: 'rgba(255,159,28,0.85)',
  station: '#4ade80',
};

async function yard() {
  const canvas = $('[data-yard]');
  if (!canvas) return;

  let base;
  let thin;
  try {
    [base, thin] = await Promise.all([
      fetch('andes-yard.json').then((r) => r.json()),
      fetch('thin-demand.json').then((r) => r.json()),
    ]);
  } catch {
    $('[data-out-verdict]').textContent =
      'The simulation could not load its configuration. Open the page over http rather than from a file.';
    return;
  }

  const ctx = canvas.getContext('2d');
  const chart = $('[data-chart]');
  const chartCtx = chart.getContext('2d');

  const controls = {
    rate: $('[data-ctl-rate]'),
    charge: $('[data-ctl-charge]'),
    ask: $('[data-ctl-ask]'),
    drain: $('[data-ctl-drain]'),
  };
  const readouts = {
    day: $('[data-out-day]'),
    done: $('[data-out-done]'),
    balance: $('[data-out-balance]'),
    battery: $('[data-out-battery]'),
    verdict: $('[data-out-verdict]'),
  };
  const playBtn = $('[data-sim-play]');

  let sim = null;
  let raf = null;
  let running = false;

  function config() {
    const cfg = structuredClone(base);
    cfg.arrival_rate_per_hour = Number(controls.rate.value);
    cfg.robots[0].drain = Number(controls.drain.value);
    for (const service of Object.keys(cfg.robots[0].offers)) {
      // move every price the robot asks by the same amount the slider moved
      const ratio = Number(controls.ask.value) / 110;
      cfg.robots[0].offers[service] = Math.round(cfg.robots[0].offers[service] * ratio);
    }
    cfg.humans[0].offers.BatteryCharging = Number(controls.charge.value);
    cfg.humans[1].offers.BatteryCharging = Number(controls.charge.value) + 10;
    return cfg;
  }

  function labels() {
    $('[data-val-rate]').textContent = Number(controls.rate.value).toFixed(2);
    $('[data-val-charge]').textContent = controls.charge.value;
    $('[data-val-ask]').textContent = controls.ask.value;
    $('[data-val-drain]').textContent = Number(controls.drain.value).toFixed(2);
  }

  function reset() {
    cancelAnimationFrame(raf);
    running = false;
    playBtn.textContent = 'Play';
    sim = new Simulation(config());
    labels();
    draw();
    drawChart();
    readouts.verdict.className = 'verdict-banner';
    readouts.verdict.textContent = 'Press play to run three simulated days.';
    updateReadouts();
  }

  function updateReadouts() {
    const last = sim.history[sim.history.length - 1];
    const robot = sim.agents.find((a) => a.kind === 'robot');
    readouts.day.textContent = String(Math.floor(sim.minute / 1440) + 1);
    readouts.done.textContent = String(last ? last.completed : 0);
    readouts.balance.textContent = Math.round(robot.wealth).toLocaleString();
    readouts.battery.textContent = `${Math.round(robot.battery)}%`;
    readouts.balance.className = `v ${robot.wealth >= base.robots[0].wealth ? 'good' : 'bad'}`;
    readouts.battery.className = `v ${robot.battery > 15 ? '' : 'bad'}`;
  }

  function fit(canvasEl, context) {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const rect = canvasEl.getBoundingClientRect();
    if (!rect.width) return { w: canvasEl.width, h: canvasEl.height };
    canvasEl.width = Math.round(rect.width * dpr);
    canvasEl.height = Math.round(rect.height * dpr);
    context.setTransform(dpr, 0, 0, dpr, 0, 0);
    return { w: rect.width, h: rect.height };
  }

  function draw() {
    const { w, h } = fit(canvas, ctx);
    const cfg = sim.config;
    const pad = 22;
    const sx = (w - pad * 2) / cfg.yard[0];
    const sy = (h - pad * 2) / cfg.yard[1];
    const s = Math.min(sx, sy);
    const ox = (w - cfg.yard[0] * s) / 2;
    const oy = (h - cfg.yard[1] * s) / 2;
    const X = (x) => ox + x * s;
    const Y = (y) => oy + (cfg.yard[1] - y) * s;

    ctx.fillStyle = YARD_COLOURS.ground;
    ctx.fillRect(0, 0, w, h);

    ctx.strokeStyle = YARD_COLOURS.grid;
    ctx.lineWidth = 1;
    for (let x = 0; x <= cfg.yard[0]; x += 10) {
      ctx.beginPath();
      ctx.moveTo(X(x), Y(0));
      ctx.lineTo(X(x), Y(cfg.yard[1]));
      ctx.stroke();
    }
    for (let y = 0; y <= cfg.yard[1]; y += 10) {
      ctx.beginPath();
      ctx.moveTo(X(0), Y(y));
      ctx.lineTo(X(cfg.yard[0]), Y(y));
      ctx.stroke();
    }

    ctx.strokeStyle = 'rgba(244,244,237,0.16)';
    ctx.strokeRect(X(0), Y(cfg.yard[1]), cfg.yard[0] * s, cfg.yard[1] * s);

    // open jobs waiting for somebody
    for (const task of sim.queue) {
      ctx.strokeStyle = YARD_COLOURS.task;
      ctx.lineWidth = 1.4;
      ctx.beginPath();
      ctx.arc(X(task.site[0]), Y(task.site[1]), 6, 0, Math.PI * 2);
      ctx.stroke();
    }

    // the charging point
    ctx.fillStyle = YARD_COLOURS.station;
    ctx.globalAlpha = 0.9;
    ctx.beginPath();
    ctx.arc(X(cfg.station[0]), Y(cfg.station[1]), 5, 0, Math.PI * 2);
    ctx.fill();
    ctx.globalAlpha = 1;
    ctx.font = '11px ui-monospace, monospace';
    ctx.fillStyle = 'rgba(244,244,237,0.45)';
    ctx.fillText('charge', X(cfg.station[0]) + 9, Y(cfg.station[1]) + 4);

    for (const agent of sim.agents) {
      const isRobot = agent.kind === 'robot';
      const flat = isRobot && agent.status === 'stalled';
      const x = X(agent.x);
      const y = Y(agent.y);

      if (isRobot) {
        ctx.fillStyle = flat ? 'rgba(255,93,93,0.16)' : 'rgba(188,19,254,0.16)';
        ctx.beginPath();
        ctx.arc(x, y, 16, 0, Math.PI * 2);
        ctx.fill();
      }

      ctx.fillStyle = isRobot ? (flat ? YARD_COLOURS.robotFlat : YARD_COLOURS.robot) : YARD_COLOURS.human;
      ctx.beginPath();
      ctx.arc(x, y, isRobot ? 7 : 5, 0, Math.PI * 2);
      ctx.fill();

      if (isRobot) {
        // a battery gauge under the robot
        const gw = 26;
        ctx.fillStyle = 'rgba(244,244,237,0.16)';
        ctx.fillRect(x - gw / 2, y + 13, gw, 3);
        ctx.fillStyle = agent.battery > 30 ? YARD_COLOURS.station : YARD_COLOURS.robotFlat;
        ctx.fillRect(x - gw / 2, y + 13, (gw * agent.battery) / 100, 3);
      }

      ctx.font = '10px ui-monospace, monospace';
      ctx.fillStyle = 'rgba(244,244,237,0.5)';
      ctx.fillText(flat ? `${agent.id} (flat)` : agent.id, x + 10, y - 8);
    }
  }

  function drawChart() {
    const { w, h } = fit(chart, chartCtx);
    chartCtx.clearRect(0, 0, w, h);
    const history = sim.history;
    if (history.length < 2) return;

    const pad = 6;
    const n = history.length;
    const total = sim.config.minutes;
    const balances = history.map((s) => s.robot_wealth);
    const lo = Math.min(...balances, base.robots[0].wealth);
    const hi = Math.max(...balances, base.robots[0].wealth);
    const spread = hi - lo || 1;
    const queueMax = Math.max(4, ...history.map((s) => s.queue));

    const px = (minute) => pad + (minute / total) * (w - pad * 2);

    const series = (values, colour, width = 1.8) => {
      chartCtx.beginPath();
      chartCtx.strokeStyle = colour;
      chartCtx.lineWidth = width;
      values.forEach(([minute, v], i) => {
        const y = pad + (1 - v) * (h - pad * 2);
        if (i === 0) chartCtx.moveTo(px(minute), y);
        else chartCtx.lineTo(px(minute), y);
      });
      chartCtx.stroke();
    };

    // the line the balance has to stay above to have paid for itself
    const startY = pad + (1 - (base.robots[0].wealth - lo) / spread) * (h - pad * 2);
    chartCtx.setLineDash([3, 4]);
    chartCtx.strokeStyle = 'rgba(244,244,237,0.22)';
    chartCtx.lineWidth = 1;
    chartCtx.beginPath();
    chartCtx.moveTo(pad, startY);
    chartCtx.lineTo(w - pad, startY);
    chartCtx.stroke();
    chartCtx.setLineDash([]);

    const step = Math.max(1, Math.floor(n / 600));
    const sampled = history.filter((_, i) => i % step === 0 || i === n - 1);
    series(sampled.map((s) => [s.minute, (s.robot_wealth - lo) / spread]), '#bc13fe');
    series(sampled.map((s) => [s.minute, s.mean_battery / 100]), '#4ade80', 1.2);
    series(sampled.map((s) => [s.minute, s.queue / queueMax]), '#ff9f1c', 1.2);
  }

  function finish() {
    const summary = sim.summary();
    readouts.verdict.className = `verdict-banner ${summary.solvent ? 'ok' : 'no'}`;
    const change = Math.round(summary.robotBalanceChange).toLocaleString();
    readouts.verdict.innerHTML = summary.solvent
      ? `<b>The fleet paid for itself.</b> ${summary.completed} jobs done, balance ${
          summary.robotBalanceChange >= 0 ? '+' : ''
        }${change} ANDE, and never a minute standing flat.`
      : `<b>The fleet did not pay for itself.</b> ${summary.completed} jobs done, balance ${
          summary.robotBalanceChange >= 0 ? '+' : ''
        }${change} ANDE, and ${summary.stalled.toLocaleString()} robot minutes flat with no credit to buy a charge.`;
  }

  function tick() {
    const perFrame = 14; // three simulated days in about five seconds
    for (let i = 0; i < perFrame && sim.minute < sim.config.minutes; i++) sim.step();
    draw();
    drawChart();
    updateReadouts();

    if (sim.minute >= sim.config.minutes) {
      running = false;
      playBtn.textContent = 'Replay';
      finish();
      return;
    }
    raf = requestAnimationFrame(tick);
  }

  playBtn.addEventListener('click', () => {
    if (running) {
      cancelAnimationFrame(raf);
      running = false;
      playBtn.textContent = 'Play';
      return;
    }
    if (sim.minute >= sim.config.minutes) reset();
    running = true;
    playBtn.textContent = 'Pause';
    raf = requestAnimationFrame(tick);
  });

  $('[data-sim-reset]').addEventListener('click', reset);

  for (const input of Object.values(controls)) {
    input.addEventListener('input', labels);
    input.addEventListener('change', reset);
  }

  for (const button of $$('[data-preset]')) {
    button.addEventListener('click', () => {
      const preset = button.dataset.preset === 'thin' ? thin : base;
      controls.rate.value = String(preset.arrival_rate_per_hour);
      controls.charge.value = String(preset.humans[0].offers.BatteryCharging);
      controls.ask.value = String(preset.robots[0].offers.WasteCollection);
      controls.drain.value = String(preset.robots[0].drain);
      for (const other of $$('[data-preset]')) {
        other.setAttribute('aria-pressed', String(other === button));
      }
      reset();
    });
  }

  window.addEventListener('resize', () => {
    draw();
    drawChart();
  });

  reset();
  return { config, base };
}

/* ---------------------------------------------------------- the frontier */

async function frontier(configSource) {
  const canvas = $('[data-frontier]');
  if (!canvas || !configSource) return;

  const ctx = canvas.getContext('2d');
  const progress = $('[data-frontier-progress]');
  const button = $('[data-frontier-run]');
  const rates = [0.2, 0.3, 0.4, 0.5, 0.6, 0.8, 1.0, 1.5, 2.0, 3.0];
  const seeds = 7;
  let rows = [];

  function paint() {
    const dpr = Math.min(2, window.devicePixelRatio || 1);
    const rect = canvas.getBoundingClientRect();
    if (rect.width) {
      canvas.width = Math.round(rect.width * dpr);
      canvas.height = Math.round(rect.height * dpr);
      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    }
    const w = rect.width || canvas.width;
    const h = rect.height || canvas.height;

    ctx.clearRect(0, 0, w, h);
    const padL = 74;
    const padR = 24;
    const padT = 26;
    const padB = 46;

    const values = rows.map((r) => r.median);
    const lo = Math.min(-600, ...values);
    const hi = Math.max(600, ...values);
    const X = (i) => padL + (i / (rates.length - 1)) * (w - padL - padR);
    const Y = (v) => padT + (1 - (v - lo) / (hi - lo)) * (h - padT - padB);

    // gridlines and the zero line
    ctx.font = '11px ui-monospace, monospace';
    ctx.textAlign = 'right';
    for (let i = 0; i <= 4; i++) {
      const v = lo + ((hi - lo) * i) / 4;
      const y = Y(v);
      ctx.strokeStyle = 'rgba(244,244,237,0.06)';
      ctx.lineWidth = 1;
      ctx.beginPath();
      ctx.moveTo(padL, y);
      ctx.lineTo(w - padR, y);
      ctx.stroke();
      ctx.fillStyle = 'rgba(244,244,237,0.4)';
      ctx.fillText(`${Math.round(v / 100) * 100}`, padL - 10, y + 4);
    }

    const zero = Y(0);
    ctx.strokeStyle = 'rgba(244,244,237,0.3)';
    ctx.setLineDash([4, 5]);
    ctx.beginPath();
    ctx.moveTo(padL, zero);
    ctx.lineTo(w - padR, zero);
    ctx.stroke();
    ctx.setLineDash([]);
    ctx.textAlign = 'left';
    ctx.fillStyle = 'rgba(244,244,237,0.45)';
    ctx.fillText('breaks even', padL + 6, zero - 8);

    ctx.textAlign = 'center';
    rates.forEach((rate, i) => {
      ctx.fillStyle = 'rgba(244,244,237,0.45)';
      ctx.fillText(String(rate), X(i), h - padB + 22);
    });
    ctx.fillStyle = 'rgba(244,244,237,0.6)';
    ctx.fillText('jobs an hour', (padL + w - padR) / 2, h - 10);

    ctx.save();
    ctx.translate(18, (padT + h - padB) / 2);
    ctx.rotate(-Math.PI / 2);
    ctx.fillText("change in the robot's balance, ANDE", 0, 0);
    ctx.restore();

    if (!rows.length) return;

    ctx.beginPath();
    ctx.strokeStyle = '#bc13fe';
    ctx.lineWidth = 2;
    rows.forEach((row, i) => {
      const x = X(i);
      const y = Y(row.median);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();

    rows.forEach((row, i) => {
      const x = X(i);
      const y = Y(row.median);
      const colour = row.solvent === seeds ? '#4ade80' : row.solvent === 0 ? '#ff5d5d' : '#ff9f1c';
      ctx.fillStyle = '#0a0a0b';
      ctx.beginPath();
      ctx.arc(x, y, 6, 0, Math.PI * 2);
      ctx.fill();
      ctx.fillStyle = colour;
      ctx.beginPath();
      ctx.arc(x, y, 4.5, 0, Math.PI * 2);
      ctx.fill();

      if (row.stalled > 0) {
        ctx.fillStyle = 'rgba(255,93,93,0.75)';
        ctx.font = '10px ui-monospace, monospace';
        ctx.fillText(`${Math.round(row.stalled / 60)}h flat`, x, y + 22);
      }
    });

    const viable = rows.filter((r) => r.solvent === seeds);
    if (viable.length) {
      const first = rows.indexOf(viable[0]);
      const x = X(first);
      ctx.strokeStyle = 'rgba(74,222,128,0.45)';
      ctx.setLineDash([3, 4]);
      ctx.beginPath();
      ctx.moveTo(x, padT);
      ctx.lineTo(x, h - padB);
      ctx.stroke();
      ctx.setLineDash([]);
      ctx.fillStyle = '#4ade80';
      ctx.font = '11px ui-monospace, monospace';
      ctx.fillText(`every seed pays from ${viable[0].rate}`, x, padT - 8);
    }
  }

  async function run() {
    button.disabled = true;
    rows = [];
    const cfg = configSource.base;

    for (let i = 0; i < rates.length; i++) {
      const rate = rates[i];
      progress.textContent = `running ${seeds} seeds at ${rate.toFixed(2)} jobs an hour … (${i + 1} of ${rates.length})`;
      // Yield between rates so the page stays responsive. A timer rather than a
      // frame callback: a backgrounded tab stops painting, and a sweep somebody
      // started before switching away should still finish.
      await new Promise((resolve) => setTimeout(resolve, 0));

      const deltas = [];
      const stalls = [];
      let solvent = 0;
      for (let seed = 1; seed <= seeds; seed++) {
        const variant = structuredClone(cfg);
        variant.arrival_rate_per_hour = rate;
        variant.seed = cfg.seed + seed;
        const sim = new Simulation(variant).run();
        const summary = sim.summary();
        deltas.push(summary.robotBalanceChange);
        stalls.push(summary.stalled);
        if (summary.solvent) solvent += 1;
      }
      deltas.sort((a, b) => a - b);
      stalls.sort((a, b) => a - b);
      rows.push({
        rate,
        median: deltas[Math.floor(deltas.length / 2)],
        stalled: stalls[Math.floor(stalls.length / 2)],
        solvent,
      });
      paint();
    }

    const viable = rows.filter((r) => r.solvent === seeds);
    progress.textContent = viable.length
      ? `${rates.length * seeds} runs. Every seed pays for itself from ${viable[0].rate.toFixed(2)} jobs an hour.`
      : `${rates.length * seeds} runs. No rate in this sweep paid for itself in every run.`;
    button.disabled = false;
    button.textContent = 'Run it again';
  }

  button.addEventListener('click', run);
  window.addEventListener('resize', paint);
  paint();
}

/* -------------------------------------------------------------------- go */

entrances();
navHighlight();
loopWalker();
station();
proofChecker();
yard().then(frontier);
