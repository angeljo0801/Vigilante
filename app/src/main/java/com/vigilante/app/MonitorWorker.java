package com.vigilante.app;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

public final class MonitorWorker extends Worker {
    public MonitorWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }

    @NonNull
    @Override
    public Result doWork() {
        String id = getInputData().getString("watch_id");
        if (id == null || id.isEmpty()) return Result.failure();
        MonitorRunner.check(getApplicationContext(), id);
        // A failed website fetch is recorded and the next periodic run retries.
        return Result.success();
    }
}
