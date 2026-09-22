import { Simulation } from './sim.js';

const copy = {
  en: {
    pageTitle: 'ROBOPRENEUR deployment planner', eyebrow: 'SIMULATED · 72 HOURS · SEED 7',
    title: 'Can the work pay for the power?', modelTag: 'Yard model',
    intro: 'Change demand to see how the robot’s wallet and battery evolve over three simulated days.',
    demand: 'Incoming jobs', lowDemand: 'Quiet yard · 0.2 / h', highDemand: 'Busy yard · 2.0 / h',
    rate: (value) => `${value} jobs / hour`, run: 'Run again ↗', loading: 'Loading the yard model.',
    jobs: 'Robot jobs completed', wallet: 'Final robot wallet', battery: 'Final battery', flat: 'Minutes with flat battery',
    results: 'Simulation results', walletAxis: 'ROBOT WALLET · ANDE', batteryAxis: 'BATTERY · %', hours: 'hours',
    chartTitle: 'Robot wallet and battery over 72 simulated hours',
    chartCaption: 'Both charts share the same time axis. Wallet in ANDE; battery in percent.',
    assumptions: 'What this model assumes',
    assumptionsBody: 'One robot, two human providers, a 60 × 40 m yard and a 4,320-minute run. The robot starts with 600 ANDE and 100% battery. Battery drain is 0.35 percentage points per minute; a charge is requested at 30% and costs at least 200 ANDE. The robot only accepts new work with at least 40% battery. Arrivals are random; the seed stays fixed at 7. Providers compete on price. ANDE is a closed accounting unit, not cash. This is a planning simulation, not a deployed robot or a live wallet.',
    metricsNote: 'Robot jobs exclude human work and charging. “Minutes with flat battery” counts sampled minutes at 0%. The model’s stalled counter also includes the first failed attempt to buy a charge. The charts retain every simulated minute.',
    config: 'Model configuration ↗', source: 'Source model ↗',
    outcome: (all, delta, stalled) => `${all} tasks completed across all providers, including charging. Robot wallet change: ${delta} ANDE. Model-reported stalled minutes: ${stalled}.`,
    complete: (jobs, wallet, battery, flat, rate) => `Simulation complete at ${rate} jobs per hour: ${jobs} robot jobs completed; final wallet ${wallet} ANDE; final battery ${battery}%; ${flat} minutes at zero battery.`,
    error: 'The model could not load. Reload this view or read the recorded reference run.', reference: 'Open reference run ↗',
  },
  es: {
    pageTitle: 'Planificador de despliegue ROBOPRENEUR', eyebrow: 'SIMULACIÓN · 72 HORAS · SEMILLA 7',
    title: '¿Puede el trabajo pagar la energía?', modelTag: 'Modelo del patio',
    intro: 'Cambia la demanda para ver cómo evolucionan el saldo y la batería del robot durante tres días simulados.',
    demand: 'Trabajos entrantes', lowDemand: 'Patio tranquilo · 0,2 / h', highDemand: 'Patio activo · 2,0 / h',
    rate: (value) => `${value} trabajos / hora`, run: 'Volver a simular ↗', loading: 'Cargando el modelo del patio.',
    jobs: 'Trabajos del robot completados', wallet: 'Saldo final del robot', battery: 'Batería final', flat: 'Minutos sin batería',
    results: 'Resultados de la simulación', walletAxis: 'SALDO DEL ROBOT · ANDE', batteryAxis: 'BATERÍA · %', hours: 'horas',
    chartTitle: 'Saldo y batería del robot durante 72 horas simuladas',
    chartCaption: 'Ambos gráficos comparten el eje temporal. Saldo en ANDE; batería en porcentaje.',
    assumptions: 'Supuestos del modelo',
    assumptionsBody: 'Un robot, dos proveedores humanos, un patio de 60 × 40 m y una simulación de 4.320 minutos. El robot empieza con 600 ANDE y la batería al 100 %. Consume 0,35 puntos porcentuales por minuto; solicita una recarga al 30 %, con un coste mínimo de 200 ANDE. Solo acepta trabajos nuevos con al menos un 40 % de batería. Las llegadas son aleatorias; la semilla permanece en 7. Los proveedores compiten por precio. ANDE es una unidad contable cerrada, no dinero real. Es una simulación de planificación, no un robot desplegado ni una cartera en vivo.',
    metricsNote: 'Los trabajos del robot excluyen el trabajo humano y las recargas. «Minutos sin batería» cuenta los minutos muestreados al 0 %. El contador de inactividad del modelo también incluye el primer intento fallido de pagar una recarga. Los gráficos conservan cada minuto simulado.',
    config: 'Configuración del modelo ↗', source: 'Modelo original ↗',
    outcome: (all, delta, stalled) => `${all} tareas completadas entre todos los proveedores, incluidas las recargas. Variación del saldo del robot: ${delta} ANDE. Minutos de inactividad según el modelo: ${stalled}.`,
    complete: (jobs, wallet, battery, flat, rate) => `Simulación completa con ${rate} trabajos por hora: ${jobs} trabajos del robot completados; saldo final de ${wallet} ANDE; batería final del ${battery} %; ${flat} minutos sin batería.`,
    error: 'No se pudo cargar el modelo. Recarga esta vista o consulta la simulación de referencia.', reference: 'Abrir simulación de referencia ↗',
  },
  de: {
    pageTitle: 'ROBOPRENEUR Einsatzplaner', eyebrow: 'SIMULATION · 72 STUNDEN · STARTWERT 7',
    title: 'Bezahlt die Arbeit den Strom?', modelTag: 'Hofmodell',
    intro: 'Ändere die Nachfrage und beobachte Guthaben und Akkustand des Roboters über drei simulierte Tage.',
    demand: 'Neue Aufträge', lowDemand: 'Ruhiger Hof · 0,2 / h', highDemand: 'Viel Betrieb · 2,0 / h',
    rate: (value) => `${value} Aufträge / Stunde`, run: 'Erneut simulieren ↗', loading: 'Das Hofmodell wird geladen.',
    jobs: 'Erledigte Roboteraufträge', wallet: 'Guthaben am Ende', battery: 'Akkustand am Ende', flat: 'Minuten mit leerem Akku',
    results: 'Simulationsergebnisse', walletAxis: 'ROBOTERGUTHABEN · ANDE', batteryAxis: 'AKKU · %', hours: 'Stunden',
    chartTitle: 'Roboterguthaben und Akkustand über 72 simulierte Stunden',
    chartCaption: 'Beide Diagramme haben dieselbe Zeitachse. Guthaben in ANDE; Akkustand in Prozent.',
    assumptions: 'Annahmen des Modells',
    assumptionsBody: 'Ein Roboter, zwei menschliche Anbieter, ein Hof von 60 × 40 m und 4.320 simulierte Minuten. Der Roboter startet mit 600 ANDE und 100 % Akku. Der Akku verliert 0,35 Prozentpunkte pro Minute; bei 30 % wird eine Ladung angefordert, die mindestens 200 ANDE kostet. Neue Arbeit wird erst ab 40 % Akku angenommen. Aufträge treffen zufällig ein; der Zufallsstartwert bleibt 7. Anbieter konkurrieren über den Preis. ANDE ist eine interne Recheneinheit, kein Bargeld. Dies ist eine Planungssimulation, kein eingesetzter Roboter und kein Live-Konto.',
    metricsNote: 'Roboteraufträge schließen menschliche Arbeit und Ladevorgänge aus. „Minuten mit leerem Akku“ zählt Minuten mit 0 %. Der Stillstandszähler des Modells erfasst auch den ersten gescheiterten Versuch, eine Ladung zu bezahlen. Die Diagramme enthalten jede simulierte Minute.',
    config: 'Modellkonfiguration ↗', source: 'Originalmodell ↗',
    outcome: (all, delta, stalled) => `${all} erledigte Aufgaben aller Anbieter, einschließlich Ladevorgängen. Änderung des Roboterguthabens: ${delta} ANDE. Stillstandsminuten laut Modell: ${stalled}.`,
    complete: (jobs, wallet, battery, flat, rate) => `Simulation mit ${rate} Aufträgen pro Stunde abgeschlossen: ${jobs} erledigte Roboteraufträge; Endguthaben ${wallet} ANDE; Akkustand ${battery} %; ${flat} Minuten mit leerem Akku.`,
    error: 'Das Modell konnte nicht geladen werden. Lade diese Ansicht neu oder öffne den gespeicherten Referenzlauf.', reference: 'Referenzlauf öffnen ↗',
  },
};

