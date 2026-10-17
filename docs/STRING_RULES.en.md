# Structured string editing

[简体中文](STRING_RULES.md) | English

Some rule strings contain multiple parameters. The editor recognizes formats by field name and presents their parameters in native lists. It does not guess a different data type for arbitrary strings.

| Field | Editable components |
| --- | --- |
| `PID_M`, `PID_T` | Groups by policy frame rate and temperature band; temperature pairs, target / minimum frame rates, Kp / Ki, and Kd when present |
| `migt` | Apps, CPU frequencies (kHz), frame times for each frame rate, CPU markers, and numeric parameters |
| `support_highfps_app`, `novatek_gex_fps_limit` | Apps / groups and frame rates |
| `recommend_tgame_thresh` | Temperatures and recommended frame rates |
| `support_scale_app_list` | App lists, including `app:scale` format |
| `force_scale_app_list` | Apps, DPI / scale, and Activities, preserving unknown additional parameters |
| Adapted lists such as `game_list` and `package_list` | Apps / groups within a single string |
| `reset_game_min_duration` | Apps and durations |
| `dynamic_fps`, `dynamic_fps_M`, `dynamic_fps_multiWin`, `dynamic_fps_multiWin_M` | Temperatures and frame rates; the common frame-rate page also provides quick values and temperature-band editing |
| `cmd` | Semicolon-separated command segments and hash-separated parameters; no assumptions about unverified resource ID meanings |

Edits replace only the corresponding character ranges, preserving whitespace, separators, unrecognized fragments, and other groups. Invalid rows do not prevent valid sibling fields from being parsed. Numeric validation checks types, ranges, duplicate bands, and temperature order. Text parameters reject separator injection and control characters. Editing changes only the local draft; only “Save and apply” triggers backend application.

`force_scale_app_list` supports `app:DPI#scale[;DPI#scale][:Activity[;Activity][:additional parameters]]`. Groups, fields, and warnings are paginated at 40 items. Original text, unparsed fragments, and their positions can be inspected; unknown formats retain the raw-string editor.

PID temperature pairs retain the A / B labels without assuming unverified trigger / exit semantics across system versions. MIGT numeric parameters with undetermined meanings retain numeric labels. Unknown formats remain editable as single raw values, not as a full JSON text area.

## JoyCtl attribution

Field names, `getComment` matching, and PID / MIGT splitting are adapted from [JoyCtl `comments.js`](https://github.com/hexwander/joyctl/blob/b16187b2c49bd357d9704926ef9d401ccb3ca18b/src/lib/comments.js), licensed under the MIT License. See [Third-party notices](../THIRD_PARTY_NOTICES.en.md#joyctl) for copyright and the original text. Platform-specific interpretations in JoyCtl are not treated as verified behavior on the tested device. MGAME / TGAME labels remain distinguished by the device adapter.

```sh
python tools/check_rules.py
python tools/check_installer.py
```

Host-side checks do not connect to a phone. Native pages, Root request counts, and actual migration are checked separately on-device; see [Development](DEVELOPMENT.en.md).
