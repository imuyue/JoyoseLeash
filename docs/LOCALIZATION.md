# 应用翻译

简体中文 | [English](LOCALIZATION.en.md)

## 格式与范围

应用采用 Android 原生字符串与复数资源，不使用自定义翻译字典：

| 路径 | 内容 |
| --- | --- |
| `src/res/values/` | 完整英文默认资源 |
| `src/res/values-zh/` | 简体中文资源 |
| `src/res/values-en/` | 英文应用名称覆盖 |
| `src/res/xml/locales_config.xml` | Android 应用语言设置的受支持语言声明 |
| `src/assets/field-label-resources.json` | 已知字段路径到资源标识的映射，不保存译文 |

`strings.xml` 为页面与通用操作，`strings_editors.xml` 为编辑器，`strings_messages.xml` 为解析与诊断，`field_names.xml` 为已知字段说明。

默认随系统语言显示；未覆盖的语言回退到英文。支持应用语言设置的 Android 版本可在系统中单独选择本应用语言。原始键名、用户输入、JSON/XML 内容、包名、命令和服务器返回数据不翻译；外部系统错误保留原始细节。旧版钩子的已知提示通过兼容元数据呈现，不修改规则内容。

## 修改译文

保留资源的 `name`，只修改文本。使用有语义的 `snake_case` 名称，不以中文原文或散列作为标识。含参数的文本采用位置参数，译文可调整顺序但不能改变参数编号和类型：

```xml
<string name="example_page">第 %1$d 页，共 %2$d 页</string>
```

计数使用 Android `plurals`，按目标语言提供必要的数量形式；必须有 `other`。中文通常只需 `other`，英文使用 `one` 和 `other`。不要在 Java 中拼接数量与译文。

```xml
<plurals name="example_records">
    <item quantity="other">%1$d 条记录</item>
</plurals>
```

遵守 Android XML 转义规则，例如 `&amp;`、`&lt;`；英文单引号按 Android 字符串规则写为 `\'`。含字面百分号且不接受格式参数的说明应保留 `formatted="false"`。不要修改数字输入、数据分隔符或原始字段名。

## 新增语言

1. 新建 Android 标准语言目录，如 `src/res/values-fr/`，复制需要翻译的资源文件并保留资源名。
2. 翻译静态界面、诊断与字段说明，保留参数编号和类型。未翻译条目可省略，由完整英文资源兜底。
3. 在 `src/res/xml/locales_config.xml` 中加入语言标记，如 `<locale android:name="fr" />`，使其出现在系统应用语言列表中。
4. 对区域或文字变体使用 Android 支持的限定符，不在代码中添加语言判断。
5. 运行检查和构建，再验证文字布局、无障碍说明、创建与改名、保存后重开及原始数据保留。

```sh
python tools/check_i18n.py
python tools/check_rules.py
python tools/build.py
```

资源检查覆盖重复键、中英文完整性、格式参数、复数形式、显式 Java 引用及字段映射。新增语言允许部分翻译，但已提供条目的类型与格式参数必须匹配默认资源。检查不替代译文与实机布局校对。

## 界面验证

测试 APK 使用与主应用相同的签名，构建方法见[开发文档](DEVELOPMENT.md)。双语言指定测试需要 Android 13 或以上；在保持设备解锁的情况下分别运行：

```sh
adb shell am instrument -w -e uiLocale zh com.muyue.joyoseleash.qa/.NativeChecks
adb shell am instrument -w -e uiLocale en com.muyue.joyoseleash.qa/.NativeChecks
```

验证只切换应用语言，不修改手机系统语言；结束后恢复原应用语言和两类草稿的原始字节与存在状态，不应用测试规则。测试依赖及设备适配范围见[开发文档](DEVELOPMENT.md)与[适配文档](COMPATIBILITY.md)。语言资源切换、数据无损与实际钩子效果是不同的验证范围。
