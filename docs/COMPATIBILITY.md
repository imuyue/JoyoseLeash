# 适配范围

## 已实测环境

| 项目 | 环境 |
| --- | --- |
| 机型 | 小米 2410DPN6CC / haotian |
| ROM | OS4.0.0.17.XOBCNXM |
| Android | API 37 |
| Joyose | 2.5.21 |
| Root 与钩子 | KernelSU、Zygisk Next、LSPosed |
| 测试游戏 | 地铁跑酷，`com.kiloo.subwaysurf.gw`;PUBG MOBLIE，`com.tencent.ig` |

最低安装 API 为 26，仅代表安装和编译下限；当前钩子按上述 Joyose / ROM 内部结构适配，其他机型与版本需要单独验证。

## 规则与帧率

实测中 Joyose 根据板级温度规则重复写入 `/data/system/mcd/df`。原 MGAME 温度表为 `10:0,38:90,42.8:60`，单次手改 `df` 会被后续采样覆盖。

测试时为地铁跑酷设置 `dynamic_fps=10:144`、`dynamic_fps_M=10:144`。确认跑动的采样区间系统呈现约 139.98～143.31 FPS，中位数 142.18 FPS，`df` 保持 144。该结果只覆盖本次机型、ROM、规则和运行区间。

新增游戏会补齐解析器需要的 `scene_ovrride` 数组，保留其他原字段。可以添加原库没有的字段与规则库；系统是否使用它们取决于对应的消费者。配置帧率不等于游戏实际渲染帧率。

## 网络边界

- 已发现的云控服务器 IPv4 通过内核 NAT 引导至回环地址；域名通过 hosts 和已适配的 Java 请求 / DNS 入口拦截。
- 静态列表无法预知未来所有服务器地址。原生请求入口或系统客户端升级后可能需要重新适配。
- 测试内核无 IPv6 NAT 表。IPv6 域名由 hosts 与 Java 钩子处理；原生 IPv6 字面地址直连未完成内核回环覆盖。
- `resolver.msg.xiaomi.net` 同时用于小米推送；“地址发现屏蔽”可以单独关闭。
- 手动获取规则复用系统客户端与正常 TLS 校验。拉取期间最多临时放行 90 秒，结束后恢复网络屏蔽。

## 数据与恢复

- 不清除游戏数据，原始小米规则数据库只读打开。
- 保存规则：`/data/user/0/com.muyue.joyoseleash/files/state.json`。
- 未应用草稿：同目录 `native-draft.json`。
- 自动备份：`/data/adb/cloudruleguard/history/`。
- 手动下载：应用私有目录中的 `download-joyose-*.json`、`download-settings-*.json`，不覆盖系统原库。
- 卸载 KSU 模块撤销模块的网络屏蔽与本地覆盖，保留管理 APK 和规则备份。
- 关闭规则锁定时，系统后续重新加载规则可能改变当前生效内容，管理界面会提示版本变化。

## 从旧包迁移

模块安装时自动安装 / 更新 `com.muyue.joyoseleash`。旧 `dev.cloudrule.guard` 的 `state.json`、`native-draft.json` 与 `download-*.json` 会按缺失文件迁移；旧文件、原应用和已有新文件不删除、不覆盖。迁移备份保存在 `/data/adb/cloudruleguard/legacy-package-backup/`。签名不一致等安装失败会终止模块安装并显示错误，不通过卸载或清数据绕过。

新包需要重新获得 KernelSU Root 授权，并在 LSPosed 启用 Joyose / HTML 查看器作用域。启用新模块后关闭旧模块，避免重复挂钩。
