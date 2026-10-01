#!/usr/bin/env python3
"""Read-only structural, reference, review and asset validation of bundled content."""
import argparse
import json
import re
import sys
from pathlib import Path

ID = re.compile(r"[A-Za-z0-9][A-Za-z0-9_-]*\Z", re.ASCII)


class Invalid(Exception):
    pass


class DuplicateKey(Invalid):
    pass


def unique_pairs(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise DuplicateKey(f"duplicate JSON key {key!r}")
        result[key] = value
    return result


def spec(required, optional=None):
    return (required, optional or {})


TEXT = 'text'
IDENT = 'id'
INTEGER = 'positive'
JP = spec({'surface': TEXT, 'reading': TEXT}, {'translation': ('nullable', TEXT),
          'gloss': ('nullable', TEXT),
          'segments': [spec({'surface': TEXT}, {'reading': ('nullable', TEXT)})],
          'romaji': ('nullable', TEXT), 'context': ('nullable', TEXT),
          'register': ('nullable', TEXT)})
REVIEW = spec({'status': ('reviewed', 'unreviewed'), 'reviewerType': ('agent', 'human'),
               'reviewDate': TEXT, 'reviewNote': TEXT, 'provenance': TEXT,
               'rights': ('publishable', 'unknown', 'notCleared'), 'rightsBasis': TEXT})
PHRASE = spec({'id': IDENT, 'text': JP, 'usage': TEXT, 'register': TEXT},
              {'sourceTurnId': ('nullable', IDENT), 'audioId': ('nullable', IDENT)})
ITEM = spec({'id': IDENT, 'targetKind': ('phrase', 'exercise'), 'targetId': IDENT,
             'skill': TEXT, 'direction': TEXT})
OPTION = spec({'id': IDENT}, {'label': ('nullable', TEXT), 'text': ('nullable', JP)})
EXERCISES = {
    'choice': spec({'id': IDENT, 'type': ('choice',), 'prompt': TEXT, 'options': [OPTION],
                    'correctOptionId': IDENT, 'explanation': TEXT}),
    'reading': spec({'id': IDENT, 'type': ('reading',), 'prompt': TEXT, 'stimulus': JP,
                     'answerRepresentation': ('kana', 'romaji'), 'acceptedAnswers': [object],
                     'explanation': TEXT}),
    'completion': spec({'id': IDENT, 'type': ('completion',), 'prompt': TEXT, 'template': TEXT,
                        'acceptedAnswers': [JP], 'expectedCompletedExample': JP, 'explanation': TEXT}),
    'production': spec({'id': IDENT, 'type': ('production',), 'prompt': TEXT,
                        'exampleResponses': [JP], 'criteria': [TEXT]}),
}
NODES = {
    'prompt': spec({'id': IDENT, 'type': ('prompt',), 'speakerId': IDENT, 'text': JP, 'nextNodeId': IDENT}),
    'choiceInteraction': spec({'id': IDENT, 'type': ('choiceInteraction',), 'exerciseId': IDENT,
                                'transitions': [spec({'optionId': IDENT, 'feedback': TEXT, 'nextNodeId': IDENT})]}),
    'completionInteraction': spec({'id': IDENT, 'type': ('completionInteraction',), 'exerciseId': IDENT,
                                    'feedback': TEXT, 'nextNodeId': IDENT}),
    'terminal': spec({'id': IDENT, 'type': ('terminal',), 'message': TEXT}),
}
CATALOG = spec({'formatVersion': ('version',), 'contentVersion': INTEGER,
                'topics': [spec({'id': IDENT, 'title': TEXT, 'description': TEXT})],
                'entries': [spec({'id': IDENT, 'kind': ('lesson', 'practice'), 'topicId': IDENT, 'path': TEXT})]},
               {'audioAssets': [spec({'id': IDENT, 'path': TEXT, 'transcript': TEXT,
                                      'permissionNote': TEXT})]})
BASE = {'formatVersion': ('version',), 'contentVersion': INTEGER, 'id': IDENT,
        'topicId': IDENT, 'title': TEXT, 'review': REVIEW, 'exercises': [object]}
COMMON = {'phrases': [PHRASE], 'reviewItems': [ITEM]}
PRACTICE = spec({**BASE, 'description': TEXT}, COMMON)
LESSON = spec({**BASE, **COMMON, 'situation': TEXT, 'communicationGoal': TEXT,
               'difficulty': ('beginner', 'intermediate', 'advanced'), 'durationMinutes': INTEGER,
               'prerequisiteLessonIds': [IDENT],
               'dialogue': spec({'speakers': [spec({'id': IDENT, 'name': TEXT, 'role': TEXT})],
                                 'turns': [spec({'id': IDENT, 'speakerId': IDENT, 'text': JP},
                                                {'audioId': ('nullable', IDENT)})]}),
               'grammarNotes': [spec({'id': IDENT, 'explanation': TEXT, 'examples': [JP]})],
               'rolePlay': spec({'task': TEXT, 'criteria': [TEXT]},
                                {'hints': [TEXT], 'examples': [JP]})},
              {'conversationGraph': ('nullable', spec({'entryNodeId': IDENT,
                                                                   'nodes': [object]}))})


def fail(location, reason):
    raise Invalid(f"{location}: {reason}")


def validate(value, schema, location='$'):
    if schema is object:
        if not isinstance(value, dict):
            fail(location, 'expected object')
        return
    if schema == TEXT or schema == IDENT:
        if not isinstance(value, str) or not value.strip():
            fail(location, 'expected nonblank string')
        if schema == IDENT and not ID.fullmatch(value):
            fail(location, 'invalid stable ID')
        return
    if schema == INTEGER or schema == ('version',):
        if type(value) is not int or not 1 <= value <= 2_147_483_647 or (schema == ('version',) and value != 1):
            fail(location, 'expected positive integer' if schema == INTEGER else 'unsupported formatVersion (expected 1)')
        return
    if isinstance(schema, list):
        if not isinstance(value, list):
            fail(location, 'expected array')
        for i, element in enumerate(value):
            validate(element, schema[0], f'{location}[{i}]')
        return
    if isinstance(schema, tuple) and len(schema) == 2 and schema[0] == 'nullable':
        if value is not None:
            validate(value, schema[1], location)
        return
    if isinstance(schema, tuple) and all(isinstance(s, str) for s in schema):
        if not isinstance(value, str) or value not in schema:
            fail(location, f"expected one of {', '.join(schema)}")
        return
    required, optional = schema
    if not isinstance(value, dict):
        fail(location, 'expected object')
    for key in required:
        if key not in value:
            fail(f'{location}.{key}', 'missing required field')
    for key, item in value.items():
        if key not in required and key not in optional:
            fail(f'{location}.{key}', 'unknown field')
        validate(item, (required | optional)[key], f'{location}.{key}')
    if schema is JP:
        if (value.get('translation') is not None) == (value.get('gloss') is not None):
            fail(location, 'exactly one translation or gloss required')
        if value.get('gloss') is not None and (len(value['surface']) != 1 or not
                                ('\u3041' <= value['surface'] <= '\u3096' or '\u30a1' <= value['surface'] <= '\u30fa')):
            fail(f'{location}.gloss', 'gloss requires isolated kana')
        if value.get('segments') and ''.join(s['surface'] for s in value['segments']) != value['surface']:
            fail(f'{location}.segments', 'segment surfaces do not concatenate to surface')
        for i, segment in enumerate(value.get('segments', [])):
            if any('\u3400' <= c <= '\u9fff' or '\uf900' <= c <= '\ufaff' or
                   '\U00020000' <= c <= '\U0003347f' or c in '々〆〇' for c in segment['surface']):
                if not segment.get('reading'):
                    fail(f'{location}.segments[{i}].reading', 'kanji segment requires authored reading')


def distinct(items, location):
    seen = set()
    for i, item in enumerate(items):
        identifier = item['id']
        if identifier in seen:
            fail(f'{location}[{i}].id', f'duplicate ID {identifier}')
        seen.add(identifier)


def local(document, location):
    for key in ('phrases', 'reviewItems'):
        distinct(document.get(key, []), f'{location}.{key}')
    for i, ex in enumerate(document['exercises']):
        loc = f'{location}.exercises[{i}]'
        kind = ex.get('type') if isinstance(ex, dict) else None
        if not isinstance(kind, str) or kind not in EXERCISES:
            fail(f'{loc}.type', f'unknown exercise type {kind!r}')
        validate(ex, EXERCISES[kind], loc)
        if kind == 'choice':
            if len(ex['options']) < 2:
                fail(f'{loc}.options', 'at least two options required')
            distinct(ex['options'], f'{loc}.options')
            if ex['correctOptionId'] not in {o['id'] for o in ex['options']}:
                fail(f'{loc}.correctOptionId', 'correct option does not exist')
            for j, opt in enumerate(ex['options']):
                if (opt.get('label') is not None) == (opt.get('text') is not None):
                    fail(f'{loc}.options[{j}]', 'exactly one label or text required')
        elif kind == 'reading':
            for j, answer in enumerate(ex['acceptedAnswers']):
                target = f'{loc}.acceptedAnswers[{j}]'
                if not isinstance(answer, dict) or answer.get('type') != ex['answerRepresentation']:
                    fail(target, 'answer type must match answerRepresentation')
                validate(answer, spec({'type': (ex['answerRepresentation'],),
                                       'text': JP if ex['answerRepresentation'] == 'kana' else TEXT}), target)
        if kind in ('reading', 'completion'):
            if not ex['acceptedAnswers']:
                fail(f'{loc}.acceptedAnswers', 'must not be empty')
            spellings = [(a['surface'] if kind == 'completion' else a['text']['surface'])
                         if kind == 'completion' or ex['answerRepresentation'] == 'kana' else a['text']
                         for a in ex['acceptedAnswers']]
            if len(spellings) != len(set(spellings)):
                fail(f'{loc}.acceptedAnswers', 'duplicate accepted answer')
        if kind == 'completion':
            if ex['template'].count('{blank}') != 1:
                fail(f'{loc}.template', 'expected exactly one {blank}')
            if not any(ex['template'].replace('{blank}', answer['surface']) ==
                       ex['expectedCompletedExample']['surface'] for answer in ex['acceptedAnswers']):
                fail(f'{loc}.expectedCompletedExample.surface', 'completed example must use an accepted fill')
        if kind == 'production' and (not ex['criteria'] or not ex['exampleResponses']):
            fail(loc, 'production needs criteria and example responses')
    distinct(document['exercises'], f'{location}.exercises')
    if 'dialogue' in document:
        if not document['rolePlay']['criteria']:
            fail(f'{location}.rolePlay.criteria', 'must not be empty')
        for i, note in enumerate(document['grammarNotes']):
            if not note['examples']:
                fail(f'{location}.grammarNotes[{i}].examples', 'must not be empty')
        if len(document['prerequisiteLessonIds']) != len(set(document['prerequisiteLessonIds'])):
            fail(f'{location}.prerequisiteLessonIds', 'duplicate prerequisite ID')
        for key in ('speakers', 'turns'):
            distinct(document['dialogue'][key], f'{location}.dialogue.{key}')
        distinct(document['grammarNotes'], f'{location}.grammarNotes')
        graph = document.get('conversationGraph')
        if graph:
            if not graph['nodes']:
                fail(f'{location}.conversationGraph.nodes', 'graph needs nodes')
            for i, node in enumerate(graph['nodes']):
                kind = node.get('type') if isinstance(node, dict) else None
                if not isinstance(kind, str) or kind not in NODES:
                    fail(f'{location}.conversationGraph.nodes[{i}].type', f'unknown node type {kind!r}')
                validate(node, NODES[kind], f'{location}.conversationGraph.nodes[{i}]')
                if kind == 'choiceInteraction':
                    options = [t['optionId'] for t in node['transitions']]
                    if not options:
                        fail(f'{location}.conversationGraph.nodes[{i}].transitions', 'nonterminal dead end: no option transitions')
                    if len(options) != len(set(options)):
                        fail(f'{location}.conversationGraph.nodes[{i}].transitions', 'duplicate option transitions')
            distinct(graph['nodes'], f'{location}.conversationGraph.nodes')


def references(document, location):
    """Resolve only document-owned IDs; an identical ID in another document is not a target."""
    turns = document.get('dialogue', {}).get('turns', [])
    speakers = {speaker['id'] for speaker in document.get('dialogue', {}).get('speakers', [])}
    turn_ids = {turn['id'] for turn in turns}
    phrases = {phrase['id'] for phrase in document.get('phrases', [])}
    exercises = {exercise['id']: exercise for exercise in document['exercises']}
    for i, turn in enumerate(turns):
        if turn['speakerId'] not in speakers:
            fail(f'{location}.dialogue.turns[{i}].speakerId', f'undeclared speaker {turn["speakerId"]}')
    for i, phrase in enumerate(document.get('phrases', [])):
        source = phrase.get('sourceTurnId')
        if source is not None and source not in turn_ids:
            fail(f'{location}.phrases[{i}].sourceTurnId', f'unknown local turn {source}')
    for i, item in enumerate(document.get('reviewItems', [])):
        targets = phrases if item['targetKind'] == 'phrase' else exercises
        if item['targetId'] not in targets:
            fail(f'{location}.reviewItems[{i}].targetId',
                 f'unknown local {item["targetKind"]} {item["targetId"]}')

    graph = document.get('conversationGraph')
    if not graph:
        return
    base = f'{location}.conversationGraph'
    nodes = {node['id']: (i, node) for i, node in enumerate(graph['nodes'])}
    entry = graph['entryNodeId']
    if entry not in nodes:
        fail(f'{base}.entryNodeId', f'unknown local node {entry}')
    edges = {}
    for i, node in enumerate(graph['nodes']):
        loc = f'{base}.nodes[{i}] ({node["id"]})'
        kind = node['type']
        if kind == 'prompt' and node['speakerId'] not in speakers:
            fail(f'{loc}.speakerId', f'undeclared speaker {node["speakerId"]}')
        if kind in ('choiceInteraction', 'completionInteraction'):
            exercise = exercises.get(node['exerciseId'])
            expected = 'choice' if kind == 'choiceInteraction' else 'completion'
            if exercise is None or exercise['type'] != expected:
                fail(f'{loc}.exerciseId', f'expected local {expected} exercise {node["exerciseId"]}')
        if kind == 'choiceInteraction':
            options = {option['id'] for option in exercise['options']}
            transitions = node['transitions']
            actual = {transition['optionId'] for transition in transitions}
            for j, transition in enumerate(transitions):
                if transition['optionId'] not in options:
                    fail(f'{loc}.transitions[{j}].optionId', f'unknown option {transition["optionId"]}')
            missing = options - actual
            if missing:
                fail(f'{loc}.transitions', f'missing option transitions: {", ".join(sorted(missing))}')
            outgoing = [(transition['nextNodeId'], f'{loc}.transitions[{j}].nextNodeId')
                        for j, transition in enumerate(transitions)]
        elif kind == 'terminal':
            outgoing = []
        else:
            outgoing = [(node['nextNodeId'], f'{loc}.nextNodeId')]
        for target, edge_loc in outgoing:
            if target not in nodes:
                fail(edge_loc, f'unknown local node {target}')
        edges[node['id']] = [target for target, _ in outgoing]

    # Iterative three-color DFS: each node/edge is visited a bounded number of times.
    # Inspect disconnected components too, so a hidden cycle cannot be masked by an
    # unreachable-node diagnostic.
    colors = {}
    for start in [entry, *nodes]:
        if start in colors:
            continue
        colors[start] = 1
        stack = [(start, iter(edges[start]))]
        while stack:
            node_id, successors = stack[-1]
            target = next(successors, None)
            if target is None:
                colors[node_id] = 2
                stack.pop()
            elif colors.get(target) == 1:
                i, _ = nodes[node_id]
                fail(f'{base}.nodes[{i}] ({node_id})', f'conversation graph cycle to {target}')
            elif target not in colors:
                colors[target] = 1
                stack.append((target, iter(edges[target])))
    seen = {entry}
    pending = [entry]
    while pending:
        for target in edges[pending.pop()]:
            if target not in seen:
                seen.add(target)
                pending.append(target)
    for node_id, (i, _) in nodes.items():
        if node_id not in seen:
            fail(f'{base}.nodes[{i}] ({node_id})', 'unreachable node')
    if not any(node['type'] == 'terminal' for _, node in nodes.values()):
        fail(base, 'nonterminal dead end: no terminal path')


def safe_path(root, name, location, prefix=None):
    """Resolve a POSIX-relative local path without following links outside its allowed root."""
    if (not isinstance(name, str) or not name or name.startswith('/') or
            any(part in ('', '.', '..') for part in name.split('/')) or
            '\\' in name or ':' in name or '?' in name or '#' in name or
            (prefix and not name.startswith(prefix + '/'))):
        fail(location, 'unsafe local path')
    path = root / name
    if not path.resolve().is_relative_to(root.resolve()):
        fail(location, 'symlink escapes allowed root')
    return path


# Negative decisions override any affirmative language elsewhere in the same evidence.
# These records are prose, not a machine-readable license: ambiguous or conflicting
# assertions must be sent back for review rather than promoted by keyword counting.
REJECTION = re.compile(
    r'\b(?:not\s+(?:publishable|reviewed|approved|cleared|licensed|permitted|granted)|'
    r'review\s+(?:rejected|denied|failed|pending|incomplete)|rights?\s+(?:denied|unknown|pending)|'
    r'(?:publication|redistribution|distribution)\s+(?:is\s+)?(?:prohibited|forbidden|denied|not\s+(?:permitted|allowed))|'
    r'(?:personal|private|noncommercial|internal)\s+use\s+only|'
    r'(?:recording|audio)\s+(?:is\s+)?(?:restricted|limited)\s+to\s+'
    r'(?:personal|private|internal|noncommercial)\s+use|'
    r'(?:permission|license)\s+(?:is\s+)?limited\s+to\s+(?:personal|private|internal)\s+use|'
    r'(?:must|should|may|can)\s+not\s+be\s+(?:published|redistributed|distributed)|'
    r'(?:recording|audio)\s+(?:must|should|has\s+to)\s+remain\s+(?:private|personal|internal)|'
    r'cannot\s+(?:be\s+)?publish(?:ed)?|'
    r'(?:do|does)\s+not\s+publish|publishing\s+(?:is\s+)?(?:forbidden|prohibited)|'
    r'publication\s+(?:is\s+)?restricted\s+to\s+(?:private|personal|internal)\s+use|'
    r'no\s+(?:publication|redistribution|permission)|permission\s+(?:denied|revoked))\b',
    re.IGNORECASE,
)
# A text/transcript license does not license its recording. The object of the
# publication grant must explicitly be the recording/audio, in the same clause.
RECORDING_GRANT = re.compile(
    r'\b(?:permission\s+(?:is\s+)?granted\s+for\s+publication\s+of\s+(?:the\s+|this\s+)?(?:recording|audio)|'
    r'permission\s+granted\s+to\s+publish\s+(?:the\s+|this\s+)?(?:recording|audio)|'
    r'(?:the\s+|this\s+)?(?:recording|audio)\s+(?:is\s+)?(?:licensed|cleared)\s+for\s+publication)\b',
    re.IGNORECASE,
)


def review_scope(note, identifier, location):
    """Select the review decision anchored to this document, not another entry in the note."""
    # Seed notes declare each reviewed document with a dated, ID-specific scope.
    # Fixtures and future notes can use the same simple prose convention.
    anchors = list(re.finditer(r'(?im)^reviewed\s+on\s+[^\n]*', note))
    scopes = []
    for index, match in enumerate(anchors):
        end = anchors[index + 1].start() if index + 1 < len(anchors) else len(note)
        opening = note[match.start():end].split('\n\n', 1)[0]
        if f'`{identifier}`' in opening:
            scopes.append(note[match.start():end])
    if not scopes:
        fail(location, f'missing affirmative document-specific review decision for {identifier}')
    # Never let an earlier approval hide a later, conflicting decision. A
    # document-specific refusal outside the dated sections also overrides them.
    for line in note.splitlines():
        if f'`{identifier}`' in line and REJECTION.search(line):
            fail(location, f'conflicting rejected decision for {identifier}')
    return '\n'.join(scopes)


def review_document(document, loc, review_root):
    meta = document['review']
    base = f'{loc}.review'
    if meta['status'] != 'reviewed' or meta['rights'] != 'publishable':
        fail(base, 'shipped content requires reviewed status and publishable rights')
    for field in ('provenance', 'rightsBasis'):
        if (meta[field].strip().lower() in ('unknown', 'pending', 'none', 'not cleared', 'notcleared') or
                REJECTION.search(meta[field])):
            fail(f'{base}.{field}', 'unresolved provenance or restricted rights basis')
    note_path = safe_path(review_root, meta['reviewNote'], f'{base}.reviewNote')
    if not note_path.is_file():
        fail(f'{base}.reviewNote', 'missing review evidence')
    note = note_path.read_text(encoding='utf-8')
    if not note.strip():
        fail(f'{base}.reviewNote', 'empty review evidence')
    scope = review_scope(note, document['id'], f'{base}.reviewNote')
    if REJECTION.search(scope):
        fail(f'{base}.reviewNote', 'review note contains a rejected or restricted decision')
    if not re.search(r'\bprovenance\b', scope, re.I):
        fail(f'{base}.reviewNote', 'review note must document provenance')
    # The rights decision must assert that this original material is publishable,
    # not merely mention the word in a negative or hypothetical sentence.
    if not re.search(r'\b(?:marked|approved|cleared)\s+(?:this\s+original\s+text\s+)?publishable\b|'
                     r'\bpublishable\s+under\s+(?:the\s+)?(?:inherited\s+)?'
                     r'(?:original-material\s+)?rights\s+decision\b', scope, re.I):
        fail(f'{base}.reviewNote', 'missing affirmative publishable rights decision')
    prose = scope.replace('**', '')
    inherited = bool(re.search(r'\b(?:inherited\s+by\s+every\s+nested\s+item|'
                               r'applies\s+to\s+(?:both|all)\s+exercises)\b', prose, re.I))
    # Backtick IDs must be attached to an item row, or to an explicit item-level
    # affirmative decision. An ID appearing in an unrelated paragraph is not coverage.
    rows = scope.splitlines()
    covered = {document['id']}  # The affirmative, ID-scoped opening covers the document itself.
    for line in rows:
        if ((line.lstrip().startswith('|') and inherited) or
                re.search(r'\breviewed\b.*\b(?:marked|approved|cleared)\s+publishable\b', line, re.I)):
            covered.update(re.findall(r'`([A-Za-z0-9][A-Za-z0-9_-]*)`', line))
    items = [('id', document['id'])]
    for key in ('exercises', 'phrases', 'reviewItems', 'grammarNotes'):
        items.extend((f'{key}[{i}].id', item['id']) for i, item in enumerate(document.get(key, [])))
    dialogue = document.get('dialogue', {})
    for key in ('speakers', 'turns'):
        items.extend((f'dialogue.{key}[{i}].id', item['id']) for i, item in enumerate(dialogue.get(key, [])))
    for i, exercise in enumerate(document['exercises']):
        items.extend((f'exercises[{i}].options[{j}].id', option['id'])
                     for j, option in enumerate(exercise.get('options', [])))
    graph = document.get('conversationGraph')
    if graph:
        items.extend((f'conversationGraph.nodes[{i}].id', node['id'])
                     for i, node in enumerate(graph['nodes']))
    for field, identifier in items:
        if identifier not in covered:
            fail(f'{loc}.{field}', f'missing review-note coverage for {identifier} in {meta["reviewNote"]}')
    return scope


def check_audio(catalog, documents, root, notes):
    assets = {asset['id']: asset for asset in catalog.get('audioAssets', [])}
    used = set()
    for name, document in documents:
        sources = [(f'dialogue.turns[{i}]', turn) for i, turn in
                   enumerate(document.get('dialogue', {}).get('turns', []))]
        sources += [(f'phrases[{i}]', phrase) for i, phrase in enumerate(document.get('phrases', []))]
        for field, source in sources:
            audio_id = source.get('audioId')
            if audio_id is None:
                continue
            loc = f'{name}:$ ({document["id"]}).{field}.audioId'
            if audio_id not in assets:
                fail(loc, f'unknown catalog audio asset {audio_id}')
            used.add(audio_id)
            asset = assets[audio_id]
            if asset['transcript'] != source['text']['surface']:
                fail(loc, f'audio {audio_id} transcript does not match associated text surface')
            # Audio permission and review must be specific to this recording, not
            # inherited silently from the document's text rights decision.
            lines = [line for line in notes[document['id']].splitlines()
                     if f'`{audio_id}`' in line]
            if not any(re.search(r'\breviewed\b', line, re.I) and
                       RECORDING_GRANT.search(line) and not REJECTION.search(line) for line in lines):
                fail(loc, f'audio {audio_id} lacks explicit review and publication permission in review note')
    for i, asset in enumerate(catalog.get('audioAssets', [])):
        loc = f'catalog.json:$.audioAssets[{i}] ({asset["id"]})'
        path = safe_path(root, asset['path'], f'{loc}.path', 'audio')
        if not path.is_file():
            fail(f'{loc}.path', 'missing local audio asset')
        permission = asset['permissionNote'].strip().lower()
        if REJECTION.search(permission) or not RECORDING_GRANT.search(permission):
            fail(f'{loc}.permissionNote', 'audio permission is not explicitly cleared for publication')
        if asset['id'] not in used:
            fail(loc, 'audio has no transcript-associated turn or phrase and review')


def load(path, root):
    name = path.relative_to(root).as_posix()
    try:
        return json.loads(path.read_text(encoding='utf-8'), object_pairs_hook=unique_pairs)
    except DuplicateKey as exc:
        fail(name, str(exc))
    except (UnicodeError, json.JSONDecodeError) as exc:
        fail(name, f'malformed JSON: {exc}')


def check(root, review_root):
    # rglob does not descend into directory symlinks; inspect links themselves so
    # even unlisted assets cannot smuggle an escaping link into the release tree.
    for base in (root, review_root):
        for path in sorted(base.rglob('*')):
            name = path.relative_to(base).as_posix()
            if path.is_symlink() and not path.resolve().is_relative_to(base.resolve()):
                fail(name, 'symlink escapes allowed root')
            if base == root and (path.suffix.lower() == '.csv' or
                                 any(part in ('drafts', 'legacy') for part in path.relative_to(base).parts)):
                fail(name, 'legacy sources and drafts cannot be bundled')
    catalog_path = safe_path(root, 'catalog.json', 'catalog.json')
    if not catalog_path.is_file():
        fail('catalog.json', 'missing catalog')
    catalog = load(catalog_path, root)
    validate(catalog, CATALOG, 'catalog.json:$')
    for key in ('topics', 'entries', 'audioAssets'):
        distinct(catalog.get(key, []), f'catalog.json:$.{key}')
    listed = {'catalog.json'}
    global_ids = {key: set() for key in ('phrases', 'exercises', 'reviewItems')}
    topics = {topic['id'] for topic in catalog['topics']}
    lessons = {}
    documents = []
    notes = {}
    for i, entry in enumerate(catalog['entries']):
        name = entry['path']
        loc = f'catalog.json:$.entries[{i}] ({entry["id"]})'
        if entry['topicId'] not in topics:
            fail(f'{loc}.topicId', f'unknown topic {entry["topicId"]}')
        path = safe_path(root, name, f'{loc}.path')
        if not name.endswith('.json'):
            fail(f'{loc}.path', 'expected relative JSON path')
        if name in listed:
            fail(f'{loc}.path', 'duplicate document path')
        listed.add(name)
        if not path.is_file():
            fail(f'{loc}.path', f'missing document {name}')
        document = load(path, root)
        docloc = f'{name}:$ ({entry["id"]})'
        validate(document, LESSON if entry['kind'] == 'lesson' else PRACTICE, docloc)
        for key in ('formatVersion', 'contentVersion', 'id', 'topicId'):
            expected = entry[key] if key in entry else catalog[key]
            if document[key] != expected:
                fail(f'{docloc}.{key}', f'mismatch: expected {expected!r}')
        local(document, docloc)
        references(document, docloc)
        notes[document['id']] = review_document(document, docloc, review_root)
        documents.append((name, document))
        if entry['kind'] == 'lesson':
            lessons[entry['id']] = (document, docloc)
        for key, seen in global_ids.items():
            for j, item in enumerate(document.get(key, [])):
                identifier = item['id']
                if identifier in seen:
                    fail(f'{docloc}.{key}[{j}].id', f'duplicate catalog-wide {key} ID {identifier}')
                seen.add(identifier)
    # All entries are loaded before prerequisite resolution, so catalog ordering is irrelevant.
    for lesson_id, (document, loc) in lessons.items():
        for i, prerequisite in enumerate(document['prerequisiteLessonIds']):
            field = f'{loc}.prerequisiteLessonIds[{i}]'
            if prerequisite == lesson_id:
                fail(field, f'self prerequisite {prerequisite}')
            if prerequisite not in lessons:
                fail(field, f'unknown bundled lesson prerequisite {prerequisite}')
    colors = {}
    for start in lessons:
        if start in colors:
            continue
        colors[start] = 1
        stack = [(start, iter(enumerate(lessons[start][0]['prerequisiteLessonIds'])))]
        while stack:
            lesson_id, prerequisites = stack[-1]
            next_edge = next(prerequisites, None)
            if next_edge is None:
                colors[lesson_id] = 2
                stack.pop()
                continue
            i, prerequisite = next_edge
            if colors.get(prerequisite) == 1:
                fail(f'{lessons[lesson_id][1]}.prerequisiteLessonIds[{i}]',
                     f'prerequisite cycle to {prerequisite}')
            if prerequisite not in colors:
                colors[prerequisite] = 1
                stack.append((prerequisite, iter(enumerate(lessons[prerequisite][0]['prerequisiteLessonIds']))))
    check_audio(catalog, documents, root, notes)
    for path in sorted(root.rglob('*.json')):
        name = path.relative_to(root).as_posix()
        safe_path(root, name, name)
        if name not in listed:
            fail(name, 'unlisted JSON document')


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('content_root', type=Path)
    parser.add_argument('--review-root', type=Path, required=True)
    args = parser.parse_args(argv)
    try:
        if not args.content_root.is_dir() or not args.review_root.is_dir():
            raise OSError('content root or review root is not a directory')
        check(args.content_root, args.review_root)
    except (OSError, UnicodeError) as exc:
        print(f'content validation I/O: {exc}', file=sys.stderr)
        return 2
    except Invalid as exc:
        print(f'content validation: {exc}', file=sys.stderr)
        return 1
    print('content valid')
    return 0


if __name__ == '__main__':
    sys.exit(main())
