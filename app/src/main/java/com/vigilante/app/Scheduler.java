package com.vigilante.app;

import android.content.Context;
import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import java.util.concurrent.TimeUnit;

public final class Scheduler {
    private Scheduler() {}

    private static String uniqueName(String id) {
        return "vigilante_watch_" + id;
    }

    public static void syncAll(Context ctx) {
        for (Watch item : WatchStore.all(ctx)) schedule(ctx, item);
    }

    public static void cancel(Context ctx, String id) {
        WorkManager.getInstance(ctx).cancelUniqueWork(uniqueName(id));
    }

    public static void schedule(Context ctx, Watch w) {
        if (!w.enabled) {
            cancel(ctx, w.id);
            return;
        }
        int minutes = Math.max(15, w.intervalMinutes);
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        Data input = new Data.Builder().putString("watch_id", w.id).build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(
                MonitorWorker.class, minutes, TimeUnit.MINUTES)
                .setInputData(input)
                .setConstraints(constraints)
                .addTag("vigilante")
                .build();
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                uniqueName(w.id), ExistingPeriodicWorkPolicy.UPDATE, req);
    }
}
