# Third-party dependencies and references

[简体中文](THIRD_PARTY_NOTICES.md) | English

JoyoseLeash’s own project source is licensed under GPL-2.0-only; see [LICENSE](LICENSE). Separately identified JoyCtl-derived portions retain the MIT License. Third-party projects and SDKs retain their respective licenses.

| Project | Use | Source |
| --- | --- | --- |
| Android SDK Platform / Build Tools | Compilation and packaging; not included in source archives | https://developer.android.com/tools |
| Xposed API 82 | Hook interfaces for compilation only; supplied by the framework at runtime | https://api.xposed.info/ |
| KernelSU | Module manager on the target device; not distributed with this project | https://github.com/tiann/KernelSU |
| LSPosed | Java hook runtime on the target device; not distributed with this project | https://github.com/LSPosed/LSPosed |
| Zygisk Next | Zygisk environment on the tested device; not distributed with this project | https://github.com/Dr-TSNG/ZygiskNext |

Source archives do not include Xiaomi or game APKs, third-party decompiled source, system databases, complete extracted cloud rules, the Android SDK, the Xposed API JAR, or development signing keys. System rules read at runtime remain under their original providers’ terms; this project’s license does not automatically apply to that content.

The GPL-2.0-only license text comes from the [SPDX License List](https://spdx.org/licenses/GPL-2.0-only.html). This project selects GNU GPL version 2 only, without an “or any later version” grant.

## JoyCtl

This software contains code from JoyCtl: https://github.com/hexwander/joyctl

Copyright (c) 2026 JoyCtl Contributors  
Licensed under the MIT License.

Complete original MIT license:

- [Text distributed with the source](licenses/JoyCtl-MIT.txt)
- [Upstream license](https://github.com/hexwander/joyctl/blob/main/LICENSE)
- [Referenced revision](https://github.com/hexwander/joyctl/blob/b16187b2c49bd357d9704926ef9d401ccb3ca18b/LICENSE)

The source is `src/lib/comments.js` at commit `b16187b2c49bd357d9704926ef9d401ccb3ca18b`: 103 Chinese field names, `getComment` path matching, and splitting logic for PID temperature pairs / MIGT strings. Corresponding project files are `src/assets/joyctl-field-names.json`, `RuleFields.java`, and `StringRules.java`. This project adds exact-fragment replacement, validation, and a native Android interface for string editing. It does not incorporate JoyCtl’s Web interface, ADB database pushing, or bulk performance templates.

The APK includes `assets/licenses/JoyCtl-MIT.txt`, available offline in About. The KSU ZIP also includes `licenses/JoyCtl-MIT.txt`. The original copyright and complete license text are retained.
