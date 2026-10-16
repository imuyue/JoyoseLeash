"""Record a user-operated game run, including focus, real presentations and df."""
from device import ROOT,EVIDENCE,adb_args
import argparse,json,re,shlex,subprocess,time

def shell(command):
    p=subprocess.run(adb_args('shell',command),capture_output=True,encoding='utf-8',errors='replace',timeout=15)
    return p.stdout+p.stderr

parser=argparse.ArgumentParser();parser.add_argument('--seconds',type=int,default=180);args=parser.parse_args()
EVIDENCE.mkdir(parents=True,exist_ok=True)
(ROOT/'build').mkdir(exist_ok=True)
stop=ROOT/'build/stop-recording';stop.unlink(missing_ok=True)
started=time.monotonic();samples=[];last_layer='';last_shot=0
print('Recording started',flush=True)
try:
    while time.monotonic()-started<args.seconds and not stop.exists():
        elapsed=time.monotonic()-started
        focus=shell('dumpsys window | grep mCurrentFocus').strip()
        sample={'elapsed':round(elapsed,2),'focus':focus}
        reading=shell('su -c '+shlex.quote("cat /data/system/mcd/df; printf '\\n'; cat /sys/class/thermal/thermal_message/board_sensor_temp")).strip()
        sample['limit_and_temperature']=reading
        if 'com.kiloo.subwaysurf.gw' in focus:
            lines=shell('dumpsys SurfaceFlinger --list').splitlines()
            candidates=[x for x in lines if 'com.kiloo.subwaysurf.gw' in x and 'SurfaceView' in x and '(BLAST)' in x]
            if candidates:
                layer=candidates[-1];match=re.search(r'RequestedLayerState\{(.+?) parentId=',layer)
                layer=match.group(1) if match else layer.strip()
                points=[]
                for line in shell('dumpsys SurfaceFlinger --latency '+shlex.quote(layer)).splitlines():
                    fields=line.split()
                    if len(fields)==3 and all(x.isdigit() for x in fields):
                        value=int(fields[1])
                        if 0<value<2**63-1:points.append(value)
                points=sorted(set(points));sample['lastPresent']=points[-1] if points else None
                if len(points)>1:sample['windowFps']=round((len(points)-1)*1e9/(points[-1]-points[0]),2)
                if elapsed-last_shot>=5:
                    last_shot=elapsed
                    p=subprocess.run(adb_args('exec-out','screencap','-p'),capture_output=True,timeout=15)
                    if not p.returncode:(EVIDENCE/('module-run-%03d.png'%elapsed)).write_bytes(p.stdout)
        samples.append(sample)
        (EVIDENCE/'module-run-live.json').write_text(json.dumps(samples,ensure_ascii=False,indent=2),encoding='utf-8')
        time.sleep(1)
finally:
    (EVIDENCE/'module-run-live.json').write_text(json.dumps(samples,ensure_ascii=False,indent=2),encoding='utf-8')
    print('Recording saved: '+str(len(samples))+' samples',flush=True)
