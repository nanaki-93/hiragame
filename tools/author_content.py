#!/usr/bin/env python3
"""Small draft/scaffold and explicit reviewed-promotion CLI. No model dependency or auto-publication."""
import argparse
import copy
from datetime import datetime, timezone
import json
from pathlib import Path
import re
import shutil
import tempfile
import validate_content as validator

ROOT = Path(__file__).resolve().parents[1]
PUBLIC = ROOT / 'site/src/jsMain/resources/public/content'
DRAFTS = ROOT / 'content-source/drafts'
REVIEWS = ROOT / 'content-source/review-notes'


def scaffold(identifier, topic_id, title, kind='lesson'):
    if not re.fullmatch(r'[a-z][a-z0-9-]{0,63}', identifier): raise ValueError('Use a stable lowercase ID up to 64 characters')
    text = {'surface':'はい。','reading':'はい。','translation':'Yes.'}
    if kind == 'topic': return {'id':identifier,'title':title,'description':'TODO: describe the situation and goals'}
    if kind == 'audio': return {'id':identifier,'path':f'audio/{identifier}.mp3','transcript':'TODO: exact reviewed Japanese transcript','permissionNote':'TODO: recording-specific redistribution permission'}
    return {'formatVersion':1,'contentVersion':1,'id':identifier,'topicId':topic_id,'title':title,
        'situation':'TODO: describe a real communication situation','communicationGoal':'TODO: define a narrow goal','difficulty':'beginner','durationMinutes':8,'prerequisiteLessonIds':[],
        'dialogue':{'speakers':[{'id':identifier+'-speaker','name':'Colleague','role':'TODO: relationship'}],
                    'turns':[{'id':identifier+'-turn','speakerId':identifier+'-speaker','text':text}]},
        'phrases':[{'id':identifier+'-phrase','text':text,'usage':'TODO: context','register':'TODO: register','sourceTurnId':identifier+'-turn'}],
        'grammarNotes':[{'id':identifier+'-grammar','explanation':'TODO: teach the intended form','examples':[text]}],
        'exercises':[
            {'id':identifier+'-choice','type':'choice','prompt':'TODO: choose a response for this intent','options':[{'id':identifier+'-yes','text':text},{'id':identifier+'-no','label':'No'}],'correctOptionId':identifier+'-yes','explanation':'TODO: explain intent and distractor'},
            {'id':identifier+'-reading','type':'reading','prompt':'TODO: read the expression','stimulus':text,'answerRepresentation':'kana','acceptedAnswers':[{'type':'kana','text':text}],'explanation':'TODO: explain reading'},
            {'id':identifier+'-completion','type':'completion','prompt':'TODO: fill a constrained blank','template':'{blank}。','acceptedAnswers':[{'surface':'はい','reading':'はい','translation':'Yes'}],'expectedCompletedExample':text,'explanation':'TODO: explain accepted variants'},
            {'id':identifier+'-production','type':'production','prompt':'TODO: produce a reply in your own words','exampleResponses':[text],'criteria':['TODO: communication criterion']}],
        'rolePlay':{'task':'TODO: a realistic objective','criteria':['TODO: communication criterion'],'hints':['TODO: removable support'],'examples':[text]},
        'reviewItems':[{'id':identifier+'-review','targetKind':'phrase','targetId':identifier+'-phrase','skill':'TODO: recall skill','direction':'meaning-to-japanese'}],
        'conversationGraph':{'entryNodeId':identifier+'-prompt','nodes':[{'id':identifier+'-prompt','type':'prompt','speakerId':identifier+'-speaker','text':text,'nextNodeId':identifier+'-end'},{'id':identifier+'-end','type':'terminal','message':'TODO: explain the next practice step'}]},
        'review':{'status':'unreviewed','reviewerType':'agent','reviewDate':datetime.now(timezone.utc).date().isoformat(),'reviewNote':identifier+'.md','provenance':'CLI scaffold; placeholder text, not reviewed curriculum. No external material copied.','rights':'unknown','rightsBasis':'TODO: document original-material or permission basis'}}


def validate_draft(document, kind='lesson'):
    schema = validator.LESSON if kind == 'lesson' else validator.spec({'id':validator.IDENT,'title':validator.TEXT,'description':validator.TEXT}) if kind == 'topic' else validator.spec({'id':validator.IDENT,'path':validator.TEXT,'transcript':validator.TEXT,'permissionNote':validator.TEXT})
    validator.validate(document,schema,'draft:$')
    if kind == 'lesson': validator.local(document,'draft:$'); validator.references(document,'draft:$')


