# 开发与验证

简体中文 | [English](DEVELOPMENT.en.md)

## 目录

| 路径 | 内容 |
| --- | --- |
| `src/java/` | 原生界面、LSPosed 钩子、Root 后端及通信 |
| `src/module/` | KernelSU 模块安装、后台与网络脚本 |
| `src/res/`、`src/assets/` | Android 资源与 Xposed 入口 |
| `tests/android/` | 原生界面与规则模型实机检查 |
| `tests/java/`、`tests/shell/` | 字符串解析与安装迁移的本机检查 |
| `licenses/` | 保留的第三方许可证全文 |
| `tools/` | 构建、源码导出和设备开发工具 |
| `docs/` | 构建、适配与验证文档 |
| `build/`、`dist/` | 忽略提交的构建输出 |
| `.local/` | 忽略提交的依赖、签名、设备数据与调试记录 |

## 模块职责

- `ManagerActivity`、`NativeUi`、`FpsEditor`、`JsonValueEditor`：原生页面与本地编辑草稿。
- `RuleModel`：字段类型、帧率规则、温度档校验。
- `StringRules`、`StringRuleEditor`：已识别字符串的分项解析、原生编辑及只替换改动片段的序列化。
- `RuleFields`：JoyCtl 字段名称与路径匹配适配，按当前 Joyose 适配器保留 MGAME / TGAME 含义。
- `RootClient`、`StateProvider`、`Protocol`：管理界面、Root 后端和钩子之间的通信。
- `Backend`：规则读取、提交、历史备份、网络配置和系统云控只读清单。
- `HookEntry`：Joyose / HTML 查看器适配、规则应用、请求拦截和手动拉取。
- `net.sh`：hosts 映射和内核回环规则；`service.sh`、`observe.sh`、`lease.sh`：启动、文件通知及临时放行恢复。

## 连接设备

开发工具使用 PATH 中的 `adb`。可设置 `ADB` 指定可执行文件，设置 `ANDROID_SERIAL` 选择设备；未指定序列号时交给 ADB 处理。也可在 `.local/config.json` 设置 `adb` 和 `adb_serial`。

```sh
python tools/device.py status
```

状态和截图可能包含个人设备信息。测试结果统一写入 `.local/evidence/`，不进入公开目录。

## 本机检查

```sh
python tools/check_rules.py
python tools/check_installer.py
```

前者检查 PID 的 6/7 字段、MIGT、应用参数、未知片段保留与非法值拦截；后者使用模拟包管理器检查 APK 自动安装 / 更新、安装失败和幂等数据迁移，不连接手机。Windows 下安装器检查需要 Git for Windows Bash。

翻译资源另运行 `python tools/check_i18n.py`；标准目录、参数与新增语言方法见[应用翻译](LOCALIZATION.md)。

## 界面检查

```sh
python tools/build_native_tests.py
adb install -r build/native-tests/checks.apk
adb shell am instrument -w com.muyue.joyoseleash.qa/com.muyue.joyoseleash.qa.NativeChecks
adb uninstall com.muyue.joyoseleash.qa
```

检查 APK 必须与主应用使用相同签名。运行时占用手机界面，结束后恢复普通草稿与系统云控草稿的原始字节及存在状态，不应用测试规则。设备检查假定已安装地铁跑酷、存在历史备份及五条通用云控备份记录；其他设备需调整验收数据。报告位于主应用私有目录 `files/native-qa/report.json`，只卸载 `.qa` 测试应用。

`python tools/test_cloud_catalog.py` 使用 Android 内存夹具检查清单、指纹、类型和格式边界，不改写系统来源。

## 其他工具

| 脚本 | 行为 |
| --- | --- |
| `measure_fps.py LABEL --package PACKAGE --seconds 20` | 读取 SurfaceFlinger 帧呈现数据；`--screenshots` 可附截图 |
| `record_run.py --seconds 60` | 采集地铁跑酷运行时帧率、焦点、df 和温度，包含截图 |
| `verify_native_backend.py` | 在开发设备上提交测试版本、验证历史替换和冲突，最后恢复规则 |
| `deploy_live.py --live` | 开发用热部署：安装模块和 APK，同步活动文件并重启 Joyose / HTML 查看器 |

后端检查和热部署会改变设备状态，不作为源码整理或普通构建步骤自动运行。正常安装使用 KernelSU 管理器。

## 验证范围

既有验证记录包含字段解析、安装迁移、系统云控夹具及原生界面检查。既有界面回归包含 27 条实际新建流程，覆盖六种字段类型、改名与排序、分页、类型保留及保存后重开。

既有检查中，本地编辑、缓存导航和空闲检查未新增 Root 请求；测试前后的草稿和规则来源已比对。以上为历史记录，不代表 0.3.3 的新增测试结果，也不代表其他 ROM 的钩子或帧率行为已验证。设备记录保存在 `.local/evidence/`，不随源码分发。
