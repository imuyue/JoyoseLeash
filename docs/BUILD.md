# 构建

需要 Python 3.10+、JDK、Android SDK Platform / Build Tools，以及 Xposed API 82 编译依赖。不需要 Gradle。已在 Windows、Python 3.12、JDK 21、Android API 35 编译平台上验证；其他主机环境尚未实测。

## 依赖

- [JDK](https://adoptium.net/)：包含 `javac`、`keytool`。
- [Android SDK](https://developer.android.com/tools)：安装 `platforms;android-35`、`build-tools;35.0.0`。Build Tools 需要提供 `aapt2`、`d8`、`zipalign`、`apksigner`。
- [Xposed API 82](https://api.xposed.info/)：从其 [Maven 仓库](https://api.xposed.info/repo/de/robv/android/xposed/api/82/api-82.jar)取得编译 JAR。该依赖仅用于编译，运行时由 LSPosed 提供。

第三方依赖不随源码包分发。SDK许可由其原提供方规定。

## 配置

构建脚本读取以下环境变量：

| 变量 | 用途 |
| --- | --- |
| `JAVA_HOME` | JDK 根目录；未设置时从 PATH 查找 javac |
| `ANDROID_HOME` | Android SDK 根目录，也支持 `ANDROID_SDK_ROOT` |
| `ANDROID_PLATFORM_JAR` | 可选，直接指定 android.jar |
| `ANDROID_BUILD_TOOLS` | 可选，直接指定 Build Tools 目录 |
| `XPOSED_API_JAR` | Xposed API 82 JAR 的路径 |

使用 `ANDROID_HOME` 时自动选择已安装的最高平台和 Build Tools 版本。需要固定工具版本时，显式设置两个可选变量。

PowerShell 示例（替换为自己的目录）：

```powershell
$env:JAVA_HOME = 'D:\Dev\jdk-21'
$env:ANDROID_HOME = 'D:\Dev\Android\Sdk'
$env:XPOSED_API_JAR = 'D:\Dev\deps\api-82.jar'
python tools/build.py
```

也可以将本机配置保存在不提交的 `.local/config.json`：

```json
{
  "java_home": "D:/Dev/jdk-21",
  "android_jar": "D:/Dev/Android/Sdk/platforms/android-35/android.jar",
  "build_tools": "D:/Dev/Android/Sdk/build-tools/35.0.0",
  "xposed_api_jar": "D:/Dev/deps/api-82.jar"
}
```

直接指定的环境变量优先于对应的本地配置。

## 开发构建

```sh
python tools/build.py
```

首次构建会在 `.local/signing/` 生成本机开发签名。保留该密钥可以覆盖安装自己此前签名的 APK；不同签名不能覆盖安装。密钥和设备数据不进入源码包。

输出目录 `dist/`：

```text
JoyoseLeash-0.3.0.apk
JoyoseLeash-KSU-0.3.0.zip
backend.jar
SHA256.json
```

应用名称为云控拴绳 / JoyoseLeash，包名为 `com.muyue.joyoseleash`。模块 ID `cloudruleguard` 保持不变，以覆盖更新既有模块。旧包的数据迁移由模块安装器完成，已有新包文件不覆盖。

## 发布签名

提前创建并保管发布密钥，然后通过环境变量提供：

| 变量 | 用途 |
| --- | --- |
| `JOYOSELEASH_KEYSTORE` | 已存在的发布密钥库路径 |
| `JOYOSELEASH_KEY_ALIAS` | 密钥别名 |
| `JOYOSELEASH_STORE_PASS` | 密钥库密码 |
| `JOYOSELEASH_KEY_PASS` | 可选，默认与密钥库密码相同 |

```sh
python tools/build.py --release
python tools/export_source.py
```

`--release` 缺少签名配置时会停止，不会自动生成替代密钥。密码通过子进程环境传递，不写入源码或命令行参数。

## 导出源码

```sh
python tools/export_source.py --check
python tools/export_source.py
```

导出器只收集项目源码、脚本和文档，输出 `dist/JoyoseLeash-0.3.0-source.zip` 并更新 `SHA256.json`。发布二进制时，一并提供对应版本源码。`dist/` 用于 Releases，不提交到源码仓库。
