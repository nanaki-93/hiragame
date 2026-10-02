#!/usr/bin/env python3
"""Static release integrity, route, manifest and hosting checks. Never launches the app."""
import argparse
import hashlib
import json
import re
import struct
from pathlib import Path
from urllib.parse import urlsplit
from build_offline import ROOT, ROUTES


def validate(artifact, hosting=None):
    errors = []
    owned = artifact / 'hiragame'
    try:
        metadata = json.loads((owned / 'precache.json').read_text())
        if metadata['routes'] != ROUTES: errors.append('Release routes differ from supported routes')
        expected = {('/hiragame/' + p.relative_to(owned).as_posix()) for p in owned.rglob('*') if p.is_file() and p.name not in ('sw.js', 'precache.json') and p.suffix != '.map'}
        urls = [a['url'] for a in metadata['assets']]
        if len(urls) != len(set(urls)) or set(urls) != expected: errors.append('Precache must cover production assets exactly once')
        for asset in metadata['assets']:
            url = urlsplit(asset['url'])
            if url.scheme or url.netloc or url.query or url.fragment or not url.path.startswith('/hiragame/') or '..' in url.path or '%' in url.path:
                errors.append('Unsafe precache URL'); continue
            target = artifact / url.path.lstrip('/')
            if target.is_symlink() or not target.is_file(): errors.append('Missing/symlink precache asset'); continue
            data = target.read_bytes()
            if hashlib.sha256(data).hexdigest() != asset['sha256'] or len(data) != asset['bytes']: errors.append('Asset digest/size mismatch: ' + asset['url'])
        shell = (owned / 'index.html').read_text()
        if shell != (artifact / 'index.html').read_text(): errors.append('Shell copies differ')
        for ref in re.findall(r'(?:src|href)="([^"]+)"', shell):
            if not ref.startswith('/hiragame/') or ref not in expected: errors.append('Unowned or missing shell resource: ' + ref)
        if '/hiragame/offline.js' not in shell or '/hiragame/manifest.webmanifest' not in shell: errors.append('Missing production offline bootstrap')
        manifest = json.loads((owned / 'manifest.webmanifest').read_text())
        if any(manifest.get(key) != '/hiragame/' for key in ('id', 'scope', 'start_url')): errors.append('Incorrect manifest base path')
        for icon in manifest['icons']:
            data = (artifact / icon['src'].lstrip('/')).read_bytes()
            if data[:8] != b'\x89PNG\r\n\x1a\n' or f'{struct.unpack(">I", data[16:20])[0]}x{struct.unpack(">I", data[20:24])[0]}' != icon['sizes']: errors.append('Invalid icon dimensions')
        template = (ROOT / 'site/src/offline/service-worker.js').read_text()
        release = hashlib.sha256((json.dumps(metadata['assets'], sort_keys=True) + template + json.dumps(ROUTES)).encode()).hexdigest()[:20]
        worker = template.replace('__RELEASE__', json.dumps(release)).replace('__ASSETS__', json.dumps(metadata['assets'])).replace('__ROUTES__', json.dumps(ROUTES))
        if metadata['release'] != release or (owned / 'sw.js').read_text() != worker: errors.append('Worker release mismatch')
        if hosting:
            config = json.loads(hosting.read_text())['hosting']
            rewrites = config['rewrites']
            permitted = {route + suffix for route in ROUTES for suffix in ('', '/')}
            if {r['source'] for r in rewrites} != permitted or any(r['destination'] != '/hiragame/index.html' for r in rewrites): errors.append('Hosting must rewrite only exact app routes; missing assets must stay missing')
            if not any(h['source'] == '/hiragame/**' and any(v['key'] == 'Cache-Control' and 'no-cache' in v['value'] for v in h['headers']) for h in config['headers']): errors.append('Missing safe cache headers')
    except (KeyError, ValueError, OSError, TypeError, struct.error) as error:
        errors.append('Incomplete release: ' + str(error))
    return errors


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('artifact', type=Path)
    parser.add_argument('--hosting', type=Path, default=ROOT / 'firebase.json')
    args = parser.parse_args()
    errors = validate(args.artifact, args.hosting)
    if errors: raise SystemExit('\n'.join(errors))
    print('Release integrity, manifest, routes and hosting checks passed')
