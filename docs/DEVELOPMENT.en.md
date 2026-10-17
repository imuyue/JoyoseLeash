# Development and verification

[简体中文](DEVELOPMENT.md) | English

## Layout

| Path | Contents |
| --- | --- |
| `src/java/` | Native interface, LSPosed hooks, Root backend, and communication |
| `src/module/` | KernelSU installation, background, and network scripts |
| `src/res/`, `src/assets/` | Android resources and Xposed entry point |
| `tests/android/` | On-device checks for the native interface and rule model |
| `tests/java/`, `tests/shell/` | Host-side checks for string parsing and installation migration |
| `licenses/` | Complete retained third-party license texts |
| `tools/` | Build, source-export, and device-development tools |
| `docs/` | Build, compatibility, and verification documentation |
| `build/`, `dist/` | Ignored build outputs |
| `.local/` | Ignored dependencies, signing keys, device data, and debugging records |

## Component responsibilities

- `ManagerActivity`, `NativeUi`, `FpsEditor`, `JsonValueEditor`: native pages and local editing drafts.
- `RuleModel`: field types, frame-rate rules, and temperature-band validation.
- `StringRules`, `StringRuleEditor`: structured parsing and native editing of recognized strings; serialization replaces only changed fragments.
- `RuleFields`: JoyCtl field names and path matching, retaining MGAME / TGAME meanings from the current Joyose adapter.
- `RootClient`, `StateProvider`, `Protocol`: communication between the manager, Root backend, and hooks.
- `Backend`: rule reading, commits, historical backups, network configuration, and read-only system cloud catalogs.
- `HookEntry`: Joyose / HTML Viewer adaptation, rule application, request blocking, and manual fetching.
- `net.sh`: hosts mappings and kernel loopback rules; `service.sh`, `observe.sh`, `lease.sh`: startup, file notifications, and restoration after temporary network access.

## Device connection

Development tools use `adb` on PATH. Set `ADB` to select an executable and `ANDROID_SERIAL` to select a device. Without a serial number, device selection is left to ADB. The local `.local/config.json` also accepts `adb` and `adb_serial`.

```sh
python tools/device.py status
```

Status output and screenshots may contain personal device information. Test results are stored in `.local/evidence/` and excluded from public directories.

## Host-side checks

```sh
python tools/check_rules.py
python tools/check_installer.py
```

The first checks six- and seven-field PID formats, MIGT, app parameters, preservation of unknown fragments, and rejection of invalid values. The second uses a simulated package manager to check APK installation / updates, installation failures, and idempotent data migration without connecting to a phone. Installer checks on Windows require Git for Windows Bash.

Run `python tools/check_i18n.py` separately for translation resources. See [Application translations](LOCALIZATION.en.md) for standard directories, arguments, and adding languages.

## UI checks

```sh
python tools/build_native_tests.py
adb install -r build/native-tests/checks.apk
adb shell am instrument -w com.muyue.joyoseleash.qa/com.muyue.joyoseleash.qa.NativeChecks
adb uninstall com.muyue.joyoseleash.qa
```

The test APK must use the same signing key as the main app. It occupies the device interface while running, then restores both ordinary and system-cloud drafts to their original bytes and existence states without applying test rules. Device checks assume Subway Surfers is installed, historical backups exist, and five common cloud backup records are available. Adjust acceptance data for other devices. The report is stored at `files/native-qa/report.json` in the main app’s private directory. Uninstall only the `.qa` test app.

`python tools/test_cloud_catalog.py` checks catalogs, fingerprints, types, and format boundaries with Android in-memory fixtures. It does not modify system sources.

## Other tools

| Script | Behavior |
| --- | --- |
| `measure_fps.py LABEL --package PACKAGE --seconds 20` | Read SurfaceFlinger frame presentation data; `--screenshots` includes screenshots |
| `record_run.py --seconds 60` | Collect Subway Surfers frame rates, focus, df, and temperature, including screenshots |
| `verify_native_backend.py` | Commit a test version on the development device, verify historical replacement and conflicts, then restore rules |
| `deploy_live.py --live` | Development hot deployment: install the module and APK, synchronize active files, and restart Joyose / HTML Viewer |

Backend checks and hot deployment change device state. They are not run automatically during source cleanup or ordinary builds. Use the KernelSU manager for normal installation.

## Verification scope

Existing verification records cover field parsing, installation migration, system-cloud fixtures, and native UI checks. The earlier UI regression includes 27 actual creation flows covering six field types, renaming and reordering, pagination, type preservation, and reopening saved data.

In those earlier checks, local editing, cached navigation, and idle checks added no Root requests; draft and rule-source data were compared before and after testing. These are historical records, not new test results for 0.3.3, and do not verify hook or frame-rate behavior on other ROMs. Device records remain in `.local/evidence/` and are not distributed with the source.
