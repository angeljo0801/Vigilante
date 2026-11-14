package com.vigilante.app;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import androidx.work.*;
import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class Scheduler {
    private Scheduler() {}
    private static String uniqueName(String id) { return "vigilante_watch_" + id; }
    private static PendingIntent alarm(Context c, String id) {
        Intent i = new Intent(c, ScheduleReceiver.class);
        i.setAction("com.vigilante.app.RUN");
        i.setData(Uri.parse("vigilante://watch/" + id));
        return PendingIntent.getBroadcast(c, 0, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
    public static void syncAll(Context c) { for (Watch w : WatchStore.all(c)) schedule(c,w); }
    public static void cancel(Context c, String id) {
        WorkManager.getInstance(c).cancelUniqueWork(uniqueName(id));
        WorkManager.getInstance(c).cancelUniqueWork(uniqueName(id)+"_clock");
        c.getSystemService(AlarmManager.class).cancel(alarm(c,id));
    }
    public static String normalizeTimes(String raw) {
        java.util.TreeSet<String> times = new java.util.TreeSet<>();
        for (String s : raw.split("[,;\\s]+")) {
            if (s.isEmpty()) continue;
            if (!s.matches("([01]?[0-9]|2[0-3]):[0-5][0-9]")) throw new IllegalArgumentException("Usa horas HH:mm, separadas por comas");
            String[] parts=s.split(":");
            times.add(String.format(java.util.Locale.ROOT,"%02d:%02d",Integer.parseInt(parts[0]),Integer.parseInt(parts[1])));
        }
        if (times.size()>24) throw new IllegalArgumentException("Máximo 24 horas diarias por tarea");
        return String.join(",",times);
    }
    public static long nextTime(String times, long now) {
        long next=Long.MAX_VALUE;
        for (String slot: normalizeTimes(times).split(",")) {
            if (slot.isEmpty()) continue;
            String[] p=slot.split(":");
            Calendar c=Calendar.getInstance(); c.setTimeInMillis(now);
            c.set(Calendar.HOUR_OF_DAY,Integer.parseInt(p[0])); c.set(Calendar.MINUTE,Integer.parseInt(p[1]));
            c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
            if (c.getTimeInMillis()<=now) c.add(Calendar.DAY_OF_MONTH,1);
            next=Math.min(next,c.getTimeInMillis());
        }
        return next;
    }
    public static void scheduleClock(Context c, Watch w) {
        if (!w.enabled || w.times.isEmpty()) return;
        long next=nextTime(w.times,System.currentTimeMillis());
        if (next==Long.MAX_VALUE) return;
        AlarmManager am=c.getSystemService(AlarmManager.class);
        PendingIntent pi=alarm(c,w.id);
        try {
            if (Build.VERSION.SDK_INT<31 || am.canScheduleExactAlarms()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pi);
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pi);
        } catch (SecurityException e) { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,next,pi); }
    }
    public static void enqueueClock(Context c, Watch w) {
        OneTimeWorkRequest req = new OneTimeWorkRequest.Builder(MonitorWorker.class)
            .setInputData(new Data.Builder().putString("watch_id",w.id).build())
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build();
        WorkManager.getInstance(c).enqueueUniqueWork(uniqueName(w.id)+"_clock",ExistingWorkPolicy.KEEP,req);
    }
    public static void schedule(Context c, Watch w) {
        AlarmNotifier.refreshValue(c,w);
        if (!w.enabled) { cancel(c,w.id); return; }
        if (!w.times.isEmpty()) {
            WorkManager.getInstance(c).cancelUniqueWork(uniqueName(w.id));
            scheduleClock(c,w); return;
        }
        c.getSystemService(AlarmManager.class).cancel(alarm(c,w.id));
        WorkManager.getInstance(c).cancelUniqueWork(uniqueName(w.id)+"_clock");
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(MonitorWorker.class,Math.max(15,w.intervalMinutes),TimeUnit.MINUTES)
            .setInputData(new Data.Builder().putString("watch_id",w.id).build())
            .setConstraints(new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).addTag("vigilante").build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(uniqueName(w.id),ExistingPeriodicWorkPolicy.UPDATE,req);
    }
}
