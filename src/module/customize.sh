#!/system/bin/sh
[ "$BOOTMODE" = true ] || abort "请从KernelSU管理器安装"
[ "$API" -ge 26 ] || abort "需要Android 8.0及以上"
[ -d /data/adb/modules/zygisk_lsposed ] || ui_print "提示：钩子需要Zygisk/LSPosed；单独网络屏蔽仍可使用"
ui_print "自动安装 / 更新云控拴绳（JoyoseLeash）"
. "$MODPATH/apk.sh"
install_manager_apk "$MODPATH/JoyoseLeash.apk" || abort "APK安装失败；保留原应用及其数据，安装详情见上方输出"
migrate_legacy_package || abort "旧规则迁移失败；原应用数据已保留，请查看安装日志"
set_perm "$MODPATH/service.sh" 0 0 0755
set_perm "$MODPATH/net.sh" 0 0 0755
set_perm "$MODPATH/observe.sh" 0 0 0755
set_perm "$MODPATH/lease.sh" 0 0 0755
set_perm "$MODPATH/uninstall.sh" 0 0 0755
set_perm "$MODPATH/action.sh" 0 0 0755
set_perm "$MODPATH/apk.sh" 0 0 0644
ui_print "请在LSPosed启用“云控拴绳”，只勾选Joyose和HTML查看器"
ui_print "从模块操作按钮或桌面图标打开原生界面；不会清除原规则库"
ui_print "从旧包升级时，请启用新模块并关闭旧的云控规则管理作用域"
