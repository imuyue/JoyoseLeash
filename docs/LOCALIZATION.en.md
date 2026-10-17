# Application translations

[简体中文](LOCALIZATION.md) | English

## Format and scope

The app uses native Android string and plural resources, not a custom translation dictionary.

| Path | Contents |
| --- | --- |
| `src/res/values/` | Complete default English resources |
| `src/res/values-zh/` | Simplified Chinese resources |
| `src/res/values-en/` | English app-name override |
| `src/res/xml/locales_config.xml` | Supported languages for Android app-language settings |
| `src/assets/field-label-resources.json` | Known field paths mapped to resource IDs, without translated text |

`strings.xml` covers pages and common actions; `strings_editors.xml` covers editors; `strings_messages.xml` covers parsing and diagnostics; `field_names.xml` covers known field descriptions.

The app follows the system language by default and falls back to English for unsupported languages. Android versions with app-language settings allow a separate language selection for this app. Raw keys, user input, JSON/XML contents, package names, commands, and server data are not translated. External system errors retain their original details. Known messages from older hooks use compatibility metadata without modifying rules.

## Editing translations

Keep each resource `name` and edit only its text. Use descriptive `snake_case` IDs, not Chinese source text or hashes. Use positional format arguments so translations can reorder them without changing argument numbers or types:

```xml
<string name="example_page">Page %1$d of %2$d</string>
```

Use Android `plurals` for counts and supply the quantity forms required by the language. An `other` form is mandatory. Chinese normally uses only `other`; English uses `one` and `other`. Do not concatenate counts with translated text in Java.

```xml
<plurals name="example_records">
    <item quantity="one">%1$d record</item>
    <item quantity="other">%1$d records</item>
</plurals>
```

Follow Android XML escaping rules, including `&amp;` and `&lt;`. Escape apostrophes as `\'` according to Android string syntax. Keep `formatted="false"` on descriptions with literal percent signs and no format arguments. Do not change numeric input, data delimiters, or raw field names.

## Adding a language

1. Create a standard Android language directory such as `src/res/values-fr/`. Copy the resource files to translate and retain their resource names.
2. Translate static interface text, diagnostics, and field descriptions while preserving argument numbers and types. Omit untranslated entries to use the complete English fallback.
3. Add the language tag, such as `<locale android:name="fr" />`, to `src/res/xml/locales_config.xml` so Android can list it in app-language settings.
4. Use Android-supported qualifiers for regional or script variants rather than adding language conditions in code.
5. Run checks and a build, then verify layout, accessibility descriptions, creation and renaming, save/reopen behavior, and raw-data preservation.

```sh
python tools/check_i18n.py
python tools/check_rules.py
python tools/build.py
```

The resource check covers duplicate IDs, complete English/Chinese key sets, format arguments, plural forms, explicit Java references, and field mappings. Additional languages may be partial, but supplied resources must match default resource types and argument signatures. These checks do not replace translation or on-device layout review.

## UI verification

Build the test APK with the same signing key as the main app, as described in [development documentation](DEVELOPMENT.en.md). Explicit dual-locale checks require Android 13 or later. Keep the device unlocked and run both locales:

```sh
adb shell am instrument -w -e uiLocale zh com.muyue.joyoseleash.qa/.NativeChecks
adb shell am instrument -w -e uiLocale en com.muyue.joyoseleash.qa/.NativeChecks
```

The checks change only the app language, not the phone’s system language. They restore the previous app-language selection and both draft files to their original bytes and existence states without applying test rules. See [development documentation](DEVELOPMENT.en.md) and [compatibility notes](COMPATIBILITY.en.md) for fixture dependencies and device scope. Locale rendering, lossless data handling, and actual hook effects are separate verification scopes.
