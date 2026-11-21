package sa.osa373.lifecampus;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;

public final class ReminderReceiver extends BroadcastReceiver {
    private static final String CHANNEL = "life_campus_sound_reminders_v2";

    static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || manager.getNotificationChannel(CHANNEL) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL, "تنبيهات حياتي وجامعتي الصوتية", NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription("تنبيهات المهام والمحاضرات وجلسات التركيز والمواعيد الشخصية");
        channel.enableVibration(true);
        Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
        if (alarmSound == null) alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
        channel.setSound(alarmSound, new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
        manager.createNotificationChannel(channel);
    }

    static void show(Context context, String title, String id) {
        createChannel(context);
        if (Build.VERSION.SDK_INT >= 33 && context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        Intent launch = new Intent(context, MainActivity.class).setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 30, launch,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL) : new Notification.Builder(context);
        builder.setSmallIcon(R.drawable.ic_app)
                .setContentTitle("حياتي × جامعتي")
                .setContentText(title)
                .setAutoCancel(true)
                .setContentIntent(open);
        if (Build.VERSION.SDK_INT >= 26) {
            builder.setCategory(Notification.CATEGORY_ALARM);
        } else {
            Uri sound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            builder.setSound(sound);
            builder.setPriority(Notification.PRIORITY_HIGH);
            builder.setVibrate(new long[]{0, 350, 150, 350});
        }
        manager.notify(id.hashCode(), builder.build());
    }

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null || !"sa.osa373.lifecampus.REMINDER".equals(intent.getAction())) return;
        String id = intent.getStringExtra("id"), title = intent.getStringExtra("title");
        if (id == null || title == null) return;
        show(context, title, id);
        ReminderStore.delivered(context, id);
    }
}
