#!/system/bin/sh
MODDIR=${0%/*}
# A hard deadline remains even if either hooked process exits during the fetch.
sleep 92
CLASSPATH="$MODDIR/backend.jar" app_process /system/bin com.muyue.joyoseleash.Backend reconcile < /dev/null > /data/adb/cloudruleguard/lease-expiry.log 2>&1
