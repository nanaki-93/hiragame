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
  update.setClients([{id:'root-tab',url:'https://example.test/hiragame',postMessage(){}}]);
  assert.equal(await update.run('safeClients()'), false); // A root tab without the trailing slash also votes.
  const clearing = setup(); const notices = [];
  const requester = {id:'clear-tab',url:'https://example.test/hiragame/settings',postMessage(data) {
    if(data.type==='CHECK_SAFE') clearing.listeners.message({data:{type:'SAFE',nonce:data.nonce,safe:true},source:requester});
    else notices.push(data);
  }};
  clearing.setClients([requester]); clearing.stores.set('unrelated-cache',new Map());
  await clearing.run('installRelease()');
  clearing.listeners.message({data:{type:'CLEAR_ASSETS'},source:requester,waitUntil(p){pending=p;}}); await pending;
  assert.equal(clearing.unregistered(),1); assert(clearing.stores.has('unrelated-cache'));
  assert(!clearing.stores.has('hiragame-release-test')); assert.equal(notices.at(-1).type,'ASSETS_CLEARED');
  clearing.context.caches.open = async () => { throw Error('storage denied'); };
  clearing.listeners.message({data:{type:'CACHE_STATUS'},source:requester,waitUntil(p){pending=p;}}); await pending;
  assert.equal(notices.at(-1).type,'OFFLINE_ERROR');
  clearing.context.caches.keys = async () => { throw Error('storage denied'); };
  clearing.listeners.message({data:{type:'CLEAR_ASSETS'},source:requester,waitUntil(p){pending=p;}}); await pending;
  assert.equal(notices.at(-1).type,'OFFLINE_ERROR');
  const concurrent = setup(); const requests = [];
  const asyncClient = {id:'async',url:requester.url,postMessage(data){requests.push(data);}};
  concurrent.setClients([asyncClient]);
  concurrent.context.Date = {now:()=>100};
  const firstVote = concurrent.run('safeClients()'), secondVote = concurrent.run('safeClients()');
  await new Promise(setImmediate); // Drain both VM and host promise queues before delivering votes.
  assert.equal(new Set(requests.map(r=>r.nonce)).size,2);
  for(const r of requests) concurrent.listeners.message({data:{type:'SAFE',nonce:r.nonce,safe:true},source:asyncClient});
  assert.equal(await firstVote,true); assert.equal(await secondVote,true);
  console.log('Offline policy: cache integrity, routing, concurrent updates, root-tab safety, scoped clearing and failure reporting passed.');
})().catch(error => {console.error(error); process.exitCode=1;});
