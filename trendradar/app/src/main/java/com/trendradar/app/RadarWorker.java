package com.trendradar.app;
import android.app.*;
import android.content.*;
import android.os.Build;
import android.graphics.Bitmap;
import org.json.JSONObject;
import java.util.List;
import androidx.work.*;
import java.util.concurrent.TimeUnit;
public class RadarWorker extends Worker {
 public RadarWorker(Context c,WorkerParameters p){super(c,p);}
 public Result doWork(){try{if(Store.token(getApplicationContext()).isEmpty())return Result.success();Store.sync(getApplicationContext());return Result.success();}catch(Exception e){Store.prefs(getApplicationContext()).edit().putString("error",e.getMessage()).apply();return Result.retry();}}
 static void schedule(Context c){Constraints n=new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();PeriodicWorkRequest r=new PeriodicWorkRequest.Builder(RadarWorker.class,15,TimeUnit.MINUTES).setConstraints(n).build();WorkManager.getInstance(c).enqueueUniquePeriodicWork("trendradar-sync",ExistingPeriodicWorkPolicy.KEEP,r);}
 static void notifyNew(Context c,List<JSONObject> items){
  NotificationManager m=c.getSystemService(NotificationManager.class);m.createNotificationChannel(new NotificationChannel("findings","Nuevos hallazgos",NotificationManager.IMPORTANCE_HIGH));
  if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission("android.permission.POST_NOTIFICATIONS")!=android.content.pm.PackageManager.PERMISSION_GRANTED)return;
  int limit=Math.min(5,items.size());for(int i=0;i<limit;i++){JSONObject o=items.get(i);String id=o.optString("id"),name=o.optString("name","Nuevo hallazgo"),cat=o.optString("category");int nid=id.hashCode()&0x7fffffff;
   Intent intent=new Intent(c,MainActivity.class).putExtra("finding_id",id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);
   PendingIntent p=PendingIntent.getActivity(c,nid,intent,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
   Bitmap image=RadarImages.artwork(cat);
   Notification.Builder b=new Notification.Builder(c,"findings").setSmallIcon(R.drawable.ic_radar).setContentTitle(name).setContentText(cat+" · "+o.optString("country")).setAutoCancel(true).setContentIntent(p).setLargeIcon(Bitmap.createScaledBitmap(image,96,96,true)).setStyle(new Notification.BigPictureStyle().bigPicture(image).setSummaryText(o.optString("summary"))).setGroup("trendradar-findings");
   m.notify(nid,b.build());
   Bitmap photo=RadarImages.photo(c,o);if(photo!=null){int w=photo.getWidth(),h=photo.getHeight();float scale=Math.min(1f,640f/Math.max(w,h));Bitmap preview=Bitmap.createScaledBitmap(photo,Math.max(1,(int)(w*scale)),Math.max(1,(int)(h*scale)),true);Bitmap thumb=Bitmap.createScaledBitmap(preview,96,96,true);b.setOnlyAlertOnce(true).setLargeIcon(thumb).setStyle(new Notification.BigPictureStyle().bigPicture(preview).setSummaryText(o.optString("summary")));m.notify(nid,b.build());}
  }
  if(items.size()>1){PendingIntent p=PendingIntent.getActivity(c,0,new Intent(c,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);m.notify(101,new Notification.Builder(c,"findings").setSmallIcon(R.drawable.ic_radar).setContentTitle("TrendRadar · "+items.size()+" nuevos resultados").setContentText("Abre el radar para ver todas las oportunidades.").setGroup("trendradar-findings").setGroupSummary(true).setAutoCancel(true).setContentIntent(p).build());}
 }
}
