package com.vigilante.app;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.ContextCompat;

public final class AlarmNotifier {
    private static final String CHANNEL = "vigilante_alarm_v1";

    private AlarmNotifier() {}

    public static void setup(Context ctx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = ctx.getSystemService(NotificationManager.class);
            if (nm.getNotificationChannel(CHANNEL) != null) return;
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL, "Alarmas de Vigilante", NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription("Sonido y vibración cuando una web alcanza el estado vigilado");
            channel.enableVibration(true);
            channel.setVibrationPattern(new long[]{0, 400, 180, 400, 180, 650});
            Uri alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (alarm == null) alarm = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            if (alarm != null) {
                AudioAttributes attributes = new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build();
                channel.setSound(alarm, attributes);
            }
            nm.createNotificationChannel(channel);
        }
    }

    public static void notifyMatch(Context ctx, Watch watch, String preview) {
        String header = "CHANGES".equals(watch.rule) ? "La página ha cambiado"
                : "DISAPPEARS".equals(watch.rule) ? "El estado ha desaparecido"
                : "Se detectó el estado esperado";
        String detail = "CHANGES".equals(watch.rule) ? "Nuevo contenido"
                : "Objetivo: " + watch.keyword;
        show(ctx, watch.name + " · " + header, detail
                + (preview.isEmpty() ? "" : " · " + preview), watch.url, watch.id.hashCode());
    }

    public static void test(Context ctx) {
        show(ctx, "Alarma de prueba", "Vigilante puede avisarte cuando detecte un cambio.",
                null, 500001);
    }

    private static void show(Context ctx, String title, String description, String url, int id) {
        setup(ctx);
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(
                ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        Intent intent = new Intent(ctx, MainActivity.class);
        if (url != null) {
            intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pending = PendingIntent.getActivity(
                ctx, id, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification notification = new NotificationCompat.Builder(ctx, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(title)
                .setContentText(description)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(description))
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setAutoCancel(true)
                .setContentIntent(pending)
                .setVibrate(new long[]{0, 400, 180, 400, 180, 650})
                .build();
        NotificationManagerCompat.from(ctx).notify(Math.abs(id), notification);
    }
}
