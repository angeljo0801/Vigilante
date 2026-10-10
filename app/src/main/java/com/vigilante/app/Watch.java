package com.vigilante.app;

import org.json.JSONException;
import org.json.JSONObject;
import java.util.UUID;

/** User-defined target plus persisted monitoring state. */
public final class Watch {
    public String id = UUID.randomUUID().toString();
    public String name = "";
    public String url = "";
    public String keyword = "";
    public String selector = "";
    public String attribute = "";
    public String rule = "APPEARS";
    public int intervalMinutes = 15;
    public boolean enabled = true;
    public boolean initialized = false;
    public boolean lastMatched = false;
    public String lastFingerprint = "";
    public String lastPreview = "";
    public String lastError = "";
    public long lastChecked = 0;
    public long lastAlert = 0;

    public JSONObject toJson() throws JSONException {
        JSONObject j = new JSONObject();
        j.put("id", id);
        j.put("name", name);
        j.put("url", url);
        j.put("keyword", keyword);
        j.put("selector", selector);
        j.put("attribute", attribute);
        j.put("rule", rule);
        j.put("intervalMinutes", intervalMinutes);
        j.put("enabled", enabled);
        j.put("initialized", initialized);
        j.put("lastMatched", lastMatched);
        j.put("lastFingerprint", lastFingerprint);
        j.put("lastPreview", lastPreview);
        j.put("lastError", lastError);
        j.put("lastChecked", lastChecked);
        j.put("lastAlert", lastAlert);
        return j;
    }

    public static Watch fromJson(JSONObject j) {
        Watch w = new Watch();
        w.id = j.optString("id", w.id);
        w.name = j.optString("name", "");
        w.url = j.optString("url", "");
        w.keyword = j.optString("keyword", "");
        w.selector = j.optString("selector", "");
        w.attribute = j.optString("attribute", "");
        w.rule = j.optString("rule", "APPEARS");
        w.intervalMinutes = j.optInt("intervalMinutes", 15);
        w.enabled = j.optBoolean("enabled", true);
        w.initialized = j.optBoolean("initialized", false);
        w.lastMatched = j.optBoolean("lastMatched", false);
        w.lastFingerprint = j.optString("lastFingerprint", "");
        w.lastPreview = j.optString("lastPreview", "");
        w.lastError = j.optString("lastError", "");
        w.lastChecked = j.optLong("lastChecked", 0L);
        w.lastAlert = j.optLong("lastAlert", 0L);
        return w;
    }

    public boolean sameTargetAs(Watch other) {
        return other != null
                && url.equals(other.url)
                && keyword.equals(other.keyword)
                && selector.equals(other.selector)
                && attribute.equals(other.attribute)
                && rule.equals(other.rule);
    }

    public void resetState() {
        initialized = false;
        lastMatched = false;
        lastFingerprint = "";
        lastPreview = "";
        lastError = "";
        lastChecked = 0;
        lastAlert = 0;
    }
}
