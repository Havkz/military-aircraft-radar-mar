package de.julien.flightradius;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;

public class BootReceiver extends BroadcastReceiver {
    private static final String CHANNEL_RESTART = "restart_reminders_v1";
    private static final int RESTART_NOTIFICATION_ID = 4100;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        boolean wasRunning = AppPreferences.get(context)
                .getBoolean(AppPreferences.KEY_MONITORING_ENABLED, false);
        AppPreferences.get(context).edit()
                .putBoolean(AppPreferences.KEY_RUNNING, false)
                .putString(AppPreferences.KEY_CONNECTION, "standby")
                .apply();
        AppPreferences.clearLiveTelemetry(context);
        if (!wasRunning) return;

        NotificationManager manager = context.getSystemService(NotificationManager.class);
        NotificationChannel channel = new NotificationChannel(CHANNEL_RESTART,
                L10n.t(context, "restart_reminders"),
                NotificationManager.IMPORTANCE_DEFAULT);
        channel.setDescription(L10n.t(context, "restart_description"));
        channel.setLightColor(MARColors.ORANGE);
        manager.createNotificationChannel(channel);

        Intent openIntent = new Intent(context, SplashActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent open = PendingIntent.getActivity(context, 0, openIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        Notification notification = new Notification.Builder(context, CHANNEL_RESTART)
                .setSmallIcon(R.drawable.ic_notification_radar)
                .setContentTitle(L10n.t(context, "restart_monitoring"))
                .setContentText(L10n.t(context, "tap_open_radar"))
                .setColor(MARColors.ORANGE)
                .setCategory(Notification.CATEGORY_REMINDER)
                .setContentIntent(open)
                .setAutoCancel(true)
                .build();
        manager.notify(RESTART_NOTIFICATION_ID, notification);
    }
}
