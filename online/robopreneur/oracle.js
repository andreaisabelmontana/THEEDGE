/* The verification service, in the browser.

   A port of oracle/rodeo_oracle from the robopreneur-andes repository: the same
   proof format, the same checks, the same thresholds, the same wording in the
   verdict. It is here so the page can actually verify a proof in front of the
   reader instead of showing a screenshot of one being verified.

   The digest is computed with SubtleCrypto, so tampering with the bundle on this
   page really does break the commitment, the same way it would on chain. */

export const STATION_YARD = [
  [-6, -4],
  [14, -4],
  [14, 16],
  [-6, 16],
];

export const POLICY = {
  taskType: 'WasteCollection',
  minSeconds: 20,
  maxSeconds: 1800,
  minPathMetres: 5,
  requireMotionShape: true,
  minArmDriftRad: 1.5,
  requireDisposal: true,
  workArea: STATION_YARD,
};

const START = 1725462000;

/* ------------------------------------------------------------------ the bundle */

export function parse(lines) {
  const bundle = { manifest: null, odom: [], joints: [], disposals: [], power: [], unknown: 0 };

  lines.forEach((raw, index) => {
    const text = raw.trim();
    if (!text || text.startsWith('#')) return;
    let record;
    try {
      record = JSON.parse(text);
    } catch {
      throw new Error(`line ${index + 1}: not valid JSON`);
    }
    switch (record.topic) {
      case 'manifest':
        if (bundle.manifest) throw new Error('a bundle carries exactly one manifest');
        bundle.manifest = record;
        break;
      case 'odom':
        bundle.odom.push(record);
        break;
      case 'joints':
        bundle.joints.push(record);
        break;
      case 'disposal':
        bundle.disposals.push(record);
        break;
      case 'power':
        bundle.power.push(record);
        break;
      default:
        bundle.unknown += 1;
    }
  });

  if (!bundle.manifest) throw new Error('bundle has no manifest line');
  for (const series of [bundle.odom, bundle.joints, bundle.disposals, bundle.power]) {
    series.sort((a, b) => a.t - b.t);
  }
  return bundle;
}

export function pathLength(bundle) {
  let total = 0;
  for (let i = 1; i < bundle.odom.length; i++) {
    total += Math.hypot(bundle.odom[i].x - bundle.odom[i - 1].x, bundle.odom[i].y - bundle.odom[i - 1].y);
  }
  return total;
}

export function span(bundle) {
  const stamps = [...bundle.odom, ...bundle.joints, ...bundle.disposals, ...bundle.power].map((r) => r.t);
  if (stamps.length < 2) return 0;
  return Math.max(...stamps) - Math.min(...stamps);
}

export function jointDrift(bundle) {
  if (!bundle.joints.length) return {};
  const start = bundle.joints[0].positions;
  const drift = {};
  for (const name of Object.keys(start)) drift[name] = 0;
  for (const sample of bundle.joints) {
    for (const [name, value] of Object.entries(sample.positions)) {
      if (name in start) drift[name] = Math.max(drift[name], Math.abs(value - start[name]));
    }
  }
  return drift;
}

/* ------------------------------------------------------------------ the checks */

const result = (name, passed, detail) => ({ name, passed, detail });

export function timingSane(bundle, min, max) {
  const seconds = span(bundle);
  if (seconds < min) return result('duration', false, `${seconds.toFixed(1)} s recorded, at least ${min} s expected`);
  if (max && seconds > max) return result('duration', false, `${seconds.toFixed(1)} s recorded, past the ${max} s ceiling`);
  return result('duration', true, `${seconds.toFixed(1)} s`);
}

export function baseTravelled(bundle, minMetres) {
  const distance = pathLength(bundle);
  return result(
    'base travelled',
    distance >= minMetres,
    `${distance.toFixed(2)} m recorded, ${minMetres.toFixed(2)} m required`
  );
}

