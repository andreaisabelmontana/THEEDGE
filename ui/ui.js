/* moves the real hero into our layout.

   the bundle boots the whole page: it awaits allriveloaded before it mounts any
   scene, and it reads the nav, the transition layer and the rive canvases on
   the way through. so nothing is removed or hidden up front. the page loads
   exactly as it does on the live site, the head scene mounts, and only then do
   we restructure around it.

   the move itself is cheap, because there is nothing to move. every scene
   draws into one shared full-screen canvas.gl inside .gl-wrap, and the
   [data-gl="head"] div is only a bounds rectangle the renderer reads to know
   where on screen to put the head. reparent that div and the scene follows it,
   no context is touched. */
(function () {
  const slot = document.querySelector('[data-hero-slot]');
  const hero = document.querySelector('.sticky-track.home-hero');
  if (!slot || !hero) return;

  const started = performance.now();

  /* the mount div stays empty by design, so "has it mounted" cannot be asked of
     it. the honest signal is the shared gl canvas existing and her loader
     having taken itself off, which is the point the scenes are live. */
  function ready() {
    return !!document.querySelector('.gl-wrap canvas.gl') &&
      !document.getElementById('am-loader');
  }

  /* the head, whole, framed into the panel on the right.

     two facts drive this, both found the hard way:

     1. the scene takes its framing from the [data-gl="head"] mount rect, and
        only the HEIGHT matters. give it less than the full viewport height and
        her crown and chin fall outside the frame. that is what kept cutting her
        face off, so ui.css restores the mount to the full viewport and the head
        renders whole and centred, exactly as on the live site.

     2. horizontal placement is not controllable from the mount at all, so the
        only way to move her right is a css transform on .gl-wrap.

     the cost of (2) is the cursor: the scene reads mousemove, touchmove and
     pointermove straight from viewport coordinates and normalises against
     innerWidth, so it does not know about the transform and the helmet lens
     tracks a point offset from the mouse. set FRAME to false to trade the panel
     back for a true cursor. */
  const FRAME = true;

  /* where her head should sit, as fractions of the viewport, and how big.
     tune these three and nothing else. */
  const HEAD_X = 0.70;  // horizontal centre
  const HEAD_Y = 0.54;  // vertical centre
  const HEAD_K = 0.68;  // scale

  let clip = null;
  let wrap = null;

  // the live mapping, so pointer events can be run back through it
  let map = null;

  function frameHead() {
    wrap = document.querySelector('.gl-wrap');
    if (!wrap || !FRAME) return;
    if (!clip) {
      clip = document.createElement('div');
      clip.className = 'gl-clip';
      wrap.parentNode.insertBefore(clip, wrap);
      clip.appendChild(wrap);
    }
    fit();
  }

  function fit() {
    if (!clip || !wrap) return;
    const s = slot.getBoundingClientRect();
    const vw = window.innerWidth;
    const vh = window.innerHeight;

    // single column: she goes back to being the full backdrop
    if (vw < 1024 || !s.width) {
      clip.style.clipPath = 'none';
      wrap.style.transform = 'none';
      map = null;
      document.documentElement.style.setProperty('--head-bottom', vh + 'px');
      return;
    }

    /* no hard clip: the gradient in ui.css does the blending, so she fades into
       the ground instead of ending on a cut edge. origin 0 0 keeps the mapping
       plainly affine, so a local point p lands at t + k*p and the translation
       can be solved from where her centre should end up. */
    const k = HEAD_K;
    const tx = HEAD_X * vw - k * (vw / 2);

    /* the canvas is scaled down, so its painted area is smaller than the
       viewport and its top edge sat below the bar, leaving a strip of bare
       background where the starfield and the line art just stopped.

       clamping the translate to the bar's height pins that top edge under the
       bar, so the area always reaches it however the three constants are
       tuned. */
    const NAV_H = 76;
    const ty = Math.min(HEAD_Y * vh - k * (vh / 2), NAV_H);

    wrap.style.transformOrigin = '0 0';
    wrap.style.transform = `translate(${tx}px, ${ty}px) scale(${k})`;
    clip.style.clipPath = 'none';
    map = { tx, ty, k };

    /* the scaled canvas stops painting at ty + k*vh, and that hard line is
       where she visibly ends. published so the hero's black can finish on the
       same line instead of running past her. */
    document.documentElement.style.setProperty('--head-bottom', (ty + k * vh) + 'px');
  }

  /* her nav stays and ours is gone: it already carries the astronaut and the
     animated hamburger, wired to her own menu overlay. the language switcher is
     the only piece of ours that belongs up there, so it is moved into her bar
     rather than a second bar being drawn over hers. */
  function adoptNav() {
    const lang = document.querySelector('[data-lang-switch]');
    const inner = document.querySelector('.nav .nav-inner');
    const ham = document.querySelector('.nav-ham, .btn-layout.is-nav');
    if (!lang || !inner) return;
    lang.hidden = false;
    if (ham && ham.parentElement === inner) inner.insertBefore(lang, ham);
    else inner.appendChild(lang);
  }

  /* put the cursor back under the helmet.

     the canvas is transformed, but the scene reads clientX/clientY and
     normalises against innerWidth, so it is still thinking in untransformed
     viewport space and the lens lands wherever the mouse would have been
     before the move. the transform is affine with origin 0 0, screen = t + k*p,
     so the inverse is exact: p = (screen - t) / k.

     the real move events are stopped in the capture phase on window, before
     anything else sees them, and a corrected copy is dispatched on the same
     target. only move events are touched: clicks are left alone so her buttons
     and menu keep working on the coordinates the dom actually uses.

     touch needs no handling because the frame is only applied at 1024 and up,
     where fit() has already cleared the transform. */
  const CORRECTED = new WeakSet();

  function correctPointer(e) {
    if (!map || CORRECTED.has(e)) return;

    const x = (e.clientX - map.tx) / map.k;
    const y = (e.clientY - map.ty) / map.k;
    if (!isFinite(x) || !isFinite(y)) return;

    e.stopImmediatePropagation();

    const Ctor = e instanceof PointerEvent ? PointerEvent : MouseEvent;
    const copy = new Ctor(e.type, {
      bubbles: true,
      cancelable: e.cancelable,
      composed: true,
      view: window,
      clientX: x,
      clientY: y,
      screenX: x,
      screenY: y,
      buttons: e.buttons,
      pointerId: e.pointerId,
      pointerType: e.pointerType,
      isPrimary: e.isPrimary,
    });
    CORRECTED.add(copy);
    e.target.dispatchEvent(copy);
  }

  function armPointerCorrection() {
    ['mousemove', 'pointermove'].forEach((t) =>
      window.addEventListener(t, correctPointer, true),
    );
  }

  /* the menu labels are renamed in index.html, not here.

     they were renamed at runtime first, by rebuilding the char spans. that
     broke the hover animation on exactly the links it touched: her bundle
     splits the word at init and binds the effect to those span elements, so
     replacing them left the timeline pointing at nodes no longer in the
     document. Home still animated because it was the one word left alone.

     renaming the source text means the split runs on the new word and binds to
     it natively, and every link animates. */

  /* her scrolling marquee lives inside the hero, which we turned into a fixed
     backdrop, so it was stranded in a band above the cards and could never line
     up with them. moving it into the cards section puts it in the same flow, so
     it scrolls with them and passes behind. */
  function marqueeBehindCards() {
    const marquee = document.querySelector('.s.home-marquee');
    const wrap = document.querySelector('.panels-wrap');
    if (marquee && wrap && !wrap.contains(marquee)) {
      wrap.insertBefore(marquee, wrap.firstChild);
    }
  }

  /* the experience card points at #journey, so her section needs that id */
  function tagJourney() {
    const j = document.querySelector('.s.am-exp');
    if (j && !j.id) j.id = 'journey';
  }

  function restructure() {
    document.body.classList.add('edge-ready');
    adoptNav();
    marqueeBehindCards();
    tagJourney();
    frameHead();
    armPointerCorrection();
    /* her scroll-triggered animations cache their start positions at init, and
       this restructure changes the document height afterwards, so those
       positions are stale and the highlight sweeps fire before the cards are
       ever on screen. gsap is not exposed globally here, but ScrollTrigger
       refreshes on resize, so a few nudges after the layout settles put the
       triggers back where the content actually is. */
    window.dispatchEvent(new Event('resize'));
    [400, 900, 1600].forEach((d) =>
      setTimeout(() => {
        fit();
        window.dispatchEvent(new Event('resize'));
      }, d),
    );
    addEventListener('scroll', fit, { passive: true });
    addEventListener('resize', fit);
  }

  /* the wait used requestAnimationFrame alone, which browsers pause outright
     in a hidden tab. Opening the site in a background tab and coming to it
     later is ordinary behaviour, and it meant restructure() had never run: the
     sections this layout hides were all still on the page, the head was
     unframed and the language switcher was still sitting outside her nav. The
     page only fixed itself if you happened to be watching it load.

     a timer drives it instead, which keeps running while the tab is hidden.
     120ms is well inside what anyone perceives here: this waits on a scene that
     takes seconds to arrive, so it never needed frame resolution. */
  var TICK = 120;

  function wait() {
    if (ready()) { restructure(); return; }
    // the loader alone runs ~10s on a cold cache, so this has to be patient,
    // but it must not hang the page forever if the scene never arrives
    if (performance.now() - started > 25000) { restructure(); return; }
    setTimeout(wait, TICK);
  }

  wait();
})();
