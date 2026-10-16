#!/system/bin/sh
# Shared by the installer and boot service. Never uninstall either application.
install_manager_apk() {
    [ -f "$1" ] || { echo "缺少配套 JoyoseLeash.apk"; return 1; }
    JL_INSTALL_OUTPUT=$(pm install --user 0 -r "$1" 2>&1)
    JL_INSTALL_RESULT=$?
    if [ "$JL_INSTALL_RESULT" -ne 0 ]; then
        printf '%s\n' "$JL_INSTALL_OUTPUT"
        return "$JL_INSTALL_RESULT"
    fi
    case "$JL_INSTALL_OUTPUT" in *Success*) return 0 ;; esac
    printf '%s\n' "$JL_INSTALL_OUTPUT"
    return 1
}

manager_uid() {
    pm list packages -U com.muyue.joyoseleash | sed -n 's/^package:com\.muyue\.joyoseleash uid:\([0-9][0-9]*\)$/\1/p'
}

migrate_manager_files() {
    JL_OLD=$1
    JL_NEW=$2
    JL_BACKUP=$3
    JL_UID=$4
    case "$JL_UID" in ''|*[!0-9]*) echo "无法取得云控拴绳 UID"; return 1 ;; esac
    [ -d "$JL_OLD" ] || return 0
    umask 077
    mkdir -p "$JL_NEW" "$JL_BACKUP" || return 1
    chown "$JL_UID:$JL_UID" "$JL_NEW" || return 1
    chmod 700 "$JL_NEW" "$JL_BACKUP" || return 1
    for JL_SOURCE in "$JL_OLD/state.json" "$JL_OLD/native-draft.json" "$JL_OLD"/download-joyose-*.json "$JL_OLD"/download-settings-*.json; do
        [ -f "$JL_SOURCE" ] && [ ! -L "$JL_SOURCE" ] || continue
        JL_NAME=${JL_SOURCE##*/}
        case "$JL_NAME" in
            state.json|native-draft.json) ;;
            download-joyose-*.json|download-settings-*.json)
                JL_NONCE=${JL_NAME#download-joyose-}
                [ "$JL_NONCE" != "$JL_NAME" ] || JL_NONCE=${JL_NAME#download-settings-}
                JL_NONCE=${JL_NONCE%.json}
                case "$JL_NONCE" in ''|*[!0-9]*) continue ;; esac
                ;;
            *) continue ;;
        esac
        # Keep a root-private copy and leave the original application untouched.
        if [ ! -e "$JL_BACKUP/$JL_NAME" ] && [ ! -L "$JL_BACKUP/$JL_NAME" ]; then
            cp "$JL_SOURCE" "$JL_BACKUP/$JL_NAME" || return 1
            chmod 600 "$JL_BACKUP/$JL_NAME" || return 1
        fi
        [ ! -e "$JL_NEW/$JL_NAME" ] && [ ! -L "$JL_NEW/$JL_NAME" ] || continue
        JL_TEMP="$JL_NEW/$JL_NAME.migrating.$$"
        cp "$JL_SOURCE" "$JL_TEMP" || return 1
        chown "$JL_UID:$JL_UID" "$JL_TEMP" && chmod 600 "$JL_TEMP" || { rm -f "$JL_TEMP"; return 1; }
        mv -n "$JL_TEMP" "$JL_NEW/$JL_NAME" || { rm -f "$JL_TEMP"; return 1; }
        rm -f "$JL_TEMP"
    done
    restorecon -R "$JL_NEW" 2>/dev/null || true
}

migrate_legacy_package() {
    JL_APP_DIR=/data/user/0/com.muyue.joyoseleash
    [ -d "$JL_APP_DIR" ] || return 1
    migrate_manager_files /data/user/0/dev.cloudrule.guard/files "$JL_APP_DIR/files" /data/adb/cloudruleguard/legacy-package-backup "$(manager_uid)"
}
