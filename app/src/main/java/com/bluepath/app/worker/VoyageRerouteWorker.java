package com.bluepath.app.worker;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.bluepath.app.MainActivity;
import com.bluepath.app.R;
import com.bluepath.app.network.ApiClient;
import com.bluepath.app.network.ApiModels;
import com.bluepath.app.repository.BluePathRepository;
import com.bluepath.app.storage.UserStore;

public class VoyageRerouteWorker extends Worker {
    public static final String CHANNEL_ID = "bluepath_voyage_reroute";

    public VoyageRerouteWorker(@NonNull Context appContext, @NonNull WorkerParameters workerParams) {
        super(appContext, workerParams);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context context = getApplicationContext();
        UserStore store = new UserStore(context);
        if (!ApiClient.isConfigured() || !store.hasCloudSession()) return Result.success();
        if (store.daysSinceRouteActivity() < 3 || store.hasPendingReroute()) return Result.success();

        try {
            BluePathRepository repository = new BluePathRepository(context);
            ApiModels.RoutePlanResponse pending = repository.previewReroute(null, "inactivity");
            if (pending == null || pending.routeId == null || pending.routeId.trim().isEmpty()) {
                return Result.success();
            }
            store.savePendingReroute(pending.routeId, pending.summary);
            showNotification(context, pending);
            return Result.success();
        } catch (Exception exception) {
            return getRunAttemptCount() < 3 ? Result.retry() : Result.failure();
        }
    }

    private void showNotification(Context context, ApiModels.RoutePlanResponse pending) {
        if (Build.VERSION.SDK_INT >= 33
                && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "BluePath automatic rerouting", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Prepares a shorter alternative route after inactivity");
            manager.createNotificationChannel(channel);
        }
        Intent intent = new Intent(context, MainActivity.class)
                .putExtra("openPendingReroute", true)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent contentIntent = PendingIntent.getActivity(
                context,
                2403,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        String summary = pending.summary == null || pending.summary.trim().isEmpty()
                ? "짧은 활동 중심의 대체 항로를 준비했습니다. 앱에서 확인 후 적용하세요."
                : pending.summary;
        NotificationCompat.Builder notification = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_anchor)
                .setContentTitle("대체 항로가 준비되었습니다")
                .setContentText(summary)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(summary))
                .setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        manager.notify(2403, notification.build());
    }
}
