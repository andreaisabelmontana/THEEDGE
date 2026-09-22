// Adapted from robopreneur-andes/planner/web/parity.mjs (MIT; see LICENSE).
// Verify the vendored browser port against the upstream Python-generated fixture.
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { Simulation } from './sim.js';

const golden = JSON.parse(readFileSync(new URL('./golden-run.json', import.meta.url), 'utf8'));
const config = JSON.parse(readFileSync(new URL('./andes-yard.json', import.meta.url), 'utf8'));
// Python exports explicit default battery fields for humans; the source JSON
// leaves them implicit. Compare their initialized agent state and all outcomes.
assert.deepEqual(new Simulation(config).agents, new Simulation(golden.config).agents, 'Initial agent state');
const simulation = new Simulation(config).run();
const fields = ['completed', 'failed', 'queue', 'robot_wealth', 'human_wealth', 'org_wealth', 'mean_battery', 'stalled', 'gini'];
for (const expected of golden.series) {
  const actual = simulation.history[expected.minute];
  assert.ok(actual, `Missing minute ${expected.minute}`);
  for (const field of fields) assert.equal(actual[field], expected[field], `Minute ${expected.minute}: ${field}`);
}
const summary = simulation.summary();
for (const [key, expected] of Object.entries(golden.final)) {
  assert.equal(key === 'robot_balance_change' ? summary.robotBalanceChange : summary[key], expected, `Final: ${key}`);
}
console.log(`Parity passed: ${golden.series.length} sampled minutes × ${fields.length} fields and ${Object.keys(golden.final).length} totals across ${config.minutes} simulated minutes.`);
for (const rate of [0.2, 1.5, 2]) {
  const run = new Simulation({ ...config, arrival_rate_per_hour: rate }).run();
  const final = run.history.at(-1);
  const robotIds = new Set(config.robots.map((robot) => robot.id));
  console.log(JSON.stringify({ rate, robotJobs: run.tasks.filter((task) => task.status === 'done' && robotIds.has(task.executor)).length, wallet: final.robot_wealth, battery: final.mean_battery, flatMinutes: run.history.filter((s) => s.mean_battery === 0).length, completedAll: final.completed, stalled: final.stalled }));
}
