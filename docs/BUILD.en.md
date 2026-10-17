# Build

[简体中文](BUILD.md) | English

Release version: **0.3.3 Beta**, versionCode **9**.

Python 3.10+, a JDK, Android SDK Platform / Build Tools, and Xposed API 82 are required. Gradle is not required. Builds have been verified on Windows with Python 3.12, JDK 21, and Android API 35 as the compilation platform; other host environments have not been tested.

## Dependencies

- [JDK](https://adoptium.net/): must include `javac` and `keytool`.
- [Android SDK](https://developer.android.com/tools): install `platforms;android-35` and `build-tools;35.0.0`. Build Tools must provide `aapt2`, `d8`, `zipalign`, and `apksigner`.
- [Xposed API 82](https://api.xposed.info/): obtain the compile-time JAR from its [Maven repository](https://api.xposed.info/repo/de/robv/android/xposed/api/82/api-82.jar). This dependency is only used for compilation; LSPosed provides it at runtime.

Third-party dependencies are not distributed in source archives. SDK licenses are determined by their original providers.

## Configuration

The build script reads these environment variables:

| Variable | Purpose |
| --- | --- |
| `JAVA_HOME` | JDK root; otherwise locate javac on PATH |
| `ANDROID_HOME` | Android SDK root; `ANDROID_SDK_ROOT` is also supported |
| `ANDROID_PLATFORM_JAR` | Optional explicit path to android.jar |
| `ANDROID_BUILD_TOOLS` | Optional explicit Build Tools directory |
| `XPOSED_API_JAR` | Path to the Xposed API 82 JAR |

With `ANDROID_HOME`, the highest installed platform and Build Tools versions are selected automatically. Set the two optional variables explicitly to pin tool versions.

PowerShell example; replace the paths with your own:

```powershell
$env:JAVA_HOME = 'D:\Dev\jdk-21'
$env:ANDROID_HOME = 'D:\Dev\Android\Sdk'
$env:XPOSED_API_JAR = 'D:\Dev\deps\api-82.jar'
python tools/build.py
```

Local configuration may also be stored in the untracked `.local/config.json`:

```json
{
  "java_home": "D:/Dev/jdk-21",
  "android_jar": "D:/Dev/Android/Sdk/platforms/android-35/android.jar",
  "build_tools": "D:/Dev/Android/Sdk/build-tools/35.0.0",
  "xposed_api_jar": "D:/Dev/deps/api-82.jar"
}
```

Explicit environment variables take precedence over their corresponding local configuration values.

## Development build

```sh
python tools/build.py
```

The first build generates a local development signing key in `.local/signing/`. Retain it to update APKs you previously signed. APKs with different signatures cannot be installed as updates. Keys and device data are excluded from source archives.

Output directory: `dist/`.

```text
JoyoseLeash-0.3.3.apk
JoyoseLeash-KSU-0.3.3.zip
backend.jar
SHA256.json
```

The app is named 云控拴绳 / JoyoseLeash and uses package `com.muyue.joyoseleash`. Module ID `cloudruleguard` is unchanged for in-place module updates. The module installer migrates data from the old app package without overwriting files already present in the new package.

## Release signing

Create and retain a release key in advance, then supply it through environment variables:

| Variable | Purpose |
| --- | --- |
| `JOYOSELEASH_KEYSTORE` | Path to an existing release keystore |
| `JOYOSELEASH_KEY_ALIAS` | Key alias |
| `JOYOSELEASH_STORE_PASS` | Keystore password |
| `JOYOSELEASH_KEY_PASS` | Optional; defaults to the keystore password |

```sh
python tools/build.py --release
python tools/export_source.py
```

`--release` stops if signing configuration is missing; it does not generate a replacement key. Passwords are passed through the child-process environment, not written to source files or command-line arguments.

## Regression checks

```sh
python tools/check_rules.py
python tools/check_installer.py
python tools/test_cloud_catalog.py
python tools/build_native_tests.py
```

Cloud fixture checks require an ADB device and use only in-memory SQLite / XML / JSON fixtures. See [Development](DEVELOPMENT.en.md) for UI-test installation, device prerequisites, and data restoration.

## Source export

```sh
python tools/export_source.py --check
python tools/export_source.py
```

The exporter collects only project source, scripts, and documentation. It writes `dist/JoyoseLeash-0.3.3-source.zip` and updates `SHA256.json`. Distribute the corresponding source alongside release binaries. `dist/` is used for Releases and is not committed to the source repository.
