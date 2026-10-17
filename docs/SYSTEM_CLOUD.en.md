# System settings and other system cloud rules

[简体中文](SYSTEM_CLOUD.md) | English

## Separate entry

Open Settings → System-settings cloud rules. The separate entry is also available when game rules are not ready.

The following read-only sources are supported:

| Source | Read-only path | Presentation |
| --- | --- | --- |
| System-settings database | `/data/user_de/0/com.android.htmlviewer/databases/cloud_all_data.db`, table `cloud_all_data` | Discover columns dynamically; list all package names, modules, and status values with search and pagination |
| Common cloud backup | `/data/system/common_cloud_backup.xml` | Show `cloud-data-item` entries by `moduleName`; use `moduleString` as the raw rule text |
| Window-management cache | `/data/system/cloud_control/cloud_control_cache.json` | Show the full window-management JSON as one rule, retaining the complete source text |

These paths are formats observed on the current device, not a guarantee that all ROMs use the same caches. Missing sources, incompatible formats, oversized files, and changed source data appear as recoverable errors. System files are not created, and cloud rules are not downloaded automatically.

## Viewing and editing

- The source selector shows record counts. Long-press a source for its full path, read limits, and structured errors. Inspect package names, modules, types, decoding depth, metadata, and complete source data; raw text can be selected and copied.
- This entry and its nested editors always show the “Local draft · Not applied” status. They do not inherit the game hook’s “Rules applied” status.
- Select “Edit local draft” to edit object / array / string / number / boolean / null values by original field names. Search, 60-item pagination, adding, deleting, renaming, type replacement, and array reordering are supported.
- JSON objects / arrays inside strings can be edited structurally, with up to four decoding layers. Edits preserve the number of string wrappers. Unknown strings remain strings; verified formats such as PID / MIGT continue to use structured string editors.
- Strict JSON validation rejects duplicate keys, lenient syntax, excessive depth / node counts, and numeric precision loss. Content that cannot be safely parsed remains raw text without silent repair or omission.
- “Save local draft” writes only `system-cloud-draft.json` in the app’s private directory. It does not enter the Joyose profile or modify system databases, XML, or JSON caches.
- Only one system-cloud draft is retained. It is restored only when the source fingerprint matches; saving another rule replaces the draft. File-source fingerprints include a digest of the complete source file. After a source update, select the rule again; a same-named record is not substituted.

**Applying system-rule overrides is not supported.** Generic visual editing does not establish that the system consumes these fields. There is no “Apply to system” button, and arbitrary system rules are not applied through the Joyose hook.

## Read limits

Earlier device records show the app reporting the system-settings database as “Missing” while ADB shell could see an empty database. This discrepancy has not been confirmed resolved. ADB visibility or an empty database does not establish that the app has read valid records.

Database connections are read-only, with bound filter parameters. Complete records are retrieved by package name, module, fingerprint, and row identifier when available. When raw record data exceeds 128 KiB or identity fields cannot be read completely, catalog status is retained without presenting a false fingerprint from truncated data. Sources are fixed to the paths above; arbitrary file paths are not accepted.

Game rules are separate from this entry. “Save and apply” for game rules continues to use the verified Joyose flow. Saving a local draft for unknown system fields does not establish that those fields take effect in the system.
