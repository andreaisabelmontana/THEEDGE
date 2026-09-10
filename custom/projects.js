/* single source of truth for the hall of ideas.

   the cards themselves stay as hand-written html in online/index.html so the
   css hover reveal and the frame svgs are untouched. this file describes those
   same cards as data, keyed to each card's data-slug, so the filter row and
   anything built later read one list instead of re-parsing the dom.

   `helm` is the NN in custom/helms/thumb-NN.webp + reveal-NN.webp.
   `tagline` and `label` are transcribed from the reveal card art, so the data
   and the picture never disagree.

   track is null where it is genuinely unknown: optitrack and notecrafted are
   video demos with no repo behind them, and guessing would put a wrong fact in
   the one file everything else trusts. set them and they join the type filter. */

window.EDGE_PROJECTS = [
  {
    slug: 'optitrack',
    title: 'OptiTrack',
    helm: '20',
    year: 2026,
    date: '2026',
    tagline: "Real-time motion capture with OptiTrack's optical tracking cameras.",
    label: 'MOCAP',
    track: 'Coursework',
    categories: ['Computer Vision', 'Real-time 3D'],
    techstack: ['OptiTrack', 'Motion capture'],
    links: { video: 'https://www.youtube.com/watch?v=2mIqTu32k6E' },
    featured: true,
  },
  {
    slug: 'notecrafted',
    title: 'NoteCrafted',
    helm: '21',
    year: 2025,
    date: '2025',
    tagline: 'An AI tutor that turns notes into practice questions and explanations.',
    label: 'AI TUTOR',
    track: 'Coursework',
    categories: ['AI & ML'],
    techstack: [],
    links: { video: 'https://www.youtube.com/watch?v=1FyiBZxcYio' },
    featured: true,
  },
  {
    slug: 'alma-de-maria',
    title: 'Alma de María',
    helm: '18',
    year: 2026,
    date: '2026',
    tagline: "The storefront for my family's handcrafted jewelry brand in Colombia.",
    label: 'STOREFRONT',
    track: 'Coursework',
    categories: ['Web', 'UI & UX'],
    techstack: ['HTML', 'JavaScript', 'Docker'],
    links: {
      live: 'https://andreaisabelmontana.github.io/Alma-De-Maria/index.html',
      github: 'https://github.com/andreaisabelmontana/Alma-De-Maria',
    },
    featured: true,
  },
  {
    slug: 'encore',
    title: 'Encore',
    helm: '03',
    year: 2026,
    date: '2026',
    tagline: 'A map of live music memory, pin the shows that mattered, relive the ones you missed.',
    label: 'LEAFLET / MAPS',
    track: 'Coursework',
    categories: ['Web', 'UI & UX'],
    techstack: ['JavaScript', 'Leaflet'],
    links: {
      live: 'https://andreaisabelmontana.github.io/Google-Maps-Awards-2025/',
      github: 'https://github.com/andreaisabelmontana/Google-Maps-Awards-2025',
    },
    featured: true,
  },
  {
    slug: 'ladybug-girl',
    title: 'Ladybug Girl',
    helm: '12',
    year: 2026,
    date: '2026',
    tagline: 'A Three.js storybook meadow with a real skeletal walk cycle.',
    label: 'THREE.JS',
    track: 'Personal',
    categories: ['Real-time 3D', 'Games'],
    techstack: ['Three.js', 'WebGL', 'JavaScript'],
    links: {
      live: 'https://andreaisabelmontana.github.io/ladybug-girl/',
      github: 'https://github.com/andreaisabelmontana/ladybug-girl',
    },
    featured: true,
  },
  {
    slug: 'polar-club',
    title: 'Polar Club',
    helm: '15',
    year: 2025,
    date: '2025',
    tagline: 'A playful login flow that ends on a 3D iceberg with a polar bear.',
    label: 'THREE.JS',
    track: 'Personal',
    categories: ['Real-time 3D', 'Web'],
    techstack: ['Three.js', 'HTML'],
    links: {
      live: 'https://andreaisabelmontana.github.io/polar-club/',
      github: 'https://github.com/andreaisabelmontana/polar-club',
    },
    featured: false,
  },
  {
    slug: 'the-shop',
    title: 'The Shop',
    helm: '22',
    year: 2025,
    date: '2025',
    tagline: 'A React + Vite storefront for a cloud-native marketplace, the SDDO capstone.',
    label: 'MARKETPLACE',
    track: 'Coursework',
    categories: ['Web', 'Cloud & DevOps'],
    techstack: ['React', 'Vite', 'JavaScript', 'Docker'],
    links: {
      live: 'https://andreaisabelmontana.github.io/Software-Development-And-Devops/',
      github: 'https://github.com/andreaisabelmontana/Software-Development-And-Devops',
    },
    featured: false,
  },
  {
    slug: 'battleship',
    title: 'Battleship',
    helm: '19',
    year: 2024,
    date: '2024',
    tagline: '10x10 Battleship against a probability-density AI, or pass-and-play.',
    label: 'C ENGINE',
    track: 'Coursework',
    categories: ['AI & ML', 'Games', 'Algorithms'],
    techstack: ['C', 'JavaScript'],
    links: {
      live: 'https://andreaisabelmontana.github.io/battleship/',
      github: 'https://github.com/andreaisabelmontana/battleship',
    },
    featured: false,
  },
];
