(function () {
  'use strict';
  var subjects = document.querySelector('.project-subjects');
  if (!subjects) return;
  var cards = Array.from(document.querySelectorAll('.project-card[data-subjects]'));
  var buttons = Array.from(subjects.querySelectorAll('button[data-subject]'));
  var count = document.querySelector('.project-count');
  var copy = {
    en: {all:'All work',ai:'AI & ML',software:'Software & Web',spatial:'3D & Games',ux:'UX & Interaction',kicker:'PROJECTS / THE LAB',title:'Hall of ideas.',intro:'Code, design and a few curious experiments. Pick a subject, or take a look around.',hint:'Start with the demo. Stay for the story.',cta:'Explore the project',course:'Course',count:function(n){return n + (n===1?' project':' projects');}},
    es: {all:'Todo',ai:'IA y ML',software:'Software y web',spatial:'3D y juegos',ux:'UX e interacción',kicker:'PROYECTOS / EL LAB',title:'Ideas en órbita.',intro:'Código, diseño y unos cuantos experimentos curiosos. Elige un tema o explora a tu ritmo.',hint:'Empieza por la demo. Descubre la historia.',cta:'Explorar el proyecto',course:'Asignatura',count:function(n){return n + (n===1?' proyecto':' proyectos');}},
    de: {all:'Alle Arbeiten',ai:'KI & ML',software:'Software & Web',spatial:'3D & Spiele',ux:'UX & Interaktion',kicker:'PROJEKTE / DAS LAB',title:'Raum für Ideen.',intro:'Code, Design und ein paar neugierige Experimente. Wähle ein Thema oder schau dich um.',hint:'Erst die Demo. Dann die Geschichte.',cta:'Projekt entdecken',course:'Kurs',count:function(n){return n + (n===1?' Projekt':' Projekte');}}
  };
  var active = new URL(location.href).searchParams.get('subject') || 'all';
  if (!buttons.some(function(b){return b.dataset.subject===active;})) active='all';
  function render() {
    var lang=document.documentElement.lang || 'en', t=copy[lang] || copy.en, shown=0;
    buttons.forEach(function(b){if(b.textContent!==t[b.dataset.subject])b.textContent=t[b.dataset.subject];b.setAttribute('aria-pressed',String(b.dataset.subject===active));});
    cards.forEach(function(card){
      var visible=active==='all'||card.dataset.subjects.split(' ').includes(active);
      card.hidden=!visible;
      if(visible) shown++;
      var project=(window.EDGE_PROJECTS||[]).find(function(p){return p.slug===card.dataset.slug;});
      var desc=card.querySelector('.project-card-description');
      var summary=project && ((project.summary && project.summary[lang])||project.tagline);
      if(desc && summary && desc.textContent!==summary) desc.textContent=summary;
      var type=card.querySelector('.project-card-meta span');
      var typeLabel=project && (project.course ? (t.course+' · '+project.course) : ((project.typeLabels && project.typeLabels[lang])||project.typeLabel));
      if(type && typeLabel && type.textContent!==typeLabel) type.textContent=typeLabel;
      var cta=card.querySelector('.project-card-cta-label');
      if(cta.textContent!==t.cta) cta.textContent=t.cta;
    });
    document.querySelectorAll('[data-project-copy]').forEach(function(el){var value=t[el.dataset.projectCopy];if(typeof value==='string' && el.textContent!==value) el.textContent=value;});
    if(count.textContent!==t.count(shown))count.textContent=t.count(shown);
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
    cards.forEach(function(card){card.classList.add('motion-ready');observer.observe(card);});
  }
})();
