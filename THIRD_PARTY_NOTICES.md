# 第三方依赖与资料

简体中文 | [English](THIRD_PARTY_NOTICES.en.md)

JoyoseLeash 自有项目源码使用 GPL-2.0-only，见 [LICENSE](LICENSE)。单独标注的 JoyCtl 衍生部分保留 MIT 许可，第三方项目与 SDK 维持各自许可。

| 项目 | 用途 | 来源 |
| --- | --- | --- |
| Android SDK Platform / Build Tools | 编译与打包；不随源码包分发 | https://developer.android.com/tools |
| Xposed API 82 | 仅编译钩子接口；运行时由框架提供 | https://api.xposed.info/ |
| KernelSU | 目标设备模块管理器；不随项目分发 | https://github.com/tiann/KernelSU |
| LSPosed | 目标设备 Java 钩子运行时；不随项目分发 | https://github.com/LSPosed/LSPosed |
| Zygisk Next | 测试设备上的 Zygisk 运行环境；不随项目分发 | https://github.com/Dr-TSNG/ZygiskNext |

源码包不含小米或游戏 APK、第三方反编译源码、系统数据库、提取的完整云控规则、Android SDK、Xposed API JAR或开发签名密钥。运行时读取的系统规则由原提供方管理，其内容不自动适用本项目许可。

GPL-2.0-only 许可证正文取自 [SPDX License List](https://spdx.org/licenses/GPL-2.0-only.html)。本项目选择仅 GNU GPL 第 2 版，不包含“或任何后续版本”授权。

## JoyCtl

本软件包含来自 JoyCtl 的代码：https://github.com/hexwander/joyctl

Copyright (c) 2026 JoyCtl Contributors  
Licensed under the MIT License.

原 MIT 许可证全文：

- [随源码分发的全文](licenses/JoyCtl-MIT.txt)
- [上游协议链接](https://github.com/hexwander/joyctl/blob/main/LICENSE)
- [本次引用版本](https://github.com/hexwander/joyctl/blob/b16187b2c49bd357d9704926ef9d401ccb3ca18b/LICENSE)

引用来源为提交 `b16187b2c49bd357d9704926ef9d401ccb3ca18b` 中的 `src/lib/comments.js`：103 个字段中文名称、`getComment` 的路径匹配，以及 PID 温度对 / MIGT 字符串的拆分逻辑。对应文件为 `src/assets/joyctl-field-names.json`、`RuleFields.java` 和 `StringRules.java`。字符串编辑的精确片段替换、校验和 Android 原生界面由本项目补充；没有引入 JoyCtl 的 Web 界面、ADB 数据库推送或批量性能模板。

APK 内置 `assets/licenses/JoyCtl-MIT.txt`，关于页面可离线查看；KSU ZIP 同样包含 `licenses/JoyCtl-MIT.txt`。保留原版权和许可全文。
