"""Local ADB helpers. Send root scripts as files to avoid nested shell quoting."""
import json, subprocess, uuid
from toolchain import ROOT, BUILD, EVIDENCE, setting

ADB=setting('ADB','adb','adb')
SERIAL=setting('ANDROID_SERIAL','adb_serial','')

def adb_args(*args):
    return [ADB]+(['-s',SERIAL] if SERIAL else [])+list(map(str,args))

def adb(*args,timeout=90):
    p=subprocess.run(adb_args(*args),capture_output=True,encoding='utf-8',errors='replace',timeout=timeout)
    if p.returncode:raise RuntimeError(p.stdout+p.stderr)
    return p.stdout

def root(script,timeout=90):
    BUILD.mkdir(parents=True,exist_ok=True)
    name='crg-'+uuid.uuid4().hex+'.sh'
    local=ROOT/'build'/name
    remote='/data/local/tmp/'+name
    local.write_text('#!/system/bin/sh\n'+script+'\n',encoding='utf-8',newline='\n')
    try:
        adb('push',local,remote)
        adb('shell','chmod','755',remote)
        return adb('shell','su','-c',remote,timeout=timeout)
    finally:
        try:adb('shell','rm','-f',remote)
        except Exception:pass
        local.unlink(missing_ok=True)

def rpc(action,payload=None):
    BUILD.mkdir(parents=True,exist_ok=True)
    if not action.replace('-','').isalpha():raise ValueError('Invalid action')
    name='crg-request-'+uuid.uuid4().hex+'.json'
    local=ROOT/'build'/name;remote='/data/local/tmp/'+name
    local.write_text(json.dumps(payload or {},ensure_ascii=False),encoding='utf-8')
    try:
        adb('push',local,remote)
        output=root('CLASSPATH=/data/adb/modules/cloudruleguard/backend.jar app_process /system/bin com.muyue.joyoseleash.Backend '+action+' < '+remote)
    finally:
        try:adb('shell','rm','-f',remote)
        except Exception:pass
        local.unlink(missing_ok=True)
    for line in reversed(output.splitlines()):
        try:
            value=json.loads(line)
            if 'ok' in value:
                if not value['ok']:raise RuntimeError(value.get('error',line))
                return value['data']
        except json.JSONDecodeError:pass
    raise RuntimeError(output)

def compact(d):
    state=d.get('state',{})
    return {**{k:state.get(k) for k in ['schema','revision','enabled','locked','networkBlocked','applyNonce','fetchNonce','fetchUntil']},
            **{k:d.get(k) for k in ['hookJoyose','hookSettings','network','fetchJoyose','fetchSettings','df','boardTemp']}}

if __name__=='__main__':
    import sys
    result=rpc(sys.argv[1] if len(sys.argv)>1 else 'status')
    print(json.dumps(compact(result) if 'state' in result else result,ensure_ascii=False,indent=2))
