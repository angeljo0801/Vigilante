package com.vigilante.app;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
public final class ScheduleReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        if ("com.vigilante.app.RUN".equals(i.getAction())) {
            if (i.getData()==null) return;
            Watch w=WatchStore.find(c,i.getData().getLastPathSegment());
            if (w==null || !w.enabled || w.times.isEmpty()) return;
            Scheduler.scheduleClock(c,w);
            Scheduler.enqueueClock(c,w);
        } else Scheduler.syncAll(c);
    }
}
