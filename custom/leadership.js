/* Progressive photo viewer. Links continue to open full images without JS. */
(function () {
  'use strict';

  const words = {
    en: {
"anthropicCertificateTitle":"Claude 101 · Anthropic Academy","anthropicCertificateCaption":"First page of the 19-page CV-provided packet. No issue dates or verification IDs are shown.","anthropicCertificateAlt":"Claude 101 certificate naming Andrea Montana, from the CV-provided Anthropic Academy packet.",
"googleCertificateTitle":"Google AI","googleCertificateCaption":"Google · Specialization · September 2026. Use the credential link for the public completion record.","googleCertificateAlt":"Google AI course completion certificate for Andrea Isabel Montaña.","neuralCertificateTitle":"Neural Networks and Deep Learning","neuralCertificateCaption":"DeepLearning.AI · Course certificate · June 2026. Use the credential link for the public completion record.","neuralCertificateAlt":"Neural Networks and Deep Learning course certificate for Andrea Isabel Montaña.","awsCertificateTitle":"AWS Educate Getting Started with Serverless","awsCertificateCaption":"Training badge artwork. The public Credly record identifies Andrea Montana as the recipient.","awsCertificateAlt":"AWS Educate Getting Started with Serverless training badge artwork.",
      heading: 'Leadership',
      intro: 'Building with people, on campus and beyond.',
      galleryHeading: 'Community in focus',
      galleryHint: 'Select a photo to see the full image.',
      basketballTitle: 'IE basketball',
      basketballCaption: 'A team moment on the court.',
      basketballAlt: 'Basketball team and coach posing together on an indoor court.',
      classroomTitle: 'Tech Venture Bootcamp',
      classroomCaption: 'A classroom gathering with the bootcamp community.',
      classroomAlt: 'Group photo in a classroom beneath Tech Venture Bootcamp displays.',
      stageTitle: 'Tech Venture Bootcamp at IE',
      stageCaption: 'A group photo with the bootcamp community at IE University.',
      stageAlt: 'Participants posing on stage in front of IE Tech Venture Bootcamp screens.',
      labTitle: 'IEX Labs / CyPhy Life',
      labCaption: 'Hands-on work with a robotic arm.',
      labAlt: 'Two people working with a robotic arm at a laboratory table.',
      datathonTitle: 'Ryanair datathon at IE',
      datathonCaption: 'A team moment from an applied data challenge.',
      datathonAlt: 'Five people posing with an IE University and Ryanair event board.',
      vrTitle: 'Meta VR Day at IE',
      vrCaption: 'Exploring virtual reality with a headset and controllers.',
      vrAlt: 'A participant using a VR headset and controllers behind an IE University lectern.',
      googleTitle: 'Google Developers Community Leads Summit',
      googleCaption: 'A gathering with the developer community.',
      googleAlt: 'Four attendees in front of a Google Developers Community Leads Summit backdrop.',
      resourcesHeading: 'Certificates & training',
      resourcesCopy: 'Explore the learning behind the work.',
      resourcesLink: 'View certificates',
      viewerEyebrow: 'Photos & certificates',
      close: 'Close',
      openPhoto: 'View full image: '
    },
    es: {
"anthropicCertificateTitle":"Claude 101 · Anthropic Academy","anthropicCertificateCaption":"Primera página del documento de 19 páginas aportado con el CV. No muestra fechas ni identificadores de verificación.","anthropicCertificateAlt":"Certificado de Claude 101 a nombre de Andrea Montana, del documento de Anthropic Academy aportado con el CV.",
"googleCertificateTitle":"Google AI","googleCertificateCaption":"Google · Especialización · Septiembre de 2026. El enlace de la credencial abre el registro público.","googleCertificateAlt":"Certificado de finalización de Google AI para Andrea Isabel Montaña.","neuralCertificateTitle":"Neural Networks and Deep Learning","neuralCertificateCaption":"DeepLearning.AI · Certificado de curso · Junio de 2026. El enlace de la credencial abre el registro público.","neuralCertificateAlt":"Certificado de Neural Networks and Deep Learning para Andrea Isabel Montaña.","awsCertificateTitle":"AWS Educate Getting Started with Serverless","awsCertificateCaption":"Imagen de la insignia de formación. El registro público de Credly identifica a Andrea Montana como destinataria.","awsCertificateAlt":"Insignia de formación AWS Educate Getting Started with Serverless.",
      heading: 'Liderazgo',
      intro: 'Construyendo con otras personas, dentro y fuera del campus.',
      galleryHeading: 'La comunidad en imágenes',
      galleryHint: 'Selecciona una foto para verla completa.',
      basketballTitle: 'Baloncesto en IE',
      basketballCaption: 'Un momento de equipo en la cancha.',
      basketballAlt: 'Equipo de baloncesto y entrenador posando juntos en una cancha cubierta.',
      classroomTitle: 'Tech Venture Bootcamp',
      classroomCaption: 'Un encuentro en el aula con la comunidad del bootcamp.',
      classroomAlt: 'Foto de grupo en un aula bajo pantallas del Tech Venture Bootcamp.',
      stageTitle: 'Tech Venture Bootcamp en IE',
      stageCaption: 'Foto de grupo con la comunidad del bootcamp en IE University.',
      stageAlt: 'Participantes posando en un escenario frente a pantallas del Tech Venture Bootcamp de IE.',
      labTitle: 'IEX Labs / CyPhy Life',
      labCaption: 'Trabajo práctico con un brazo robótico.',
      labAlt: 'Dos personas trabajando con un brazo robótico en una mesa de laboratorio.',
      datathonTitle: 'Datathon de Ryanair en IE',
      datathonCaption: 'Un momento de equipo en un reto de datos aplicados.',
      datathonAlt: 'Cinco personas posando con un cartel de evento de IE University y Ryanair.',
      vrTitle: 'Meta VR Day en IE',
      vrCaption: 'Explorando la realidad virtual con un visor y mandos.',
      vrAlt: 'Una participante usando un visor de realidad virtual y mandos detrás de un atril de IE University.',
      googleTitle: 'Google Developers Community Leads Summit',
      googleCaption: 'Un encuentro con la comunidad de desarrolladores.',
      googleAlt: 'Cuatro asistentes frente a un fondo del Google Developers Community Leads Summit.',
      resourcesHeading: 'Certificados y formación',
      resourcesCopy: 'Descubre la formación detrás del trabajo.',
      resourcesLink: 'Ver certificados',
      viewerEyebrow: 'Fotos y certificados',
      close: 'Cerrar',
      openPhoto: 'Ver imagen completa: '
    },
    de: {
"anthropicCertificateTitle":"Claude 101 · Anthropic Academy","anthropicCertificateCaption":"Erste Seite der 19-seitigen Sammlung aus dem Lebenslauf. Ausstellungsdaten oder Verifizierungsnummern werden nicht angezeigt.","anthropicCertificateAlt":"Claude-101-Zertifikat für Andrea Montana aus der Anthropic-Academy-Sammlung des Lebenslaufs.",
"googleCertificateTitle":"Google AI","googleCertificateCaption":"Google · Spezialisierung · September 2026. Der Nachweislink öffnet den öffentlichen Abschlussnachweis.","googleCertificateAlt":"Google-AI-Abschlusszertifikat für Andrea Isabel Montaña.","neuralCertificateTitle":"Neural Networks and Deep Learning","neuralCertificateCaption":"DeepLearning.AI · Kurszertifikat · Juni 2026. Der Nachweislink öffnet den öffentlichen Abschlussnachweis.","neuralCertificateAlt":"Neural-Networks-and-Deep-Learning-Zertifikat für Andrea Isabel Montaña.","awsCertificateTitle":"AWS Educate Getting Started with Serverless","awsCertificateCaption":"Grafik des Schulungsabzeichens. Der öffentliche Credly-Nachweis nennt Andrea Montana als Empfängerin.","awsCertificateAlt":"AWS-Educate-Schulungsabzeichen Getting Started with Serverless.",
      heading: 'Engagement & Führung',
      intro: 'Gemeinsam mit anderen gestalten, auf dem Campus und darüber hinaus.',
      galleryHeading: 'Gemeinschaft in Bildern',
      galleryHint: 'Ein Foto auswählen, um das vollständige Bild zu sehen.',
      basketballTitle: 'Basketball an der IE',
      basketballCaption: 'Ein Teammoment auf dem Spielfeld.',
      basketballAlt: 'Basketballteam und Trainer posieren gemeinsam in einer Sporthalle.',
      classroomTitle: 'Tech Venture Bootcamp',
      classroomCaption: 'Ein Treffen mit der Bootcamp-Gemeinschaft im Unterrichtsraum.',
      classroomAlt: 'Gruppenfoto in einem Unterrichtsraum unter Bildschirmen des Tech Venture Bootcamps.',
      stageTitle: 'Tech Venture Bootcamp an der IE',
      stageCaption: 'Ein Gruppenfoto mit der Bootcamp-Gemeinschaft an der IE University.',
      stageAlt: 'Teilnehmende posieren auf einer Bühne vor Bildschirmen des IE Tech Venture Bootcamps.',
      labTitle: 'IEX Labs / CyPhy Life',
      labCaption: 'Praktische Arbeit mit einem Roboterarm.',
      labAlt: 'Zwei Personen arbeiten an einem Labortisch mit einem Roboterarm.',
      datathonTitle: 'Ryanair-Datathon an der IE',
      datathonCaption: 'Ein Teammoment bei einer praxisbezogenen Daten-Challenge.',
      datathonAlt: 'Fünf Personen posieren mit einem Veranstaltungsschild der IE University und Ryanair.',
      vrTitle: 'Meta VR Day an der IE',
      vrCaption: 'Virtuelle Realität mit einem Headset und Controllern erkunden.',
      vrAlt: 'Eine Teilnehmerin nutzt ein VR-Headset und Controller hinter einem Rednerpult der IE University.',
      googleTitle: 'Google Developers Community Leads Summit',
      googleCaption: 'Ein Treffen mit der Entwicklergemeinschaft.',
      googleAlt: 'Vier Teilnehmende vor einer Kulisse des Google Developers Community Leads Summit.',
      resourcesHeading: 'Zertifikate & Weiterbildung',
      resourcesCopy: 'Die Weiterbildung hinter der Arbeit entdecken.',
      resourcesLink: 'Zertifikate ansehen',
      viewerEyebrow: 'Fotos & Zertifikate',
      close: 'Schließen',
      openPhoto: 'Vollständiges Bild ansehen: '
    }
  };
  const links = Array.from(document.querySelectorAll('[data-leadership-photo]'));
  const dialog = document.getElementById('leadership-viewer');
  const canShowDialog = dialog && typeof dialog.showModal === 'function';
  let activeLink = null;
  let language = 'en';

  function renderViewer() {
    if (!activeLink || !dialog) return;
    const key = activeLink.getAttribute('data-leadership-photo');
    const text = words[language];
    const full = dialog.querySelector('[data-leadership-full]');
    full.alt = text[key + 'Alt'] || activeLink.querySelector('img').alt;
    dialog.querySelector('#leadership-viewer-title').textContent = text[key + 'Title'] || '';
    dialog.querySelector('#leadership-viewer-caption').textContent = text[key + 'Caption'] || '';
  }

  function localize(requestedLanguage) {
    const requested = typeof requestedLanguage === 'string' ? requestedLanguage : document.documentElement.lang;
    language = Object.prototype.hasOwnProperty.call(words, requested) ? requested : 'en';
    const text = words[language];
    document.querySelectorAll('[data-leadership-copy]').forEach(function (node) {
      const value = text[node.getAttribute('data-leadership-copy')];
      if (typeof value === 'string') node.textContent = value;
    });
    document.querySelectorAll('[data-leadership-alt]').forEach(function (node) {
      const value = text[node.getAttribute('data-leadership-alt')];
      if (typeof value === 'string') node.alt = value;
    });
    links.forEach(function (link) {
      const key = link.getAttribute('data-leadership-photo');
      const title = text[key + 'Title'];
      if (title) link.setAttribute('aria-label', text.openPhoto + title);
    });
    renderViewer();
  }

  function closeViewer() {
    if (dialog && dialog.open) dialog.close();
  }

  if (canShowDialog) {
    links.forEach(function (link) {
      link.setAttribute('aria-haspopup', 'dialog');
      link.setAttribute('aria-controls', 'leadership-viewer');
      link.addEventListener('click', function (event) {
        if (event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
        event.preventDefault();
        activeLink = link;
        const full = dialog.querySelector('[data-leadership-full]');
        full.src = link.href;
        renderViewer();
        dialog.showModal();
        document.documentElement.classList.add('am-leadership-viewing');
      });
    });
    dialog.querySelector('[data-leadership-close]').addEventListener('click', closeViewer);
    dialog.addEventListener('cancel', function (event) {
      event.preventDefault();
      closeViewer();
    });
    dialog.addEventListener('keydown', function (event) {
      // Preserve native Escape handling without opening or closing the site menu.
      if (event.key === 'Escape') event.stopPropagation();
      if (event.key === 'Tab') {
        const controls = Array.from(dialog.querySelectorAll('button, a[href], [tabindex="0"]'));
        const first = controls[0];
        const last = controls[controls.length - 1];
        if (event.shiftKey && document.activeElement === first) {
          event.preventDefault();
          last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
          event.preventDefault();
          first.focus();
        }
      }
    });
    dialog.addEventListener('click', function (event) {
      if (event.target !== dialog) return;
      const bounds = dialog.getBoundingClientRect();
      if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) closeViewer();
    });
    dialog.addEventListener('close', function () {
      document.documentElement.classList.remove('am-leadership-viewing');
      const returnTo = activeLink;
      activeLink = null;
      if (returnTo && returnTo.isConnected) returnTo.focus({ preventScroll: true });
    });
    window.addEventListener('pagehide', closeViewer);
  }
  document.addEventListener('edge:lang', function (event) { localize(event.detail); });
  localize(document.documentElement.lang);
})();
