package com.muyue.joyoseleash;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import org.json.*;
import java.util.Arrays;

/** Focused Android-runtime checks; every write is to an in-memory fixture, never a system DB. */
public final class CloudCatalogChecks {
    static int checks;
    static void check(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static JSONObject parse(String raw)throws Exception{return Backend.CloudCatalog.parse(raw);}
    static void type(String raw,String expected)throws Exception{check(parse(raw).getString("type").equals(expected),"Wrong type for "+raw);}
    static String fingerprint(Object pkg,Object module,Object raw)throws Exception{return Backend.CloudCatalog.fingerprint(pkg,module,"text",raw,true,"integer",1);}
    static JSONObject call(SQLiteDatabase db,JSONObject request,boolean full)throws Exception{
        return Backend.readSystemCloud(db,request,full,new JSONObject().put("path",":memory:").put("table","cloud_all_data").put("readOnly",true).put("available",false));
    }
    static JSONObject entry(JSONObject library,int index)throws Exception{return library.getJSONArray("entries").getJSONObject(index);}
    static JSONObject identity(JSONObject entry)throws Exception{
        JSONObject request=new JSONObject().put("id",entry.get("id")).put("packageName",entry.get("packageName")).put("moduleName",entry.get("moduleName"));
        if(entry.has("rowLocator"))request.put("rowLocator",entry.get("rowLocator"));if(entry.has("sourceId"))request.put("sourceId",entry.get("sourceId"));return request;
    }
    static JSONObject file(String id,byte[] bytes,JSONObject request,boolean full)throws Exception{return Backend.readFileCloud(id,bytes,request,full,Backend.cloudSource(id));}
    static JSONObject file(String id,String text,JSONObject request,boolean full)throws Exception{return file(id,text.getBytes(java.nio.charset.StandardCharsets.UTF_8),request,full);}
    static String xmlAttribute(String text){return text.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;").replace("'","&apos;");}
    static String item(String module,String raw){return "<cloud-data-item moduleName=\""+xmlAttribute(module)+"\" moduleVersion=\"7\" moduleBeanPath=\"test.Fixture\" moduleString=\""+xmlAttribute(raw)+"\"/>";}
    static String common(String items){return "<?xml version='1.0' encoding='UTF-8'?><common-cloud-config><cloud-control>"+items+"</cloud-control></common-cloud-config>";}
    static void fileChecks()throws Exception{
        String exact=" {     \"version\": \"0\",     \"packages\": [], \"private_xml_payload_marker\":true }";
        String xml=common(item("drag_not_allow_packages",exact)+item("miui_use_astc","{\"version\":\"7\",\"enableASTC\":\"0\",\"scale\":\"0.75\"}")
            +item("miui_free_drag","{\"version\":\"6\",\"rules\":[{\"packageName\":\"private.pkg\",\"viewClass\":\"android.widget.TextView\"}]}")
            +item("miui_vk_mode","{\"enabled\":true,\"whiteListPackages\":[]}")+item("hyperos_use_mimotion","{\"enableMimotionOs\":\"0\",\"refreshRateParameter\":\"\"}"));
        JSONObject library=file("common-backup",xml,new JSONObject(),false);
        check(library.getLong("total")==5&&library.getJSONObject("source").getBoolean("available"),"Five common-cloud XML records unavailable");
        check(library.getJSONObject("source").getString("sourceId").equals("common-backup")&&entry(library,0).getString("sourceId").equals("common-backup"),"XML sourceId lost");
        check(!library.toString().contains("private_xml_payload_marker")&&!library.toString().contains("private.pkg"),"XML metadata leaked raw payloads");
        check(entry(library,0).isNull("packageName"),"XML invented package ownership");
        check(entry(library,0).getJSONObject("metadata").getJSONObject("moduleVersion").getString("value").equals("7"),"XML moduleVersion metadata lost");
        JSONObject selected=entry(library,0),request=identity(selected),full=file("common-backup",xml,request,true).getJSONObject("entry");
        check(full.getString("rawData").equals(exact)&&full.getJSONObject("productData").getString("type").equals("object"),"XML moduleString was reserialized or not parsed");
        check(file("common-backup",xml,new JSONObject().put("query","MIUI_"),false).getLong("total")==3,"XML module filtering wrong");
        check(file("common-backup",xml,new JSONObject().put("packageName",JSONObject.NULL),false).getLong("total")==5,"XML null package filter wrong");
        check(file("common-backup",xml,new JSONObject().put("packageName","android"),false).getLong("total")==0,"XML invented android package filter");
        JSONObject page=file("common-backup",xml,new JSONObject().put("offset",1).put("limit",2),false);
        check(page.getJSONArray("entries").length()==2&&page.getBoolean("hasMore")&&entry(page,0).getString("id").equals(entry(library,1).getString("id")),"XML pagination unstable");
        error(file("common-backup",xml,new JSONObject().put("status","1"),false),"unsupported_filter");
        error(file("common-backup",xml,new JSONObject().put("limit",101),false),"invalid_request");
        error(file("common-backup",xml,identity(selected).put("packageName","android"),true),"record_changed");
        error(file("common-backup",xml,identity(selected).put("rowLocator","../0"),true),"invalid_request");
        error(file("common-backup",xml+"\n",request,true),"record_changed");
        error(file("common-backup",common(item("inserted","{}")+item("drag_not_allow_packages",exact)),request,true),"record_changed");
        for(String[] sample:new String[][]{{"[true,1,null]","array"},{"true","boolean"},{"42","number"},{"null","null"},{JSONObject.quote("{\"a\":1}"),"object"},{"{bad}","raw"},{"raw text & <unknown>","raw"}}){
            String fixture=common(item("typed",sample[0]));JSONObject info=entry(file("common-backup",fixture,new JSONObject(),false),0);
            JSONObject value=file("common-backup",fixture,identity(info),true).getJSONObject("entry");
            check(value.getString("rawData").equals(sample[0])&&value.getJSONObject("productData").getString("type").equals(sample[1]),"XML entity decoding/typed raw preservation failed");
        }
        JSONObject duplicate=file("common-backup",common(item("same","{}")+item("same","{}")),new JSONObject(),false);
        check(!entry(duplicate,0).getString("id").equals(entry(duplicate,1).getString("id")),"Same-file duplicate modules conflated");
        check(file("common-backup",common(""),new JSONObject(),false).getLong("total")==0,"Empty valid XML treated as fatal");
        check(!entry(file("common-backup",common("<cloud-data-item moduleName='missing'/>"),new JSONObject(),false),0).getBoolean("retrievable"),"Missing moduleString became an editable value");
        String malicious="<!DOCTYPE common-cloud-config [<!ENTITY leak SYSTEM 'file:///does-not-exist-sensitive'>]>"+common(item("bad","{}"));
        error(file("common-backup",malicious,new JSONObject(),false),"unsafe_xml");
        error(file("common-backup","<other/>",new JSONObject(),false),"unsupported_schema");
        JSONObject invalid=file("common-backup","<common-cloud-config><cloud-control private='private_error_marker'",new JSONObject(),false);
        error(invalid,"invalid_source_content");check(!invalid.toString().contains("private_error_marker"),"XML parse error leaked source content");
        StringBuilder depth=new StringBuilder("<common-cloud-config>");for(int i=0;i<33;i++)depth.append("<n>");for(int i=0;i<33;i++)depth.append("</n>");depth.append("</common-cloud-config>");
        error(file("common-backup",depth.toString(),new JSONObject(),false),"source_structure_limit");
        StringBuilder nodes=new StringBuilder();for(int i=0;i<10001;i++)nodes.append("<n/>");error(file("common-backup",common(nodes.toString()),new JSONObject(),false),"source_structure_limit");
        StringBuilder records=new StringBuilder();for(int i=0;i<4097;i++)records.append(item("m"+i,"0"));error(file("common-backup",common(records.toString()),new JSONObject(),false),"source_structure_limit");
        byte[] oversized=new byte[Protocol.MAX_BYTES+1];error(file("common-backup",oversized,new JSONObject(),false),"source_too_large");
        error(file("common-backup",new byte[]{(byte)0xc3,0x28},new JSONObject(),false),"invalid_source_encoding");
        char[] huge=new char[Backend.CLOUD_RAW_BYTES+1];Arrays.fill(huge,'x');String hugeText=new String(huge);
        JSONObject tooBig=entry(file("common-backup",common(item("large",hugeText)),new JSONObject(),false),0);
        check(!tooBig.getBoolean("retrievable")&&tooBig.isNull("id")&&tooBig.getLong("rawBytes")==huge.length,"XML oversize payload was truncated/fingerprinted");
        String wm=" \n{\"wm_cloud_version\":\"20260912\",\"private_wm_payload_marker\":[\"p\\/a\"],\"nested\":{\"enabled\":true}}\n";
        JSONObject wmLibrary=file("wm-cache",wm,new JSONObject(),false),wmEntry=entry(wmLibrary,0);
        check(wmLibrary.getLong("total")==1&&wmEntry.getString("moduleName").equals("window_manager")&&wmEntry.isNull("packageName"),"WM source was split or given fake package ownership");
        check(!wmLibrary.toString().contains("private_wm_payload_marker"),"WM metadata leaked raw keys");
        JSONObject wmFull=file("wm-cache",wm,identity(wmEntry),true).getJSONObject("entry");
        check(wmFull.getString("rawData").equals(wm)&&wmFull.getJSONObject("productData").getString("type").equals("object"),"WM exact root document was reserialized");
        check(wmLibrary.getJSONObject("source").getString("sha256").equals(Backend.cloudDigest(wm.getBytes(java.nio.charset.StandardCharsets.UTF_8))),"WM source digest not exact bytes");
        error(file("wm-cache",wm+" ",identity(wmEntry),true),"record_changed");
        error(file("wm-cache",wm,request,true),"record_changed");
        JSONObject malformedEntry=entry(file("wm-cache","not recognized {bad}",new JSONObject(),false),0);
        check(file("wm-cache","not recognized {bad}",identity(malformedEntry),true).getJSONObject("entry").getJSONObject("productData").getString("type").equals("raw"),"Unknown WM document not preserved raw");
        byte[] binary={(byte)0xc3,0x28};JSONObject binaryEntry=entry(file("wm-cache",binary,new JSONObject(),false),0),binaryFull=file("wm-cache",binary,identity(binaryEntry),true).getJSONObject("entry");
        check(binaryFull.getString("rawEncoding").equals("base64")&&binaryFull.getString("rawData").equals("wyg=")&&binaryFull.getJSONObject("productData").getString("type").equals("raw"),"Invalid UTF-8 WM source silently replaced bytes");
        check(!entry(file("wm-cache",hugeText,new JSONObject(),false),0).getBoolean("retrievable"),"WM oversize root bypassed record bound");
        error(file("wm-cache",oversized,new JSONObject(),false),"source_too_large");
        error(Backend.systemCloud(new JSONObject().put("sourceId","../../unknown"),false),"invalid_source");
        error(Backend.systemCloud(new JSONObject().put("sourceId",JSONObject.NULL),false),"invalid_source");
        check(Backend.SYSTEM_SOURCE_IDS.length==3&&Backend.cloudSource("common-backup").getString("path").equals("/data/system/common_cloud_backup.xml")&&Backend.cloudSource("wm-cache").getString("path").equals("/data/system/cloud_control/cloud_control_cache.json"),"Source scope grew beyond three fixed paths");
    }
    static void error(JSONObject result,String code)throws Exception{check(result.getJSONObject("error").getString("code").equals(code),"Expected "+code+" error, got "+result);}
    static void add(SQLiteDatabase db,Object pkg,Object module,Object status,Object raw){db.execSQL("INSERT INTO cloud_all_data(packageName,moduleName,status,productData) VALUES(?,?,?,?)",new Object[]{pkg,module,status,raw});}
    public static void main(String[] args)throws Exception{
        type("{\"unknown\":{\"enabled\":true,\"list\":[1,2,null]}}","object");type("[true,1,\"a\"]","array");
        type("true","boolean");type("false","boolean");type("null","null");type("42","number");type("-1.25e2","number");type("\"hello\"","string");
        String object="{\"enabled\":true}";String encoded=JSONObject.quote(object);
        JSONObject nested=parse(encoded);check(nested.getString("type").equals("object")&&nested.getInt("encodingDepth")==1,"JSON-encoded object not decoded");
        check(parse(JSONObject.quote(encoded)).getInt("encodingDepth")==2,"Nested JSON string depth lost");
        check(Backend.CloudCatalog.parse(JSONObject.NULL).getString("type").equals("null"),"SQL null not represented");
        for(String malformed:new String[]{"","plain text","{bad}","{'a':1}","{\"a\":1,}","[1,]","01","true trailing","/*x*/{}","{\"a\":1,\"a\":2}","1e9999","9223372036854775809","0.12345678901234567890123456789"}){
            JSONObject parsed=parse(malformed);check(!parsed.getBoolean("parseable")&&parsed.getString("type").equals("raw")&&parsed.getString("value").equals(malformed),"Malformed/lossy input was not preserved");
        }
        char[] deep=new char[60];Arrays.fill(deep,'[');char[] close=new char[60];Arrays.fill(close,']');type(new String(deep)+"0"+new String(close),"raw");
        String id=fingerprint("pkg","module","{}");check(id.matches("[0-9a-f]{64}"),"Not a SHA-256 fingerprint");
        check(id.equals(fingerprint("pkg","module","{}")),"Fingerprint unstable");
        check(!id.equals(fingerprint("other","module","{}")),"Package omitted from identity");
        check(!id.equals(fingerprint("pkg","other","{}")),"Module omitted from identity");
        check(!id.equals(fingerprint("pkg","module","{ }")),"Raw whitespace omitted from identity");
        check(!fingerprint("a\nb","c","{}").equals(fingerprint("a","b\nc","{}")),"Fingerprint has delimiter collision");
        check(!fingerprint(JSONObject.NULL,"module","{}").equals(fingerprint("null","module","{}")),"Null identity conflated with text");

        try(SQLiteDatabase db=SQLiteDatabase.create(null)){
            db.execSQL("CREATE TABLE cloud_all_data(packageName TEXT COLLATE NOCASE,moduleName TEXT,status INTEGER,productData,unknownField TEXT)");
            add(db,"com.xiaomi.joyose","booster_config",1,"{\"private_payload_marker\":1}");
            add(db,"com.android.settings","generic",0,"[true,{\"future\":2}]");
            add(db,"com.android.settings","generic",1,"unrecognized raw");
            add(db,"COM.ANDROID.SETTINGS","generic",1,"42");
            add(db,null,null,null,null);add(db,"binary.pkg","binary",1,new byte[]{0,1,2});
            char[] large=new char[Backend.CLOUD_RAW_BYTES+1];Arrays.fill(large,'x');add(db,"large.pkg","large",1,new String(large));
            JSONObject all=call(db,new JSONObject(),false);
            check(all.getLong("total")==7&&all.getJSONArray("entries").length()==7,"Inventory excluded a package, status, null, blob or oversize row");
            check(!all.toString().contains("private_payload_marker"),"Inventory leaked raw productData");
            check(all.getBoolean("readOnly")&&!all.getBoolean("overrideSupported"),"Inventory implies unsupported apply capability");
            JSONObject page=call(db,new JSONObject().put("offset",1).put("limit",2),false);
            check(page.getJSONArray("entries").length()==2&&page.getBoolean("hasMore")&&page.getLong("total")==7,"Pagination/counts wrong");
            check(entry(page,0).getString("id").equals(entry(all,1).getString("id")),"Pagination identity changed");
            check(call(db,new JSONObject().put("query","SETTINGS"),false).getLong("total")==3,"Substring filter wrong");
            check(call(db,new JSONObject().put("packageName","com.android.settings"),false).getLong("total")==2,"Exact package filter used NOCASE column collation");
            check(call(db,new JSONObject().put("status","0"),false).getLong("total")==1,"Status filter wrong");
            check(call(db,new JSONObject().put("query","%' OR 1=1 --"),false).getLong("total")==0,"Query was SQL/wildcard interpreted");
            JSONObject selected=entry(all,1),request=identity(selected),full=call(db,request,true).getJSONObject("entry");
            check(full.getJSONObject("productData").getString("type").equals("array"),"Generic array not available for local visual draft");
            check(full.getString("rawData").equals("[true,{\"future\":2}]"),"Raw record changed during retrieval");
            check(!selected.getString("id").equals(entry(all,2).getString("id")),"Same module variants conflated");
            check(call(db,identity(entry(all,4)),true).getJSONObject("entry").getJSONObject("productData").getString("type").equals("null"),"Null identity not retrievable");
            JSONObject binary=call(db,identity(entry(all,5)),true).getJSONObject("entry");
            check(binary.getString("rawEncoding").equals("base64")&&binary.getString("rawData").equals("AAEC")&&binary.getJSONObject("productData").getString("type").equals("raw"),"Binary data not preserved");
            check(!entry(all,6).getBoolean("retrievable")&&entry(all,6).isNull("id")&&entry(all,6).getLong("rawBytes")==large.length,"Oversize record silently truncated or fingerprinted");
            error(call(db,new JSONObject().put("limit",101),false),"invalid_request");
            error(call(db,new JSONObject().put("offset",-1),false),"invalid_request");
            error(call(db,new JSONObject().put("offset","0"),false),"invalid_request");
            error(call(db,new JSONObject().put("id","../bad").put("packageName","x").put("moduleName","x"),true),"invalid_request");
            error(call(db,new JSONObject().put("id",selected.get("id")).put("moduleName",selected.get("moduleName")),true),"invalid_request");
            JSONObject wrong=identity(selected).put("packageName","COM.ANDROID.SETTINGS");error(call(db,wrong,true),"record_changed");
            db.execSQL("UPDATE cloud_all_data SET productData=? WHERE rowid=?",new Object[]{"{\"changed\":true}",selected.getString("rowLocator")});
            error(call(db,request,true),"record_changed");
            try(Cursor c=db.rawQuery("SELECT count(*) FROM cloud_all_data",null)){check(c.moveToFirst()&&c.getLong(0)==7,"Reader inserted/deleted fixture rows");}
        }
        try(SQLiteDatabase db=SQLiteDatabase.create(null)){
            db.execSQL("CREATE TABLE cloud_all_data(PackageName TEXT,ModuleName TEXT,ProductData TEXT,PRIMARY KEY(PackageName,ModuleName)) WITHOUT ROWID");
            db.execSQL("INSERT INTO cloud_all_data VALUES('optional.pkg','optional.module','{\"k\":1}')");
            JSONObject library=call(db,new JSONObject(),false);check(library.getJSONObject("source").getBoolean("available"),"Optional status/version incorrectly required");
            check(!entry(library,0).has("rowLocator"),"WITHOUT ROWID source gained a false locator");
            check(call(db,identity(entry(library,0)),true).has("entry"),"Case-variant column schema or WITHOUT ROWID retrieval failed");
            error(call(db,new JSONObject().put("status","1"),false),"unsupported_filter");
        }
        try(SQLiteDatabase db=SQLiteDatabase.create(null)){
            db.execSQL("CREATE TABLE cloud_all_data(packageName TEXT)");JSONObject unavailable=call(db,new JSONObject(),false);
            error(unavailable,"unsupported_schema");check(unavailable.getJSONArray("entries").length()==0&&!unavailable.getJSONObject("source").getBoolean("available"),"Unavailable schema was fatal or opaque");
        }
        fileChecks();System.out.println("Cloud catalog checks passed: "+checks);
    }
}
