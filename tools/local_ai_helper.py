#!/usr/bin/env python3
"""Optional stateless loopback static server + fixed Ollama adapter. Never needed by core learning."""
import argparse
import json
import re
from functools import partial
from http.server import SimpleHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from urllib.parse import unquote, urlsplit
from urllib.request import Request, build_opener, ProxyHandler, HTTPRedirectHandler

MAX_REQUEST = 24_000
MAX_RESPONSE = 512_000
MODEL = re.compile(r'[A-Za-z0-9][A-Za-z0-9._:/-]{0,119}')
ROUTES = {'/hiragame/', '/hiragame/topics', '/hiragame/lesson', '/hiragame/review', '/hiragame/settings'}


def validate_proxy_request(method, path, headers, raw, port):
    allowed = {f'127.0.0.1:{port}', f'localhost:{port}'}
    if headers.get('Host') not in allowed: raise ValueError('Unrecognized host')
    origin = headers.get('Origin')
    if origin and origin not in {'http://' + host for host in allowed}: raise ValueError('Unrecognized origin')
    if method == 'GET' and path == '/ollama/api/tags' and not raw: return '/api/tags', None
    if method != 'POST' or path != '/ollama/api/chat': raise ValueError('Unsupported endpoint')
    if not origin or headers.get('Content-Type', '').split(';')[0] != 'application/json': raise ValueError('Expected local JSON request')
    if len(raw) > MAX_REQUEST: raise ValueError('Request too large')
    body = json.loads(raw)
    if set(body) != {'model', 'messages', 'stream', 'format', 'options', 'keep_alive'}: raise ValueError('Unsupported request fields')
    model = body['model']
    if not isinstance(model, str) or not MODEL.fullmatch(model) or 'cloud' in model.lower() or '..' in model: raise ValueError('Expected local model')
    if body['stream'] is not False or body['keep_alive'] != '0': raise ValueError('Expected bounded request')
    messages = body['messages']
    if not isinstance(messages, list) or not 1 <= len(messages) <= 13: raise ValueError('Too many turns')
    for message in messages:
        if set(message) != {'role', 'content'} or message['role'] not in ('system', 'user', 'assistant') or not isinstance(message['content'], str) or not 1 <= len(message['content']) <= 3000:
            raise ValueError('Invalid message')
    options = body['options']
    if not isinstance(options, dict) or set(options) != {'num_predict', 'num_ctx', 'temperature'}: raise ValueError('Unsupported options')
    if not (type(options['num_predict']) is int and 1 <= options['num_predict'] <= 400 and type(options['num_ctx']) is int and 1 <= options['num_ctx'] <= 4096 and type(options['temperature']) in (int, float) and 0 <= options['temperature'] <= 1): raise ValueError('Options exceed bounds')
    schema = {'type': 'object', 'additionalProperties': False, 'properties': {'reply': {'type': 'string'}, 'suggestion': {'type': 'string'}}, 'required': ['reply', 'suggestion']}
    if body['format'] != schema: raise ValueError('Expected bounded reply schema')
    return '/api/chat', json.dumps(body).encode()


class NoRedirect(HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs): raise ValueError('Upstream redirect refused')


class Handler(SimpleHTTPRequestHandler):
    def log_message(self, *_): pass  # No transcript or request logging.
    def list_directory(self, _): self.send_error(403); return None
    def do_OPTIONS(self): self.send_error(403)  # Same-origin helper needs no CORS relay.
    def do_GET(self):
        path = urlsplit(self.path).path
        if path.startswith('/ollama/'):
            self.proxy('GET'); return
        if self.headers.get('Host') not in {f'127.0.0.1:{self.server.server_port}', f'localhost:{self.server.server_port}'}:
            self.send_error(403); return
        decoded = unquote(path)
        if not decoded.startswith('/hiragame/') or '..' in decoded.split('/') or '\\' in decoded:
            self.send_error(404); return
        if decoded.rstrip('/') in {r.rstrip('/') for r in ROUTES}: self.path = '/hiragame/index.html'
        resolved = Path(self.translate_path(self.path)).resolve()
        if not resolved.is_relative_to(Path(self.directory).resolve()): self.send_error(403); return
        super().do_GET()
    def do_POST(self): self.proxy('POST')
    def proxy(self, method):
        try:
            length = int(self.headers.get('Content-Length', '0'))
            if not 0 <= length <= MAX_REQUEST: raise ValueError('Request too large')
            path, body = validate_proxy_request(method, self.path, self.headers, self.rfile.read(length), self.server.server_port)
            request = Request('http://127.0.0.1:11434' + path, data=body, method=method, headers={'Content-Type': 'application/json'})
            with build_opener(ProxyHandler({}), NoRedirect()).open(request, timeout=35) as response:
                data = response.read(MAX_RESPONSE + 1)
                if len(data) > MAX_RESPONSE: raise ValueError('Response too large')
            self.send_response(200); self.send_header('Content-Type', 'application/json'); self.send_header('Cache-Control', 'no-store'); self.end_headers(); self.wfile.write(data)
        except (ValueError, TypeError, KeyError, AttributeError, OSError):
            self.send_error(400, 'Local model request unavailable or rejected')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--artifact', type=Path, default=Path('site/build/dist/js/productionExecutable/public'))
    parser.add_argument('--port', type=int, default=8765)
    args = parser.parse_args()
    if not (args.artifact / 'hiragame/index.html').is_file() or not 1024 <= args.port <= 65535: parser.error('Build the static artifact first; use a port from 1024 to 65535')
    server = ThreadingHTTPServer(('127.0.0.1', args.port), partial(Handler, directory=str(args.artifact.resolve())))
    print(f'Optional local preview: http://127.0.0.1:{args.port}/hiragame/ — AI endpoint http://127.0.0.1:{args.port}/ollama')
    try: server.serve_forever()
    except KeyboardInterrupt: pass
    finally: server.server_close()
