# 字符串分项编辑

简体中文 | [English](STRING_RULES.en.md)

规则中的部分字符串自身包含多个参数。编辑器按字段名识别格式，以原生列表编辑参数；不会将任意字符串猜成另一种数据类型。

| 字段 | 分项内容 |
| --- | --- |
| `PID_M`、`PID_T` | 按策略帧率、温度档分组；温度对、目标 / 最低帧率、Kp / Ki，以及存在时的 Kd |
| `migt` | 应用、CPU 频率（kHz）、各帧率的帧耗时、CPU 标记与数值参数 |
| `support_highfps_app`、`novatek_gex_fps_limit` | 应用 / 分组与帧率 |
| `recommend_tgame_thresh` | 温度与推荐帧率 |
| `support_scale_app_list` | 应用列表，兼容 `应用:比例` 格式 |
| `force_scale_app_list` | 应用、DPI / 比例与 Activity，保留未知附加参数 |
| `game_list`、`package_list` 等已适配列表 | 单个字符串中的应用 / 分组 |
| `reset_game_min_duration` | 应用与时长 |
| `dynamic_fps`、`dynamic_fps_M`、`dynamic_fps_multiWin`、`dynamic_fps_multiWin_M` | 温度与帧率；常用帧率页面另有快捷值和温度档编辑 |
| `cmd` | 分号分隔的命令段及井号分隔的参数；不推断未验证的资源 ID 含义 |

修改只替换对应字段的字符区间，保留空格、分隔符、未识别片段及其他组。无效行不影响同级有效字段的解析。数字校验类型、范围、重复档位和温度顺序；文本参数禁止注入分隔符与控制字符。编辑属于本地草稿，只有“保存并应用”触发后端应用。

`force_scale_app_list` 支持 `应用:DPI#比例[;DPI#比例][:Activity[;Activity][:附加参数]]`。分组、字段与警告每页 40 项；可查看原文、未解析片段及其位置，未知格式保留原始字符串编辑入口。

PID 温度对沿用 A / B 名称，不推断不同系统版本中未验证的触发 / 退出语义。MIGT 中未确定含义的数值保留编号。未知格式仍使用单个原始值编辑；不会展开成整份 JSON 编辑框。

## JoyCtl 来源

适配自 [JoyCtl `comments.js`](https://github.com/hexwander/joyctl/blob/b16187b2c49bd357d9704926ef9d401ccb3ca18b/src/lib/comments.js) 的字段名称、`getComment` 匹配和 PID / MIGT 拆分方式，原代码采用 MIT License。版权及原始全文见 [第三方说明](../THIRD_PARTY_NOTICES.md#joyctl)。JoyCtl 中依赖特定平台的解释没有直接视为本机行为，MGAME / TGAME 标签继续按本机适配器区分。

```sh
python tools/check_rules.py
python tools/check_installer.py
```

本机检查不连接手机。原生页面、Root 请求次数和真实迁移另用实机检查，见 [开发文档](DEVELOPMENT.md)。
