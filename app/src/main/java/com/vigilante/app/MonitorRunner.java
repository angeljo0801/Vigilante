package com.vigilante.app;

import android.content.Context;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MonitorRunner {
    private MonitorRunner() {}

    /** Called on a background thread by WorkManager or the manual-check UI. */
    public static String check(Context context, String id) {
        Watch before = WatchStore.find(context, id);
        if (before == null) return "Esta vigilancia ya no existe.";
        if (!before.enabled) return "Esta vigilancia está pausada.";

        long now = System.currentTimeMillis();
        try {
            WebScanner.Observation observation = WebScanner.scan(before);
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
                triggered.set(alert);
                updated.set(true);
            });
            if (!updated.get()) return "La vigilancia cambió durante la comprobación; se descartó el resultado.";
            if (triggered.get()) AlarmNotifier.notifyMatch(context, before, observation.preview());
            if (triggered.get()) return "¡Coincidencia! Se ha enviado la alarma.";
            if ("CHANGES".equals(before.rule)) {
                return "Comprobado. No hay cambios desde la lectura anterior (o se fijó el estado inicial).";
            }
            return observation.matched
                    ? "Comprobado. La palabra o estado está presente. Se avisará al próximo cambio configurado."
                    : "Comprobado. La palabra o estado todavía no aparece.";
        } catch (Exception error) {
            String message = error.getMessage() == null ? error.toString() : error.getMessage();
            WatchStore.mutate(context, id, current -> {
                if (!current.sameTargetAs(before)) return;
                current.lastChecked = now;
                current.lastError = message;
            });
            return "Error al consultar la web: " + message;
        }
    }
}
