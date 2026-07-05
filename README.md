# https://andreaisabelmontana.github.io/THEEDGE/

## What the site is made of

- **Webflow** — the pages (`index.html`, `calendar/`, `online/`, `offline/`,
  `legal/*`; `on-track/` and `off-track/` are kept as legacy URLs whose
  canonical points at the new pages) are Webflow-generated. One shared stylesheet + `webflow.*.js`
  (both under `assets/cdn.prod.website-files.com/`).
- **Agency bundle** (`assets/lando.itsoffbrand.io/dev-js/`) — the studio's
  custom orchestration bundle (GSAP + Lenis + Three.js + Rive), restored from
  git history. Only the `gold-android-fix-03` variant is loaded by the pages;
  the other file is kept for reference. It drives the page transitions, the
  scroll choreography, all Rive canvases, and the WebGL scenes.
- **Rive animations** (`rive/*.riv`) — page transition, animated buttons,
  signature, circuits, phrases marquee, etc. Played via the Rive runtime
  embedded in the agency bundle (wasm in `vendor/@rive-app/canvas-lite@2.26.4/`).
- **WebGL scene** (`gl/`) — Three.js assets: Draco-compressed GLB models
  (helmet, head "disco" ball, track ribbons), PBR texture sets in two variants
  (`webp` for desktop >991px, `ktx2` for mobile), HDRI environment maps for
  lighting, and MSDF font atlases for 3D text. Decoders in `gl/draco/` and
  `gl/basis/`.


## Good places to start reading

- `index.html` — search for `data-wf-page` and `w-embed` to see how Webflow
  structures a page, and `data-rive-` / `data-gl` for the animation hooks the
  agency bundle consumes.
- Watch the Network tab while scrolling: Rive files and GL textures load lazily.
