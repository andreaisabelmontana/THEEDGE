# edge-portfolio

Andrea Montaña's site. The landing runs her existing hero unchanged; the
information architecture is being rebuilt around it.

```bash
python -m http.server 8920
```

## Why the whole runtime is here

The hero cannot be extracted. It mounts on a single `<div data-gl="head">`, but
the bundle that fills that div (`assets/lando.itsoffbrand.io/dev-js/`) boots the
entire site first: its entry awaits `allriveloaded`, so it will not mount any
scene until every Rive canvas on the page has loaded, and it expects Lenis and
GSAP to be running too.

Lifting just the bundle, its GL assets and the mount div into a bare page was
tried. `window.landoGL` appears and registers all six scenes, then the boot
throws `Cannot read properties of undefined (reading 'style')` and nothing
mounts: zero canvases. A working hero means the working page.

That is the trade this repo takes: the full template, about 63MB, in exchange
for a hero that behaves exactly as the live one does.

## Layout

```
index.html      her landing, running the real hero
custom/         her css and scripts
gl/             models, textures, draco, basis, hdri, fonts
assets/         the mirrored template bundles and images
rive/           rive animations the bundle waits on
online/ off-track/ calendar/ coursework/   her other pages
structure/      the rebuilt landing, to be merged onto the above
```

`structure/` holds the version built on the portfolio layout: astronaut brand
with no tabs, overlay menu, en/es/de, hero band, four section panels. It is the
target arrangement. The job now is to move that structure onto the page above
without disturbing the hero.

## Not included on purpose

**`CNAME`.** Her live repo claims `andreamontana.com`. A second repo publishing
the same CNAME would fight the live site for the domain, so it is deliberately
absent. Add it only when this repo is the one meant to serve that domain, and
remove it from the other at the same time.

## Notes

- All internal links carry `data-taxi-ignore`; the Taxi.js page transitions
  crash on subpages without it.
- `custom/custom.css` is cache-busted with `?v=YYYYMMDDx` in every page's
  `<link>`. Bump it on any CSS edit or the change will not take.
- The loading screen is `custom/loading.mp4`: 2.45MB and not preloaded, so it
  downloads before it can display. Worth revisiting.
- `THEEDGE-README.md` is the original repo's readme, kept for its notes.
