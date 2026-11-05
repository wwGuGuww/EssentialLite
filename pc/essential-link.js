// Essential Lite · PC tarafı. Telefondan gelen şifreli bağlantıyı karşılar, tarayıcıda arayüzü gösterir.
// Çalıştırmak için: node essential-link.js   (ek paket gerekmez)
'use strict';
const net = require('net');
const http = require('http');
const crypto = require('crypto');
const os = require('os');
const fs = require('fs');
const path = require('path');
const { execFile } = require('child_process');
const qr = require('./qr');

const HTTP_PORT = 7878;
const TCP_PORT = 7879;
const CYCLE_MS = 60000;
const STORE = path.join(os.homedir(), '.essential-link.json');
const DOWNLOADS = path.join(os.homedir(), 'Downloads');
const PC_NAME = (os.hostname() || 'PC').toUpperCase();

// ---------- Kalıcı eşleşmeler ----------
let store = { phones: [] };
try { store = JSON.parse(fs.readFileSync(STORE, 'utf8')); } catch (e) { /* ilk çalıştırma */ }
const saveStore = () => fs.writeFileSync(STORE, JSON.stringify(store));

// ---------- Yardımcılar ----------
const b64u = (b) => b.toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');
const fromB64u = (s) => Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
const confirmCode = (key) => {
  const h = crypto.createHash('sha256').update(Buffer.concat([key, Buffer.from('confirm')])).digest();
  return String(h.readUInt32BE(0) % 1000000).padStart(6, '0');
};
function lanIp() {
  let best = null;
  for (const list of Object.values(os.networkInterfaces())) for (const i of list || []) {
    if (i.family !== 'IPv4' || i.internal) continue;
    if (i.address.startsWith('192.168.') || i.address.startsWith('10.') || /^172\.(1[6-9]|2\d|3[01])\./.test(i.address)) return i.address;
    best = best || i.address;
  }
  return best || '127.0.0.1';
}

// ---------- Eşleştirme anahtarı (60 sn'de bir yenilenir) ----------
let cycle = 0, pairKey = crypto.randomBytes(32), prevKey = null, cycleAt = Date.now();
function rotate() {
  prevKey = pairKey; pairKey = crypto.randomBytes(32); cycle++; cycleAt = Date.now();
  broadcast({ type: 'qr', ...qrInfo() });
}
setInterval(rotate, CYCLE_MS);
function qrInfo() {
  const url = `el://${lanIp()}:${TCP_PORT}/${b64u(pairKey)}`;
  return { url, rows: qr.encode(url), left: Math.max(0, Math.ceil((cycleAt + CYCLE_MS - Date.now()) / 1000)), sid: 'EL-' + (4829 + cycle * 7) };
}

// ---------- Tarayıcı (SSE) ----------
const sse = new Set();
function broadcast(obj) {
  const line = 'data: ' + JSON.stringify(obj) + '\n\n';
  for (const r of sse) r.write(line);
}

// ---------- Telefon oturumu ----------
let session = null; // { sock, key, buf, paired, phone, files:Map }
let notifs = [];
let ping = 0;
let incoming = new Map();

function encrypt(key, obj) {
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-256-gcm', key, iv);
  const ct = Buffer.concat([c.update(Buffer.from(JSON.stringify(obj))), c.final()]);
  const body = Buffer.concat([iv, ct, c.getAuthTag()]);
  const head = Buffer.alloc(4); head.writeUInt32BE(body.length);
  return Buffer.concat([head, body]);
}
function decrypt(key, body) {
  const iv = body.subarray(0, 12), tag = body.subarray(body.length - 16), ct = body.subarray(12, body.length - 16);
  const d = crypto.createDecipheriv('aes-256-gcm', key, iv);
  d.setAuthTag(tag);
  return JSON.parse(Buffer.concat([d.update(ct), d.final()]).toString());
}
const send = (obj) => { if (session && session.sock && !session.sock.destroyed) session.sock.write(encrypt(session.key, obj)); };

function state() {
  return {
    type: 'state',
    status: !session ? 'wait' : session.paired ? 'live' : 'confirm',
    phone: session && session.phone,
    code: session && session.code,
    notifs,
    ping,
    pc: PC_NAME
  };
}

