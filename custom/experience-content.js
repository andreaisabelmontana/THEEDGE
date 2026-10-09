/* Page-local copy. English is also in the HTML, so the full experience is readable without JavaScript. */
(function () {
  'use strict';
  if (!document.body.classList.contains('experience-page')) return;
  var copy = {
  "credentials-heading": {
    "en": "Certificates & training",
    "es": "Certificados y formación",
    "de": "Zertifikate & Weiterbildung"
  },
  "specialization": {
    "en": "Specialization",
    "es": "Especialización",
    "de": "Spezialisierung"
  },
  "course-certificate": {
    "en": "Course certificate",
    "es": "Certificado de curso",
    "de": "Kurszertifikat"
  },
  "anthropic-collection": {
    "en": "CV-provided certificate packet · includes Claude 101",
    "es": "Certificados incluidos en el CV · incluye Claude 101",
    "de": "Zertifikate aus dem Lebenslauf · inklusive Claude 101"
  },
  "training-badge": {
    "en": "Training badge",
    "es": "Insignia de formación",
    "de": "Schulungsabzeichen"
  },
  "credential-open": {
    "en": "View credential",
    "es": "Ver credencial",
    "de": "Nachweis ansehen"
  },
  "date-2026-09": {
    "en": "September 2026",
    "es": "Septiembre de 2026",
    "de": "September 2026"
  },
  "date-2026-06": {
    "en": "June 2026",
    "es": "Junio de 2026",
    "de": "Juni 2026"
  },
  "date-2024-11": {
    "en": "November 2024",
    "es": "Noviembre de 2024",
    "de": "November 2024"
  },
  "robo-video": {
    "en": "ROBOPRENEUR video",
    "es": "Vídeo de ROBOPRENEUR",
    "de": "ROBOPRENEUR-Video"
  },
  "eyebrow": {
    "en": "MADRID, SPAIN / CLASS OF 2028",
    "es": "MADRID, ESPAÑA / PROMOCIÓN DE 2028",
    "de": "MADRID, SPANIEN / ABSCHLUSS 2028"
  },
  "title": {
    "en": "Experience",
    "es": "Experiencia",
    "de": "Erfahrung"
  },
  "lead": {
    "en": "Research, software & interactive systems.",
    "es": "Investigación, software y sistemas interactivos.",
    "de": "Forschung, Software & interaktive Systeme."
  },
  "intro": {
    "en": "Computer Science & AI student at IE University and Research Assistant at IEX Labs. Exploring how intelligent systems meet the physical world.",
    "es": "Estudiante de Computer Science & AI en IE University y asistente de investigación en IEX Labs. Exploro cómo los sistemas inteligentes se conectan con el mundo físico.",
    "de": "Studentin der Informatik und KI an der IE University und Forschungsassistentin bei IEX Labs. Ich erforsche, wie intelligente Systeme auf die physische Welt treffen."
  },
  "eligibility": {
    "en": "Eligible for internships through an IE University internship agreement.",
    "es": "Elegible para prácticas mediante un convenio de IE University.",
    "de": "Praktika im Rahmen einer Praktikumsvereinbarung mit der IE University möglich."
  },
  "projects-link": {
    "en": "Explore projects",
    "es": "Ver proyectos",
    "de": "Projekte ansehen"
  },
  "contact-link": {
    "en": "Get in touch",
    "es": "Hablemos",
    "de": "Kontakt aufnehmen"
  },
  "nav-experience": {
    "en": "Experience",
    "es": "Experiencia",
    "de": "Erfahrung"
  },
  "nav-education": {
    "en": "Education",
    "es": "Educación",
    "de": "Ausbildung"
  },
  "nav-skills": {
    "en": "Skills",
    "es": "Habilidades",
    "de": "Kenntnisse"
  },
  "nav-community": {
    "en": "Leadership",
    "es": "Liderazgo",
    "de": "Engagement"
  },
  "nav-journey": {
    "en": "Journey",
    "es": "Recorrido",
    "de": "Stationen"
  },
  "experience-heading": {
    "en": "Experience",
    "es": "Experiencia",
    "de": "Erfahrung"
  },
  "experience-sub": {
    "en": "From research prototypes to software used by real people.",
    "es": "De prototipos de investigación a software que usan personas reales.",
    "de": "Von Forschungsprototypen zu Software für echte Nutzer."
  },
  "iex-date": {
    "en": "Jan 2025 — Present",
    "es": "Ene 2025 — Actualidad",
    "de": "Jan. 2025 — Heute"
  },
  "iex-place": {
    "en": "Madrid, Spain",
    "es": "Madrid, España",
    "de": "Madrid, Spanien"
  },
  "iex-role": {
    "en": "Research Assistant",
    "es": "Asistente de investigación",
    "de": "Forschungsassistentin"
  },
  "current": {
    "en": "Current",
    "es": "Actual",
    "de": "Aktuell"
  },
  "iex-robo": {
    "en": "Supported ROBOPRENEUR research associated with RODEO, accepted at IEEE ICRA 2026: human–robot interaction simulation with cryptocurrency-based task rewards.",
    "es": "Colaboré en la investigación de ROBOPRENEUR vinculada a RODEO, aceptado en IEEE ICRA 2026: simulación de interacción humano-robot con recompensas por tareas basadas en criptomonedas.",
    "de": "Mitarbeit an der ROBOPRENEUR-Forschung im Zusammenhang mit RODEO, angenommen für IEEE ICRA 2026: Simulation der Mensch-Roboter-Interaktion mit kryptowährungsbasierten Aufgabenbelohnungen."
  },
  "iex-dj": {
    "en": "Tested the TouchDesigner–OptiTrack interface for DJESTHESIA, selected for SIGGRAPH Real-Time Live! 2025, helping deliver a failure-free live demo.",
    "es": "Probé la interfaz TouchDesigner–OptiTrack de DJESTHESIA, seleccionado para SIGGRAPH Real-Time Live! 2025, y contribuí a una demostración en vivo sin fallos.",
    "de": "Die TouchDesigner–OptiTrack-Schnittstelle von DJESTHESIA getestet, ausgewählt für SIGGRAPH Real-Time Live! 2025, und zu einer fehlerfreien Live-Demo beigetragen."
  },
  "iex-tiago": {
    "en": "Enabling VR telepresence and imitation learning through a TIAGo digital twin with real-time Unity–ROS TCP integration.",
    "es": "Desarrollo telepresencia en VR y aprendizaje por imitación mediante un gemelo digital de TIAGo con integración Unity–ROS TCP en tiempo real.",
    "de": "VR-Telepräsenz und Imitationslernen durch einen digitalen TIAGo-Zwilling mit Unity–ROS-TCP-Integration in Echtzeit ermöglichen."
  },
  "robo-link": {
    "en": "ROBOPRENEUR case study",
    "es": "Caso de ROBOPRENEUR",
    "de": "ROBOPRENEUR-Projekt"
  },
  "dj-link": {
    "en": "DJESTHESIA demo",
    "es": "Demo de DJESTHESIA",
    "de": "DJESTHESIA-Demo"
  },
  "ie-date": {
    "en": "Jun — Jul 2025",
    "es": "Jun — Jul 2025",
    "de": "Juni — Juli 2025"
  },
  "ie-place": {
    "en": "Madrid, Spain",
    "es": "Madrid, España",
    "de": "Madrid, Spanien"
  },
  "ie-role": {
    "en": "Project Manager (112&114)",
    "es": "Project Manager (112&114)",
    "de": "Project Manager (112&114)"
  },
  "ie-point": {
    "en": "Restructured internal learning assets across IE departments, using AI agents and Microsoft Copilot workflows in place of manual editing.",
    "es": "Reorganicé recursos internos de aprendizaje entre departamentos de IE, utilizando agentes de IA y flujos de Microsoft Copilot en lugar de edición manual.",
    "de": "Interne Lernmaterialien über mehrere IE-Abteilungen hinweg neu strukturiert und manuelle Bearbeitung durch KI-Agenten und Microsoft-Copilot-Workflows ersetzt."
  },
  "top-date": {
    "en": "Apr 2021 — Apr 2024",
    "es": "Abr 2021 — Abr 2024",
    "de": "Apr. 2021 — Apr. 2024"
  },
  "top-place": {
    "en": "Bogotá, Colombia",
    "es": "Bogotá, Colombia",
    "de": "Bogotá, Kolumbien"
  },
  "top-role": {
    "en": "Software Engineer",
    "es": "Ingeniera de software",
    "de": "Softwareentwicklerin"
  },
  "top-users": {
    "en": "Supported 11,000+ active users by translating requirements into web improvements and managing deployments for topliving.com.co.",
    "es": "Di soporte a más de 11.000 usuarios activos, convirtiendo requisitos en mejoras web y gestionando despliegues de topliving.com.co.",
    "de": "Mehr als 11.000 aktive Nutzer unterstützt: Anforderungen in Website-Verbesserungen umgesetzt und Deployments für topliving.com.co betreut."
  },
  "top-search": {
    "en": "Built map-based property search, REST/Domus API integrations and automated lead routing; designed layouts in Adobe XD and implemented a consistent visual identity.",
    "es": "Desarrollé búsqueda de propiedades en mapa, integraciones REST/Domus API y distribución automática de contactos; diseñé interfaces en Adobe XD e implementé una identidad visual consistente.",
    "de": "Immobiliensuche mit Kartenfiltern, REST-/Domus-API-Integrationen und automatische Weiterleitung von Anfragen entwickelt; Layouts in Adobe XD gestaltet und eine einheitliche visuelle Identität umgesetzt."
  },
  "top-case": {
    "en": "Explore the project",
    "es": "Ver el proyecto",
    "de": "Projekt ansehen"
  },
  "top-live": {
    "en": "Live website",
    "es": "Sitio en vivo",
    "de": "Website öffnen"
  },
  "education-heading": {
    "en": "Education",
    "es": "Educación",
    "de": "Ausbildung"
  },
  "education-sub": {
    "en": "Learning the principles. Applying them in the lab.",
    "es": "Aprender los principios. Aplicarlos en el laboratorio.",
    "de": "Grundlagen lernen. Im Labor anwenden."
  },
  "degree-date": {
    "en": "Expected July 2028",
    "es": "Finalización prevista: julio de 2028",
    "de": "Voraussichtlicher Abschluss: Juli 2028"
  },
  "degree": {
    "en": "B.S. in Computer Science & Artificial Intelligence",
    "es": "B.S. in Computer Science & Artificial Intelligence",
    "de": "B.S. in Computer Science & Artificial Intelligence"
  },
  "scholarship": {
    "en": "IE High Potential Scholarship",
    "es": "Beca IE High Potential",
    "de": "IE High Potential Scholarship"
  },
  "coursework": {
    "en": "Coursework: Algorithms & Data Structures, Machine Learning, Computer Vision, Natural Language Processing, Reinforcement Learning, Robotics and Cloud Computing.",
    "es": "Asignaturas: algoritmos y estructuras de datos, aprendizaje automático, visión por computador, procesamiento del lenguaje natural, aprendizaje por refuerzo, robótica y computación en la nube.",
    "de": "Kurse: Algorithmen und Datenstrukturen, Machine Learning, Computer Vision, natürliche Sprachverarbeitung, Reinforcement Learning, Robotik und Cloud Computing."
  },
  "prior": {
    "en": "Computer Science studies; later transferred to IE University.",
    "es": "Estudios de Computer Science; posteriormente me trasladé a IE University.",
    "de": "Informatikstudium; späterer Wechsel zur IE University."
  },
  "prior-label": {
    "en": "Prior study",
    "es": "Estudios previos",
    "de": "Früheres Studium"
  },
  "prior-coursework": {
    "en": "Relevant coursework: programming, mathematics, digital logic, electronics and information technology, and computers and society.",
    "es": "Asignaturas relevantes: programación, matemáticas, lógica digital, electrónica y tecnología de la información, e informática y sociedad.",
    "de": "Relevante Lehrveranstaltungen: Programmierung, Mathematik, digitale Logik, Elektronik und Informationstechnologie sowie Computer und Gesellschaft."
  },
  "prior-handbook": {
    "en": "Programme handbook",
    "es": "Manual del programa",
    "de": "Studienhandbuch"
  },
  "cng-date": {
    "en": "Graduated June 2022",
    "es": "Graduación: junio de 2022",
    "de": "Abschluss: Juni 2022"
  },
  "cng-curriculum": {
    "en": "U.S. High School Diploma and Colombian Bachillerato program, with Advanced Placement offerings and a Mind, Body & Character focus on academics, athletics, leadership and service.",
    "es": "Programa de U.S. High School Diploma y Bachillerato colombiano, con oferta de Advanced Placement y un enfoque Mind, Body & Character en formación académica, deporte, liderazgo y servicio.",
    "de": "US-High-School-Diploma- und kolumbianisches Bachillerato-Programm mit Advanced-Placement-Angeboten und einem Mind, Body & Character-Fokus auf akademische Bildung, Sport, Führung und soziales Engagement."
  },
  "skills-heading": {
    "en": "Skills",
    "es": "Habilidades",
    "de": "Kenntnisse"
  },
  "skills-sub": {
    "en": "Languages, systems and tools I bring to the work.",
    "es": "Lenguajes, sistemas y herramientas con los que trabajo.",
    "de": "Sprachen, Systeme und Werkzeuge für meine Arbeit."
  },
  "skill-languages": {
    "en": "Languages",
    "es": "Lenguajes",
    "de": "Programmiersprachen"
  },
  "skill-ai": {
    "en": "AI & data",
    "es": "IA y datos",
    "de": "KI & Daten"
  },
  "skill-web": {
    "en": "Web & 3D",
    "es": "Web y 3D",
    "de": "Web & 3D"
  },
  "skill-tools": {
    "en": "Tools & platforms",
    "es": "Herramientas y plataformas",
    "de": "Tools & Plattformen"
  },
  "skill-spoken": {
    "en": "Spoken languages",
    "es": "Idiomas",
    "de": "Gesprochene Sprachen"
  },
  "spoken": {
    "en": "Spanish (native) · English (fluent) · German (beginner)",
    "es": "Español (nativo) · Inglés (fluido) · Alemán (principiante)",
    "de": "Spanisch (Muttersprache) · Englisch (fließend) · Deutsch (Anfängerin)"
  },
  "skill-other": {
    "en": "Also",
    "es": "También",
    "de": "Außerdem"
  },
  "other": {
    "en": "UI/UX design · Prototyping · Video editing · Technical writing",
    "es": "Diseño UI/UX · Prototipado · Edición de vídeo · Escritura técnica",
    "de": "UI/UX-Design · Prototyping · Videoschnitt · Technisches Schreiben"
  },
  "community-heading": {
    "en": "Leadership",
    "es": "Liderazgo",
    "de": "Engagement"
  },
  "community-sub": {
    "en": "Building with people, on campus and beyond.",
    "es": "Crear con otras personas, dentro y fuera del campus.",
    "de": "Gemeinsam gestalten, auf dem Campus und darüber hinaus."
  },
  "campus-date": {
    "en": "Aug 2023 — Aug 2026",
    "es": "Ago 2023 — Ago 2026",
    "de": "Aug. 2023 — Aug. 2026"
  },
  "campus-title": {
    "en": "Club Officer & BB Co-Captain",
    "es": "Club Officer & BB Co-Captain",
    "de": "Club Officer & BB Co-Captain"
  },
  "campus-copy": {
    "en": "Led student engagement across Basketball, Venezuela Club, Colombia Club and Art Club through events and campus programming.",
    "es": "Impulsé la participación estudiantil en Baloncesto, Venezuela Club, Colombia Club y Art Club mediante eventos y actividades universitarias.",
    "de": "Studentisches Engagement in Basketball, Venezuela Club, Colombia Club und Art Club durch Veranstaltungen und Campusprogramme gefördert."
  },
  "hack-date": {
    "en": "Aug 2023 — Present",
    "es": "Ago 2023 — Actualidad",
    "de": "Aug. 2023 — Heute"
  },
  "hack-title": {
    "en": "Participant",
    "es": "Participante",
    "de": "Teilnehmerin"
  },
  "hack-copy": {
    "en": "Built applied AI and venture solutions through the Sustainability Datathon, Tech Venture Bootcamp and hackathon-style product challenges.",
    "es": "Desarrollé soluciones de IA aplicada y emprendimiento en el Sustainability Datathon, Tech Venture Bootcamp y retos de producto tipo hackathon.",
    "de": "Angewandte KI- und Venture-Lösungen im Sustainability Datathon, Tech Venture Bootcamp und bei Produkt-Challenges im Hackathon-Format entwickelt."
  },
  "estate-date": {
    "en": "Apr 2021 — Apr 2022",
    "es": "Abr 2021 — Abr 2022",
    "de": "Apr. 2021 — Apr. 2022"
  },
  "estate-title": {
    "en": "Real Estate Agent",
    "es": "Agente inmobiliaria",
    "de": "Immobilienmaklerin"
  },
  "estate-copy": {
    "en": "Managed 150 property listings and coordinated viewings to match clients, including the Dutch Embassy, with suitable homes.",
    "es": "Gestioné 150 anuncios de propiedades y coordiné visitas para encontrar viviendas adecuadas a clientes, incluida la Embajada de los Países Bajos.",
    "de": "150 Immobilienangebote verwaltet und Besichtigungen koordiniert, um Kunden, darunter die niederländische Botschaft, mit passenden Wohnungen zusammenzubringen."
  },
  "journey-heading": {
    "en": "Journey",
    "es": "Recorrido",
    "de": "Stationen"
  },
  "journey-sub": {
    "en": "The places that shaped how I learn, build and collaborate.",
    "es": "Los lugares que marcaron mi forma de aprender, crear y colaborar.",
    "de": "Die Orte, die mein Lernen, Arbeiten und Zusammenarbeiten geprägt haben."
  },
  "resume-link": {
    "en": "Request résumé",
    "es": "Solicitar currículum",
    "de": "Lebenslauf anfordern"
  },
  "packet-open": {
    "en": "View packet (PDF)",
    "es": "Ver certificados (PDF)",
    "de": "Sammlung öffnen (PDF)"
  }
};
  function localize(language) {
    var lang = ['en', 'es', 'de'].indexOf(language) >= 0 ? language : 'en';
    document.querySelectorAll('[data-experience-copy]').forEach(function (node) {
      var entry = copy[node.getAttribute('data-experience-copy')];
      if (entry && node.textContent !== entry[lang]) node.textContent = entry[lang];
    });
    var nav = document.querySelector('.am-section-nav');
    if (nav) nav.setAttribute('aria-label', { en: 'Experience sections', es: 'Secciones de experiencia', de: 'Abschnitte zur Erfahrung' }[lang]);
  }
  document.addEventListener('edge:lang', function (event) { localize(event.detail); });
  localize(document.documentElement.lang);
})();
