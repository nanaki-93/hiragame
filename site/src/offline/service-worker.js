/* Generated with a production release. Never registered by the development shell. */
'use strict';
const RELEASE = __RELEASE__;
const ASSETS = __ASSETS__;
const ROUTES = __ROUTES__;
const PREFIX = 'hiragame-release-';
const CACHE = PREFIX + RELEASE;
const paths = new Map(ASSETS.map(asset => [asset.url, asset]));

function owned(url) { return url.origin === self.location.origin && url.pathname.startsWith('/hiragame/'); }
function requestKind(request) {
  const url = new URL(request.url);
  if (request.method !== 'GET' || !owned(url)) return 'network';
  if (request.mode === 'navigate' && ROUTES.includes(url.pathname.replace(/\/$/, '') || '/')) return 'navigation';
  if (paths.has(url.pathname)) return 'asset';
  return 'missing';
}
async function digest(bytes) {
  return [...new Uint8Array(await crypto.subtle.digest('SHA-256', bytes))].map(b => b.toString(16).padStart(2, '0')).join('');
}
async function installRelease(preserveExisting = false) {
  const cache = await caches.open(CACHE);
  try {
    for (const asset of ASSETS) {
      const url = new URL(asset.url, self.location.origin);
      if (!owned(url) || url.search || url.hash) throw new Error('Unowned precache asset');
      const response = await fetch(url.href, { cache: 'no-store', credentials: 'same-origin' });
      if (!response.ok || response.redirected || await digest(await response.clone().arrayBuffer()) !== asset.sha256) throw new Error('Incomplete release');
      await cache.put(asset.url, response);
    }
  } catch (error) {
    if (!preserveExisting) await caches.delete(CACHE);
    throw error;
  }
}
async function cachedResponse(request) {
  const kind = requestKind(request);
  if (kind === 'network') return fetch(request);
  if (kind === 'missing') return new Response('Not available in this release.', { status: 404 });
  const path = kind === 'navigation' ? '/hiragame/index.html' : new URL(request.url).pathname;
  return await (await caches.open(CACHE)).match(path) || new Response('This offline asset is unavailable. Reconnect and cache the app again.', { status: 503 });
}
async function cleanOldCaches() {
  for (const name of await caches.keys()) if (name.startsWith(PREFIX) && name !== CACHE) await caches.delete(name);
}

const votes = new Map();
async function safeClients() {
  const clients = (await self.clients.matchAll({ type: 'window', includeUncontrolled: true })).filter(c => owned(new URL(c.url)));
  const nonce = RELEASE + '-' + Date.now();
  const waiting = new Set(clients.map(c => c.id));
  let finish;
  const answer = new Promise(resolve => { finish = resolve; });
  const timer = setTimeout(() => finish(false), 2000);
  votes.set(nonce, { waiting, finish });
  clients.forEach(client => client.postMessage({ type: 'CHECK_SAFE', nonce }));
  if (!waiting.size) finish(true);
  const safe = await answer;
  clearTimeout(timer); votes.delete(nonce);
  const latest = (await self.clients.matchAll({ type: 'window', includeUncontrolled: true })).filter(c => owned(new URL(c.url)));
  const unchanged = latest.every(c => clients.some(old => old.id === c.id));
  return safe && unchanged;
}
self.addEventListener('install', event => event.waitUntil(installRelease()));
self.addEventListener('activate', event => event.waitUntil((async () => { await self.clients.claim(); await cleanOldCaches(); })()));
self.addEventListener('fetch', event => {
  if (requestKind(event.request) !== 'network') event.respondWith(cachedResponse(event.request));
});
self.addEventListener('message', event => {
  const data = event.data || {};
  if (data.type === 'SAFE') {
    const vote = votes.get(data.nonce);
    if (!vote || !vote.waiting.has(event.source?.id)) return;
    if (!data.safe) vote.finish(false);
    else { vote.waiting.delete(event.source.id); if (!vote.waiting.size) vote.finish(true); }
  }
  if (data.type === 'REPAIR_CACHE' && event.source && owned(new URL(event.source.url))) event.waitUntil((async () => {
    try { await installRelease(true); event.source.postMessage({ type: 'CACHE_READY', complete: true }); }
    catch (_) { event.source.postMessage({ type: 'CACHE_READY', complete: false }); }
  })());
  if (data.type === 'CACHE_STATUS' && event.source && owned(new URL(event.source.url))) event.waitUntil((async () => {
    const cache = await caches.open(CACHE);
    const present = await Promise.all(ASSETS.map(asset => cache.match(asset.url)));
    event.source.postMessage({ type: 'CACHE_READY', complete: present.every(Boolean) });
  })());
  if (data.type === 'CLEAR_ASSETS' && event.source && owned(new URL(event.source.url))) event.waitUntil((async () => {
    if (await safeClients()) {
      await self.registration.unregister();
      for (const key of await caches.keys()) if (key.startsWith(PREFIX)) await caches.delete(key);
      event.source.postMessage({ type: 'ASSETS_CLEARED' });
    } else event.source.postMessage({ type: 'UPDATE_DEFERRED' });
  })());
  if (data.type === 'APPLY_UPDATE' && event.source && owned(new URL(event.source.url))) event.waitUntil((async () => {
    if (await safeClients()) await self.skipWaiting();
    else event.source.postMessage({ type: 'UPDATE_DEFERRED' });
  })());
});
