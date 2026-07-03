# landonorris.com — local study mirror

A self-contained offline snapshot of https://landonorris.com for learning how the
site is built. **Private study copy only — the design, photos, 3D models and
animations are copyrighted (Lando Norris / OFF+BRAND). Do not publish, deploy,
or push this folder to a public repo.**

## Run it

```
python -m http.server 8735
```

then open http://localhost:8735/ (it must be served over http — opening
index.html directly with file:// breaks fetch/WASM).

## What the site is made of

- **Webflow** — the pages (`index.html`, `calendar/`, `on-track/`, `off-track/`,
  `legal/*`) are Webflow-generated. One shared stylesheet + `webflow.*.js`
  (both under `assets/cdn.prod.website-files.com/`).
- **Custom agency bundle** — `assets/lando.itsoffbrand.io/dev-js/lando.OFF+BRAND.*.js`
  (~1.3 MB, by OFF+BRAND). Contains GSAP + Lenis smooth scroll + Three.js +
  Rive runtime and all the site's custom animation/scene code.
- **Rive animations** (`rive/*.riv`) — page transition, animated buttons,
  signature, circuits, phrases marquee, etc. Played via the Rive WASM runtime
  (`vendor/@rive-app/canvas-lite@2.26.4/rive.wasm`).
- **WebGL scene** (`gl/`) — Three.js assets: Draco-compressed GLB models
  (helmet, head "disco" ball, track ribbons), PBR texture sets in two variants
  (`webp` for desktop >991px, `ktx2` for mobile), HDRI environment maps for
  lighting, and MSDF font atlases for 3D text. Decoders in `gl/draco/` and
  `gl/basis/`.

## Changes made vs the live site (so it runs offline)

1. All CDN asset URLs rewritten to local relative paths (`assets/…`).
2. `integrity`/`crossorigin` attributes stripped from `<link>`/`<script>` tags
   (hashes no longer match the rewritten files; browsers reject them otherwise).
3. Inside the JS bundles, `https://lando.itsoffbrand.io` and
   `https://assets.itsoffbrand.io/lando` → root-relative (`/gl/…`, `/rive/…`),
   and the unpkg Rive-WASM URL → `/vendor/…`.
4. Webflow's analytics beacon 404s locally (harmless).

## Good places to start reading

- `index.html` — search for `data-wf-page`, `w-embed`, and the two `<script>`
  tags at the bottom to see how Webflow structures a page.
- In the JS bundle, search for `window.landoGL` — the whole WebGL asset
  manifest and scene state lives on that object (inspectable live in DevTools).
- Watch the Network tab while scrolling: Rive files and GL textures load lazily.
