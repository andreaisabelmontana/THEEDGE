/* The yard model, in the browser.

   This is a line for line port of planner/andes_planner, kept deliberately
   parallel so the two can be read side by side. It exists because a page that
   lets a recruiter turn the demand knob and watch the robot go flat explains the
   result far better than a chart of it does.

   The port is checked, not asserted: planner/parity/golden-run.json holds a run
   produced by the Python model, and planner/web/parity.mjs replays it here and
   compares every sampled minute. They agree exactly, which is why the generator
   below is written in 32 bit integer arithmetic and why nothing in the model
   reaches for a logarithm.

   No build step, no dependencies. It is an ES module and it runs in Node for the
   parity check and in the page for the live version. */

const MASK32 = 0xffffffff;
const TWO32 = 4294967296;
export const CHARGING = 'BatteryCharging';

/* ------------------------------------------------------------------ mulberry32 */

export class Rng {
  constructor(seed) {
    this.state = seed >>> 0;
  }

  random() {
    this.state = (this.state + 0x6d2b79f5) >>> 0;
    const a = this.state;
    let t = Math.imul(a ^ (a >>> 15), 1 | a) >>> 0;
    t = (((t + Math.imul(t ^ (t >>> 7), 61 | t)) >>> 0) ^ t) >>> 0;
    return ((t ^ (t >>> 14)) >>> 0) / TWO32;
  }

  chance(p) {
    return this.random() < p;
  }

  integer(low, high) {
    if (high <= low) return low;
    return low + Math.floor(this.random() * (high - low + 1));
  }

  uniform(low, high) {
    return low + this.random() * (high - low);
  }

  pick(items) {
    if (!items.length) throw new Error('nothing to pick from');
    return items[this.integer(0, items.length - 1)];
  }

  weighted(items, weights) {
    const total = weights.reduce((a, b) => a + b, 0);
    if (total <= 0) return this.pick(items);
    const threshold = this.random() * total;
    let running = 0;
    for (let i = 0; i < items.length; i++) {
      running += weights[i];
      if (running > threshold) return items[i];
    }
    return items[items.length - 1];
  }
}

/* ----------------------------------------------------------------- the measures */

export function gini(values) {
  if (!values.length) return 0;
  const total = values.reduce((a, b) => a + b, 0);
  if (total <= 0) return 0;
  const ordered = [...values].sort((a, b) => a - b);
  const n = ordered.length;
  let weighted = 0;
  for (let i = 0; i < n; i++) weighted += (i + 1) * ordered[i];
  return (2 * weighted) / (n * total) - (n + 1) / n;
}

// Python rounds its snapshot fields to six places before recording them, so the
// port has to round the same way or the comparison drowns in float dust.
const round6 = (v) => Math.round(v * 1e6) / 1e6;

/* --------------------------------------------------------------- the simulation */

export class Simulation {
  constructor(config) {
    this.config = config;
    this.rng = new Rng(config.seed);
    this.minute = 0;
    this.tasks = [];
    this.queue = [];
    this.history = [];
    this.nextTaskId = 1;
    this.orgWealth = config.org_wealth;

    this.agents = [];
    for (const spec of config.robots ?? []) this.agents.push(makeAgent(spec, 'robot'));
    for (const spec of config.humans ?? []) this.agents.push(makeAgent(spec, 'human'));
  }

  agent(id) {
    return this.agents.find((a) => a.id === id) ?? null;
  }

  balance(holder) {
    if (holder === 'org') return this.orgWealth;
    return this.agent(holder)?.wealth ?? 0;
  }

  pay(payer, payee, amount) {
    if (payer === 'org') this.orgWealth -= amount;
    else {
      const from = this.agent(payer);
      if (from) from.wealth -= amount;
    }
    const to = this.agent(payee);
    if (to) to.wealth += amount;
  }

  step() {
    this.arrivals();
    this.batteries();
    this.assign();
    this.work();
    const snapshot = this.measure();
    this.history.push(snapshot);
    this.minute += 1;
    return snapshot;
  }

  run(minutes = this.config.minutes) {
    while (this.minute < minutes) this.step();
    return this;
  }

