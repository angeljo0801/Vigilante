package com.vigilante.app;

import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.JSONObject;
import org.json.JSONArray;

public final class MonitorRunner {
    private MonitorRunner() {}

    /** Called on a background thread by WorkManager or the manual-check UI. */
    public static String check(Context context, String id) { return check(context,id,false); }

    public static synchronized String check(Context context, String id, boolean manual) {
        Watch before = WatchStore.find(context, id);
        if (before == null) return "Esta vigilancia ya no existe.";
        if (!before.enabled) return "Esta vigilancia está pausada.";
        if (before.actionRequired && !manual) return "Esperando intervención manual. No se harán nuevas consultas automáticas.";

        long now = System.currentTimeMillis();
        // Ignore duplicate or stale background work until the next configured slot.
        if (!manual && before.lastChecked > 0) {
            long due = before.times.isEmpty()
                    ? before.lastChecked + Math.max(15, before.intervalMinutes) * 60_000L
                    : Scheduler.nextTime(before.times, before.lastChecked);
            if (now < due) return "Comprobación duplicada omitida.";
        }
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
                current.actionRequired = false;
                current.previousValueKnown=current.initialized;
                current.previousValueChanged=current.initialized&&!current.lastFingerprint.equals(observation.fingerprint);
                current.lastPreview = observation.preview();
                NumericTrend.record(current,observation.observed,now);
                current.lastMatched = observation.matched;
                current.lastFingerprint = observation.fingerprint;
                current.initialized = true;
                // Manual refreshes of clock-based watches update the value silently.
                if (manual && !current.times.isEmpty()) alert = false;
                if (alert) current.lastAlert = now;
                record(current, now, observation.observed, "", alert);
                triggered.set(alert);
                updated.set(true);
            });
            if (!updated.get()) return "La vigilancia cambió durante la comprobación; se descartó el resultado.";
            AlarmNotifier.clearAction(context,id);
            Watch latest=WatchStore.find(context,id);
            if(latest!=null){AlarmNotifier.refreshValue(context,latest);if(triggered.get())AlarmNotifier.notifyMatch(context,latest,observation.preview());}
            String trend=latest==null?"":NumericTrend.detail(latest);
            String result=observation.preview()+(trend.isEmpty()?"":"\n"+trend);
            if (triggered.get()) return "Alerta generada.\n\n" + result;
            if ("CHANGES".equals(before.rule)) {
                return "Dato extraído:\n" + result;
            }
            return "Dato extraído:\n" + result + "\n\nCondición: " + (observation.matched ? "cumplida" : "no cumplida");
        } catch (ActionRequired error) {
            AtomicBoolean notify=new AtomicBoolean(false);
            WatchStore.mutate(context,id,current->{
                if(!current.enabled || !current.sameTargetAs(before)) return;
                notify.set(!current.actionRequired);
                current.actionRequired=true;
                current.lastChecked=now;
                current.lastError=error.getMessage();
                record(current,now,"",error.getMessage(),false);
            });
            AlarmNotifier.refreshValue(context,WatchStore.find(context,id));
            if(notify.get()) AlarmNotifier.notifyAction(context,before.name,before.url,id,error.getMessage());
            return error.getMessage()+"\n\nLas consultas automáticas quedan en espera. Abre la página y reintenta manualmente solo cuando el sitio lo permita.";
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.toString() : error.getMessage();
            final String detail=error instanceof java.net.UnknownHostException?"DNS: dominio no disponible. Comprueba Internet y DNS privado de Android.":message;
            WatchStore.mutate(context, id, current -> {
                if (!current.sameTargetAs(before)) return;
                current.lastChecked = now;
                current.lastError = detail;
                record(current, now, "", detail, false);
            });
            AlarmNotifier.refreshValue(context,WatchStore.find(context,id));
            return "Error al consultar la web: " + detail;
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
