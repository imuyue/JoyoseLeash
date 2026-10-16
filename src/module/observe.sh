#!/system/bin/sh
case "$3" in state.json|fetch-joyose.json|fetch-settings.json) ;; *) exit 0;; esac
MODDIR=${0%/*}
CLASSPATH="$MODDIR/backend.jar" app_process /system/bin com.muyue.joyoseleash.Backend reconcile < /dev/null > /data/adb/cloudruleguard/reconcile.log 2>&1