export function stoppedMovedStopped(bundle, stillMetres = 0.02, minStill = 3) {
  if (bundle.odom.length < 3) return result('motion shape', false, 'fewer than three position samples');

  const states = [];
  for (let i = 1; i < bundle.odom.length; i++) {
    const step = Math.hypot(bundle.odom[i].x - bundle.odom[i - 1].x, bundle.odom[i].y - bundle.odom[i - 1].y);
    states.push(step > stillMetres ? 'moving' : 'still');
  }

  const runs = [];
  for (const state of states) {
    if (runs.length && runs[runs.length - 1][0] === state) runs[runs.length - 1][1] += 1;
    else runs.push([state, 1]);
  }

  const collapsed = [];
  for (const [state, length] of runs) {
    if (state !== 'moving' && length < minStill) continue;
    if (!collapsed.length || collapsed[collapsed.length - 1] !== state) collapsed.push(state);
  }

  let found = false;
  for (let i = 0; i + 2 < collapsed.length; i++) {
    if (collapsed[i] === 'still' && collapsed[i + 1] === 'moving' && collapsed[i + 2] === 'still') found = true;
  }
  return result('motion shape', found, `observed ${collapsed.join(' ') || 'nothing'}`);
}

export function armWorked(bundle, minDrift) {
  const drift = jointDrift(bundle);
  const names = Object.keys(drift);
  if (!names.length) return result('arm moved', false, 'no joint samples in the bundle');
  const total = names.reduce((a, n) => a + drift[n], 0);
  const busiest = names
    .sort((a, b) => drift[b] - drift[a])
    .slice(0, 3)
    .map((n) => `${n} ${drift[n].toFixed(3)}`)
    .join(', ');
  return result(
    'arm moved',
    total >= minDrift,
    `net drift ${total.toFixed(3)} rad, ${minDrift.toFixed(3)} rad required (${busiest})`
  );
}

export function disposalCorrect(bundle) {
  if (!bundle.disposals.length) return result('disposal', false, 'no disposal event was recorded');
  const good = bundle.disposals.filter((d) => d.bin === d.expected);
  if (good.length) return result('disposal', true, `${good[good.length - 1].bin} bin, matching the station`);
  const last = bundle.disposals[bundle.disposals.length - 1];
  return result('disposal', false, `thrown in ${last.bin || 'nothing'}, station asked for ${last.expected}`);
}

export function insideWorkArea(bundle, polygon) {
  if (!polygon || !polygon.length) return result('work area', true, 'no boundary declared for this task');
  if (!bundle.odom.length) return result('work area', false, 'no position samples to place inside the area');
  const strays = bundle.odom.filter((o) => !contains(polygon, o.x, o.y));
  if (strays.length) {
    return result(
      'work area',
      false,
      `${strays.length} samples outside, first at (${strays[0].x.toFixed(2)}, ${strays[0].y.toFixed(2)})`
    );
  }
  return result('work area', true, `all ${bundle.odom.length} samples inside the boundary`);
}

function contains(polygon, x, y) {
  let inside = false;
  for (let i = 0; i < polygon.length; i++) {
    const [x1, y1] = polygon[i];
    const [x2, y2] = polygon[(i + 1) % polygon.length];
    if (onSegment(x1, y1, x2, y2, x, y)) return true;
    if (y1 > y !== y2 > y) {
      const crossing = x1 + ((y - y1) * (x2 - x1)) / (y2 - y1);
      if (crossing > x) inside = !inside;
    }
  }
  return inside;
}

function onSegment(x1, y1, x2, y2, x, y, eps = 1e-9) {
  const cross = (x2 - x1) * (y - y1) - (y2 - y1) * (x - x1);
  if (Math.abs(cross) > eps) return false;
  return (
    Math.min(x1, x2) - eps <= x && x <= Math.max(x1, x2) + eps && Math.min(y1, y2) - eps <= y && y <= Math.max(y1, y2) + eps
  );
}

/* ----------------------------------------------------------------- the verdict */

