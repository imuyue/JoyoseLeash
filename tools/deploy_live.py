"""Install the built module, then activate its files and restart only its targets."""
from device import ROOT,adb,root
import argparse
from toolchain import version as project_version

parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument("--live",action="store_true",help="Activate files immediately and restart Joyose/HTMLViewer")
args=parser.parse_args()
if not args.live:parser.error("This developer helper requires --live; normal users install the ZIP in KernelSU")

version=project_version()
archive=ROOT/'dist'/('JoyoseLeash-KSU-'+version+'.zip')
remote='/sdcard/Download/'+archive.name
print(adb('push',archive,remote))
print(adb('push',ROOT/'dist'/('JoyoseLeash-'+version+'.apk'),'/sdcard/Download/JoyoseLeash-'+version+'.apk'))
print(root('''set -e
BASE=/data/adb/modules/cloudruleguard
DATA=/data/adb/cloudruleguard
STAMP=$(date +%s)
mkdir -p "$DATA/deploy-backup-$STAMP"
cp -a "$BASE/." "$DATA/deploy-backup-$STAMP/"
if [ -f /data/user/0/com.muyue.joyoseleash/files/state.json ]; then
    cp /data/user/0/com.muyue.joyoseleash/files/state.json "$DATA/deploy-backup-$STAMP/state-before.json"
elif [ -f /data/user/0/dev.cloudrule.guard/files/state.json ]; then
    cp /data/user/0/dev.cloudrule.guard/files/state.json "$DATA/deploy-backup-$STAMP/state-before.json"
fi
/data/adb/ksu/bin/ksud module install /sdcard/Download/@ARCHIVE@
NEXT=/data/adb/modules_update/cloudruleguard
[ -f "$NEXT/backend.jar" ] || { echo 'No staged module; leaving current files unchanged'; exit 1; }
for name in backend.jar net.sh service.sh observe.sh lease.sh uninstall.sh action.sh apk.sh module.prop JoyoseLeash.apk; do
    cp "$NEXT/$name" "$BASE/$name"
done
mkdir -p "$BASE/licenses"
cp "$NEXT/LICENSE" "$BASE/LICENSE"
cp "$NEXT/licenses/JoyCtl-MIT.txt" "$BASE/licenses/JoyCtl-MIT.txt"
# The native manager replaces the old module WebUI. Remove only our three files.
rm -f "$BASE/webroot/index.html" "$BASE/webroot/app.js" "$BASE/webroot/style.css"
rmdir "$BASE/webroot" 2>/dev/null || true
chmod 755 "$BASE/"*.sh
# Stop only this module's existing directory watcher, then start the new one.
for PID in $(pidof busybox); do
    CMD=$(tr '\\000' ' ' < /proc/$PID/cmdline)
    case "$CMD" in *"inotifyd $BASE/observe.sh "*) kill "$PID";; esac
done
nohup /system/bin/sh "$BASE/service.sh" > "$DATA/service.log" 2>&1 < /dev/null &
# Killing exact target processes lets Android restart them without force-stop state.
for PKG in com.xiaomi.joyose com.android.htmlviewer:remote; do
    for PID in $(pidof "$PKG"); do kill "$PID"; done
done
echo "Live update installed; backup: $DATA/deploy-backup-$STAMP"
sha256sum "$BASE/backend.jar"
'''.replace('@ARCHIVE@',archive.name),timeout=120))
