(function () {
  'use strict';
  const words = {
    en: {
      title: 'Photos', previous: 'Previous photo', next: 'Next photo',
      captions: ['Robotics workbench', 'Virtual reality', 'IE campus', 'Street photography'],
      alts: ['Andrea working on a small wheeled robot at a workbench', 'Andrea trying a VR headset with two controllers', 'Andrea beside the blue IE campus mascot', 'Andrea looking through a camera on a busy street'],
    },
    es: {
      title: 'Fotos', previous: 'Foto anterior', next: 'Foto siguiente',
      captions: ['Mesa de robótica', 'Realidad virtual', 'Campus de IE', 'Fotografía callejera'],
      alts: ['Andrea trabaja en un pequeño robot con ruedas', 'Andrea prueba un visor de realidad virtual con dos mandos', 'Andrea junto a la mascota azul de IE', 'Andrea mira por el visor de una cámara en una calle concurrida'],
    },
    de: {
      title: 'Fotos', previous: 'Vorheriges Foto', next: 'Nächstes Foto',
      captions: ['Robotik-Werkbank', 'Virtuelle Realität', 'IE-Campus', 'Straßenfotografie'],
      alts: ['Andrea arbeitet an einem kleinen Roboter auf Rädern', 'Andrea probiert ein VR-Headset mit zwei Controllern aus', 'Andrea neben dem blauen IE-Maskottchen', 'Andrea schaut auf einer belebten Straße durch eine Kamera'],
    },
  };
  function boot() {
    const gallery = document.querySelector('[data-contact-photos]');
    if (!gallery || gallery.hasAttribute('data-ready')) return;
    const title = gallery.querySelector('h2');
    const track = gallery.querySelector('[data-photo-track]');
    const slides = Array.from(track.querySelectorAll('figure'));
    const controls = gallery.querySelector('[data-photo-controls]');
    const prev = controls.querySelector('[data-photo-prev]');
    const next = controls.querySelector('[data-photo-next]');
    const caption = controls.querySelector('[data-photo-caption]');
    const count = controls.querySelector('[data-photo-count]');
    const strip = gallery.querySelector('[data-photo-choices]');
    const choices = slides.map((slide, i) => {
      const button = document.createElement('button');
      button.type = 'button';
      button.className = 'contact-photos__choice';
      button.setAttribute('aria-controls', track.id);
      const image = document.createElement('img');
      image.src = slide.querySelector('img').src;
      image.alt = '';
      image.loading = 'lazy';
      image.decoding = 'async';
      button.append(image);
      button.addEventListener('click', () => render(i));
      strip.append(button);
      return button;
    });
    let index = -1;
    let locale = '';

    function render(at) {
      if (at < 0 || at >= slides.length || at === index) return;
      index = at;
      const copy = words[locale];
      caption.textContent = copy.captions[index];
      count.textContent = (index + 1) + ' / ' + slides.length;
      prev.setAttribute('aria-disabled', String(index === 0));
      next.setAttribute('aria-disabled', String(index === slides.length - 1));
      slides.forEach((slide, i) => { slide.hidden = i !== index; });
      choices.forEach((button, i) => button.setAttribute('aria-pressed', String(i === index)));
    }
    function localize(lang) {
      lang = words[lang] ? lang : 'en';
      if (locale === lang) return;
      locale = lang;
      const copy = words[lang];
      title.textContent = copy.title;
      prev.setAttribute('aria-label', copy.previous);
      next.setAttribute('aria-label', copy.next);
      slides.forEach((slide, i) => {
        slide.querySelector('img').alt = copy.alts[i];
        slide.querySelector('figcaption').textContent = copy.captions[i];
        choices[i].setAttribute('aria-label', copy.captions[i]);
      });
      const current = Math.max(0, index);
      index = -1;
      render(current);
    }
    prev.addEventListener('click', () => render(index - 1));
    next.addEventListener('click', () => render(index + 1));
    let touchStart = null;
    track.addEventListener('touchstart', event => {
      touchStart = event.touches.length === 1 ? event.touches[0] : null;
    }, { passive: true });
    track.addEventListener('touchcancel', () => { touchStart = null; }, { passive: true });
    track.addEventListener('touchend', event => {
      const start = touchStart;
      touchStart = null;
      if (!start) return;
      const end = Array.from(event.changedTouches).find(touch => touch.identifier === start.identifier);
      if (!end) return;
      const x = end.clientX - start.clientX;
      const y = end.clientY - start.clientY;
      if (Math.abs(x) > 45 && Math.abs(x) > Math.abs(y) * 1.5) render(index + (x < 0 ? 1 : -1));
    }, { passive: true });
    gallery.addEventListener('keydown', event => {
      let target;
      if (event.key === 'ArrowLeft') target = index - 1;
      else if (event.key === 'ArrowRight') target = index + 1;
      else if (event.key === 'Home') target = 0;
      else if (event.key === 'End') target = slides.length - 1;
      else return;
      event.preventDefault();
      render(target);
      if (choices.includes(event.target)) choices[index].focus();
    });
    document.addEventListener('edge:lang', event => localize(event.detail));
    localize(document.documentElement.lang);
    gallery.setAttribute('data-ready', '');
    controls.hidden = false;
    strip.hidden = false;
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', boot);
  else boot();
})();
