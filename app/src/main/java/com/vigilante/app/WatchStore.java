package com.vigilante.app;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class WatchStore {
    private static final Object LOCK = new Object();
    private static final String PREF = "vigilante_data";
    private static final String KEY = "watches";

    private WatchStore() {}

    private static List<Watch> read(Context ctx) {
        List<Watch> out = new ArrayList<>();
        String raw = ctx.getApplicationContext().getSharedPreferences(PREF, Context.MODE_PRIVATE)
                .getString(KEY, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                if (obj != null) out.add(Watch.fromJson(obj));
            }
        } catch (Exception ignored) {
            // Corrupt saved JSON should not crash the app.
        }
        return out;
    }

    private static void write(Context ctx, List<Watch> all) {
        JSONArray array = new JSONArray();
        try {
            for (Watch w : all) array.put(w.toJson());
        } catch (Exception e) {
            throw new IllegalStateException("No se pudieron guardar las vigilancias", e);
        }
        SharedPreferences pref = ctx.getApplicationContext()
                .getSharedPreferences(PREF, Context.MODE_PRIVATE);
        if (!pref.edit().putString(KEY, array.toString()).commit()) {
            throw new IllegalStateException("No se pudieron guardar los datos");
        }
    }

    public static List<Watch> all(Context ctx) {
        synchronized (LOCK) {
            return read(ctx);
        }
    }

    public static Watch find(Context ctx, String id) {
        synchronized (LOCK) {
            for (Watch w : read(ctx)) if (w.id.equals(id)) return w;
            return null;
        }
    }

    public static void upsert(Context ctx, Watch item) {
        synchronized (LOCK) {
            List<Watch> all = read(ctx);
            for (int i = 0; i < all.size(); i++) {
                if (all.get(i).id.equals(item.id)) {
                    all.set(i, item);
                    write(ctx, all);
                    return;
                }
            }
            all.add(item);
            write(ctx, all);
        }
    }

    public static void remove(Context ctx, String id) {
        synchronized (LOCK) {
            List<Watch> all = read(ctx);
            all.removeIf(w -> w.id.equals(id));
            write(ctx, all);
        }
    }

    public static boolean mutate(Context ctx, String id, Consumer<Watch> change) {
        synchronized (LOCK) {
            List<Watch> all = read(ctx);
            for (Watch w : all) {
                if (w.id.equals(id)) {
                    change.accept(w);
                    write(ctx, all);
                    return true;
                }
            }
            return false;
        }
    }
}
