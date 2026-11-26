(() => {
  const auroras = [...document.querySelectorAll('[data-un-aura]')];
  if (!auroras.length) return;
  const reduce = matchMedia('(prefers-reduced-motion: reduce)');
  const layers = new Map(auroras.map(el => [el, {
    visible: !('IntersectionObserver' in window), circles: [...el.children].map(node => ({node, x:0, y:0, tx:0, ty:0}))
  }]));
  let frame = 0;
  function step() {
    frame = 0;
    let unsettled = false;
    for (const layer of layers.values()) {
      if (!layer.visible) continue;
      for (const circle of layer.circles) {
        // Same easing as the UN Nav: move 8% of the remaining distance per frame.
        circle.x += (circle.tx-circle.x)*.08;
        circle.y += (circle.ty-circle.y)*.08;
        if (Math.hypot(circle.tx-circle.x,circle.ty-circle.y) > .08) unsettled = true;
        circle.node.style.transform = 'translate('+circle.x.toFixed(2)+'px,'+circle.y.toFixed(2)+'px)';
      }
    }
    if (unsettled) queue();
  }
  function queue() {
    if (!frame && !reduce.matches && !document.hidden) frame = requestAnimationFrame(step);
  }
  function refresh() {
    for (const [el, layer] of layers) {
      el.toggleAttribute('data-active',layer.visible && !reduce.matches && !document.hidden);
      if (reduce.matches) for (const circle of layer.circles) {
        circle.x=circle.y=circle.tx=circle.ty=0;
        circle.node.style.removeProperty('transform');
      }
    }
  }
  if ('IntersectionObserver' in window) {
    const watcher = new IntersectionObserver(entries => {
      for (const e of entries) layers.get(e.target).visible=e.isIntersecting;
      refresh();
    });
    auroras.forEach(el=>watcher.observe(el));
  }
  document.addEventListener('pointermove', e => {
    if (reduce.matches || e.pointerType==='touch') return;
    for (const [el, layer] of layers) {
      if (!layer.visible) continue;
      const box=el.getBoundingClientRect();
      for (const c of layer.circles) {
        const dx=e.clientX-Math.max(0,box.left+c.node.offsetLeft-c.node.offsetWidth*.5);
        const dy=e.clientY-Math.max(0,box.top+c.node.offsetTop-c.node.offsetHeight*.5);
        const distance=Math.hypot(dx,dy);
        // Port the original 600px repulsion radius and 150px maximum travel.
        const force=distance>0 && distance<600 ? (1-distance/600)*150/distance : 0;
        c.tx=-dx*force;
        c.ty=-dy*force;
      }
    }
    queue();
  },{passive:true});
  document.documentElement.addEventListener('pointerleave',() => {
    for (const l of layers.values()) for (const c of l.circles) c.tx=c.ty=0;
    queue();
  });
  reduce.addEventListener?.('change',refresh);
  document.addEventListener('visibilitychange',refresh);
  refresh();
})();