function onFrame(s, body) {
  let msg = null;
  if (!s.key) {
    const candidates = [{ k: pairKey, pair: true }];
    if (prevKey) candidates.push({ k: prevKey, pair: true });
    for (const p of store.phones) candidates.push({ k: fromB64u(p.key), pair: false });
    for (const c of candidates) {
      try { msg = decrypt(c.k, body); s.key = c.k; s.pairing = c.pair; break; } catch (e) { /* sıradaki */ }
    }
    if (!msg) { s.sock.destroy(); return; }
    if (session && session !== s) { try { session.sock.destroy(); } catch (e) { /* */ } }
    session = s;
    s.paired = !s.pairing;
    s.code = confirmCode(s.key);
  } else {
    try { msg = decrypt(s.key, body); } catch (e) { s.sock.destroy(); return; }
  }
  handle(s, msg);
}

function handle(s, m) {
  switch (m.t) {
    case 'hello':
      s.phone = { name: (m.name || 'TELEFON').toUpperCase(), bat: m.bat, net: m.net };
      send({ t: 'hello', pc: PC_NAME, os: 'WINDOWS ' + (os.release().startsWith('10.0.2') ? '11' : '10') });
      if (s.paired) send({ t: 'ok' });
      broadcast(state());
      break;
    case 'ok': // telefonda onaylandı
      if (s.pairing && !s.paired) {
        s.paired = true;
        store.phones = store.phones.filter((p) => p.key !== b64u(s.key));
        store.phones.push({ key: b64u(s.key), name: s.phone && s.phone.name });
        saveStore();
      }
      broadcast(state());
      break;
    case 'ping': send({ t: 'pong', ts: m.ts }); break;
    case 'pong': ping = Math.max(1, Date.now() - m.ts); broadcast({ type: 'ping', ping }); break;
    case 'bat': if (s.phone) { s.phone.bat = m.bat; s.phone.net = m.net; broadcast(state()); } break;
    case 'notif':
      notifs = notifs.filter((n) => n.id !== m.id);
      notifs.unshift({ id: m.id, app: m.app, who: m.who, text: m.text, time: m.time, canReply: !!m.canReply });
      notifs = notifs.slice(0, 30);
      broadcast(state());
      break;
    case 'notif-gone':
      notifs = notifs.filter((n) => n.id !== m.id);
      broadcast(state());
      break;
    case 'clip':
      copyToClipboard(m.text);
      broadcast({ type: 'clip', text: m.text, from: 'phone' });
      break;
    case 'fstart': {
      fs.mkdirSync(DOWNLOADS, { recursive: true });
      const safe = path.basename(m.name).replace(/[<>:"/\\|?*]/g, '_');
      let dest = path.join(DOWNLOADS, safe), n = 1;
      const ext = path.extname(safe), stem = path.basename(safe, ext);
      while (fs.existsSync(dest)) dest = path.join(DOWNLOADS, `${stem} (${n++})${ext}`);
      incoming.set(m.id, { fd: fs.openSync(dest, 'w'), dest, got: 0, size: m.size, name: safe });
      broadcast({ type: 'file', id: m.id, name: safe, dir: 'in', pct: 0 });
      break;
    }
    case 'fchunk': {
      const f = incoming.get(m.id); if (!f) break;
      const b = Buffer.from(m.d, 'base64');
      fs.writeSync(f.fd, b); f.got += b.length;
      broadcast({ type: 'file', id: m.id, name: f.name, dir: 'in', pct: Math.min(99, Math.round((f.got / Math.max(1, f.size)) * 100)) });
      break;
    }
    case 'fend': {
      const f = incoming.get(m.id); if (!f) break;
      fs.closeSync(f.fd); incoming.delete(m.id);
      broadcast({ type: 'file', id: m.id, name: f.name, dir: 'in', pct: 100, path: f.dest });
      break;
    }
    case 'frame': broadcast({ type: 'frame', d: m.d, w: m.w, h: m.h }); break;
    case 'control': broadcast({ type: 'control', on: !!m.on }); break;
  }
}

function copyToClipboard(text) {
  try {
    const p = execFile('clip.exe', [], () => {});
    p.stdin.end(Buffer.from(String(text), 'utf16le'));
  } catch (e) { /* clip yok */ }
}

const tcp = net.createServer((sock) => {
  sock.setNoDelay(true);
  const s = { sock, key: null, buf: Buffer.alloc(0), paired: false, phone: null };
  sock.on('data', (d) => {
    s.buf = Buffer.concat([s.buf, d]);
    while (s.buf.length >= 4) {
      const len = s.buf.readUInt32BE(0);
      if (len > 64 * 1024 * 1024 || len < 28) { sock.destroy(); return; }
      if (s.buf.length < 4 + len) break;
      const body = s.buf.subarray(4, 4 + len);
      s.buf = s.buf.subarray(4 + len);
      onFrame(s, body);
      if (sock.destroyed) return;
    }
  });
  const end = () => {
    if (session === s) { session = null; ping = 0; broadcast(state()); }
  };
  sock.on('close', end);
  sock.on('error', end);
});
tcp.listen(TCP_PORT, '0.0.0.0');

setInterval(() => { if (session && session.key && session.paired) send({ t: 'ping', ts: Date.now() }); }, 2500);

// ---------- Tarayıcı sunucusu (yalnızca bu PC) ----------
const PAGE = path.join(__dirname, 'page.html');
const server = http.createServer((req, res) => {
  const url = new URL(req.url, 'http://localhost');
  if (url.pathname === '/') {
    res.writeHead(200, { 'Content-Type': 'text/html; charset=utf-8', 'Cache-Control': 'no-store' });
    return res.end(fs.readFileSync(PAGE));
  }
  if (url.pathname === '/events') {
    res.writeHead(200, { 'Content-Type': 'text/event-stream', 'Cache-Control': 'no-store', Connection: 'keep-alive' });
    sse.add(res);
    req.on('close', () => sse.delete(res));
    res.write('data: ' + JSON.stringify({ type: 'qr', ...qrInfo() }) + '\n\n');
    res.write('data: ' + JSON.stringify(state()) + '\n\n');
    return;
  }
  if (req.method === 'POST' && url.pathname.startsWith('/api/')) {
    const chunks = [];
    req.on('data', (c) => chunks.push(c));
    req.on('end', () => {
      const raw = Buffer.concat(chunks);
      const act = url.pathname.slice(5);
      try {
        if (act === 'upload') return upload(url, raw, res);
        const j = raw.length ? JSON.parse(raw.toString()) : {};
        api(act, j);
        res.writeHead(200); res.end('ok');
      } catch (e) { res.writeHead(400); res.end(String(e)); }
    });
    return;
  }
  res.writeHead(404); res.end();
});

function api(act, j) {
  if (!session || !session.paired) return;
  switch (act) {
    case 'reply': send({ t: 'reply', id: j.id, text: j.text }); notifs = notifs.filter((n) => n.id !== j.id); broadcast(state()); break;
    case 'dismiss': send({ t: 'dismiss', id: j.id }); notifs = notifs.filter((n) => n.id !== j.id); broadcast(state()); break;
    case 'clip': send({ t: 'clip', text: j.text }); break;
    case 'touch': send({ t: 'touch', k: j.k, x: j.x, y: j.y, x2: j.x2, y2: j.y2, ms: j.ms }); break;
    case 'key': send({ t: 'key', k: j.k }); break;
    case 'text': send({ t: 'text', s: j.s }); break;
    case 'control': send({ t: 'control', on: !!j.on }); break;
    case 'disconnect': send({ t: 'bye' }); try { session.sock.end(); } catch (e) { /* */ } break;
  }
}

function upload(url, raw, res) {
  if (!session || !session.paired) { res.writeHead(409); return res.end('bağlı değil'); }
  const name = url.searchParams.get('name') || 'dosya';
  const id = crypto.randomBytes(4).toString('hex');
  send({ t: 'fstart', id, name, size: raw.length });
  const CH = 48 * 1024;
  for (let i = 0; i < raw.length; i += CH) send({ t: 'fchunk', id, d: raw.subarray(i, i + CH).toString('base64') });
  send({ t: 'fend', id });
  broadcast({ type: 'file', id, name, dir: 'out', pct: 100 });
  res.writeHead(200); res.end('ok');
}

server.listen(HTTP_PORT, '127.0.0.1', () => {
  const url = 'http://localhost:' + HTTP_PORT;
  console.log('\n  ESSENTIAL ● LITE · PC\n  Tarayıcıda aç: ' + url + '\n  Telefon bu ağda (' + lanIp() + ') olmalı.\n');
  if (!process.argv.includes('--no-open')) execFile('cmd', ['/c', 'start', '', url], () => {});
});
