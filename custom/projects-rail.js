(() => {
  const root = document.querySelector('.project-night');
  const grid = root?.querySelector('#project-grid');
  const rail = root?.querySelector('.project-rail');
  if (!grid || !rail) return;
  const list = rail.querySelector('.project-rail__list');
  const top = rail.querySelector('.project-rail__top');
  const compact = matchMedia('(max-width:960px)');
  const reduce = matchMedia('(prefers-reduced-motion:reduce)');
  const words = {en:['Jump to','Top','Browse project rows'],es:['Ir a','Inicio','Navegar por las filas de proyectos'],de:['Zu','Nach oben','Projektzeilen durchsuchen']};
  let rows = [], buttons = [], rebuild = 0, frame = 0;
  function jump(el) {
    el.scrollIntoView({block:'start',behavior:reduce.matches ? 'auto' : 'smooth'});
  }
  top.addEventListener('click', () => jump(root));
  function update() {
    frame = 0;
    const bounds = root.getBoundingClientRect();
    rail.hidden = !rows.length || (!compact.matches && (bounds.top > innerHeight * .8 || bounds.bottom < innerHeight * .25));
    const marker = Math.min(250, innerHeight * .36);
    let current = 0;
    rows.forEach((row,i) => { if (row[0].getBoundingClientRect().top <= marker) current = i; });
    buttons.forEach((button,i) => {
      if (i === current) button.setAttribute('aria-current','location');
      else button.removeAttribute('aria-current');
    });
  }
  function refresh() {
    rebuild = 0;
    const lang = words[document.documentElement.lang] || words.en;
    top.querySelector('span').textContent = lang[1];
    rail.setAttribute('aria-label',lang[2]);
    rows = [];
    const cards = Array.from(grid.querySelectorAll(':scope > .project-card:not([hidden])')).filter(card => card.getClientRects().length);
    const columns = Math.max(1, getComputedStyle(grid).gridTemplateColumns.trim().split(/\s+/).length);
    for (let i=0;i<cards.length;i+=columns) rows.push(cards.slice(i,i+columns));
    const key = rows.map(row => row.map(card => card.dataset.slug || card.id).join(',')).join('|')+'#'+lang[0];
    if (list.dataset.rows !== key) {
      list.dataset.rows = key;
      buttons = rows.map(row => {
        const names = row.map(card => card.querySelector('h2')?.textContent.trim() || card.dataset.slug).join(' / ');
        const button = document.createElement('button');
        button.type = 'button';
        button.className = 'project-rail__stop';
        button.setAttribute('aria-label', lang[0]+' '+names);
        const label = document.createElement('span');
        label.textContent = names;
        label.setAttribute('aria-hidden','true');
        button.append(label);
        button.addEventListener('click', () => jump(row[0]));
        return button;
      });
      list.replaceChildren(...buttons);
    }
    update();
  }
  const queueRefresh = () => { if (!rebuild) rebuild = requestAnimationFrame(refresh); };
  const queueUpdate = () => { if (!frame) frame = requestAnimationFrame(update); };
  addEventListener('scroll',queueUpdate,{passive:true});
  addEventListener('resize',queueRefresh,{passive:true});
  document.addEventListener('edge:lang',queueRefresh);
  new MutationObserver(queueRefresh).observe(grid,{attributes:true,attributeFilter:['hidden'],subtree:true,childList:true});
  queueRefresh();
})();
