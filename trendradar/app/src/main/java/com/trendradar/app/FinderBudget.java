package com.trendradar.app;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;

/**
 * Credit-aware SerpAPI gateway for Product Finder. Caches only search responses,
 * never API keys or photograph bytes. Account endpoint is not a billable search.
 */
final class FinderBudget {
    private static final long TTL = 12L * 60 * 60 * 1000; // 12 hours
    private static final int MAX_ENTRIES = 35;
    private static final int MAX_JSON = 2500000;
    private FinderBudget() { }

    static String enc(String value) {
        try { return URLEncoder.encode(value, "UTF-8"); }
        catch (Exception e) { return value; }
    }

    private static String digest(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder s = new StringBuilder();
            for (byte b : bytes) s.append(String.format(Locale.US, "%02x", b & 255));
            return s.toString();
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }
    static String imageHash(byte[] bytes) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(bytes);
            return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    private static File folder(Context c) {
        File dir = new File(c.getCacheDir(), "productfinder-search-v2");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }
    private static File file(Context c, String name, String key) {
        return new File(folder(c), digest(key + "|" + name) + ".json");
    }
    static synchronized JSONObject cached(Context c, String name, String key) {
        File f = file(c, name, key);
        if (!f.isFile() || f.length() > MAX_JSON || f.length() < 5) return null;
        if (System.currentTimeMillis() - f.lastModified() > TTL) { f.delete(); return null; }
        try (FileInputStream in = new FileInputStream(f); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n;
            while ((n = in.read(b)) != -1) {
                if (out.size() + n > MAX_JSON) return null;
                out.write(b,0,n);
            }
            return new JSONObject(out.toString("UTF-8"));
        } catch (Exception e) { f.delete(); return null; }
    }
    static synchronized void putCache(Context c, String name, String key, JSONObject value) {
        try {
            byte[] bytes = value.toString().replace(key, "[REDACTED]").replace(enc(key), "[REDACTED]").getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_JSON) return;
            File f = file(c,name,key);
            try (FileOutputStream out = new FileOutputStream(f,false)) { out.write(bytes); }
            File[] all = folder(c).listFiles((dir,filename) -> filename.endsWith(".json"));
            if (all != null && all.length > MAX_ENTRIES) {
                Arrays.sort(all, Comparator.comparingLong(File::lastModified));
                for (int i=0; i<all.length-MAX_ENTRIES; i++) all[i].delete();
            }
        } catch (Exception ignored) { }
    }

    private static JSONObject read(String url, int maxBytes) throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(12000);
        conn.setReadTimeout(23000);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Accept","application/json");
        try {
            int status = conn.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("SerpAPI HTTP " + status);
            try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] b = new byte[8192]; int n;
                while ((n = in.read(b)) != -1) {
                    if (out.size() + n > maxBytes) throw new IOException("Respuesta demasiado grande");
                    out.write(b,0,n);
                }
                JSONObject j = new JSONObject(out.toString("UTF-8"));
                if (j.has("error")) throw new IOException("SerpAPI: " + j.optString("error"));
                return j;
            }
        } finally { conn.disconnect(); }
    }

    /** This official account endpoint costs zero SerpAPI search credits. */
    static JSONObject account(String key) throws Exception {
        return read("https://serpapi.com/account.json?api_key=" + enc(key), 100000);
    }
    /** Block new searches unless account API confirms at least 3 credits. */
    private static void checkAvailable(String key) throws Exception {
        JSONObject info = account(key);
        if (info.optDouble("plan_monthly_price",0)>0) throw new IOException("Plan SerpAPI de pago detectado: consultas bloqueadas para evitar cargos.");
        int remaining = info.optInt("total_searches_left", -1);
        if (remaining < 0) throw new IOException("No se pudo confirmar el saldo de SerpAPI. No se gastaron créditos.");
        if (remaining <= 2) throw new IOException("Quedan " + remaining + " créditos SerpAPI. Modo ahorro: se reservan 2; no se hacen consultas nuevas.");
    }
    static synchronized void ensureAvailable(String key) throws Exception {
        checkAvailable(key);
    }
    /** Fetches from disk if fresh. Checks live balance BEFORE each uncached SerpAPI search. */
    static synchronized JSONObject search(Context c, String url, String cacheTag, String key) throws Exception {
        JSONObject existing = cached(c,cacheTag,key);
        if (existing != null) return existing;
        checkAvailable(key);
        JSONObject data = read(url, MAX_JSON);
        putCache(c,cacheTag,key,data);
        return data;
    }
}
