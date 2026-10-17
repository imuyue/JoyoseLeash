# Compatibility

[简体中文](COMPATIBILITY.md) | English

## Tested environment

| Item | Environment |
| --- | --- |
| Device | Xiaomi 2410DPN6CC / haotian |
| ROM | OS4.0.0.17.XOBCNXM |
| Android | API 37 |
| Joyose | 2.5.21 |
| Root and hooks | KernelSU, Zygisk Next, LSPosed |
| Test games | Subway Surfers, `com.kiloo.subwaysurf.gw`; PUBG Mobile, `com.tencent.ig` |

The minimum installation API is 26. This is only the installation and compilation baseline. Current hooks target the internal structures of the Joyose / ROM versions above; other devices and versions require separate verification.

## Rules and frame rates

In prior testing, Joyose repeatedly wrote `/data/system/mcd/df` according to board-temperature rules. The original MGAME temperature table was `10:0,38:90,42.8:60`. A one-time manual change to `df` was overwritten by subsequent sampling.

Earlier testing set `dynamic_fps=10:144` and `dynamic_fps_M=10:144` for Subway Surfers. During a confirmed running interval, system presentation measured approximately 139.98–143.31 FPS, with a median of 142.18 FPS, while `df` remained 144. This historical record covers only the device, ROM, rules, and running interval above; it is not a new test result for 0.3.3.

Adding a game supplies the `scene_ovrride` array required by the parser and retains other original fields. Fields and rule libraries absent from the original database may be added; whether the system uses them depends on the corresponding consumer. Configured frame rates are not equivalent to actual game rendering rates.

## Interface language

0.3.3 Beta (versionCode 9) supports Chinese and English, following the system locale. Raw field keys, user input, named-field names, and rule data remain unchanged. Switching languages does not convert existing data.

## System cloud rules and field structure

The system-cloud entry only browses sources and saves local drafts. It does not write system databases or caches, and system-rule overrides are not supported. Source details show read failures. ADB shell access does not establish that the app’s execution environment has the same access.

Named fields in lists are stored as single-key objects. Converting a scalar to a named field changes the list item’s type; confirm that its consumer supports the structure. Unknown fields are not guaranteed to take effect.

## Network limits

- Discovered cloud-server IPv4 addresses are redirected to loopback through kernel NAT. Domains are blocked through hosts and adapted Java request / DNS entry points.
- A static list cannot anticipate every future server address. Native request paths or system-client updates may require new adaptation.
- The tested kernel has no IPv6 NAT table. IPv6 domains are handled through hosts and Java hooks; direct native connections to IPv6 literals are not fully covered by kernel loopback rules.
- `resolver.msg.xiaomi.net` is also used for Xiaomi push notifications. Address-discovery blocking can be disabled separately.
- Manual rule fetching reuses system clients and normal TLS validation. Network access is temporarily allowed for up to 90 seconds, then blocking is restored.

## Data and recovery

- Game data is not cleared; original Xiaomi rule databases are opened read-only.
- Saved rules: `/data/user/0/com.muyue.joyoseleash/files/state.json`.
- Unapplied draft: `native-draft.json` in the same directory.
- Automatic backups: `/data/adb/cloudruleguard/history/`.
- Manual downloads: `download-joyose-*.json` and `download-settings-*.json` in the app’s private directory. They do not overwrite original system databases.
- Uninstalling the KSU module removes its network blocking and local overrides while retaining the manager APK and rule backups.
- With rule locking disabled, subsequent system reloads may change the active rules. The manager reports version changes.

## Migration from the old package

Module installation automatically installs / updates `com.muyue.joyoseleash`. Missing `state.json`, `native-draft.json`, and `download-*.json` files are migrated from `dev.cloudrule.guard`. Old files, the old app, and existing files in the new package are neither deleted nor overwritten. Migration backups are stored in `/data/adb/cloudruleguard/legacy-package-backup/`. Installation failures, including signature mismatches, stop module installation and display an error; the installer does not bypass them by uninstalling the app or clearing data.

Grant the new package KernelSU Root access and enable its Joyose / HTML Viewer scope in LSPosed. Disable the old module after enabling the new one to prevent duplicate hooks.
