(() => {
  'use strict';
  const deck = document.querySelector('.photo-fan__deck');
  if (!deck) return;
  const cards = [...deck.querySelectorAll('.photo-fan__card')];
  const dialog = deck.nextElementSibling;
  const reduced = matchMedia('(prefers-reduced-motion: reduce)');
  const hover = matchMedia('(hover:hover) and (pointer:fine)');
  const midpoint = Math.floor(cards.length / 2);
  const natural = cards.map(card => {
    const s = card.style;
    return [
      Number(s.getPropertyValue('--base-x')),
      Number(s.getPropertyValue('--base-y')),
      7 * Number(s.getPropertyValue('--i')),
      Number(s.getPropertyValue('--base-scale'))
    ];
  });
  const running = new Map();
  let active = -1, openedBy, timer, observer, revealed = false;
  const mix = (a, b, progress) => a.map((v, i) => v + (b[i] - v) * progress);
  const elastic = t => t === 0 || t === 1 ? t : 1 - 2 ** (-10 * t) * Math.cos(2 * Math.PI * t / .75);
  const pose = ([x,y,rotation,scale]) =>
    `translate(calc(-50% + ${x} * var(--unit)),calc(${y} * var(--unit))) rotate(${rotation}deg) scale(${scale})`;
  const animate = (card, frames, duration, delay = 0) => {
    const old = running.get(card);
    if (old) old.cancel();
    const newMotion = card.animate(frames, {duration, delay, fill:'both'});
    running.set(card, newMotion);
    return newMotion;
  };
  function select(index, immediate = false) {
    active = index;
    for (const [i, card] of cards.entries()) {
      const distance = Math.abs((index < 0 ? midpoint : index) - i);
      const to = natural[i].slice();
      card.toggleAttribute('data-fan-active', i === index);
      if (i === index) { to[1] -= 2.5; to[3] *= 1.08; }
      else if (index >= 0) {
        const side = Math.sign(i-index);
        const spread = 8 * Math.max(0, 1 - Math.abs(i-midpoint)/midpoint) * (1 + Math.max(0, 3-distance)*.2);
        to[0] += side * spread;
        to[2] += side * 3/(1+distance);
        if (i === cards.length - 1) to[1] -= 1;
      }
      const start = getComputedStyle(card).transform;
      card.style.transform = pose(to);
      if (!immediate && card.animate && !reduced.matches) {
        const spring = Array.from({length:26}, (_, j) => elastic(j/25).toFixed(4)).join(',');
        animate(card, [{transform:start},{transform:pose(to)}], 500, distance*20);
        const effect = running.get(card).effect;
        try { effect.updateTiming({easing:`linear(${spring})`}); }
        catch { effect.updateTiming({easing:'cubic-bezier(.2,1.3,.4,1)'}); }
      } else running.get(card)?.cancel();
    }
  }
  function show(quiet = false) {
    if (revealed) return;
    revealed = true;
    observer?.disconnect();
    deck.removeAttribute('data-fan-waiting');
    if (quiet || reduced.matches || !deck.animate) return;
    const total = 2300;
    cards.forEach((card,i) => {
      const riseStart = 500 * (1-i/(cards.length-1));
      const spreadStart = 900 + 200 * Math.abs(i-midpoint)/midpoint;
      const frames = Array.from({length:81},(_, f) => {
        const t = f * total/80;
        const rise = Math.max(0,Math.min(1,(t-riseStart)/800));
        const spread = Math.max(0,Math.min(1,(t-spreadStart)/1200));
        const origin = [0,10*(1-rise)**3,0,1];
        return {transform:pose(mix(origin,natural[i],elastic(spread))), offset:f/80};
      });
      animate(card, frames, total);
    });
  }
  if (!reduced.matches && deck.animate && 'IntersectionObserver' in window) {
    deck.setAttribute('data-fan-waiting','');
    observer = new IntersectionObserver(entries => {
      if (entries.some(entry => entry.isIntersecting)) show();
    }, {rootMargin:'0px 0px -10% 0px'});
    observer.observe(deck);
  } else show(true);
  cards.forEach((card,index) => {
    card.addEventListener('pointerenter', event => {
      if (!revealed || !hover.matches || event.pointerType === 'touch') return;
      clearTimeout(timer);
      if (active !== index) select(index);
    });
    card.addEventListener('pointerleave', () => {
      if (active !== index || !hover.matches) return;
      clearTimeout(timer);
      timer = setTimeout(() => { if (active === index) select(-1); },50);
    });
    card.addEventListener('focus', () => {
      if (card.matches(':focus-visible')) { show(true); select(index); }
    });
    card.addEventListener('click', event => {
      if (typeof dialog.showModal !== 'function' || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      event.preventDefault();
      const photo = card.querySelector('img'), image = dialog.querySelector('img');
      openedBy = card;
      image.src = card.href;
      image.alt = photo.alt;
      dialog.showModal();
    });
  });
  deck.addEventListener('pointerleave', () => { clearTimeout(timer); if(active >= 0) select(-1); });
  deck.addEventListener('focusout', event => {
    if (!deck.contains(event.relatedTarget) && active >= 0) select(-1);
  });
  deck.addEventListener('keydown', event => {
    const index=cards.indexOf(event.target);
    if(index < 0 || !['ArrowLeft','ArrowRight'].includes(event.key)) return;
    event.preventDefault();
    cards[(index+cards.length+(event.key==='ArrowRight'?1:-1))%cards.length].focus();
  });
  reduced.addEventListener('change', () => {
    if (reduced.matches) { show(true); select(active, true); }
  });
  dialog.addEventListener('click', event => { if (event.target===dialog) dialog.close(); });
  dialog.addEventListener('close', () => openedBy?.focus({preventScroll:true}));
})();
