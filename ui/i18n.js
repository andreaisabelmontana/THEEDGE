/* three languages, across the whole site, with no build step.

   two layers:

   1. keyed strings. anything carrying data-i18n is swapped from DICT. english
      is the markup itself, so the page is correct before this runs and a
      missing key falls back to what the html already said.

   2. her own copy. the rest of the site is her markup and is not going to be
      annotated, so PHRASES matches visible text nodes by their english wording
      and swaps them in place. every node's original is cached the first time it
      is touched, so switching back to english is exact rather than a reverse
      lookup.

   the walk is leaf level, which matters because her split-text animation breaks
   headings into per-line spans; matching whole nodes would miss them, matching
   leaves does not.

   the switcher builds itself if the page has not got one, so making another
   page mirror is one script tag and nothing else. */
(function () {
  const STORE = 'edge-lang';

  const DICT = {
    es: {
      'nav.menu': 'menú',
      'hero.eyebrow': 'CS e IA · IE University, Madrid · Graduación 2028',
      'tag.ml': 'Aprendizaje automático e IA',
      'tag.cv': 'Visión por computador',
      'tag.rt3d': '3D en tiempo real y Unreal',
      'tag.ui': 'Interfaces interactivas e IPC',
      'tag.js': 'JavaScript y React',
      'tag.cloud': 'Docker y cloud-native',
      'hero.lead': 'Diseño en el borde donde la inteligencia digital se encuentra con el mundo físico.',
      'hero.facts': 'Aprendizaje automático, visión por computador e interacción persona-computador, llevados a 3D interactivo y desarrollo web completo.',
      'tag.dsa': 'Estructuras de datos y algoritmos',
      'hero.status': 'Disponible para prácticas y empleo en otoño de 2026 y verano de 2027',
      'hero.work': 'Ver el trabajo',
      'panel.projects.desc': 'Visión por computador, aprendizaje automático y 3D en tiempo real, desde un motor en C hasta desarrollo web completo. Demo y código de cada proyecto.',
      'panel.experience.title': 'Experiencia',
      'panel.experience.desc': 'Estudios y trabajo, situados donde ocurrieron.',
      'panel.offline.title': 'Fuera de línea',
      'panel.offline.desc': 'La vida lejos de la pantalla.',
      'panel.about.desc': 'De Bogotá a Dublín a Madrid. Titulación, premios en competiciones y hasta dónde me ha llevado el trabajo.',
      'cta.view': 'Ver',
    },
    de: {
      'nav.menu': 'Menü',
      'hero.eyebrow': 'CS & KI · IE University, Madrid · Abschluss 2028',
      'tag.ml': 'Machine Learning und KI',
      'tag.cv': 'Computer Vision',
      'tag.rt3d': 'Echtzeit-3D und Unreal',
      'tag.ui': 'Interaktive UI und MCI',
      'tag.js': 'JavaScript und React',
      'tag.cloud': 'Docker und Cloud-native',
      'hero.lead': 'Ich gestalte dort, wo digitale Intelligenz auf die physische Welt trifft.',
      'hero.facts': 'Machine Learning, Computer Vision und Mensch-Computer-Interaktion, umgesetzt als interaktives 3D und Full-Stack-Web.',
      'tag.dsa': 'Datenstrukturen und Algorithmen',
      'hero.status': 'Offen für Praktika und Stellen im Herbst 2026 und Sommer 2027',
      'hero.work': 'Zur Arbeit',
      'panel.projects.desc': 'Computer Vision, Machine Learning und Echtzeit-3D, von einer C-Engine bis Full-Stack-Web. Demo und Quellcode zu jedem Projekt.',
      'panel.experience.title': 'Erfahrung',
      'panel.experience.desc': 'Studium und Arbeit, verortet wo sie stattfanden.',
      'panel.offline.title': 'Offline',
      'panel.offline.desc': 'Das Leben abseits des Bildschirms.',
      'panel.about.desc': 'Von Bogotá über Dublin nach Madrid. Abschluss, Wettbewerbserfolge und wohin die Arbeit geführt hat.',
      'cta.view': 'Ansehen',
    },
  };

  /* her own wording, matched on the english. proper nouns are deliberately
     absent: LinkedIn, Instagram, Youtube, GitHub and GALLERY stay as they are
     in every language. */
  const PHRASES = {
    "Journey": { es: "Trayectoria", de: "Werdegang" },
    "Work": { es: "Trabajo", de: "Arbeit" },
    "Education": { es: "Educación", de: "Ausbildung" },
    "Skills": { es: "Habilidades", de: "Kenntnisse" },
    "Beyond the classroom": { es: "Más allá del aula", de: "Über den Hörsaal hinaus" },
    "BEYOND THE CLASSROOM": { es: "MÁS ALLÁ DEL AULA", de: "ÜBER DEN HÖRSAAL HINAUS" },
    "HACKATHONS / DATATHONS / BOOTCAMPS": { es: "HACKATHONES / DATATHONES / BOOTCAMPS", de: "HACKATHONS / DATATHONS / BOOTCAMPS" },
    "Building, experimenting and learning together.": { es: "Crear, experimentar y aprender en equipo.", de: "Gemeinsam entwickeln, experimentieren und lernen." },
    "Hackathons": { es: "Hackathones", de: "Hackathons" },
    "Datathons": { es: "Datathones", de: "Datathons" },
    "Bootcamps": { es: "Bootcamps", de: "Bootcamps" },
    "Co-organiser · Google Developer Group": { es: "Coorganizadora · Google Developer Group", de: "Mitorganisatorin · Google Developer Group" },
    "Co-organised both hackathons, running logistics and supporting participants throughout.": { es: "Coorganicé ambos hackathones, coordinando la logística y apoyando a los participantes.", de: "Beide Hackathons mitorganisiert, die Logistik koordiniert und die Teilnehmenden unterstützt." },
    "Details coming soon": { es: "Más detalles próximamente", de: "Details folgen bald" },
    "Events, projects and takeaways will be added here.": { es: "Aquí se añadirán eventos, proyectos y aprendizajes.", de: "Hier folgen Veranstaltungen, Projekte und Erkenntnisse." },
    'Projects': { es: 'Proyectos', de: 'Projekte' },
    'PROJECTS': { es: 'PROYECTOS', de: 'PROJEKTE' },
    'ABOUT': { es: 'SOBRE MÍ', de: 'ÜBER MICH' },
    'EXPERIENCE': { es: 'EXPERIENCIA', de: 'ERFAHRUNG' },
    'About': { es: 'Sobre mí', de: 'Über mich' },
    'Home': { es: 'Inicio', de: 'Start' },
    'Experience': { es: 'Experiencia', de: 'Erfahrung' },
    'Online': { es: 'En línea', de: 'Online' },
    'Offline': { es: 'Fuera de línea', de: 'Offline' },
    'Calendar': { es: 'Calendario', de: 'Kalender' },
    'Partnerships': { es: 'Colaboraciones', de: 'Kooperationen' },
    'Sign Up': { es: 'Suscríbete', de: 'Anmelden' },
    'contact': { es: 'contacto', de: 'kontakt' },
    'Contact': { es: 'Contacto', de: 'Kontakt' },
    'CONTACT': { es: 'CONTACTO', de: 'KONTAKT' },
    'Go to home': { es: 'Ir al inicio', de: 'Zur Startseite' },
    'All rights reserved': { es: 'Todos los derechos reservados', de: 'Alle Rechte vorbehalten' },
    'Computer Science and': { es: 'Ciencias de la Computación e', de: 'Informatik und' },
    'Artificial Intelligence': { es: 'Inteligencia Artificial', de: 'Künstliche Intelligenz' },
    'BCSAI @ IE University': { es: 'BCSAI en IE University', de: 'BCSAI an der IE University' },
    'Please rotate your device,': { es: 'Gira tu dispositivo,', de: 'Bitte dreh dein Gerät,' },
    'This is a vertical drive.': { es: 'Esta experiencia es vertical.', de: 'Das ist eine vertikale Fahrt.' },
    'Project portfolio of code and research.': { es: 'Portafolio de proyectos de código e investigación.', de: 'Portfolio aus Code und Forschung.' },
    'My personal life away from the screen.': { es: 'Mi vida personal lejos de la pantalla.', de: 'Mein Leben abseits des Bildschirms.' },

    /* the hall of ideas */
    'Hall of Ideas': { es: 'Salón de Ideas', de: 'Halle der Ideen' },
    'Every project here ships with a live demo and its source, not a screenshot. Filter by what one is made of, or what it was built for.': {
      es: 'Cada proyecto aquí viene con una demo en vivo y su código, no con una captura. Filtra por aquello de lo que está hecho, o por aquello para lo que fue construido.',
      de: 'Jedes Projekt hier kommt mit einer Live-Demo und seinem Quellcode, nicht mit einem Screenshot. Filtere danach, woraus es gebaut ist oder wofür es gebaut wurde.',
    },

    /* the filter row. these match on the visible label only: every pill keeps
       its english data-value, which is what the filter compares against, so
       translating the face of a pill cannot change what it selects. */
    'TYPE': { es: 'TIPO', de: 'ART' },
    'DOMAIN': { es: 'ÁMBITO', de: 'BEREICH' },
    'YEAR': { es: 'AÑO', de: 'JAHR' },
    'All': { es: 'Todo', de: 'Alle' },
    'Coursework': { es: 'Académico', de: 'Studium' },
    'Personal': { es: 'Personal', de: 'Privat' },
    'AI & ML': { es: 'IA y ML', de: 'KI & ML' },
    'Algorithms': { es: 'Algoritmos', de: 'Algorithmen' },
    'Cloud & DevOps': { es: 'Cloud y DevOps', de: 'Cloud & DevOps' },
    'Computer Vision': { es: 'Visión por computador', de: 'Computer Vision' },
    'Games': { es: 'Videojuegos', de: 'Spiele' },
    'Real-time 3D': { es: '3D en tiempo real', de: 'Echtzeit-3D' },
    'UI & UX': { es: 'UI y UX', de: 'UI & UX' },
    'Motion capture': { es: 'Captura de movimiento', de: 'Motion Capture' },

    /* the journey section. the timeline entries are built by experience.js
       after this script has already run, which is exactly the case the
       observer at the bottom of this file exists for: they are translated when
       they appear rather than on a guess about when that will be. */
    'JOURNEY': { es: 'TRAYECTORIA', de: 'WERDEGANG' },
    'My experience log: studies and work, mapped where they happened.': {
      es: 'Mi registro de experiencia: estudios y trabajo, situados donde ocurrieron.',
      de: 'Mein Erfahrungsprotokoll: Studium und Arbeit, verortet wo sie stattfanden.',
    },
    'BOGOTA, COLOMBIA': { es: 'BOGOTÁ, COLOMBIA', de: 'BOGOTÁ, KOLUMBIEN' },
    'DUBLIN, IRELAND': { es: 'DUBLÍN, IRLANDA', de: 'DUBLIN, IRLAND' },
    'MADRID, SPAIN': { es: 'MADRID, ESPAÑA', de: 'MADRID, SPANIEN' },
    'Colegio Nueva Granada, and the digital presence for Top Living Inmobiliaria': {
      es: 'Colegio Nueva Granada, y la presencia digital de Top Living Inmobiliaria',
      de: 'Colegio Nueva Granada, und die digitale Präsenz für Top Living Inmobiliaria',
    },
    'Trinity College Dublin, first year of the degree': {
      es: 'Trinity College Dublin, primer año del grado',
      de: 'Trinity College Dublin, erstes Studienjahr',
    },
    'IE University, IEX Labs research and the Google Developer Group': {
      es: 'IE University, investigación en IEX Labs y el Google Developer Group',
      de: 'IE University, Forschung bei IEX Labs und die Google Developer Group',
    },
    '2010 to 2024': { es: '2010 a 2024', de: '2010 bis 2024' },
    '2022 to 2023': { es: '2022 a 2023', de: '2022 bis 2023' },
    '2023 to present': { es: '2023 a hoy', de: '2023 bis heute' },

    /* the experience page: hero, then the three cv sections */
    'Madrid, Spain · graduating 2028': {
      es: 'Madrid, España · graduación 2028',
      de: 'Madrid, Spanien · Abschluss 2028',
    },
    'Research assistant at': { es: 'Asistente de investigación en', de: 'Forschungsassistentin bei' },
    'Technical lead of the': { es: 'Líder técnica del', de: 'Technical Lead der' },
    'BSc Computer Science and AI at IE University.': {
      es: 'Grado en Ciencias de la Computación e IA en IE University.',
      de: 'BSc Computer Science and AI an der IE University.',
    },
    'Open to internships and roles for fall 2026 and summer 2027.': {
      es: 'Disponible para prácticas y empleo en otoño de 2026 y verano de 2027.',
      de: 'Offen für Praktika und Stellen im Herbst 2026 und Sommer 2027.',
    },

    'WORK': { es: 'TRABAJO', de: 'ARBEIT' },
    'Research, developer community leadership and client work.': {
      es: 'Investigación, liderazgo en comunidad de desarrolladores y trabajo con clientes.',
      de: 'Forschung, Leitung einer Entwickler-Community und Kundenarbeit.',
    },
    'Technical Lead': { es: 'Líder técnica', de: 'Technical Lead' },
    'Research Assistant': { es: 'Asistente de investigación', de: 'Forschungsassistentin' },
    'Real Estate Agent': { es: 'Agente inmobiliaria', de: 'Immobilienmaklerin' },
    'Sept 2025 to present': { es: 'sept 2025 a hoy', de: 'Sept 2025 bis heute' },
    'Jan 2025 to present': { es: 'ene 2025 a hoy', de: 'Jan 2025 bis heute' },
    'Apr 2021 to Apr 2024': { es: 'abr 2021 a abr 2024', de: 'Apr 2021 bis Apr 2024' },
    'Madrid, Spain': { es: 'Madrid, España', de: 'Madrid, Spanien' },
    'Bogota, Colombia': { es: 'Bogotá, Colombia', de: 'Bogotá, Kolumbien' },
    'Dublin, Ireland': { es: 'Dublín, Irlanda', de: 'Dublin, Irland' },
    'Led hands-on workshops on Git and GitHub, AI agent development and the Gemini CLI, upskilling student developers.': {
      es: 'Impartí talleres prácticos sobre Git y GitHub, desarrollo de agentes de IA y Gemini CLI, formando a desarrolladores estudiantes.',
      de: 'Praxis-Workshops zu Git und GitHub, KI-Agenten-Entwicklung und der Gemini CLI geleitet und studentische Entwickler weitergebildet.',
    },
    'Co-organised the Build with AI and Tech Roulette hackathons, running logistics and support throughout.': {
      es: 'Coorganicé los hackathones Build with AI y Tech Roulette, encargándome de la logística y el soporte.',
      de: 'Die Hackathons Build with AI und Tech Roulette mitorganisiert, samt Logistik und Betreuung.',
    },
    'Coordinated a female leadership panel with Women in Tech Madrid, and a session on VR applications in patient care.': {
      es: 'Coordiné un panel de liderazgo femenino con Women in Tech Madrid y una sesión sobre aplicaciones de RV en la atención al paciente.',
      de: 'Ein Panel zu weiblicher Führung mit Women in Tech Madrid koordiniert, dazu eine Session zu VR in der Patientenversorgung.',
    },
    "Set up and tested DJESTHESIA's tangible multimedia interface using TouchDesigner and OptiTrack motion capture.": {
      es: 'Monté y probé la interfaz multimedia tangible de DJESTHESIA con TouchDesigner y captura de movimiento OptiTrack.',
      de: "DJESTHESIAs greifbare Multimedia-Schnittstelle mit TouchDesigner und OptiTrack-Motion-Capture aufgebaut und getestet.",
    },
    'Ran the literature review for ROBOPRENEUR and worked on the final video prototype for its IEEE submission.': {
      es: 'Realicé la revisión bibliográfica de ROBOPRENEUR y trabajé en el prototipo de vídeo final para su envío al IEEE.',
      de: 'Die Literaturrecherche für ROBOPRENEUR durchgeführt und am finalen Video-Prototyp für die IEEE-Einreichung mitgearbeitet.',
    },
    'Started a motion capture and Blender animation pipeline, prototyping character work for a Unity based game.': {
      es: 'Inicié un pipeline de captura de movimiento y animación en Blender, prototipando personajes para un juego en Unity.',
      de: 'Eine Motion-Capture- und Blender-Animationspipeline aufgesetzt und Charakterarbeit für ein Unity-Spiel prototypisiert.',
    },
    "Built and maintained the brand's digital presence through social media, website design and digital marketing.": {
      es: 'Construí y mantuve la presencia digital de la marca con redes sociales, diseño web y marketing digital.',
      de: 'Die digitale Präsenz der Marke über Social Media, Webdesign und digitales Marketing aufgebaut und gepflegt.',
    },
    'Organised property documentation to support evaluation, giving the sales team faster decisions.': {
      es: 'Organicé la documentación de inmuebles para apoyar su valoración, agilizando las decisiones del equipo de ventas.',
      de: 'Immobilienunterlagen für die Bewertung aufbereitet und so schnellere Entscheidungen im Vertrieb ermöglicht.',
    },

    'EDUCATION': { es: 'FORMACIÓN', de: 'AUSBILDUNG' },
    'Where the degree has been taken, and what it covered.': {
      es: 'Dónde he cursado el grado, y qué ha incluido.',
      de: 'Wo das Studium stattfand und was es umfasste.',
    },
    'BSc Computer Science and Artificial Intelligence': {
      es: 'Grado en Ciencias de la Computación e Inteligencia Artificial',
      de: 'BSc Computer Science and Artificial Intelligence',
    },
    'BSc Computer Science and Artificial Intelligence, year one': {
      es: 'Grado en Ciencias de la Computación e Inteligencia Artificial, primer año',
      de: 'BSc Computer Science and Artificial Intelligence, erstes Jahr',
    },
    'expected July 2028': { es: 'prevista julio de 2028', de: 'voraussichtlich Juli 2028' },
    'Coursework: machine learning, computer vision, natural language processing, reinforcement learning, robotics, human-computer interaction.': {
      es: 'Asignaturas: aprendizaje automático, visión por computador, procesamiento del lenguaje natural, aprendizaje por refuerzo, robótica, interacción persona-computador.',
      de: 'Kurse: Machine Learning, Computer Vision, Verarbeitung natürlicher Sprache, Reinforcement Learning, Robotik, Mensch-Computer-Interaktion.',
    },
    'Awarded the High Potential Scholarship for academic excellence.': {
      es: 'Becada con la High Potential Scholarship por excelencia académica.',
      de: 'Mit dem High Potential Scholarship für akademische Leistungen ausgezeichnet.',
    },
    'Coursework: electrotechnology, mathematics (calculus, statistics, linear algebra), computers and society, digital logic design, computational theory.': {
      es: 'Asignaturas: electrotecnia, matemáticas (cálculo, estadística, álgebra lineal), informática y sociedad, diseño lógico digital, teoría de la computación.',
      de: 'Kurse: Elektrotechnik, Mathematik (Analysis, Statistik, lineare Algebra), Informatik und Gesellschaft, digitales Schaltungsdesign, Berechenbarkeitstheorie.',
    },
    'Transferred to IE University after the first year.': {
      es: 'Traslado a IE University tras el primer año.',
      de: 'Nach dem ersten Jahr an die IE University gewechselt.',
    },

    'TECHNICAL SKILLS': { es: 'COMPETENCIAS TÉCNICAS', de: 'TECHNISCHE KENNTNISSE' },
    'What the work above was actually built with.': {
      es: 'Con qué está construido realmente el trabajo de arriba.',
      de: 'Womit die Arbeit oben tatsächlich gebaut wurde.',
    },
    'AI and machine learning': { es: 'IA y aprendizaje automático', de: 'KI und Machine Learning' },
    'Languages': { es: 'Lenguajes', de: 'Sprachen' },
    'Frameworks and databases': { es: 'Frameworks y bases de datos', de: 'Frameworks und Datenbanken' },
    'Cloud and deployment': { es: 'Cloud y despliegue', de: 'Cloud und Deployment' },
    'Coursework: machine learning, computer vision, natural language processing, reinforcement learning, robotics, cloud computing, human-computer interaction.': {
      es: 'Asignaturas: aprendizaje automático, visión por computador, procesamiento del lenguaje natural, aprendizaje por refuerzo, robótica, computación en la nube, interacción persona-computador.',
      de: 'Kurse: Machine Learning, Computer Vision, Verarbeitung natürlicher Sprache, Reinforcement Learning, Robotik, Cloud Computing, Mensch-Computer-Interaktion.',
    },
    'Tools and platforms': { es: 'Herramientas y plataformas', de: 'Werkzeuge und Plattformen' },
  };

  const SKIP = new Set(['SCRIPT', 'STYLE', 'NOSCRIPT', 'CANVAS', 'SVG']);

  // set up at the bottom, but apply() clears its queue, and apply() runs first
  let watcher = null;

  const nodes = [...document.querySelectorAll('[data-i18n]')];
  const EN = {};
  nodes.forEach((n) => { EN[n.getAttribute('data-i18n')] = n.innerHTML; });

  /* every known wording of a phrase, in any language, mapped back to its
     english key.

     the first version cached each text node's original english and reverted by
     node identity. that breaks the moment her markup is rebuilt: the menu
     overlay's contents are recreated when it opens, so those nodes were not the
     ones that had been cached, and the menu stayed in spanish after switching
     back to english.

     matching on the text itself is stateless. a node can be found in any
     language, at any time, and still resolves to the right target. */
  const CANON = new Map();
  Object.keys(PHRASES).forEach((en) => {
    CANON.set(en, en);
    Object.values(PHRASES[en]).forEach((variant) => CANON.set(variant, en));
  });

  /* the same map again, with every space removed.

     her line splitter cuts a paragraph into one span per rendered line, so a
     sentence stops being one text node and no node-level match can ever see it
     whole. the pieces do still concatenate back to the sentence, but not
     reliably with the spaces intact, so comparing on squeezed text is what
     makes a split paragraph findable again. */
  const tight = (s) => (s || '').replace(/\s+/g, '').toLowerCase();

  const CANON_TIGHT = new Map();
  CANON.forEach((en, variant) => CANON_TIGHT.set(tight(variant), en));

  function translateNodes(lang) {
    const walker = document.createTreeWalker(document.body, NodeFilter.SHOW_TEXT, {
      acceptNode(n) {
        if (SKIP.has(n.parentNode.nodeName)) return NodeFilter.FILTER_REJECT;
        if (n.parentNode.closest && n.parentNode.closest('[data-i18n]')) return NodeFilter.FILTER_REJECT;
        return CANON.has(n.nodeValue.trim()) ? NodeFilter.FILTER_ACCEPT : NodeFilter.FILTER_REJECT;
      },
    });

    const found = [];
    let n;
    while ((n = walker.nextNode())) found.push(n);

    found.forEach((node) => {
      const current = node.nodeValue.trim();
      const key = CANON.get(current);
      if (!key) return;
      const target = lang === 'en' ? key : (PHRASES[key][lang] || key);
      if (target !== current) node.nodeValue = node.nodeValue.replace(current, target);
    });
  }

  /* whole paragraphs, for the ones the splitter has already taken apart.

     only split-text="lines" is touched. that is body copy, and rewriting it
     costs nothing but the line spans, which are re-revealed as plain visible
     text. split-text="chars" is deliberately left alone: those are the nav
     labels and buttons whose hover animation is bound to the individual char
     spans, and flattening them would kill it.

     none of this runs on a first load in another language anyway. this script
     goes before her splitter, so the paragraph is still one text node when it
     is translated and the split then happens on the translated words with the
     animation fully intact. this is the path for switching language later, on
     a page that has already been split. */
  function translateBlocks(lang) {
    document.querySelectorAll('[split-text="lines"]').forEach((el) => {
      if (el.closest('[data-i18n]')) return;

      // the splitter copies the pre-split wording into aria-label, which is a
      // cleaner source than reassembled spans
      const key = CANON_TIGHT.get(tight(el.getAttribute('aria-label') || el.textContent));
      if (!key) return;

      const target = lang === 'en' ? key : (PHRASES[key][lang] || key);
      if (tight(el.textContent) === tight(target)) return;
      el.textContent = target;
    });
  }

  function apply(lang) {
    const dict = lang === 'en' ? EN : DICT[lang];
    if (!dict) return;

    nodes.forEach((n) => {
      const key = n.getAttribute('data-i18n');
      const value = dict[key] !== undefined ? dict[key] : EN[key];
      if (value === undefined) return;

      /* never rewrite an element that already reads correctly.

         this is what kept killing the sweeps. her bundle splits these into
         span.line elements and binds the highlight to them, and setting
         innerHTML replaced those spans with a bare string, so the animation had
         nothing left to drive. the re-apply after load did it every time, in
         the same language, for no reason.

         comparing the rendered text against the target means a repeat pass is a
         no-op and the spans survive. a real language change still rewrites, and
         loses the split, but by then the sweep has already played so there is
         nothing to see. */
      const target = document.createElement('div');
      target.innerHTML = value;
      if (n.textContent.trim() === target.textContent.trim()) return;

      n.innerHTML = value;
    });

    translateNodes(lang);
    translateBlocks(lang);

    document.documentElement.lang = lang;
    document.querySelectorAll('[data-lang]').forEach((b) => {
      const on = b.getAttribute('data-lang') === lang;
      b.classList.toggle('is-on', on);
      b.setAttribute('aria-pressed', String(on));
    });
    try { localStorage.setItem(STORE, lang); } catch (e) {}

    /* anything that renders its own words rather than carrying them in the
       markup, like the project count on the hall of ideas, cannot be reached by
       either pass. this tells those scripts to redraw themselves. */
    document.dispatchEvent(new CustomEvent('edge:lang', { detail: lang }));

    // discard the mutations this pass just made, so the observer below does not
    // read our own writing as a change and call us straight back
    if (watcher) watcher.takeRecords();
  }

  function current() {
    try { return localStorage.getItem(STORE) || 'en'; } catch (e) { return 'en'; }
  }

  /* build the switcher if this page has not got one, so a page mirrors with
     nothing but this script added to it */
  function ensureSwitcher() {
    let sw = document.querySelector('[data-lang-switch]');
    if (!sw) {
      sw = document.createElement('div');
      sw.className = 'lang';
      sw.setAttribute('data-lang-switch', '');
      sw.setAttribute('role', 'group');
      sw.setAttribute('aria-label', 'Language');
      sw.innerHTML = ['en', 'es', 'de']
        .map((l) => `<button type="button" data-lang="${l}">${l}</button>`)
        .join('<span aria-hidden="true">/</span>');
    }
    sw.hidden = false;
    const inner = document.querySelector('.nav .nav-inner');
    const ham = document.querySelector('.nav-ham, .btn-layout.is-nav');
    if (inner && !inner.contains(sw)) {
      if (ham && ham.parentElement === inner) inner.insertBefore(sw, ham);
      else inner.appendChild(sw);
    }
  }

  document.addEventListener('click', (e) => {
    const btn = e.target.closest('[data-lang]');
    if (btn) { apply(btn.getAttribute('data-lang')); return; }

    // her menu builds its contents as it opens, so run again once it is there
    if (e.target.closest('.nav-ham, .btn-layout.is-nav, .nav-menu-w')) {
      setTimeout(() => apply(current()), 120);
      setTimeout(() => apply(current()), 600);
    }
  });

  function boot() {
    ensureSwitcher();
    apply(current());
  }

  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();

  /* re-translate whatever her bundle rebuilds.

     this used to be two fixed timeouts, on the guess that everything would have
     settled by then. it does not: the splitter, the menu overlay and the filter
     row each rewrite their part of the page on their own schedule, and anything
     landing after the last timeout simply stayed in english.

     watching for the rebuild instead of predicting when it happens covers all
     of them, including anything added later. only structure and text are
     watched, not attributes, so gsap writing inline styles every frame does not
     reach this. apply() changes nothing when the page already reads correctly,
     and clears its own mutations on the way out, so a redundant pass is free and
     cannot feed itself. */
  let pass = 0;
  watcher = new MutationObserver(() => {
    clearTimeout(pass);
    pass = setTimeout(() => apply(current()), 250);
  });
  watcher.observe(document.body, { childList: true, subtree: true, characterData: true });
})();