const requestedLanguage = new URLSearchParams(location.search).get('lang');
const language = Object.hasOwn(copy, requestedLanguage) ? requestedLanguage : 'en';
const t = copy[language];
const number = new Intl.NumberFormat(language, { maximumFractionDigits: 1 });
const signedNumber = new Intl.NumberFormat(language, { maximumFractionDigits: 1, signDisplay: 'exceptZero' });
const rateNumber = new Intl.NumberFormat(language, { minimumFractionDigits: 1, maximumFractionDigits: 1 });
const $ = (id) => document.getElementById(id);
document.documentElement.lang = language;
document.title = t.pageTitle;
document.querySelectorAll('[data-text]').forEach((node) => { node.textContent = t[node.dataset.text]; });
$('metrics').setAttribute('aria-label', t.results);
$('chart-title').textContent = t.chartTitle;

let config;
let simulation;
let lastWidth = 0;
const SVG = 'http://www.w3.org/2000/svg';
function element(name, attributes, text) {
  const node = document.createElementNS(SVG, name);
  Object.entries(attributes).forEach(([key, value]) => node.setAttribute(key, value));
  if (text !== undefined) node.textContent = text;
  return node;
}

function drawHistory() {
  if (!simulation) return;
  const svg = $('history');
  const width = Math.max(270, Math.round(svg.getBoundingClientRect().width));
  svg.setAttribute('viewBox', `0 0 ${width} 254`);
  svg.querySelector('[data-series]')?.remove();
  const group = element('g', { 'data-series': '', 'aria-hidden': 'true' });
  const left = width < 500 ? 45 : 56;
  const right = width - 12;
  const x = (hours) => left + (hours / 72) * (right - left);
  const snapshots = [{ minute: -1, robot_wealth: config.robots[0].wealth, mean_battery: config.robots[0].battery }, ...simulation.history];
  const largestWallet = Math.max(...snapshots.map((s) => s.robot_wealth));
  const walletMax = Math.max(1000, Math.ceil(largestWallet / 1000) * 1000);
  const panels = [
    { key: 'robot_wealth', title: t.walletAxis, titleY: 11, top: 24, bottom: 101, max: walletMax, ticks: [0, walletMax / 2, walletMax], line: 'wallet-line', titleClass: 'plot-title' },
    { key: 'mean_battery', title: t.batteryAxis, titleY: 133, top: 146, bottom: 220, max: 100, ticks: [0, 50, 100], line: 'battery-line', titleClass: 'plot-title battery-title' },
  ];
  for (const panel of panels) {
    const y = (value) => panel.bottom - (value / panel.max) * (panel.bottom - panel.top);
    group.append(element('text', { x: 0, y: panel.titleY, class: panel.titleClass }, panel.title));
    for (const tick of panel.ticks) {
      group.append(element('line', { x1: left, x2: right, y1: y(tick), y2: y(tick), class: 'grid-line' }));
      group.append(element('text', { x: left - 9, y: y(tick) + 4, 'text-anchor': 'end' }, number.format(tick)));
    }
    for (const hour of [0, 24, 48, 72]) {
      group.append(element('line', { x1: x(hour), x2: x(hour), y1: panel.top, y2: panel.bottom, class: 'grid-line' }));
    }
    if (panel.key === 'mean_battery') {
      group.append(element('line', { x1: left, x2: right, y1: y(30), y2: y(30), class: 'threshold-line' }));
    }
    const points = snapshots.map((s) => `${x((s.minute + 1) / 60).toFixed(2)},${y(s[panel.key]).toFixed(2)}`).join(' ');
    group.append(element('polyline', { points, class: panel.line }));
  }
  for (const hour of [0, 24, 48, 72]) {
    group.append(element('text', { x: x(hour), y: 242, 'text-anchor': hour === 72 ? 'end' : hour === 0 ? 'start' : 'middle' }, `${hour}${hour === 72 ? ` ${t.hours}` : ''}`));
  }
  svg.append(group);
}

