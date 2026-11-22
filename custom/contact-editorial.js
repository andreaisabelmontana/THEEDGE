(() => {
  const translations = {
    en: {
      eyebrow:'Contact', headline:'Let’s talk.', intro:'Have an internship, research question, or project in mind? I’d love to hear about it.',
      email:'Email me', availability:'Open to internships', start:'May 01', end:'Dec 30, 2027',
      degree:'Computer Science & AI · Class of 2028', agreement:'Internship agreement', eligible:'Eligible through IE University',
      based:'Based in', madrid:'Madrid, Spain', languages:'Languages', fluency:'Spanish (native) · English (fluent) · German (beginner)',
      videoKicker:'From the archives', videoTitle:'An early robotics demo.', videoIntro:'Before the software projects, there was a small robot following a marked course.',
      videoAlt:'Early robotics demo: a small robot follows a marked course', download:'Download the robotics video',
      portraitAlt:'Andrea Montaña at the Google Developers Community Leads Summit'
    },
    es: {
      eyebrow:'Contacto', headline:'Hablemos.', intro:'¿Tienes en mente una práctica, una pregunta de investigación o un proyecto? Escríbeme.',
      email:'Escríbeme', availability:'Disponible para prácticas', start:'01 may', end:'30 dic de 2027',
      degree:'Informática e IA · Promoción de 2028', agreement:'Convenio de prácticas', eligible:'Disponible a través de IE University',
      based:'En', madrid:'Madrid, España', languages:'Idiomas', fluency:'Español (nativo) · Inglés (fluido) · Alemán (básico)',
      videoKicker:'Del archivo', videoTitle:'Una de mis primeras demos de robótica.', videoIntro:'Antes de los proyectos de software, un pequeño robot seguía un recorrido marcado.',
      videoAlt:'Demostración temprana de robótica: un pequeño robot sigue un recorrido marcado', download:'Descargar el video de robótica',
      portraitAlt:'Andrea Montaña en el encuentro Google Developers Community Leads Summit'
    },
    de: {
      eyebrow:'Kontakt', headline:'Lass uns reden.', intro:'Geht es um ein Praktikum, eine Forschungsfrage oder ein Projekt? Ich freue mich auf deine Nachricht.',
      email:'E-Mail schreiben', availability:'Offen für Praktika', start:'01. Mai', end:'30. Dez. 2027',
      degree:'Informatik und KI · Abschlussjahrgang 2028', agreement:'Praktikumsvereinbarung', eligible:'Über die IE University möglich',
      based:'Standort', madrid:'Madrid, Spanien', languages:'Sprachen', fluency:'Spanisch (Muttersprache) · Englisch (fließend) · Deutsch (Grundkenntnisse)',
      videoKicker:'Aus dem Archiv', videoTitle:'Eine frühe Robotikdemo.', videoIntro:'Vor den Softwareprojekten folgte ein kleiner Roboter einer markierten Strecke.',
      videoAlt:'Frühe Robotikdemo: Ein kleiner Roboter folgt einer markierten Strecke', download:'Robotikvideo herunterladen',
      portraitAlt:'Andrea Montaña beim Google Developers Community Leads Summit'
    }
  };
  function localize(lang) {
    const copy = translations[lang] || translations.en;
    document.querySelectorAll('[data-contact-copy]').forEach(node => { node.textContent = copy[node.dataset.contactCopy]; });
    document.querySelectorAll('[data-contact-label]').forEach(node => { node.setAttribute('aria-label', copy[node.dataset.contactLabel]); });
    document.querySelectorAll('[data-contact-alt]').forEach(node => { node.alt = copy[node.dataset.contactAlt]; });
  }
  document.addEventListener('edge:lang', event => localize(event.detail));
  localize(document.documentElement.lang);
})();
