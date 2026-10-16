#!/system/bin/sh
MODDIR=${0%/*}
CLASSPATH="$MODDIR/backend.jar" app_process /system/bin com.muyue.joyoseleash.Backend restore-network < /dev/null > /data/adb/cloudruleguard/uninstall.log 2>&1
CLASSPATH="$MODDIR/backend.jar" app_process /system/bin com.muyue.joyoseleash.Backend disable-override < /dev/null >> /data/adb/cloudruleguard/uninstall.log 2>&1
sh "$MODDIR/net.sh" open
# Keep the manager APK, its state and rollback copies so uninstalling the KSU
# module never implicitly deletes user-edited rules.
