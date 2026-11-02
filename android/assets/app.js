'use strict';
const $ = id => document.getElementById(id);
let paired = false;
let activeTab = 'touch';
let irAvailable = false;
let chain = Promise.resolve();
let queued = 0;
const mods = new Set();
let sensitivity = 0.65;
let keyboardPage = 'functions';
let discoveredServers = [];
let serverPage = 0;
let noticeTimer;
function status(message, error = false) {
  $('status').textContent = message;
  $('status').title = message;
  $('status').classList.toggle('error', error);
  clearTimeout(noticeTimer);
  const showNotice = error || (!paired && activeTab === 'touch');
  $('notice').textContent = message;
  $('notice').classList.toggle('error', error);
  $('notice').hidden = !showNotice;
  if (showNotice) noticeTimer = setTimeout(() => {$('notice').hidden = true;}, 5000);
}
function setPaired(value) {
  paired = value;
  if (!value && activeTab === 'keyboard') selectTab('touch');
  $('pairing').hidden = value || activeTab !== 'touch';
  $('touch').hidden = !value || activeTab !== 'touch';
  $('disconnect').hidden = !value;
  document.querySelector('[data-tab=keyboard]').disabled = !value;
  $('connection').textContent = value ? 'PC vinculado' : 'Sin vincular';
  $('connection').classList.toggle('online', value);
  $('lock-pc').hidden = !value;
  if (!value) {resetModifiers(); dx = dy = 0;}
}
const nativePending = new Map();
let nativeSequence = 0;
window.nativeReply = (id, result) => {
  const pending = nativePending.get(id);
  if (!pending) return;
  clearTimeout(pending.timer); nativePending.delete(id);
  if (result.error) pending.reject(new Error(result.error)); else pending.resolve(result);
};
async function api(path, data) {
  if (!window.MandoNative) throw new Error('El puente Android no está disponible.');
  const id = String(++nativeSequence);
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {nativePending.delete(id); reject(new Error('El PC no responde; vuelve a conectar.'));}, path === '/api/scan' ? 30000 : 8000);
    nativePending.set(id, {resolve, reject, timer});
    window.MandoNative.request(id, JSON.stringify({path, data}));
  });
}
window.mandoDisconnected = () => {setPaired(false); status('Desconectado al salir de la app.');};
window.mandoKeyboardError = message => {status(message, true);};
window.mandoImeChanged = visible => {
  document.body.classList.toggle('ime-open', Boolean(visible));
  const label = visible ? 'Ocultar teclado Android' : 'Abrir teclado Android';
  $('show-keyboard').textContent = label;
  $('control-keyboard').setAttribute('aria-label', label);
  $('control-keyboard').setAttribute('aria-expanded', String(Boolean(visible)));
  $('control-keyboard').title = label;
  $('ime-help').hidden = !visible || document.body.classList.contains('bulk-edit');
};
function selectKeyboardPage(page) {
  keyboardPage = page;
  document.body.classList.toggle('bulk-edit', page === 'text');
  document.querySelectorAll('[data-keyboard-page]').forEach(button => {
    button.setAttribute('aria-pressed', String(button.dataset.keyboardPage === page));
  });
  document.querySelectorAll('[data-keyboard-view]').forEach(view => {view.hidden = view.dataset.keyboardView !== page;});
  $('ime-help').hidden = !document.body.classList.contains('ime-open') || page === 'text';
}
document.querySelectorAll('[data-keyboard-page]').forEach(button => button.addEventListener('click', () => {
  selectKeyboardPage(button.dataset.keyboardPage);
  api('/api/keyboard', {visible: false}).catch(error => status(error.message, true));
}));
function renderServers() {
  const size = 2;
  const pages = Math.max(1, Math.ceil(discoveredServers.length / size));
  serverPage = Math.max(0, Math.min(serverPage, pages - 1));
  $('servers').replaceChildren();
  for (const server of discoveredServers.slice(serverPage * size, (serverPage + 1) * size)) {
    const button = document.createElement('button');
    button.type = 'button'; button.className = 'full';
    button.textContent = server.host + ' · Windows ' + server.version + (server.requiresPassword ? ' · clave' : '');
    button.addEventListener('click', () => {$('pin').value = server.host; status('Seleccionado. Pulsa Conectar.');});
    $('servers').appendChild(button);
  }
  $('server-pages').hidden = pages === 1;
  $('server-prev').disabled = serverPage === 0;
  $('server-next').disabled = serverPage === pages - 1;
  $('server-page-label').textContent = (serverPage + 1) + ' / ' + pages;
}
$('server-prev').addEventListener('click', () => {serverPage--; renderServers();});
$('server-next').addEventListener('click', () => {serverPage++; renderServers();});
function setSensitivity(value) {
  sensitivity = Math.max(0.15, Math.min(2.5, Number(value)));
  $('sensitivity').value = String(sensitivity);
  $('sensitivity-value').textContent = sensitivity.toFixed(2).replace('.', ',') + '×';
  dx = dy = 0;
}
function saveSettings(data) {return api('/api/settings', data).catch(error => status(error.message, true));}
$('sensitivity').addEventListener('input', event => setSensitivity(event.target.value));
$('sensitivity').addEventListener('change', () => saveSettings({sensitivity}));
document.querySelectorAll('[data-sensitivity]').forEach(button => button.addEventListener('click', () => {
  setSensitivity(button.dataset.sensitivity); saveSettings({sensitivity});
}));
async function openAndroidKeyboard() {
  if (keyboardPage === 'text') selectKeyboardPage('functions');
  resetModifiers(); flushMove(true);
  try {
    await api('/api/keyboard', {visible: true});
  } catch (error) {status(error.message, true);}
}
function toggleAndroidKeyboard() {
  if (document.body.classList.contains('ime-open')) api('/api/keyboard', {visible: false}).catch(error => status(error.message, true));
  else openAndroidKeyboard();
}
$('show-keyboard').addEventListener('click', toggleAndroidKeyboard);
$('control-keyboard').addEventListener('click', toggleAndroidKeyboard);
window.mandoLockChanged = ready => {
  $('lock-pc').setAttribute('aria-label', ready ? 'Bloquear PC' : 'Configurar bloqueo del PC');
  $('lock-pc').title = ready ? 'Bloquear PC' : 'Configurar bloqueo';
  $('lock-pc').classList.toggle('ready', ready);
};
$('lock-pc').addEventListener('click', async () => {
  try {
    await api('/api/keyboard', {visible: false});
    flushMove(true);
    const result = await api('/api/lock', {});
    status(result.needsSetup ? 'El bloqueo necesita instalar MandoLock en Windows.' : 'Solicitud de bloqueo enviada al PC.');
  } catch (error) {status(error.message, true);}
});
$('lock-setup').addEventListener('click', () => api('/api/lock-setup', {}).catch(error => status(error.message, true)));
api('/api/settings').then(settings => {
  setSensitivity(settings.sensitivity); window.mandoLockChanged(settings.lockHelperInstalled || false);
  setIrTabVisible(settings.irTabVisible !== false);
}).catch(error => status(error.message, true));
setInterval(() => {
  if (paired && !document.hidden) api('/api/status').then(result => {
    if (!result.paired) {setPaired(false); status('El servidor ha cerrado la conexión. Vuelve a conectar.', true);}
  }).catch(() => {});
}, 5000);
$('scan').addEventListener('click', async () => {
  $('scan').disabled = true; $('pair-button').disabled = true;
  $('servers').replaceChildren();
  $('server-pages').hidden = true;
  status('Buscando servidores Remote Mouse en la red Wi-Fi…');
  try {
    const result = await api('/api/scan', {});
    discoveredServers = result.servers; serverPage = 0; renderServers();
    status(result.servers.length ? 'Selecciona tu PC y pulsa Conectar.' : 'No se han encontrado servidores en este segmento. Comprueba que Remote Mouse está abierto o introduce la IP.');
  } catch (error) {status(error.message, true);}
  finally {$('scan').disabled = false; $('pair-button').disabled = false;}
});
function action(data) {
  if (!paired || queued >= 24) return Promise.reject(new Error('Espera a que termine el envío al PC'));
  queued++;
  const next = chain.then(() => {
    if (!paired) throw new Error('Vuelve a vincular el PC');
    return api('/api/action', data);
  });
  chain = next.catch(error => {status(error.message, true); setPaired(false);}).finally(() => queued--);
  return next;
}
function resetModifiers() {
  mods.clear();
  document.querySelectorAll('[data-mod]').forEach(b => b.setAttribute('aria-pressed', 'false'));
  $('modifier-hint').textContent = 'Activa modificadores y pulsa una tecla. Se aplican a la siguiente combinación.';
}
function key(name, explicitMods) {
  let selected = explicitMods || [...mods];
  if (['PLAY_PAUSE', 'PREVIOUS', 'NEXT', 'VOLUME_UP', 'VOLUME_DOWN', 'MUTE'].includes(name)) selected = [];
  resetModifiers();
  flushMove(true);
  return action({type: 'key', key: name, modifiers: selected});
}
$('pair-form').addEventListener('submit', async event => {
  event.preventDefault();
  $('pair-button').disabled = true;
  try {
    const connection = await api('/api/pair', {host: $('pin').value.trim()});

    setPaired(true);
    status('Conectado por Wi-Fi al servidor ' + connection.version + '. Los controles ya están disponibles.');
  } catch (error) {status(error.message, true);}
  finally {$('pair-button').disabled = false;}
});
function selectTab(tab) {
  activeTab = tab;
  $('notice').hidden = true;
  document.querySelectorAll('[data-tab]').forEach(button => {
    const active = button.dataset.tab === tab;
    button.classList.toggle('active', active);
    button.setAttribute('aria-pressed', String(active));
    $(button.dataset.tab).hidden = !active;
  });
  $('pairing').hidden = paired || tab !== 'touch';
  $('touch').hidden = !paired || tab !== 'touch';
  $('connection-help').textContent = tab === 'ir' ? 'LGTV1 · Infrarrojos' : 'Servidor oficial · Wi-Fi';
  if (tab === 'keyboard') openAndroidKeyboard();
  else api('/api/keyboard', {visible: false}).catch(() => {});
}
document.querySelectorAll('[data-tab]').forEach(button => button.addEventListener('click', () => selectTab(button.dataset.tab)));
function setIrTabVisible(visible) {
  document.querySelector('[data-tab=ir]').hidden = !visible;
  $('show-ir-tab').checked = visible;
  if (!visible && activeTab === 'ir') selectTab('settings');
}
$('show-ir-tab').addEventListener('change', async event => {
  const input = event.target;
  const requested = input.checked;
  input.disabled = true;
  try {
    const settings = await api('/api/settings', {irTabVisible:requested});
    setIrTabVisible(settings.irTabVisible);
  } catch (error) {input.checked = !requested; status(error.message, true);}
  finally {input.disabled = false;}
});
function setIrAvailable(available, message) {
  irAvailable = available;
  $('ir-availability').textContent = message;
  document.querySelectorAll('[data-ir]').forEach(button => {button.disabled = !available;});
}
setIrAvailable(false, 'Comprobando emisor infrarrojo…');
api('/api/ir/status').then(result => {
  setIrAvailable(Boolean(result.available), result.available ? 'Emisor listo' : 'El emisor infrarrojo no está disponible para este perfil.');
}).catch(error => setIrAvailable(false, error.message));
let irSending = false;
const irLabels = {POWER:'Encendido / apagado', VOLUME_UP:'Subir volumen', VOLUME_DOWN:'Bajar volumen', MUTE:'Silencio', EXIT:'Exit'};
document.querySelectorAll('[data-ir]').forEach(button => button.addEventListener('click', async () => {
  if (!irAvailable || irSending) return;
  irSending = true;
  try {
    await api('/api/ir/send', {command:button.dataset.ir});
    status(irLabels[button.dataset.ir] + ' · señal IR enviada.');
  } catch (error) {status(error.message, true);}
  finally {irSending = false;}
}));
for (let i = 1; i <= 12; i++) {
  const b = document.createElement('button');
  b.textContent = `F${i}`; b.dataset.key = `F${i}`;
  $('function-keys').appendChild(b);
}
document.querySelectorAll('[data-seek]').forEach(b => b.addEventListener('click', () => key(b.dataset.seek, []).catch(() => {})));
document.querySelectorAll('[data-key]').forEach(b => b.addEventListener('click', () => key(b.dataset.key).catch(() => {})));
document.querySelectorAll('[data-mod]').forEach(b => b.addEventListener('click', () => {
  const name = b.dataset.mod;
  if (mods.has(name)) mods.delete(name); else mods.add(name);
  b.setAttribute('aria-pressed', String(mods.has(name)));
  $('modifier-hint').textContent = mods.size ? `${[...mods].join(' + ')} + siguiente tecla` : 'Selecciona una combinación.';
}));
document.querySelectorAll('[data-shortcut]').forEach(b => b.addEventListener('click', () => {
  const [modifier, name] = b.dataset.shortcut.split(':');
  key(name, [modifier]).catch(() => {});
}));
$('send-letter').addEventListener('click', () => {
  const letter = $('letter').value.toUpperCase();
  if (!/^[A-Z0-9]$/.test(letter)) return status('Introduce una letra de A a Z o un número.', true);
  key(letter).then(() => status('Tecla enviada.')).catch(() => {});
});
$('text-form').addEventListener('submit', async event => {
  event.preventDefault();
  const text = $('text').value;
  if (!text) return;
  resetModifiers(); $('send-text').disabled = true;
  try {
    await action({type: 'text', text});
    if ($('text').value === text) $('text').value = '';
    status('Texto enviado.');
  } catch (_) { /* Keep the text for a deliberate retry. */ }
  finally {$('send-text').disabled = false;}
});
$('disconnect').addEventListener('click', async () => {
  try {await chain; await api('/api/logout', {}); setPaired(false); status('Conexión con el PC cerrada.');}
  catch (error) {status(error.message, true);}
});
// Movement is coalesced, not queued once per pointer event. No automatic retries.
let dx = 0, dy = 0, moving = 0;
function flushMove(force = false) {
  if ((moving && !force) || (!dx && !dy) || !paired) return;
  const x = Math.max(-500, Math.min(500, Math.trunc(dx)));
  const y = Math.max(-500, Math.min(500, Math.trunc(dy)));
  if (!x && !y) return;
  dx -= x; dy -= y; moving++;
  action({type: 'move', dx: x, dy: y}).catch(() => {}).finally(() => {moving--;});
}
setInterval(flushMove, 20);
document.querySelectorAll('[data-click]').forEach(b => b.addEventListener('click', () => {
  flushMove(true); action({type: 'click', button: b.dataset.click}).catch(() => {});
}));
document.querySelectorAll('[data-scroll]').forEach(b => b.addEventListener('click', () => action({type: 'scroll', steps: Number(b.dataset.scroll)}).catch(() => {})));
const pointers = new Map();
let multi = false, distance = 0, scroll = 0, startTime = 0;
const pad = $('touchpad');
pad.addEventListener('pointerdown', event => {
  if (event.pointerType === 'mouse' && event.button !== 0) return;
  event.preventDefault(); pad.setPointerCapture(event.pointerId);
  if (!pointers.size) {multi = false; distance = 0; scroll = 0; startTime = Date.now();}
  pointers.set(event.pointerId, {x: event.clientX, y: event.clientY});
  if (pointers.size > 1) multi = true;
});
pad.addEventListener('pointermove', event => {
  const old = pointers.get(event.pointerId);
  if (!old) return;
  const x = event.clientX - old.x, y = event.clientY - old.y;
  distance += Math.abs(x) + Math.abs(y);
  pointers.set(event.pointerId, {x: event.clientX, y: event.clientY});
  if (pointers.size > 1) {
    scroll += y / pointers.size;
    const steps = Math.trunc(scroll / 20);
    if (steps) {scroll -= steps * 20; action({type: 'scroll', steps: Math.max(-10, Math.min(10, -steps))}).catch(() => {});}
  } else if (!multi) {dx += x * sensitivity; dy += y * sensitivity;}
});
function endPointer(event) {
  if (!pointers.has(event.pointerId)) return;
  pointers.delete(event.pointerId);
  if (!pointers.size) {
    flushMove(true);
    if (event.type === 'pointerup' && !multi && distance < 8 && Date.now() - startTime < 500) action({type: 'click', button: 'left'}).catch(() => {});
  }
}
['pointerup','pointercancel','lostpointercapture'].forEach(name => pad.addEventListener(name, endPointer));
document.addEventListener('visibilitychange', () => {pointers.clear(); dx = dy = 0; resetModifiers();});
api('/api/status').then(result => {
  setPaired(result.paired);
  window.mandoImeChanged(result.imeVisible || false);
  if (result.dryRun) status('SIMULACIÓN: puedes probar la interfaz; no se controla ningún ordenador.');
}).catch(error => status(error.message, true));
