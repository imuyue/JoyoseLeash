/* SPDX-License-Identifier: MIT
 * Adapted from JoyCtl FIELD_COMMENTS / getComment, src/lib/comments.js.
 * Copyright (c) 2026 JoyCtl Contributors. See licenses/JoyCtl-MIT.txt.
 */
package com.muyue.joyoseleash;

import android.content.Context;
import org.json.*;
import java.util.*;

final class RuleFields {
    final Map<String,String> names=new LinkedHashMap<>();
    RuleFields(Context context){try{JSONObject data=new JSONObject(Protocol.read(context.getAssets().open("joyctl-field-names.json")));for(Iterator<String> it=data.keys();it.hasNext();){String key=it.next();names.put(key,data.getString(key));}}catch(Exception ignored){}}
    String label(String path,String fallback){
        String clean=path.replaceFirst("^params\\.","").replaceAll("\\[[0-9]+\\]","");
        // Keep the meaning verified against this Joyose adapter, rather than
        // importing upstream's platform-specific interpretation of these names.
        if(clean.endsWith(".dynamic_fps_M"))return "MGAME帧率";
        if(clean.endsWith(".dynamic_fps"))return "TGAME帧率";
        if(names.containsKey(clean))return names.get(clean);
        String best=null;boolean withDot=clean.contains(".");
        for(String key:names.keySet())if(clean.endsWith("."+key)||(withDot&&key.endsWith("."+clean)))if(best==null||key.length()>best.length())best=key;
        return best==null?fallback:names.get(best);
    }
}
