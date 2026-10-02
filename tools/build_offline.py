#!/usr/bin/env python3
"""Build coherent /hiragame offline metadata from actual production files; no running app."""
import argparse
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
ROUTES = ['/hiragame', '/hiragame/topics', '/hiragame/lesson', '/hiragame/review', '/hiragame/settings']


def build(artifact):
    artifact = artifact.resolve()
    owned = artifact / 'hiragame'
    shell = artifact / 'index.html'
    if not shell.is_file() or not (owned / 'hiragame.js').is_file():
        raise ValueError('Production shell and executable are required')
    html = shell.read_text()
    tag = '<script src="/hiragame/offline.js" defer></script>'
    if tag not in html:
        html = html.replace('</body>', tag + '\n</body>')
    manifest_link = '<link rel="manifest" href="/hiragame/manifest.webmanifest">'
    if manifest_link not in html:
        html = html.replace('</head>', manifest_link + '\n</head>')
    shell.write_text(html)
    (owned / 'index.html').write_text(html)
    assets = []
    for path in sorted(owned.rglob('*')):
        if path.is_symlink(): raise ValueError(f'Symlink in production output: {path}')
        if not path.is_file() or path.name in ('sw.js', 'precache.json') or path.suffix == '.map': continue
        data = path.read_bytes()
        assets.append({'url': '/hiragame/' + path.relative_to(owned).as_posix(),
                       'sha256': hashlib.sha256(data).hexdigest(), 'bytes': len(data)})
    template = (ROOT / 'site/src/offline/service-worker.js').read_text()
    release = hashlib.sha256((json.dumps(assets, sort_keys=True) + template + json.dumps(ROUTES)).encode()).hexdigest()[:20]
    metadata = {'release': release, 'routes': ROUTES, 'assets': assets}
    (owned / 'precache.json').write_text(json.dumps(metadata, indent=2) + '\n')
    (owned / 'sw.js').write_text(template.replace('__RELEASE__', json.dumps(release)).replace('__ASSETS__', json.dumps(assets)).replace('__ROUTES__', json.dumps(ROUTES)))
    print(f'Offline release {release}: {len(assets)} owned assets, {sum(a["bytes"] for a in assets)} bytes')
    return metadata


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('artifact', type=Path)
    args = parser.parse_args()
    build(args.artifact)
