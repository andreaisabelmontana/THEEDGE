/* filter row above the hall of ideas.

   this never creates or replaces a card. the cards are static html and their
   hover reveal is a css clip-path transition on .helmet-grid-item-reveal-img,
   so rebuilding the dom would drop the animation on the floor. instead each
   card carries data-slug, we look its record up in window.EDGE_PROJECTS, and
   filtering only toggles a class on the wrapper.

   groups are derived from the data rather than hard-coded, so adding a project
   to projects.js grows the filter row on its own. a group with fewer than two
   distinct values is dropped: a filter that cannot change the result is noise. */
(function () {
  if (window.__edgeFilter) return;
  window.__edgeFilter = true;

  /* the count is the one string on this page that is written rather than
     marked up, so the translator cannot find it in the dom. it is rebuilt here
     on every language change instead, and again after each filter click, which
     is the other moment it changes. */
  var COUNT = {
    en: function (n) { return n + (n === 1 ? ' project' : ' projects'); },
    es: function (n) { return n + (n === 1 ? ' proyecto' : ' proyectos'); },
    de: function (n) { return n + (n === 1 ? ' Projekt' : ' Projekte'); },
  };

  var GROUPS = [
    { key: 'type', label: 'TYPE', from: function (p) { return p.track ? [p.track] : []; } },
    { key: 'domain', label: 'DOMAIN', from: function (p) { return p.categories || []; } },
    { key: 'stack', label: 'STACK', from: function (p) { return p.techstack || []; } },
    { key: 'year', label: 'YEAR', from: function (p) { return p.year ? [String(p.year)] : []; } },
  ];

  function boot() {
    var data = window.EDGE_PROJECTS;
    var grid = document.querySelector('[data-helmet-grid]');
    if (!data || !grid) return;

    var list = grid.closest('.w-dyn-list') || grid;
    var cards = [].slice.call(grid.querySelectorAll('[data-slug]'));
    if (!cards.length) return;

    var bySlug = {};
    data.forEach(function (p) { bySlug[p.slug] = p; });

    // only the projects actually on the page can be filtered
    var present = cards
      .map(function (c) { return bySlug[c.getAttribute('data-slug')]; })
      .filter(Boolean);

    var active = {};
    GROUPS.forEach(function (g) { active[g.key] = 'All'; });

    var bar = document.createElement('div');
    bar.className = 'edge-filter';

    var groupsUsed = [];
    GROUPS.forEach(function (g) {
      var values = [];
      present.forEach(function (p) {
        g.from(p).forEach(function (v) {
          if (values.indexOf(v) === -1) values.push(v);
        });
      });
      if (values.length < 2) return;
      values.sort(g.key === 'year'
        ? function (a, b) { return Number(b) - Number(a); }
        : undefined);
      groupsUsed.push(g);

      var row = document.createElement('div');
      row.className = 'edge-filter-row';

      var label = document.createElement('span');
      label.className = 'edge-filter-label';
      label.textContent = g.label;
      row.appendChild(label);

      ['All'].concat(values).forEach(function (v) {
        var b = document.createElement('button');
        b.type = 'button';
        b.className = 'edge-filter-pill' + (v === 'All' ? ' is-on' : '');
        b.textContent = v;
        b.setAttribute('data-group', g.key);
        b.setAttribute('data-value', v);
        b.addEventListener('click', function () {
          active[g.key] = v;
          [].forEach.call(row.querySelectorAll('.edge-filter-pill'), function (o) {
            o.classList.toggle('is-on', o === b);
          });
          apply();
        });
        row.appendChild(b);
      });

      bar.appendChild(row);
    });

    if (!groupsUsed.length) return;

    var count = document.createElement('div');
    count.className = 'edge-filter-count';
    bar.appendChild(count);

    function matches(p) {
      return groupsUsed.every(function (g) {
        var want = active[g.key];
        return want === 'All' || g.from(p).indexOf(want) !== -1;
      });
    }

    var shown = 0;

    function renderCount() {
      var say = COUNT[document.documentElement.lang] || COUNT.en;
      var text = say(shown);
      if (count.textContent !== text) count.textContent = text;
    }

    function apply() {
      shown = 0;
      cards.forEach(function (card) {
        var p = bySlug[card.getAttribute('data-slug')];
        var on = p ? matches(p) : true;
        card.classList.toggle('edge-hidden', !on);
        if (on) shown++;
      });
      renderCount();
    }

    document.addEventListener('edge:lang', renderCount);

    list.parentNode.insertBefore(bar, list);
    apply();
  }

  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', boot);
  } else {
    boot();
  }
})();
