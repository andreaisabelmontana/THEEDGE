# Site snapshot — study copy

A self-contained snapshot of a commercial motorsport-driver website, kept for
learning how the site is built. Name references and photos have been removed,
but the remaining material (the Webflow page design and stylesheet, the agency
JS bundle with its GSAP/Three.js/Rive orchestration, the Rive animations, and
the 3D models/textures) is **still the original studio's copyrighted work**.
It is deployed to GitHub Pages at the repo owner's request and risk; a
`noindex` robots meta tag is set on every page to keep it out of search
engines. If the studio or rights holder objects, take the deployment down.

## Run it locally

```
python serve.py
```

then open http://localhost:8735/ (it must be served over http — opening
index.html directly with file:// breaks fetch/WASM). `serve.py` disables
caching so edits show up on refresh.

## Deployment

Pushes to `main` deploy to GitHub Pages via `.github/workflows/pages.yml`.
The site is served under the `/THEEDGE/` subpath, so:

- Internal links in the HTML are relative (`../calendar` etc.), not absolute.
- An inline script in each page's `<head>` computes `window.__SITE_ROOT`
  from `location.pathname` (empty at a domain root, `/THEEDGE` on Pages).
- The agency bundles are patched so their asset bases (`/gl`, `/rive/`,
  `/vendor/`) are prefixed with `window.__SITE_ROOT`.

## What the site is made of

- **Webflow** — the pages (`index.html`, `calendar/`, `on-track/`, `off-track/`,
  `legal/*`) are Webflow-generated. One shared stylesheet + `webflow.*.js`
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

## Removed from this copy

1. All photos and raster images (their `<img>` tags remain with empty `src`,
   so a handful of 404s in the network tab are expected).
2. All name references in the HTML/CSS.

## Good places to start reading

- `index.html` — search for `data-wf-page` and `w-embed` to see how Webflow
  structures a page, and `data-rive-` / `data-gl` for the animation hooks the
  agency bundle consumes.
- Watch the Network tab while scrolling: Rive files and GL textures load lazily.