function showRate() {
  const rate = rateNumber.format(Number($('demand').value));
  $('demand-value').value = t.rate(rate);
  $('demand').setAttribute('aria-valuetext', t.rate(rate));
}

function simulate() {
  if (!config) return;
  const rate = Number($('demand').value);
  simulation = new Simulation({ ...config, seed: 7, minutes: 4320, arrival_rate_per_hour: rate }).run();
  const final = simulation.history.at(-1);
  const robotIds = new Set(config.robots.map((robot) => robot.id));
  const jobs = simulation.tasks.filter((task) => task.status === 'done' && robotIds.has(task.executor)).length;
  const flat = simulation.history.filter((snapshot) => snapshot.mean_battery === 0).length;
  const delta = final.robot_wealth - config.robots.reduce((total, robot) => total + robot.wealth, 0);
  $('jobs').textContent = number.format(jobs);
  $('wallet').textContent = number.format(final.robot_wealth);
  $('battery').textContent = `${number.format(final.mean_battery)}%`;
  $('flat').textContent = number.format(flat);
  $('outcome').textContent = t.outcome(number.format(final.completed), signedNumber.format(delta), number.format(final.stalled));
  const description = t.complete(number.format(jobs), number.format(final.robot_wealth), number.format(final.mean_battery), number.format(flat), rateNumber.format(rate));
  $('status').textContent = description;
  $('chart-description').textContent = description;
  drawHistory();
}

function postHeight() {
  if (window.parent === window || location.origin === 'null') return;
  const height = Math.ceil(document.querySelector('.planner').getBoundingClientRect().height) + 2;
  window.parent.postMessage({ type: 'robopreneur-planner-height', height }, location.origin);
}

$('demand').addEventListener('input', showRate);
$('demand').addEventListener('change', simulate);
$('controls').addEventListener('submit', (event) => { event.preventDefault(); simulate(); });
const observer = new ResizeObserver(() => {
  const width = document.querySelector('.planner').clientWidth;
  if (width !== lastWidth) { lastWidth = width; drawHistory(); }
  postHeight();
});
observer.observe(document.querySelector('.planner'));
showRate();
$('run').disabled = true;

try {
  const response = await fetch('./andes-yard.json');
  if (!response.ok) throw new Error(`Model configuration: HTTP ${response.status}`);
  config = await response.json();
  simulate();
  $('run').disabled = false;
} catch (error) {
  $('status').className = 'error';
  $('status').textContent = t.error;
  const reference = document.createElement('a');
  reference.href = 'golden-run.json';
  reference.textContent = t.reference;
  $('status').append(document.createTextNode(' '), reference);
  $('demand').disabled = true;
  console.error('ROBOPRENEUR planner:', error);
}
postHeight();
