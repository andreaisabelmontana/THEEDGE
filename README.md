# Local site snapshot — study copy

A self-contained offline snapshot of a commercial motorsport-driver website,
kept for learning how the site is built. The name references, photos, and the
agency's custom JS bundle have been removed, but the remaining material
(the Webflow page design and stylesheet, the Rive animations, and the 3D
models/textures) is **still the original studio's copyrighted work**.
**Private study copy only — do not publish, deploy, or push to a public repo.**

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
- **Rive animations** (`rive/*.riv`) — page transition, animated buttons,
  signature, circuits, phrases marquee, etc. Played via the Rive WASM runtime
  (`vendor/@rive-app/canvas-lite@2.26.4/rive.wasm`).
- **WebGL scene** (`gl/`) — Three.js assets: Draco-compressed GLB models
  (helmet, head "disco" ball, track ribbons), PBR texture sets in two variants
  (`webp` for desktop >991px, `ktx2` for mobile), HDRI environment maps for
  lighting, and MSDF font atlases for 3D text. Decoders in `gl/draco/` and
  `gl/basis/`.

## Removed from this copy

1. All photos and raster images.
2. The agency's custom animation/scene JS bundle (GSAP + Lenis + Three.js +
   Rive orchestration) — the WebGL scene and animations no longer run.
3. All name references in the HTML/CSS.

Because of (2), the site renders as static Webflow pages only.

## Good places to start reading

- `index.html` — search for `data-wf-page` and `w-embed` to see how Webflow
  structures a page.
- Watch the Network tab while scrolling: Rive files and GL textures load lazily.
