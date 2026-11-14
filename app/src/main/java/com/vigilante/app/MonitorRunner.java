package com.vigilante.app;

import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.json.JSONArray;

public final class MonitorRunner {
    private MonitorRunner() {}

    /** Called on a background thread by WorkManager or the manual-check UI. */
    public static synchronized String check(Context context, String id) {
        Watch before = WatchStore.find(context, id);
        if (before == null) return "Esta vigilancia ya no existe.";
        if (!before.enabled) return "Esta vigilancia está pausada.";

        long now = System.currentTimeMillis();
        try {
            WebScanner.Observation observation = WebScanner.scan(context, before);
            AtomicBoolean triggered = new AtomicBoolean(false);
            AtomicBoolean updated = new AtomicBoolean(false);
            WatchStore.mutate(context, id, current -> {
                if (!current.enabled || !current.sameTargetAs(before)) return;
                boolean alert = TriggerLogic.shouldAlert(
                        current.rule, current.initialized, current.lastMatched,
                        observation.matched, current.lastFingerprint, observation.fingerprint);
                current.lastChecked = now;
                current.lastError = "";
                current.lastPreview = observation.preview();
                current.lastMatched = observation.matched;
                current.lastFingerprint = observation.fingerprint;
                current.initialized = true;
                if (alert) current.lastAlert = now;
                record(current, now, observation.observed, "", alert);
                triggered.set(alert);
                updated.set(true);
            });
            if (!updated.get()) return "La vigilancia cambió durante la comprobación; se descartó el resultado.";
            if (triggered.get()) AlarmNotifier.notifyMatch(context, before, observation.preview());
            if (triggered.get()) return "Alerta generada.\n\n" + observation.preview();
            if ("CHANGES".equals(before.rule)) {
                return "Dato extraído:\n" + observation.preview();
            }
            return "Dato extraído:\n" + observation.preview() + "\n\nCondición: " + (observation.matched ? "cumplida" : "no cumplida");
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.toString() : error.getMessage();
            WatchStore.mutate(context, id, current -> {
                if (!current.sameTargetAs(before)) return;
                current.lastChecked = now;
                current.lastError = message;
                record(current, now, "", message, false);
            });
            return "Error al consultar la web: " + message;
        }
    }

    static void record(Watch w, long time, String value, String error, boolean alert) {
        try {
            JSONObject row = new JSONObject();
            row.put("time", time);
            row.put("value", value.substring(0, Math.min(value.length(), 8000)));
            row.put("error", error);
            row.put("alert", alert);
            JSONArray next = new JSONArray();
            next.put(row);
            for (int i = 0; i < Math.min(49, w.history.length()); i++) next.put(w.history.get(i));
            w.history = next;
        } catch (Exception ignored) {}
    }
}
