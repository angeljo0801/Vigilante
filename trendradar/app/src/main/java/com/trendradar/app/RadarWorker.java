package com.trendradar.app;
import android.app.*;
import android.content.*;
import android.os.Build;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
public class RadarWorker extends Worker {
 public RadarWorker(Context c,WorkerParameters p){super(c,p);}
 public Result doWork(){try{if(Store.token(getApplicationContext()).isEmpty())return Result.success();Store.sync(getApplicationContext());return Result.success();}catch(Exception e){Store.prefs(getApplicationContext()).edit().putString("error",e.getMessage()).apply();return Result.retry();}}
 static void schedule(Context c){Constraints n=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();PeriodicWorkRequest r=new PeriodicWorkRequest.Builder(RadarWorker.class,15,TimeUnit.MINUTES).setConstraints(n).build();WorkManager.getInstance(c).enqueueUniquePeriodicWork("trendradar-sync",ExistingPeriodicWorkPolicy.KEEP,r);}
 static void notifyNew(Context c,int count){
  NotificationManager m=c.getSystemService(NotificationManager.class);m.createNotificationChannel(new NotificationChannel("findings","Nuevos hallazgos",NotificationManager.IMPORTANCE_HIGH));
  if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
  PendingIntent p=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
  m.notify(101,new Notification.Builder(c,"findings").setSmallIcon(R.drawable.ic_radar).setContentTitle("TrendRadar · nuevos resultados").setContentText(count+" hallazgos nuevos. Abre el radar para analizarlos.").setAutoCancel(true).setContentIntent(p).build());
 }
}
