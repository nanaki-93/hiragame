/* Isolated Cache/worker-policy tests in Node. No browser, server or running app. */
const assert = require('node:assert/strict');
const vm = require('node:vm');
const fs = require('node:fs');
const crypto = require('node:crypto');
const payloads = new Map([
  ['/hiragame/index.html', '<html>shell</html>'],
  ['/hiragame/content/catalog.json', '{"version":1}'],
  ['/hiragame/content/audio/one.mp3', 'recording fixture']
]);
const assets = [...payloads].map(([url, body]) => ({url, sha256: crypto.createHash('sha256').update(body).digest('hex')}));
const template = fs.readFileSync('site/src/offline/service-worker.js', 'utf8')
  .replace('__RELEASE__', '"test"').replace('__ASSETS__', JSON.stringify(assets))
  .replace('__ROUTES__', JSON.stringify(['/hiragame', '/hiragame/lesson', '/hiragame/review']));
function setup() {
  const stores = new Map(); const listeners = {}; let broken = null; let activated = 0; let unregistered = 0; let clients = [];
  const context = {
    URL, Response, Uint8Array, Map, Set, Date, crypto: crypto.webcrypto,
    setTimeout: fn => setTimeout(fn, 25), clearTimeout,
    fetch: async input => { const path = new URL(typeof input === 'string' ? input : input.url).pathname;
      if (path === broken) throw Error('offline');
      return new Response(payloads.get(path) || 'missing', {status: payloads.has(path) ? 200 : 404}); },
    caches: {
      async open(name) { if (!stores.has(name)) stores.set(name, new Map()); const cache = stores.get(name); return {
        async put(key, value) { cache.set(key, value.clone()); }, async match(key) { return cache.get(key)?.clone(); }
      }; }, async keys() { return [...stores.keys()]; }, async delete(name) { return stores.delete(name); }
    },
    self: { location: {origin: 'https://example.test'},
      addEventListener(name, fn) { listeners[name] = fn; }, async skipWaiting() { activated++; },
      registration: {async unregister() { unregistered++; }},
      clients: {async matchAll() { return clients; }, async claim() {}}
    }
  };
  vm.createContext(context); vm.runInContext(template, context);
  return {context, stores, listeners, run: code => vm.runInContext(code, context),
    fail: path => broken = path, setClients: value => clients = value,
    activated: () => activated, unregistered: () => unregistered};
}
function request(path, mode='same-origin') { return { url: 'https://example.test'+path, method:'GET', mode }; }
(async () => {
  const app = setup(); app.stores.set('unrelated-cache', new Map()); app.stores.set('hiragame-release-old', new Map());
  await app.run('installRelease()');
  assert.equal(app.stores.get('hiragame-release-test').size, 3);
  app.context.req = request('/hiragame/lesson?lessonId=one','navigate');
  assert.equal(await (await app.run('cachedResponse(req)')).text(), '<html>shell</html>');
  app.context.req = request('/hiragame/content/audio/one.mp3'); app.fail('/hiragame/content/audio/one.mp3');
  assert.equal(await (await app.run('cachedResponse(req)')).text(), 'recording fixture');
  app.context.req = request('/hiragame/content/audio/missing.mp3'); assert.equal((await app.run('cachedResponse(req)')).status,404);
  app.context.req = request('/hiragame/missing.js','navigate'); assert.equal((await app.run('cachedResponse(req)')).status,404);
  app.stores.get('hiragame-release-test').delete('/hiragame/content/catalog.json');
  app.context.req = request('/hiragame/content/catalog.json'); assert.equal((await app.run('cachedResponse(req)')).status,503);
  app.context.req = {url:'https://third.party/api', method:'GET'}; assert.equal(app.run('requestKind(req)'), 'network');
  await app.run('cleanOldCaches()'); assert(app.stores.has('unrelated-cache')); assert(!app.stores.has('hiragame-release-old'));
  const partial = setup(); partial.stores.set('hiragame-release-old', new Map()); partial.fail('/hiragame/content/catalog.json');
  await assert.rejects(partial.run('installRelease()')); assert(!partial.stores.has('hiragame-release-test')); assert(partial.stores.has('hiragame-release-old'));
  const changed = setup(); changed.context.fetch = async () => new Response('wrong release'); await assert.rejects(changed.run('installRelease()'));
  const update = setup(); let vote = false;
  const client = {id:'one',url:'https://example.test/hiragame/lesson',postMessage(data) {
    if(data.type==='CHECK_SAFE') update.listeners.message({data:{type:'SAFE',nonce:data.nonce,safe:vote},source:client});
  }};
  update.setClients([client]);
  assert.equal(await update.run('safeClients()'), false);
  vote = true; assert.equal(await update.run('safeClients()'), true);
  update.setClients([{id:'silent',url:client.url,postMessage(){}}]); assert.equal(await update.run('safeClients()'),false);
  let pending; update.setClients([client]);
  update.listeners.message({data:{type:'APPLY_UPDATE'},source:client,waitUntil(p){pending=p;}}); await pending; assert.equal(update.activated(),1);
  vote=false; update.listeners.message({data:{type:'APPLY_UPDATE'},source:client,waitUntil(p){pending=p;}}); await pending; assert.equal(update.activated(),1);
  console.log('Offline policy: 17 cache, integrity, routing and update assertions passed.');
})().catch(error => {console.error(error); process.exitCode=1;});
