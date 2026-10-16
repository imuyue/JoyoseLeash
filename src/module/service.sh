#!/system/bin/sh
MODDIR=${0%/*}
DATA=/data/adb/cloudruleguard
mkdir -p "$DATA/inbox" "$DATA/net"
chmod 700 "$DATA" "$DATA/inbox" "$DATA/net"
until [ "$(getprop sys.boot_completed)" = 1 ]; do sleep 2; done
FILES=/data/user/0/com.muyue.joyoseleash/files
COUNT=0
until [ -d /data/user/0/com.muyue.joyoseleash ]; do
    COUNT=$((COUNT + 1)); [ "$COUNT" -gt 60 ] && exit 1; sleep 2
done
. "$MODDIR/apk.sh"
migrate_legacy_package >> "$DATA/package-migration.log" 2>&1 || exit 1
CLASSPATH="$MODDIR/backend.jar" app_process /system/bin com.muyue.joyoseleash.Backend init < /dev/null > "$DATA/init.log" 2>&1
CLASSPATH="$MODDIR/backend.jar" app_process /system/bin com.muyue.joyoseleash.Backend refresh-ips < /dev/null >> "$DATA/init.log" 2>&1
[ -d "$FILES" ] || exit 1
# Directory events survive atomic state.json replacement. No fixed-rate polling.
exec /data/adb/ksu/bin/busybox inotifyd "$MODDIR/observe.sh" "$FILES:wy"
