/* Concise localized overview; detailed technical history stays in its own page. */
(function () {
  'use strict';
  var historicalIds = ["challenge-heading", "idea-heading", "outcome-heading", "evolution-title", "web-demo-title", "why", "why-title", "experience", "experience-title", "principle", "principle-title", "eink", "eink-title", "process", "process-title", "evidence", "evidence-title", "research-retrieval", "research-spacing", "research-adaptive", "research-interleaving", "research-feedback", "research-hebb", "research-eink", "research-ai", "research-tutor", "build", "build-title", "next", "next-title", "documentation-title"];
  function preserveDeepLink() {
    var id;
    try { id = decodeURIComponent(location.hash.slice(1)); } catch (_) { return false; }
    if (historicalIds.indexOf(id) === -1) return false;
    location.replace('./details.html' + location.search + location.hash);
    return true;
  }
  if (preserveDeepLink()) return;
  window.addEventListener('hashchange', preserveDeepLink);
  var copy = {
  "en": {
    "eyebrow": "Personal project · Native Android prototype",
    "status": "In development",
    "intro": "A pen-first notebook for BOOX and Android tablets, connecting handwritten thinking, study materials and optional AI assistance.",
    "code": "View Android source",
    "demo-link": "Watch the earlier web demo",
    "docs": "Technical documentation",
    "art": "AI-generated concept illustration · not the current app UI",
    "role-label": "My contribution",
    "role": "Interaction design, Android prototyping & technical documentation",
    "stack-label": "Built with",
    "stack": "Java · Android View/Canvas · local JSON · KaTeX",
    "snapshot-label": "Documented version",
    "snapshot": "v0.1.23 · 17 October 2026",
    "problem-title": "The problem",
    "problem": "Switching between handwritten notes, explanations and practice can break a study session’s flow. NoteCrafted explores a single notebook where learners keep their own working, ask for help deliberately and return to what they tried.",
    "built-title": "What works in the prototype",
    "built": "A freeform Think canvas supports pressure-aware ink, selection, erasing, undo and redo. A structured Tutor brings together local calculus lessons, practice, saved attempts and assistance history. Goal-specific storage preserves working between sessions.",
    "decisions-title": "Three design decisions",
    "decision-1-title": "Protect the original thinking",
    "decision-1": "Keep AI replies separate from the learner’s ink, so help adds context without replacing their work.",
    "decision-2-title": "Make the notebook useful offline",
    "decision-2": "Local lessons and deterministic practice work without an AI request; assistance is an explicit connection.",
    "decision-3-title": "Make the evidence legible",
    "decision-3": "Record attempts and help used. Numeric checks cover typed answers; they do not assess handwritten reasoning or certify mastery.",
    "evolution-title": "From web demo to tablet",
    "evolution": "The earlier NoteCrafted web demo explored an AI tutor. The tablet prototype, developed as PLIA, shifted the focus toward handwriting and direct interaction. Tools such as NotebookLM helped sharpen that design direction: how could AI support the way someone chooses to take notes?",
    "status-title": "Verification & next steps",
    "verification": "The public v0.1.23 record includes installation and offline-lesson checks on the device. It is a working prototype, not a production release; the page does not establish the latest installed UI or measured learning gains.",
    "next": "Flashcards, adaptive review and a richer note-to-practice loop remain proposed work. Further validation needs real-device usability checks and independent learning measures.",
    "demo-title": "The starting idea, on video",
    "demo-copy": "This recorded web concept preserves the project’s first phase; it is not the Android interface.",
    "play": "Watch the web demo on YouTube",
    "youtube": "Open on YouTube",
    "record-title": "Want the details?",
    "record": "Explore the implementation, research rationale, development history and known limitations.",
    "history": "Detailed development & research record",
    "documentation": "Android documentation",
    "zip": "Source ZIP",
    "all": "All projects",
    "artNav": "Design & Art"
  },
  "es": {
    "eyebrow": "Proyecto personal · Prototipo nativo de Android",
    "status": "En desarrollo",
    "intro": "Un cuaderno para tabletas BOOX y Android, pensado para el lápiz, que reúne escritura a mano, materiales de estudio y ayuda opcional de IA.",
    "code": "Ver el código Android",
    "demo-link": "Ver la primera demo web",
    "docs": "Documentación técnica",
    "art": "Ilustración conceptual generada con IA · no es la interfaz actual",
    "role-label": "Mi contribución",
    "role": "Diseño de interacción, prototipado Android y documentación técnica",
    "stack-label": "Tecnologías",
    "stack": "Java · Android View/Canvas · JSON local · KaTeX",
    "snapshot-label": "Versión documentada",
    "snapshot": "v0.1.23 · 17 de octubre de 2026",
    "problem-title": "El problema",
    "problem": "Pasar de apuntes manuscritos a explicaciones y ejercicios puede interrumpir el estudio. NoteCrafted explora un único cuaderno donde conservar el razonamiento propio, pedir ayuda de forma deliberada y retomar lo que se ha intentado.",
    "built-title": "Qué funciona en el prototipo",
    "built": "El lienzo libre Think permite escribir con sensibilidad a la presión, seleccionar, borrar, deshacer y rehacer. El Tutor reúne lecciones locales de cálculo, ejercicios, intentos guardados e historial de ayuda. El almacenamiento por objetivo conserva el trabajo entre sesiones.",
    "decisions-title": "Tres decisiones de diseño",
    "decision-1-title": "Proteger el razonamiento original",
    "decision-1": "Las respuestas de IA permanecen separadas de los trazos del estudiante: añaden contexto sin reemplazar su trabajo.",
    "decision-2-title": "Un cuaderno útil sin conexión",
    "decision-2": "Las lecciones locales y los ejercicios deterministas funcionan sin solicitar IA; la ayuda requiere una conexión explícita.",
    "decision-3-title": "Hacer legible la evidencia",
    "decision-3": "Se registran los intentos y la ayuda utilizada. Las comprobaciones numéricas evalúan respuestas tecleadas, no el razonamiento manuscrito ni el dominio de una materia.",
    "evolution-title": "De una demo web a la tableta",
    "evolution": "La primera demo web de NoteCrafted exploraba un tutor de IA. El prototipo para tabletas, desarrollado como PLIA, pasó a centrarse en la escritura a mano y la interacción directa. Herramientas como NotebookLM ayudaron a precisar la dirección: ¿cómo podría la IA apoyar la manera de tomar apuntes que cada persona prefiere?",
    "status-title": "Verificación y próximos pasos",
    "verification": "El registro público de v0.1.23 incluye comprobaciones de instalación y de una lección sin conexión en el dispositivo. Es un prototipo funcional, no una versión de producción; esta página no acredita la interfaz instalada más reciente ni mejoras de aprendizaje medidas.",
    "next": "Las tarjetas de repaso, la revisión adaptativa y una conexión más rica entre apuntes y práctica siguen siendo propuestas. Falta validar la usabilidad en el dispositivo y medir el aprendizaje independiente.",
    "demo-title": "La idea inicial, en vídeo",
    "demo-copy": "Este concepto web grabado conserva la primera etapa del proyecto; no muestra la interfaz Android.",
    "play": "Ver la demo web en YouTube",
    "youtube": "Abrir en YouTube",
    "record-title": "¿Quieres más detalles?",
    "record": "Consulta la implementación, los fundamentos de investigación, la historia del desarrollo y las limitaciones conocidas.",
    "history": "Registro detallado de desarrollo e investigación",
    "documentation": "Documentación Android",
    "zip": "Código en ZIP",
    "all": "Todos los proyectos",
    "artNav": "Diseño y arte"
  },
  "de": {
    "eyebrow": "Persönliches Projekt · Nativer Android-Prototyp",
    "status": "In Entwicklung",
    "intro": "Ein stiftorientiertes Notizbuch für BOOX- und Android-Tablets, das handschriftliches Denken, Lernmaterialien und optionale KI-Hilfe verbindet.",
    "code": "Android-Quellcode ansehen",
    "demo-link": "Frühere Web-Demo ansehen",
    "docs": "Technische Dokumentation",
    "art": "KI-generierte Konzeptillustration · keine aktuelle App-Oberfläche",
    "role-label": "Mein Beitrag",
    "role": "Interaktionsdesign, Android-Prototyping und technische Dokumentation",
    "stack-label": "Technologien",
    "stack": "Java · Android View/Canvas · lokales JSON · KaTeX",
    "snapshot-label": "Dokumentierte Version",
    "snapshot": "v0.1.23 · 17. Oktober 2026",
    "problem-title": "Das Problem",
    "problem": "Der Wechsel zwischen handschriftlichen Notizen, Erklärungen und Übungen kann den Lernfluss unterbrechen. NoteCrafted erkundet ein gemeinsames Notizbuch, in dem Lernende ihre eigenen Überlegungen behalten, bewusst Hilfe anfordern und frühere Versuche wieder aufnehmen.",
    "built-title": "Was im Prototyp funktioniert",
    "built": "Die freie Think-Zeichenfläche unterstützt drucksensitive Tinte, Auswahl, Radieren sowie Rückgängig und Wiederholen. Ein strukturierter Tutor verbindet lokale Analysis-Lektionen, Übungen, gespeicherte Versuche und den Hilfeverlauf. Zielbezogene Speicherung erhält die Arbeit zwischen Sitzungen.",
    "decisions-title": "Drei Gestaltungsentscheidungen",
    "decision-1-title": "Eigene Überlegungen schützen",
    "decision-1": "KI-Antworten bleiben von der Handschrift getrennt: Sie ergänzen Kontext, ohne die eigene Arbeit zu ersetzen.",
    "decision-2-title": "Auch offline nützlich bleiben",
    "decision-2": "Lokale Lektionen und deterministische Übungen funktionieren ohne KI-Anfrage. Unterstützung benötigt eine ausdrücklich aktivierte Verbindung.",
    "decision-3-title": "Nachweise verständlich machen",
    "decision-3": "Versuche und genutzte Hilfe werden dokumentiert. Numerische Prüfungen bewerten eingegebene Antworten, nicht handschriftliche Lösungswege oder allgemeine Beherrschung.",
    "evolution-title": "Von der Web-Demo zum Tablet",
    "evolution": "Die frühe NoteCrafted-Web-Demo erkundete einen KI-Tutor. Der als PLIA entwickelte Tablet-Prototyp verlagerte den Schwerpunkt auf Handschrift und direkte Interaktion. Werkzeuge wie NotebookLM schärften die Gestaltungsfrage: Wie könnte KI die selbst gewählte Art unterstützen, Notizen zu machen?",
    "status-title": "Prüfung und nächste Schritte",
    "verification": "Der öffentliche v0.1.23-Stand dokumentiert die Installation und eine Offline-Lektion auf dem Gerät. Er ist ein funktionsfähiger Prototyp, keine Produktionsversion; diese Seite belegt weder die neueste installierte Oberfläche noch gemessene Lerngewinne.",
    "next": "Karteikarten, adaptive Wiederholung und eine engere Verbindung zwischen Notizen und Übungen sind weiterhin Vorschläge. Weitere Prüfungen brauchen Nutzungstests auf dem Gerät und unabhängige Lernmaße.",
    "demo-title": "Die ursprüngliche Idee im Video",
    "demo-copy": "Das aufgezeichnete Web-Konzept bewahrt die erste Projektphase; es zeigt keine Android-Oberfläche.",
    "play": "Web-Demo auf YouTube ansehen",
    "youtube": "Auf YouTube öffnen",
    "record-title": "Mehr erfahren?",
    "record": "Implementierung, Forschungsgrundlagen, Entwicklungsgeschichte und bekannte Grenzen sind ausführlich dokumentiert.",
    "history": "Ausführliche Entwicklungs- und Forschungsdokumentation",
    "documentation": "Android-Dokumentation",
    "zip": "Quellcode-ZIP",
    "all": "Alle Projekte",
    "artNav": "Design & Kunst"
  }
};
  function render(language) {
    var lang = copy[language] ? language : 'en';
    document.documentElement.lang = lang;
    document.querySelectorAll('[data-nc]').forEach(function (node) {
      var value = copy[lang][node.dataset.nc];
      if (value !== undefined) node.textContent = value;
    });
    document.querySelectorAll('[data-nc-lang]').forEach(function (button) { button.setAttribute('aria-pressed', String(button.dataset.ncLang === lang)); });
    try { localStorage.setItem('edge-lang', lang); } catch (_) {}
  }
  document.querySelectorAll('[data-nc-lang]').forEach(function (button) { button.addEventListener('click', function () { render(button.dataset.ncLang); }); });
  var initial = 'en'; try { initial = localStorage.getItem('edge-lang') || 'en'; } catch (_) {}
  render(initial);
})();
