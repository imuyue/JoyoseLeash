#!/bin/sh
set -eu
SOURCE=$1
WORK=$2
mkdir -p "$WORK/old" "$WORK/new" "$WORK/backup"
. "$SOURCE"

chown() { [ "${FAIL_CHOWN:-0}" = 0 ]; }
restorecon() { return 0; }
pm() {
    printf '%s\n' "$*" >> "$WORK/pm-args"
    if [ "$1" = list ]; then
        echo 'package:com.muyue.joyoseleash uid:10234'
    elif [ "${INSTALL_FAIL:-0}" = 1 ]; then
        echo 'Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE]' >&2
        return 1
    else
        echo Success
    fi
}

printf '%s' '{"profiles":{"original":true}}' > "$WORK/old/state.json"
printf '%s' '{"draft":"keep"}' > "$WORK/old/native-draft.json"
printf '%s' '{"rules":[]}' > "$WORK/old/download-joyose-7.json"
printf '%s' '{"rules":[]}' > "$WORK/old/download-settings-8.json"
printf '%s' 'ignore' > "$WORK/old/download-joyose-bad.json"
printf '%s' 'stale hook report' > "$WORK/old/hook-joyose.json"
printf '%s' 'fake package for command testing' > "$WORK/JoyoseLeash.apk"

install_manager_apk "$WORK/JoyoseLeash.apk"
[ "$(manager_uid)" = 10234 ]
grep -F -- 'install --user 0 -r ' "$WORK/pm-args" > /dev/null
migrate_manager_files "$WORK/old" "$WORK/new" "$WORK/backup" 10234
for NAME in state.json native-draft.json download-joyose-7.json download-settings-8.json; do
    cmp "$WORK/old/$NAME" "$WORK/new/$NAME"
    cmp "$WORK/old/$NAME" "$WORK/backup/$NAME"
done
[ ! -e "$WORK/new/download-joyose-bad.json" ]
[ ! -e "$WORK/new/hook-joyose.json" ]

# A second installation may add missing files but never overwrite new app data.
printf '%s' 'new app state' > "$WORK/new/state.json"
printf '%s' 'new app draft' > "$WORK/new/native-draft.json"
migrate_manager_files "$WORK/old" "$WORK/new" "$WORK/backup" 10234
[ "$(cat "$WORK/new/state.json")" = 'new app state' ]
[ "$(cat "$WORK/new/native-draft.json")" = 'new app draft' ]
[ "$(cat "$WORK/old/native-draft.json")" = '{"draft":"keep"}' ]

INSTALL_FAIL=1
if install_manager_apk "$WORK/JoyoseLeash.apk" > "$WORK/failure.log" 2>&1; then exit 1; fi
grep -F 'INSTALL_FAILED_UPDATE_INCOMPATIBLE' "$WORK/failure.log" > /dev/null
[ "$(cat "$WORK/new/state.json")" = 'new app state' ]
if grep -E 'uninstall|clear' "$WORK/pm-args"; then exit 1; fi
if migrate_manager_files "$WORK/old" "$WORK/new" "$WORK/backup" invalid > /dev/null 2>&1; then exit 1; fi
mkdir "$WORK/protected"
FAIL_CHOWN=1
if migrate_manager_files "$WORK/old" "$WORK/protected" "$WORK/backup" 10234; then exit 1; fi
[ ! -e "$WORK/protected/state.json" ]
echo 'Installer checks passed: auto-install/update, failure output, migration, existing data, invalid UID, permission failure.'