  /* ------------------------------------------------------------------ phases */

  arrivals() {
    const demand = this.config.demand ?? {};
    const names = Object.keys(demand);
    if (!names.length) return;
    if (!this.rng.chance(this.config.arrival_rate_per_hour / 60)) return;
    const service = this.rng.weighted(names, names.map((n) => demand[n]));
    this.post(service, 'org', this.somewhere());
  }

  post(service, poster, site) {
    const task = {
      id: this.nextTaskId++,
      service,
      poster,
      site,
      created: this.minute,
      price: 0,
      duration: 0,
      remaining: 0,
      executor: '',
      status: 'open',
      assigned: -1,
      finished: -1,
    };
    this.tasks.push(task);
    this.queue.push(task);
    return task;
  }

  somewhere() {
    return [this.rng.uniform(0, this.config.yard[0]), this.rng.uniform(0, this.config.yard[1])];
  }

  batteries() {
    for (const robot of this.agents.filter((a) => a.kind === 'robot')) {
      if (robot.awaitingCharge) {
        if (robot.status !== 'working') robot.battery = Math.max(0, robot.battery - robot.drain);
        if (robot.battery <= 0 && robot.chargeTask === null) robot.stalledMinutes += 1;
        continue;
      }

      robot.battery = Math.max(0, robot.battery - robot.drain);
      if (robot.battery > robot.rechargeTrigger) continue;

      if (robot.task !== null) {
        this.requeue(robot.task);
        robot.task = null;
      }
      robot.awaitingCharge = true;
      robot.status = 'waiting';
      robot.target = this.config.station;

      const price = this.cheapestChargePrice();
      if (price === null || robot.wealth < price) {
        robot.status = 'stalled';
        robot.stalledMinutes += 1;
        continue;
      }
      robot.chargeTask = this.post(CHARGING, robot.id, this.config.station);
    }
  }

  cheapestChargePrice() {
    const prices = this.agents.filter((a) => CHARGING in a.offers).map((a) => a.offers[CHARGING]);
    return prices.length ? Math.min(...prices) : null;
  }

  requeue(task) {
    task.status = 'open';
    task.executor = '';
    task.assigned = -1;
    task.remaining = 0;
    if (!this.queue.includes(task)) this.queue.push(task);
  }

  assign() {
    const stillOpen = [];
    for (const task of this.queue) {
      const provider = this.cheapestProvider(task);
      if (provider === null) {
        stillOpen.push(task);
        continue;
      }
      const price = provider.offers[task.service];
      if (this.balance(task.poster) < price) {
        stillOpen.push(task);
        continue;
      }
      const spec = this.config.services[task.service];
      task.price = price;
      task.duration = this.duration(spec, task);
      task.remaining = task.duration;
      task.executor = provider.id;
      task.status = 'running';
      task.assigned = this.minute;

      provider.task = task;
      provider.status = 'travelling';
      provider.target = task.site;
    }
    this.queue = stillOpen;
  }

  cheapestProvider(task) {
    let best = null;
    for (const agent of this.agents) {
      if (agent.status !== 'idle' && agent.status !== 'waiting') continue;
      if (agent.task !== null) continue;
      if (agent.id === task.poster) continue;
      if (!(task.service in agent.offers)) continue;
      if (agent.kind === 'robot' && (agent.awaitingCharge || agent.battery < agent.minAccept)) continue;
      if (best === null || agent.offers[task.service] < best.offers[task.service]) best = agent;
    }
    return best;
  }

  duration(spec, task) {
    if (task.service === CHARGING) {
      const robot = this.agent(task.poster);
      if (robot && robot.rechargeRate > 0) {
        return Math.max(1, Math.ceil((100 - robot.battery) / robot.rechargeRate));
      }
    }
    return this.rng.integer(spec.duration_min, spec.duration_max);
  }

