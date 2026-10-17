package com.pdclockout.helper.download;

import org.json.JSONException;
import org.json.JSONObject;

public class DownloadItem {
    public int index;
    public String rawUrl = "";
    public String mediaUrl = "";
    public String note = "";
    public String error = "";
    public boolean supported;
    public boolean duplicate;
    public int duplicateOfIndex;

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("index", index);
        o.put("rawUrl", rawUrl);
        o.put("mediaUrl", mediaUrl);
        o.put("note", note);
        o.put("error", error);
        o.put("supported", supported);
        o.put("duplicate", duplicate);
        o.put("duplicateOfIndex", duplicateOfIndex);
        return o;
    }

    public static DownloadItem fromJson(JSONObject o) {
        DownloadItem i = new DownloadItem();
        i.index = o.optInt("index");
        i.rawUrl = o.optString("rawUrl");
        i.mediaUrl = o.optString("mediaUrl");
        i.note = o.optString("note");
        i.error = o.optString("error");
        i.supported = o.optBoolean("supported");
        i.duplicate = o.optBoolean("duplicate");
        i.duplicateOfIndex = o.optInt("duplicateOfIndex");
        return i;
    }
}
