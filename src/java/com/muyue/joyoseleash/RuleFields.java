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
    final Context context;
    RuleFields(Context context){this.context=context;try{JSONObject data=new JSONObject(Protocol.read(context.getAssets().open("field-label-resources.json")));for(Iterator<String> it=data.keys();it.hasNext();){String key=it.next();names.put(key,data.getString(key));}}catch(Exception ignored){}}
    private String localized(String resource,String fallback){int id=context.getResources().getIdentifier(resource,"string",Protocol.PKG);return id==0?fallback:context.getString(id);}
    String label(String path,String fallback){
        String clean=path.replaceFirst("^params\\.","").replaceAll("\\[[0-9]+\\]","");
        // Keep the meaning verified against this Joyose adapter, rather than
        // importing upstream's platform-specific interpretation of these names.
        if(clean.endsWith(".dynamic_fps_M"))return localized("field_mgame_fps",fallback);
        if(clean.endsWith(".dynamic_fps"))return localized("field_tgame_fps",fallback);
        if(names.containsKey(clean))return localized(names.get(clean),fallback);
        String best=null;boolean withDot=clean.contains(".");
        for(String key:names.keySet())if(clean.endsWith("."+key)||(withDot&&key.endsWith("."+clean)))if(best==null||key.length()>best.length())best=key;
        return best==null?fallback:localized(names.get(best),fallback);
    }
}
