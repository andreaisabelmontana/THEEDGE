# ROBOPRENEUR browser planner

An iframe-ready, dependency-free interface for the existing `robopreneur-andes`
planning model. It is a simulation, not a connection to deployed hardware, a
blockchain, or a live wallet. No requests leave the hosting origin while running.

## Provenance

- Upstream: https://github.com/andreaisabelmontana/robopreneur-andes
- Source commit: `70a7cde2b37826b549004cdf9b5dab7be7bf3e00`
- `sim.js`: exact copy of `planner/web/sim.js` (not modified).
- `andes-yard.json`: exact copy of `planner/configs/andes-yard.json`.
- `golden-run.json`: exact copy of `planner/parity/golden-run.json`, produced by
  the upstream Python model.
- `parity.mjs`: adapted from the upstream browser parity checker for this layout.
- `LICENSE`: upstream MIT license, Copyright (c) 2026 Andrea Montaña.
- `index.html`, `planner.css`, and `controller.js`: presentation layer added for
  the portfolio integration. These files do not change the simulation model.

## Embed

```html
<iframe
  src="/custom/robopreneur-planner/index.html?lang=en"
  title="ROBOPRENEUR deployment planner"
  width="100%"
  height="720"
  loading="lazy"
  style="border:0"
></iframe>
```

Languages: `en`, `es`, `de`; unknown values fall back to English. Updating the
iframe's `lang` query parameter reloads the translated interface. A ResizeObserver
posts `{type: "robopreneur-planner-height", height: number}` to the same-origin
parent. Validate both `event.origin` and `event.source` before applying its height.
Content expands when the assumptions disclosure is opened.

## Metric definitions

- **Robot jobs completed**: successful tasks whose executor is a robot. The yard
  configuration offers no battery-charging service from robots, so this excludes
  charging as well as work executed by human providers.
- **Final robot wallet**: final `robot_wealth` snapshot, in closed ANDE accounting
  units; it is not cash, revenue, or a forecast.
- **Final battery**: final `mean_battery` snapshot. There is one robot in this
  configuration.
- **Minutes with flat battery**: snapshots at `mean_battery === 0`, each spanning
  one minute. This differs from the upstream `stalled` counter, which also counts
  the first inability to afford charging while some battery may remain. The
  interface separately shows that unchanged upstream counter.
- **Completed tasks across all providers**: upstream `completed`, which includes
  human work and battery-charging tasks.
- The two aligned plots show every recorded minute, plus the starting wallet and
  battery at hour zero; the final snapshot is positioned at hour 72.

The slider changes only `arrival_rate_per_hour` from 0.2 through 2.0. The default is
1.5; seed 7 and 4,320 minutes remain fixed. Assumptions and the configuration link
are visible in the interface. Results are recomputed on slider change or submit;
there is no perpetual animation.

## Verify

Run `node custom/robopreneur-planner/parity.mjs` from the repository root. This
compares every sampled minute, every sampled measure, and all summary totals with
the Python-generated upstream reference, then prints computed minimum, default,
and maximum demand scenarios for inspection. Node 18+ is sufficient; there are no
packages to install. Serve over HTTP for ES module and JSON loading.
