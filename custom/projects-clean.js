(function () {
  'use strict';
  var subjects = document.querySelector('.project-subjects');
  if (!subjects) return;
  var cards = Array.from(document.querySelectorAll('.project-card[data-subjects]'));
  var buttons = Array.from(subjects.querySelectorAll('button[data-subject]'));
  var count = document.querySelector('.project-count');
  var copy = {
    en: {all:'All work',ai:'AI & ML',robotics:'Robotics',software:'Software & Web',spatial:'3D & Games',ux:'UX & Interaction',art:'Design & Art',artType:'Website',artIntro:'My online gallery for paintings, drawings and ceramics.',artCTA:'Visit website',artCount:function(n){return n+(n===1?' artwork':' artworks');},kicker:'PROJECTS / THE LAB',title:'Projects',intro:'Browse software, robotics and interactive 3D projects by subject.',hint:'Start with the demo. Stay for the story.',cta:'Explore the project',course:'Course',count:function(n){return n + (n===1?' project':' projects');}},
    es: {all:'Todo',ai:'IA y ML',robotics:'Robótica',software:'Software y web',spatial:'3D y juegos',ux:'UX e interacción',art:'Diseño y arte',artType:'Sitio web',artIntro:'Mi galería en línea de pinturas, dibujos y cerámica.',artCTA:'Visitar sitio web',artCount:function(n){return n+(n===1?' obra':' obras');},kicker:'PROYECTOS / EL LAB',title:'Proyectos',intro:'Explora proyectos de software, robótica y 3D interactivo por tema.',hint:'Empieza por la demo. Descubre la historia.',cta:'Explorar el proyecto',course:'Asignatura',count:function(n){return n + (n===1?' proyecto':' proyectos');}},
    de: {all:'Alle Arbeiten',ai:'KI & ML',robotics:'Robotik',software:'Software & Web',spatial:'3D & Spiele',ux:'UX & Interaktion',art:'Design & Kunst',artType:'Website',artIntro:'Meine Onlinegalerie für Gemälde, Zeichnungen und Keramik.',artCTA:'Website besuchen',artCount:function(n){return n+(n===1?' Kunstwerk':' Kunstwerke');},kicker:'PROJEKTE / DAS LAB',title:'Projekte',intro:'Entdecke Projekte zu Software, Robotik und interaktivem 3D nach Themen.',hint:'Erst die Demo. Dann die Geschichte.',cta:'Projekt entdecken',course:'Kurs',count:function(n){return n + (n===1?' Projekt':' Projekte');}}
  };
  var active = new URL(location.href).searchParams.get('subject') || 'all';
  if (!buttons.some(function(b){return b.dataset.subject===active;})) active='all';
  function render() {
    var lang=document.documentElement.lang || 'en', t=copy[lang] || copy.en, shown=0;
    buttons.forEach(function(b){if(b.textContent!==t[b.dataset.subject])b.textContent=t[b.dataset.subject];b.setAttribute('aria-pressed',String(b.dataset.subject===active));});
    cards.forEach(function(card){
      var visible=card.hasAttribute('data-artwork') ? active==='art' : active==='all'||card.dataset.subjects.split(' ').includes(active);
      card.hidden=!visible;
      if(visible) shown++;
      var project=(window.EDGE_PROJECTS||[]).find(function(p){return p.slug===card.dataset.slug;});
      var desc=card.querySelector('.project-card-description');
      var summary=project && ((project.summary && project.summary[lang])||project.tagline);
      if(desc && summary && desc.textContent!==summary) desc.textContent=summary;
      var type=card.querySelector('.project-card-meta span');
      var typeLabel=project && (project.course ? (t.course+' · '+project.course) : ((project.typeLabels && project.typeLabels[lang])||project.typeLabel));
      if(type && typeLabel && type.textContent!==typeLabel) type.textContent=typeLabel;
      var visualNote=card.querySelector('[data-project-visual-note]');
      if(visualNote) visualNote.textContent=({en:'AI-generated concept illustration',es:'Ilustración conceptual generada con IA',de:'KI-generierte Konzeptillustration'})[lang] || 'AI-generated concept illustration';
      var sitePreviewNote=card.querySelector('[data-project-site-preview="globo"]');
      if(sitePreviewNote) sitePreviewNote.textContent=({en:'Public site preview · 6 Nov 2026',es:'Vista del sitio público · 6 nov 2026',de:'Öffentliche Website · 6. Nov. 2026'})[lang] || 'Public site preview · 6 Nov 2026';
      var cta=card.querySelector('.project-card-cta-label');
      var ctaLabel=card.dataset.slug==='arte-de-la-montana' ? t.artCTA : (project && project.ctaLabels && project.ctaLabels[lang] || t.cta);
      if(cta && cta.textContent!==ctaLabel) cta.textContent=ctaLabel;
    });
    document.querySelectorAll('[data-project-copy]').forEach(function(el){var value=t[el.dataset.projectCopy];if(typeof value==='string' && el.textContent!==value) el.textContent=value;});
    var countLabel=active==='art' ? t.artCount(shown) : t.count(shown);
    if(count.textContent!==countLabel)count.textContent=countLabel;
  }
  subjects.addEventListener('click',function(e){
    var button=e.target.closest('button[data-subject]'); if(!button)return;
    active=button.dataset.subject;
    var url=new URL(location.href);
    if(active==='all') url.searchParams.delete('subject'); else url.searchParams.set('subject',active);
    history.replaceState(null,'',url.pathname+url.search+url.hash);
    render();
  });
  window.addEventListener('popstate',function(){active=new URL(location.href).searchParams.get('subject')||'all';if(!buttons.some(function(b){return b.dataset.subject===active;}))active='all';render();});
  document.addEventListener('edge:lang',render);
  render();
  if ('IntersectionObserver' in window && !matchMedia('(prefers-reduced-motion: reduce)').matches) {
    var observer=new IntersectionObserver(function(entries){entries.forEach(function(entry){if(entry.isIntersecting){entry.target.classList.add('is-visible');observer.unobserve(entry.target);}});},{threshold:0.06});
    cards.forEach(function(card){if(!card.hasAttribute('data-artwork')){card.classList.add('motion-ready');observer.observe(card);}});
  }
})();
