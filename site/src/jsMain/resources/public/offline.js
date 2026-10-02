/* Production shell loads this adapter explicitly; development never registers a worker. */
(() => {
  'use strict';
  const listeners = new Set();
  let registration;
  let state = 'Preparing offline support…';
  const emit = value => { state = value; listeners.forEach(fn => fn(value)); };
  const safe = () => window.hiragameProgressSaved === true && window.hiragameSessionActive !== true;
  const api = window.hiragameOffline = {
    subscribe(fn) { listeners.add(fn); fn(state); return () => listeners.delete(fn); },
    check() { if (registration) registration.update().catch(() => emit('Update check failed. Keep using the current release or retry online.')); },
    apply() {
      if (!safe()) { emit('Finish your activity and save or export progress before applying an update.'); return; }
      if (registration?.waiting) { emit('Checking that all open Hiragame tabs are saved and idle…'); registration.waiting.postMessage({ type: 'APPLY_UPDATE' }); }
      else emit('No downloaded update is waiting.');
    },
    async download(repair = true) {
      if (!('serviceWorker' in navigator)) { emit('Offline caching is unsupported here. Text lessons still work online.'); return; }
      try {
        registration = await navigator.serviceWorker.register('/hiragame/sw.js', { scope: '/hiragame/', updateViaCache: 'none' });
        const report = () => {
          if (registration.waiting) emit('An update is downloaded. Finish activities in all tabs, then apply it here.');
          else if (registration.active) registration.active.postMessage({ type: 'CACHE_STATUS' });
          else emit('Downloading the app and reviewed text. Keep this page open until caching finishes.');
        };
        const observe = () => {
          const worker = registration.installing;
          worker?.addEventListener('statechange', () => {
            if (worker.state === 'redundant') emit('Caching failed or was interrupted. The previous release and progress are retained. Retry online.');
            else report();
          });
          report();
        };
        registration.addEventListener('updatefound', observe);
        observe();
        if (repair && registration.active && !registration.waiting) {
          emit('Checking and downloading this release again…');
          registration.active.postMessage({ type: 'REPAIR_CACHE' });
        }
      } catch (_) { emit('Offline caching failed or is unavailable. Retry online; lessons and backups remain available.'); }
    },
    async clear() {
      if (!safe()) { emit('Finish the activity and save progress before clearing downloaded assets.'); return; }
      try {
        const worker = registration || await navigator.serviceWorker.getRegistration('/hiragame/');
        const controller = worker?.active;
        if (controller) controller.postMessage({ type: 'CLEAR_ASSETS' });
        else emit('No active downloaded release to clear.');
      } catch (_) { emit('Could not clear downloaded assets. Progress has not been changed.'); }
    }
  };
  if ('serviceWorker' in navigator) {
    navigator.serviceWorker.addEventListener('message', event => {
      if (event.data?.type === 'CHECK_SAFE') {
        const ready = safe();
        if (ready) { document.body.inert = true; setTimeout(() => { document.body.inert = false; }, 4000); }
        event.source?.postMessage({ type: 'SAFE', nonce: event.data.nonce, safe: ready });
      }
      if (event.data?.type === 'CACHE_READY') emit(event.data.complete ? 'This release is cached. Browser storage may be evicted; keep a progress backup.' : 'Some assets are missing or could not download. Reconnect and select Download app / retry; existing progress is retained.');
      if (event.data?.type === 'ASSETS_CLEARED') {
        document.body.inert = false; registration = null;
        emit('Downloaded assets removed; progress retained. Reconnect and reload, then download again.');
      }
      if (event.data?.type === 'UPDATE_DEFERRED') emit('Update deferred: another tab is active, unsaved or not responding. Close or finish it, then retry.');
      if (event.data?.type === 'OFFLINE_ERROR') {
        document.body.inert = false;
        emit('Offline storage or the update action is unavailable. Check browser storage permissions, reconnect and retry. Learner progress was not changed.');
      }
    });
    navigator.serviceWorker.addEventListener('controllerchange', () => {
      // All open clients voted before activation. A last-moment activity still prevents a forced reload.
      if (safe()) location.reload();
      else emit('A new release is available. Finish and save this activity, then reload.');
    });
  }
  window.dispatchEvent(new Event('hiragame-offline-ready'));
  api.download(false);
})();