def promote(draft, public=PUBLIC, reviews=REVIEWS, topic=None):
    """Validate a complete staged release before any public write. Never changes review claims automatically."""
    document = copy.deepcopy(draft)
    validate_draft(document)
    if re.search(r'\b(?:TODO|TBD)\b',json.dumps(document),re.I): raise ValueError('Resolve all scaffold placeholders before promotion')
    if document['review']['status'] != 'reviewed' or document['review']['rights'] != 'publishable': raise ValueError('Explicit review and rights are required')
    validator.check(public,reviews)
    with tempfile.TemporaryDirectory(prefix='hiragame-content-') as temp:
        stage = Path(temp) / 'content'; shutil.copytree(public,stage)
        catalog = json.loads((stage/'catalog.json').read_text())
        if topic:
            validate_draft(topic,'topic')
            if any(t['id']==topic['id'] for t in catalog['topics']): raise ValueError('Topic already exists')
            catalog['topics'].append(topic)
        version = catalog['contentVersion'] + 1
        catalog['contentVersion'] = version; document['contentVersion'] = version
        entry = next((e for e in catalog['entries'] if e['id']==document['id']),None)
        if entry and (entry['kind']!='lesson' or entry['topicId']!=document['topicId']): raise ValueError('Stable ID ownership cannot change')
        if entry is None:
            entry = {'id':document['id'],'topicId':document['topicId'],'kind':'lesson','path':f'lessons/{document["id"]}.json'}
            catalog['entries'].append(entry)
        for old in catalog['entries']:
            path=stage/old['path']
            if path.is_file():
                path.write_text(re.sub(r'"contentVersion"\s*:\s*\d+',f'"contentVersion": {version}',path.read_text(),count=1))
        (stage/entry['path']).parent.mkdir(parents=True,exist_ok=True)
        (stage/entry['path']).write_text(json.dumps(document,ensure_ascii=False,indent=2)+'\n')
        (stage/'catalog.json').write_text(json.dumps(catalog,ensure_ascii=False,indent=2)+'\n')
        validator.check(stage,reviews)
        files=sorted(p for p in stage.rglob('*') if p.is_file() and p.name!='catalog.json')+[stage/'catalog.json']
        originals={}
        try:
            for source in files:
                target=public/source.relative_to(stage)
                data=source.read_bytes()
                if target.is_file() and target.read_bytes()==data: continue
                originals[target]=target.read_bytes() if target.exists() else None
                target.parent.mkdir(parents=True,exist_ok=True)
                temporary=target.with_suffix(target.suffix+'.promoting')
                temporary.write_bytes(data); temporary.replace(target)
        except OSError:
            for target,data in originals.items():
                if data is None: target.unlink(missing_ok=True)
                else: target.write_bytes(data)
                target.with_suffix(target.suffix+'.promoting').unlink(missing_ok=True)
            raise
        return entry['path'],version


def main():
    parser=argparse.ArgumentParser(description=__doc__); commands=parser.add_subparsers(dest='command',required=True)
    new=commands.add_parser('new');new.add_argument('--id',required=True);new.add_argument('--topic-id',default='workplace-clarification');new.add_argument('--title',required=True);new.add_argument('--kind',choices=['lesson','topic','audio'],default='lesson')
    check=commands.add_parser('validate-draft');check.add_argument('file',type=Path);check.add_argument('--kind',choices=['lesson','topic','audio'],default='lesson')
    promotion=commands.add_parser('promote');promotion.add_argument('file',type=Path);promotion.add_argument('--topic-draft',type=Path)
    args=parser.parse_args()
    try:
        if args.command=='new':
            output=DRAFTS/f'{args.id}.{args.kind}.json';output.parent.mkdir(parents=True,exist_ok=True)
            with output.open('x') as file: json.dump(scaffold(args.id,args.topic_id,args.title,args.kind),file,ensure_ascii=False,indent=2);file.write('\n')
            print(output.relative_to(ROOT))
        else:
            if args.file.is_symlink() or not args.file.resolve().is_relative_to(DRAFTS.resolve()): raise ValueError('Use a regular file in content-source/drafts')
            data=json.loads(args.file.read_text())
            if args.command=='validate-draft': validate_draft(data,args.kind);print('Draft structure valid; no review or publication implied')
            else:
                topic=json.loads(args.topic_draft.read_text()) if args.topic_draft else None
                path,version=promote(data,topic=topic);print(f'Promoted reviewed {path}; coherent content version {version}')
    except (ValueError,OSError,validator.Invalid) as error: parser.exit(1,str(error)+'\n')


if __name__=='__main__': main()
