# Changelog

[简体中文](CHANGELOG.md) | English

## 0.3.3 Beta

- Rebuilt the manager UI: a single title and action bar on top, four native tabs at the bottom, and list rows that carry only a name, a state, and an action.
- Removed the stacked hint lines and caption blocks; the system-cloud entry lists only the three real sources, while refresh, the override switch, and connection status moved into the top menu.
- Rule history rows are titled by date with the version and game count as the subtitle; the system-cloud header now reads "Local draft · Not applied".
- The system-cloud preview shows an explicit message instead of null when the source declares no package name.
- Update the version to 0.3.3 and versionCode to 9.

## 0.3.2 Beta

- Add Chinese / English support to the app and documentation. The interface follows the system locale; raw field keys, user input, and rule data remain unchanged.
- Use standard Android strings, positional arguments, and plurals; add app-language metadata and translation checks, with standard language directories for additional translations.
- Update the version to 0.3.2 and versionCode to 8.

## 0.3.1 Beta

- Add a system-settings cloud-rule entry for read-only browsing and local draft editing of databases, common cloud backups, and window-management caches.
- Add named fields in lists, including creation, renaming, reordering, and explicit conversion of existing list items.
- Complete field action menus, fix long-press and rename access on switch rows, and distinguish booleans from “on / off” text.
- Preserve values when reselecting the current type and require confirmation before changing types.
- Fix read-only status values appearing as switches and system drafts inheriting game-rule application status.
- Improve parsing, validation, and preservation of unrecognized fragments in PID, MIGT, temperature-band, and DPI strings.
- Add search pagination and editing of nested JSON strings while preserving field types and encoding depth.
- Add regression checks for actual creation flows, reopening saved data, and source-data isolation.

## 0.3.0 Beta

- Standardize the name as 云控拴绳 / JoyoseLeash and change the app package to `com.muyue.joyoseleash`.
- Automatically install / update the APK during module installation; migrate rules, drafts, and downloads from the old package while retaining old data.
- Adapt JoyCtl field names and PID / MIGT splitting logic; add native structured string editors.
- Add JoyCtl attribution, copyright, MIT links, and the complete offline license to About.

- Use a concise, square-cornered native Android interface with Games, Global, Rule library, and Settings navigation.
- Support direct entry, increment / decrement controls, quick frame-rate selections, and temperature-band editing on the same page.
- Support separate MGAME / TGAME editing, game creation / deletion, and fields of unknown types.
- Show historical rules by version, time, and game count, with preview and replacement. Merge identical content in the list while retaining backup files.
- Keep unapplied drafts locally, cache navigation, and update status through file events.
- Add a native About page and a red Beta notice.
- Organize the open-source layout, GPL-2.0-only license, build configuration, and source-export tools.

The backend retains cloud-server blocking, rule versions and locking, manual fetching from two sources, and backup recovery.
