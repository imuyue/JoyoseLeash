package com.muyue.joyoseleash;

/** Locale-free message identity, with no Android or JSON dependency. */
public final class Messages {
    public static final class Text {
        public final String key,fallback;
        public final Object[] args;
        private Text(String key,String fallback,Object[] args){this.key=key;this.fallback=fallback;this.args=args==null?new Object[0]:args.clone();}
    }
    public static Text text(String key,String fallback,Object...args){return new Text(key,fallback,args);}
    public static final class Failure extends IllegalArgumentException {
        public final Text caption;
        // Compatibility provenance for Protocol.message, not a translation identity.
        final String legacyType;
        public Failure(Text caption){this(caption,"IllegalArgumentException");}
        private Failure(Text caption,String legacyType){super(caption.fallback);this.caption=caption;this.legacyType=legacyType;}
    }
    public static Failure failure(String key,String fallback,Object...args){return failureAs("Exception",key,fallback,args);}
    public static Failure failureAs(String legacyType,String key,String fallback,Object...args){return new Failure(text(key,fallback,args),legacyType);}
    /** Exact legacy diagnostics only; never use for user or source data. */
    public static Text diagnostic(String message){
        if(message==null)return null;
        switch(message){
        case "内容不能为空":return text("diag_content_required",message);
        case "参数不能包含控制字符或换行":return text("diag_control_characters_forbidden",message);
        case "参数不能包含分号或井号":return text("diag_parameter_delimiters_forbidden",message);
        case "应用包名或分组名无效":return text("diag_package_or_group_invalid",message);
        case "Activity 类名无效":return text("diag_activity_class_invalid",message);
        case "请输入有效数字":return text("diag_number_invalid",message);
        case "数字超出范围":return text("diag_number_out_of_range",message);
        case "请输入非负整数":return text("diag_nonnegative_integer_required",message);
        case "帧率须为0至1000":return text("diag_fps_out_of_range",message);
        case "整数超出范围":return text("diag_integer_out_of_range",message);
        case "温度须为-100至200℃":return text("diag_temperature_out_of_range",message);
        case "数值不能为负数":return text("diag_nonnegative_number_required",message);
        case "字段不属于当前规则":return text("diag_field_not_in_rule",message);
        case "策略帧率重复":return text("diag_strategy_fps_duplicate",message);
        case "DPI 档位重复":return text("diag_dpi_duplicate",message);
        case "温度档须严格递增，不能重复或跨越相邻档位":return text("diag_temperature_neighbors_invalid",message);
        case "温度档须严格递增":return text("diag_temperature_order_invalid",message);
        case "名称不能为空或只有空白":return text("diag_name_required",message);
        case "字段已存在":return text("diag_field_already_exists",message);
        case "内容超过4 MiB":return text("diag_content_too_large",message);
        case "无法创建目录":return text("diag_directory_create_failed",message);
        case "规则库名称不合法":return text("diag_rule_library_name_invalid",message);
        case "版本必须为1至2147483647的整数":return text("diag_profile_version_invalid",message);
        case "规则内容过大":return text("diag_rule_content_too_large",message);
        case "温度表格式为10:0,38:90,42.8:60":return text("diag_temperature_table_format",message);
        case "温度须递增，帧率须为0至1000":return text("diag_temperature_table_range",message);
        case "JSON过大":return text("diag_json_too_large",message);
        case "非严格JSON或结构超过读取上限":return text("diag_json_not_strict_or_too_complex",message);
        case "此操作需要Root权限":return text("diag_root_required",message);
        case "缺少操作":return text("diag_operation_required",message);
        case "请先安装配套的云控拴绳APK":return text("diag_companion_app_required",message);
        case "当前规则已变化，请刷新后重试":return text("diag_profiles_changed",message);
        case "规则已被其他操作修改，请刷新后重试":return text("diag_profiles_modified_elsewhere",message);
        case "Joyose钩子未就绪；请启用LSPosed作用域并重启Joyose/手机":return text("diag_joyose_hook_not_ready",message);
        case "已有云端拉取任务进行中":return text("diag_fetch_already_running",message);
        case "备份编号不合法":return text("diag_backup_id_invalid",message);
        case "规则编号无效":return text("diag_rule_id_invalid",message);
        case "规则已变化，请刷新规则库":return text("diag_rule_changed",message);
        case "目标进程已退出，等待钩子重新连接":return text("diag_hook_process_exited",message);
        case "地址最多128条":return text("diag_host_count_limit",message);
        case "未读取到Joyose规则，请确认机型与文件路径":return text("diag_no_joyose_rules",message);
        case "sourceId须为固定来源编号":return text("diag_cloud_source_id_invalid",message);
        case "仅支持settings-db、common-backup、wm-cache三个只读来源":return text("diag_cloud_sources_supported",message);
        case "系统云控数据库尚不存在；不会为读取而启动下载或创建数据库":return text("diag_cloud_database_missing",message);
        case "未取得只读数据库连接":return text("diag_cloud_readonly_connection_failed",message);
        case "该系统云控来源尚不存在；不会创建、下载或改写来源":return text("diag_cloud_file_missing",message);
        case "来源超过4 MiB，已停止只读读取":return text("diag_cloud_source_too_large",message);
        case "无法只读读取该固定系统云控文件，请检查Root权限与ROM路径":return text("diag_cloud_file_unavailable",message);
        case "文件清单只支持common-backup和wm-cache两个固定来源":return text("diag_cloud_file_sources_supported",message);
        case "系统云控文件无法安全解析；未输出其中的原始内容":return text("diag_cloud_file_parse_failed",message);
        case "记录id须为规则库返回的64位指纹":return text("diag_cloud_record_id_invalid",message);
        case "rowLocator无效，请刷新规则库":return text("diag_cloud_row_locator_invalid",message);
        case "非UTF-8文本；仅保留原始Base64":return text("diag_cloud_non_utf8_preserved",message);
        case "来源或记录已变化、删除或超出读取上限；请刷新规则库重新选择":return text("diag_cloud_file_record_changed",message);
        case "此文件来源没有status字段；请清除此筛选条件":return text("diag_cloud_file_status_unavailable",message);
        case "请求参数不合法":return text("diag_request_invalid",message);
        case "公共云控XML不是有效UTF-8；未尝试转换或改写":return text("diag_cloud_xml_encoding_invalid",message);
        case "公共云控XML含DTD或实体声明，已拒绝读取":return text("diag_cloud_xml_declarations_forbidden",message);
        case "当前XML解析器无法禁用DTD处理，已拒绝读取":return text("diag_cloud_xml_parser_unsafe",message);
        case "公共云控XML超过32层或20000个结构节点":return text("diag_cloud_xml_structure_limit",message);
        case "公共云控XML含不受支持的声明或实体引用":return text("diag_cloud_xml_entities_forbidden",message);
        case "公共云控XML单节点属性超过32项":return text("diag_cloud_xml_attribute_limit",message);
        case "公共云控XML根节点不是common-cloud-config":return text("diag_cloud_xml_root_invalid",message);
        case "公共云控XML记录超过4096条":return text("diag_cloud_xml_record_limit",message);
        case "公共云控XML语法无效；未输出其中的原始内容":return text("diag_cloud_xml_syntax_invalid",message);
        case "公共云控XML没有可识别的根节点":return text("diag_cloud_xml_root_missing",message);
        case "原文超过128 KiB；未加载为可编辑记录，也未截断其原文":return text("diag_cloud_raw_too_large",message);
        case "包名或模块名超过1024字节":return text("diag_cloud_identity_too_large",message);
        case "缺少moduleName":return text("diag_cloud_module_name_missing",message);
        case "缺少moduleString":return text("diag_cloud_module_string_missing",message);
        case "云控数据表列数超过128，已停止读取":return text("diag_cloud_column_limit",message);
        case "数据表缺少packageName、moduleName或productData列；可查看source.columns确认ROM结构":return text("diag_cloud_schema_missing_columns",message);
        case "当前ROM没有status列；请清除此筛选条件":return text("diag_cloud_status_column_missing",message);
        case "同名记录超过1000条；请携带规则库返回的rowLocator精确读取":return text("diag_cloud_record_variant_limit",message);
        case "非文本二进制数据；仅保留原始Base64":return text("diag_cloud_binary_preserved",message);
        case "记录已变化、删除或超出读取上限；请刷新规则库后重新选择，不会读取其他同名规则":return text("diag_cloud_record_changed",message);
        case "productData超过128 KiB；未加载、未截断其原文":return text("diag_cloud_product_data_too_large",message);
        case "包名或模块名不是文本":return text("diag_cloud_identity_not_text",message);
        case "status字段过长，无法验证完整记录身份":return text("diag_cloud_status_too_large",message);
        case "未识别为有界、严格JSON；原文已完整保留":return text("diag_cloud_json_unrecognized",message);
        case "已达到4层字符串解码上限；更深内容仍按文本保留":return text("diag_cloud_json_decode_limit",message);
        case "当前Joyose版本的解析入口未匹配":return text("diag_joyose_parser_unmatched",message);
        case "缺少booster_config":return text("diag_booster_config_missing",message);
        case "Joyose返回规则加载失败；保留备份以便回滚":return text("diag_joyose_rule_load_failed",message);
        case "尚未取得系统候选规则，请重启Joyose恢复系统配置":return text("diag_system_rule_unavailable",message);
        case "系统候选规则恢复失败，请重启Joyose":return text("diag_system_rule_restore_failed",message);
        case "TEG服务器未返回响应":return text("diag_teg_no_response",message);
        case "无响应":return text("diag_server_no_response",message);
        case "下载规则超过4 MiB":return text("diag_download_rules_too_large",message);
        case "包名或分组名无效":return text("diag_package_or_group_invalid",message);
        case "备份编号无效":return text("diag_backup_id_invalid",message);
        case "JoyoseLeash: address discovery redirected to loopback policy":return text("diag_address_discovery_blocked",message);
        case "Invalid message metadata":return text("diag_message_metadata_invalid",message);
        default:return null;
        }
    }
    /** Compatibility for already persisted diagnostic reports, not arbitrary data. */
    public static Text legacy(String message){
        if(message==null)return null;
        Text exact=diagnostic(message);if(exact!=null)return exact;
        String[][] prefixes={
            {"状态读取失败：","diag_state_read_failed"},
            {"云控专用网络入口：","diag_cloud_network_entry"},
            {"地址发现入口：","diag_discovery_entry"},
            {"系统云控网络结果：","diag_settings_network_result"},
            {"系统云控错误：","diag_settings_server_error"},
            {"TEG服务器返回code=","diag_teg_response_code"},
            {"系统云控数据库无法只读读取：","diag_cloud_database_unavailable"},
            {"无法原子保存 ","diag_atomic_save_failed"},
            {"应用/分组规则名称重复或为空：","diag_rule_name_duplicate_or_empty"},
            {"域名不合法：","diag_host_invalid"},
            {"不支持的操作：","diag_operation_unsupported"},
            {"命令超时：","diag_command_timeout"}
        };
        for(String[] prefix:prefixes)if(message.startsWith(prefix[0])){
            String detail=message.substring(prefix[0].length());
            // Only diagnostic wrappers have recursively localizable details.
            Object argument=prefix[1].equals("diag_state_read_failed")||prefix[1].equals("diag_cloud_network_entry")||prefix[1].equals("diag_discovery_entry")||prefix[1].equals("diag_cloud_database_unavailable")||prefix[1].equals("diag_settings_server_error")?legacyDetail(detail):detail;
            return text(prefix[1],message,argument);
        }
        for(String kind:new String[]{"Exception","IOException","IllegalArgumentException","NumberFormatException","RuntimeException","InvocationTargetException","CloudSourceFailure","Failure"}){
            String prefix=kind+": ";if(message.startsWith(prefix)){
                Text inner=legacy(message.substring(prefix.length()));
                return inner==null?null:text("diag_exception_details",message,kind,inner);
            }
        }
        String[][] counts={{"TEG服务器已响应，取得","份规则，原数据库未改写","diag_teg_fetch_complete"},{"系统云控服务器已响应，取得","份Joyose规则，原数据库未改写","diag_settings_fetch_complete"}};
        for(String[] count:counts)if(message.startsWith(count[0])&&message.endsWith(count[1])){
            String number=message.substring(count[0].length(),message.length()-count[1].length());
            if(number.matches("[0-9]{1,9}"))return text(count[2],message,Integer.parseInt(number));
        }
        return null;
    }
    private static Object legacyDetail(String detail){Text caption=legacy(detail);return caption==null?detail:caption;}
    public static Text reason(Throwable error){
        Throwable current=error;
        while(current!=null){
            if(current instanceof Failure)return ((Failure)current).caption;
            Text known=legacy(current.getMessage());if(known!=null)return known;
            Throwable cause=current.getCause();if(cause==null||cause==current)break;current=cause;
        }
        if(current==null)return null;
        String kind=current.getClass().getSimpleName(),detail=String.valueOf(current.getMessage());
        return text("diag_exception_details",kind+": "+detail,kind,detail);
    }
    private Messages(){}
}