  work() {
    for (const agent of this.agents) {
      const task = agent.task;
      if (task === null) {
        if (agent.status !== 'stalled' && agent.status !== 'waiting') agent.status = 'idle';
        continue;
      }

      if (!at(agent, task.site)) {
        agent.status = 'travelling';
        advance(agent);
        continue;
      }

      if (task.service === CHARGING) {
        const robot = this.agent(task.poster);
        if (robot === null) {
          this.finish(agent, task, false);
          continue;
        }
        advance(robot);
        if (!at(robot, task.site)) {
          agent.status = 'working';
          continue;
        }
        robot.status = 'working';
        robot.battery = Math.min(100, robot.battery + robot.rechargeRate);
      }

      agent.status = 'working';
      task.remaining -= 1;
      if (task.remaining > 0) continue;

      const spec = this.config.services[task.service];
      const failed = this.rng.chance(spec.failure * (1 - agent.skill));
      this.finish(agent, task, !failed);
    }
  }

  finish(agent, task, success) {
    task.finished = this.minute;
    task.status = success ? 'done' : 'failed';
    if (success) this.pay(task.poster, agent.id, task.price);

    agent.task = null;
    agent.status = 'idle';
    agent.target = null;

    if (task.service === CHARGING) {
      const robot = this.agent(task.poster);
      if (robot !== null) {
        if (success) robot.battery = 100;
        robot.awaitingCharge = false;
        robot.chargeTask = null;
        robot.status = 'idle';
        robot.target = null;
      }
    }
  }

  measure() {
    const robots = this.agents.filter((a) => a.kind === 'robot');
    const humans = this.agents.filter((a) => a.kind !== 'robot');
    const sum = (xs) => xs.reduce((a, b) => a + b, 0);
    return {
      minute: this.minute,
      completed: this.tasks.filter((t) => t.status === 'done').length,
      failed: this.tasks.filter((t) => t.status === 'failed').length,
      queue: this.queue.length,
      robot_wealth: round6(sum(robots.map((a) => a.wealth))),
      human_wealth: round6(sum(humans.map((a) => a.wealth))),
      org_wealth: round6(this.orgWealth),
      mean_battery: robots.length ? round6(sum(robots.map((a) => a.battery)) / robots.length) : 0,
      stalled: sum(robots.map((a) => a.stalledMinutes)),
      gini: round6(gini(this.agents.map((a) => a.wealth))),
    };
  }

  /* ------------------------------------------------------------------ reading */

  summary() {
    const last = this.history[this.history.length - 1];
    const first = this.history[0];
    const change = last && first ? last.robot_wealth - first.robot_wealth : 0;
    return {
      posted: this.tasks.length,
      completed: this.tasks.filter((t) => t.status === 'done').length,
      failed: this.tasks.filter((t) => t.status === 'failed').length,
      abandoned: this.tasks.filter((t) => t.status === 'open' || t.status === 'abandoned').length,
      robotBalanceChange: change,
      stalled: last ? last.stalled : 0,
      solvent: change > 0 && last !== undefined && last.stalled === 0,
    };
  }
}

function makeAgent(spec, kind) {
  return {
    id: spec.id,
    kind,
    x: spec.start[0],
    y: spec.start[1],
    speed: spec.speed,
    wealth: spec.wealth,
    skill: spec.skill,
    offers: { ...spec.offers },
    status: 'idle',
    task: null,
    target: null,
    battery: spec.battery ?? 100,
    drain: spec.drain ?? 0,
    rechargeRate: spec.recharge_rate ?? 0,
    rechargeTrigger: spec.recharge_trigger ?? 0,
    minAccept: spec.min_accept ?? 0,
    awaitingCharge: false,
    chargeTask: null,
    stalledMinutes: 0,
  };
}

function at(agent, point, tolerance = 0.5) {
  return Math.hypot(agent.x - point[0], agent.y - point[1]) <= tolerance;
}

function advance(agent) {
  if (agent.target === null) return;
  const dx = agent.target[0] - agent.x;
  const dy = agent.target[1] - agent.y;
  const distance = Math.hypot(dx, dy);
  if (distance <= agent.speed) {
    agent.x = agent.target[0];
    agent.y = agent.target[1];
    return;
  }
  agent.x += (agent.speed * dx) / distance;
  agent.y += (agent.speed * dy) / distance;
}

export function run(config, minutes = config.minutes) {
  return new Simulation(config).run(minutes);
}
