"""Verify atomic apply and history replacement; restore the starting rule."""
import copy,json,time
from device import ROOT,EVIDENCE,rpc,compact

def record(name,value):
    EVIDENCE.mkdir(parents=True,exist_ok=True)
    (EVIDENCE/name).write_text(json.dumps(value,ensure_ascii=False,indent=2),encoding='utf-8')

def ack(version):
    for i in range(12):
        d=rpc('status');h=d['hookJoyose']
        if h.get('appliedNonce')==d['state']['applyNonce'] and h.get('activeVersion')==version and not h.get('error'):return d
        time.sleep(.5)
    raise RuntimeError(json.dumps(compact(d),ensure_ascii=False))

initial=rpc('status');record('native-backend-before.json',initial)
original=copy.deepcopy(initial['state']['profiles']);version=original['booster_config']['version']
library=rpc('library');history=rpc('history')
assert library and history and all('params' not in x for x in library)
assert all('modules' in x for x in history)
assert set(x['kind'] for x in library)<={'system','rom','cloud'}
assert {'rom','cloud'}<=set(x['kind'] for x in library)
entry=next(x for x in library if x['module']=='booster_config')
preview=rpc('library-rule',{'kind':entry['kind'],'id':entry['id'],'module':entry['module']})
assert preview['version']==entry['version'] and 'params' in preview
summary={'classifiedMetadata':True,'previewRead':True,'historyVersions':True}
restore_id=None
try:
    proposed=copy.deepcopy(original);proposed['booster_config']['version']=version-1
    rpc('commit',{'profiles':proposed,'baseProfiles':original,'locked':initial['state']['locked']})
    restore_id=rpc('history')[0]['id']
    changed=ack(version-1);summary['atomicCommitApplied']=True
    response=rpc('replace-rule',{'kind':'history','id':restore_id,'module':'booster_config','baseProfiles':changed['state']['profiles']})
    restored=ack(version);assert restored['state']['profiles']==original
    summary['historyReplacedAndApplied']=True;restore_id=None
    before=restored['state']['revision']
    try:rpc('commit',{'profiles':proposed,'baseProfiles':proposed})
    except RuntimeError:summary['staleEditorRejected']=True
    else:raise AssertionError('Stale editor was accepted')
    final=rpc('status');assert final['state']['revision']==before and final['state']['profiles']==original
    assert final['state']['locked']==initial['state']['locked'] and final['state']['networkBlocked']==initial['state']['networkBlocked']
    summary['originalProfilesRestored']=True;summary['final']=compact(final)
finally:
    if restore_id:
        current=rpc('status')
        rpc('replace-rule',{'kind':'history','id':restore_id,'module':'booster_config','baseProfiles':current['state']['profiles']})
        ack(version)
    record('native-backend-checks.json',summary)
print(json.dumps(summary,ensure_ascii=False,indent=2),flush=True)