export function judge(bundle, policy = POLICY) {
  const results = [];
  if (policy.minSeconds || policy.maxSeconds) results.push(timingSane(bundle, policy.minSeconds, policy.maxSeconds));
  if (policy.minPathMetres) results.push(baseTravelled(bundle, policy.minPathMetres));
  if (policy.requireMotionShape) results.push(stoppedMovedStopped(bundle));
  if (policy.workArea) results.push(insideWorkArea(bundle, policy.workArea));
  if (policy.minArmDriftRad) results.push(armWorked(bundle, policy.minArmDriftRad));
  if (policy.requireDisposal) results.push(disposalCorrect(bundle));

  const failures = results.filter((r) => !r.passed);
  const approved = results.length > 0 && failures.length === 0;
  const reason = approved ? '' : failures.map((r) => `${r.name}: ${r.detail}`).join('; ').slice(0, 128);
  return { taskId: bundle.manifest.task_id, taskType: bundle.manifest.task_type, approved, reason, results };
}

export async function digest(text) {
  const bytes = new TextEncoder().encode(text);
  const hash = await crypto.subtle.digest('SHA-256', bytes);
  return (
    '0x' +
    [...new Uint8Array(hash)].map((b) => b.toString(16).padStart(2, '0')).join('')
  );
}

/* ------------------------------------------------------- a run to verify */

/* A port of rodeo_oracle.sample.collection_run, including its seeded jitter, so
   the bundle on the page is the same bundle the repository's tests use. */

export function collectionRun({
  taskId = 1,
  seed = 7,
  metres = 12,
  armDrift = 3.1,
  thrown = 'verde',
  expected = 'verde',
  pauseSamples = 6,
  stray = false,
} = {}) {
  const rng = mulberry32(seed);
  const records = [
    {
      topic: 'manifest',
      task_id: taskId,
      task_type: 'WasteCollection',
      robot: '0x1d5aC2D4b2b7B62C1a17F2A9E1D3b0F5Cc3A8e11',
      site: 'andes-station-yard',
      recorded_at: '2026-09-05T09:00:00-05:00',
    },
  ];

  let t = START;
  let x = 0.5;
  let y = 0.5;
  const odom = () => records.push({ topic: 'odom', t: round2(t), x: round3(x), y: round3(y) });

  for (let i = 0; i < pauseSamples; i++) {
    odom();
    t += 1;
  }

  const steps = Math.max(1, Math.floor(metres));
  const step = metres / steps;
  for (let i = 0; i < steps; i++) {
    const heading = 0.35 + 0.12 * Math.sin(i / 2);
    x += step * Math.cos(heading) + (rng() * 0.02 - 0.01);
    y += step * Math.sin(heading) + (rng() * 0.02 - 0.01);
    t += 2;
    odom();
  }

  if (stray) {
    x = 40;
    y = 40;
    t += 2;
    odom();
  }

  for (let i = 0; i < pauseSamples; i++) {
    t += 1;
    odom();
  }

  const joints = ['shoulder', 'elbow', 'wrist', 'gripper'];
  const rest = {};
  for (const name of joints) rest[name] = 0;
  records.push({ topic: 'joints', t: round2(t), positions: { ...rest } });
  const perJoint = armDrift / joints.length;
  for (const phase of [0.35, 0.7, 1.0, 0.6, 0.1]) {
    t += 1.5;
    const positions = {};
    for (const name of joints) positions[name] = round4(perJoint * phase);
    records.push({ topic: 'joints', t: round2(t), positions });
  }

  t += 1;
  records.push({ topic: 'disposal', t: round2(t), bin: thrown, expected });
  return records;
}

export const serialise = (records) => records.map((r) => JSON.stringify(r)).join('\n') + '\n';

const round2 = (v) => Math.round(v * 100) / 100;
const round3 = (v) => Math.round(v * 1000) / 1000;
const round4 = (v) => Math.round(v * 10000) / 10000;

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a) >>> 0;
    t = (((t + Math.imul(t ^ (t >>> 7), 61 | t)) >>> 0) ^ t) >>> 0;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}
