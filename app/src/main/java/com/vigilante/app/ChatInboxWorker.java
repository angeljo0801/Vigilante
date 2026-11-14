package com.vigilante.app;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
public final class ChatInboxWorker extends Worker{
    public ChatInboxWorker(@NonNull Context c,@NonNull WorkerParameters p){super(c,p);}
    @NonNull @Override public Result doWork(){
        try{ChatInbox.sync(getApplicationContext());return Result.success();}
        catch(Exception e){ChatInbox.status(getApplicationContext(),"Error de conexión: "+e.getMessage());return Result.retry();}
    }
}
