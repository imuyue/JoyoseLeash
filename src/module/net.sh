#!/system/bin/sh
set -u
DATA=/data/adb/cloudruleguard
NET=$DATA/net
BB=/data/adb/ksu/bin/busybox
CHAIN=CRG_CLOUD
TAG=cloudruleguard_output
mkdir -p "$NET"
if [ "${1:-}" != inner ]; then
    exec "$BB" flock -x "$DATA/network.lock" /system/bin/sh "$0" inner "${1:-status}"
fi
shift
ACTION=${1:-status}
case "$ACTION" in block|open|dns-window|status) ;; *) exit 2;; esac
initns() { "$BB" nsenter -t 1 -m -- "$@"; }
own_mount() { initns cat /proc/self/mountinfo | grep ' /system/etc/hosts ' | grep -q '/cloudruleguard/net/hosts.blocked'; }
unmount_own() { if own_mount; then initns umount /system/etc/hosts || return 1; fi; }
clear_chain() {
    BIN=$1
    while "$BIN" -w 3 -t nat -C OUTPUT -m comment --comment "$TAG" -j "$CHAIN" >/dev/null 2>&1; do
        "$BIN" -w 3 -t nat -D OUTPUT -m comment --comment "$TAG" -j "$CHAIN" || return 1
    done
    # The exact chain is ours only after this module created its ownership marker.
    if [ -f "$NET/owns-chains" ]; then
        "$BIN" -w 3 -t nat -F "$CHAIN" >/dev/null 2>&1 || true
        "$BIN" -w 3 -t nat -X "$CHAIN" >/dev/null 2>&1 || true
    fi
}
apply_chain() {
    BIN=$1; LIST=$2; LOOP=$3
    if "$BIN" -w 3 -t nat -L "$CHAIN" >/dev/null 2>&1; then
        [ -f "$NET/owns-chains" ] || { echo '同名防火墙链已存在，拒绝覆盖'; return 1; }
    else
        "$BIN" -w 3 -t nat -N "$CHAIN" || return 1
    fi
    touch "$NET/owns-chains"
    "$BIN" -w 3 -t nat -F "$CHAIN" || return 1
    while IFS= read -r IP || [ -n "$IP" ]; do
        [ -n "$IP" ] || continue
        "$BIN" -w 3 -t nat -A "$CHAIN" -d "$IP" -j DNAT --to-destination "$LOOP" || return 1
    done < "$LIST"
    "$BIN" -w 3 -t nat -C OUTPUT -m comment --comment "$TAG" -j "$CHAIN" >/dev/null 2>&1 ||
        "$BIN" -w 3 -t nat -I OUTPUT 1 -m comment --comment "$TAG" -j "$CHAIN" || return 1
}
restore_hosts_content() {
    # App mount namespaces can retain an inherited bind after init unmounts it.
    # Keep the bound inode and restore its contents before opening the window.
    if [ -f "$NET/hosts.base" ] && [ -f "$NET/hosts.blocked" ]; then
        cat "$NET/hosts.base" > "$NET/hosts.blocked" || return 1
    fi
}
if [ "$ACTION" = dns-window ]; then restore_hosts_content && unmount_own; exit $?; fi
V4=false; V6=false; HOSTS=false; WANT=false
if [ "$ACTION" = block ]; then
    WANT=true
    unmount_own || exit 1
    # Capture the currently visible hosts file, preserving other modules' entries.
    initns cat /system/etc/hosts > "$NET/hosts.base" || exit 1
    cp "$NET/hosts.base" "$NET/hosts.blocked" || exit 1
    printf '\n# JoyoseLeash managed entries\n' >> "$NET/hosts.blocked"
    cat "$NET/host-entries" >> "$NET/hosts.blocked"
    chmod 644 "$NET/hosts.blocked"
    initns mount --bind "$NET/hosts.blocked" /system/etc/hosts && HOSTS=true
    apply_chain /system/bin/iptables "$NET/ips4" 127.0.0.1 && V4=true
    # Some Xiaomi kernels ship ip6tables without a nat table. IPv6 remains
    # covered by the hosts mapping and Java DNS/request hooks in that case.
    if /system/bin/ip6tables -t nat -L >/dev/null 2>&1; then
        apply_chain /system/bin/ip6tables "$NET/ips6" ::1 && V6=true
    else
        echo 'IPv6 nat table unavailable; Java hooks and hosts mapping remain active.' >&2
    fi
elif [ "$ACTION" = open ]; then
    restore_hosts_content || exit 1
    unmount_own || exit 1
    clear_chain /system/bin/iptables || exit 1
    if /system/bin/ip6tables -t nat -L >/dev/null 2>&1; then clear_chain /system/bin/ip6tables || exit 1; fi
else
    own_mount && HOSTS=true
    /system/bin/iptables -w 3 -t nat -C OUTPUT -m comment --comment "$TAG" -j "$CHAIN" >/dev/null 2>&1 && V4=true
    /system/bin/ip6tables -w 3 -t nat -C OUTPUT -m comment --comment "$TAG" -j "$CHAIN" >/dev/null 2>&1 && V6=true
fi
N4=$(grep -c . "$NET/ips4" 2>/dev/null || true); N6=$(grep -c . "$NET/ips6" 2>/dev/null || true)
[ -n "$N4" ] || N4=0; [ -n "$N6" ] || N6=0
printf '{"requestedBlocked":%s,"hostsMounted":%s,"ipv4Nat":%s,"ipv6Nat":%s,"ipv4Count":%s,"ipv6Count":%s,"time":%s}\n' "$WANT" "$HOSTS" "$V4" "$V6" "$N4" "$N6" "$(date +%s)" > "$DATA/network-status.json"
cat "$DATA/network-status.json"
if [ "$ACTION" = block ] && { [ "$HOSTS" != true ] || [ "$V4" != true ]; }; then
    echo 'IPv4或hosts网络层未成功；请在管理界面查看，钩子入口与防火墙状态分别报告。' >&2
    exit 1
fi
