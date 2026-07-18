package com.bluepath.app.util;

import android.content.Context;

import androidx.work.Constraints;
import androidx.work.Data;
import androidx.work.NetworkType;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

import com.bluepath.app.worker.LearningReminderWorker;
import com.bluepath.app.worker.VoyageRerouteWorker;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

public final class NotificationHelper {
    private static final String DAILY_WORK = "bluepath_daily_learning_reminder";
    private static final String VOYAGE_REROUTE_WORK = "bluepath_voyage_auto_reroute";

    private NotificationHelper() {}

    public static void scheduleDaily(Context context, int hour, int minute) {
        Calendar now = Calendar.getInstance();
        Calendar target = Calendar.getInstance();
        target.set(Calendar.HOUR_OF_DAY, hour);
        target.set(Calendar.MINUTE, minute);
        target.set(Calendar.SECOND, 0);
        if (!target.after(now)) target.add(Calendar.DAY_OF_YEAR, 1);
        long delay = target.getTimeInMillis() - now.getTimeInMillis();

        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                LearningReminderWorker.class, 24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(new Data.Builder()
                        .putString("title", "🌊 오늘의 BluePath 항해")
                        .putString("message", "추천 영상을 학습하고 다음 티어를 준비해 보세요.")
                        .build())
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                DAILY_WORK, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void scheduleOneTime(Context context, String title, long triggerAtMillis) {
        long delay = Math.max(0, triggerAtMillis - System.currentTimeMillis());
        String workName = "bluepath_exam_" + Math.abs((title + triggerAtMillis).hashCode());
        OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(LearningReminderWorker.class)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .setInputData(new Data.Builder()
                        .putString("title", "🗓️ " + title)
                        .putString("message", "등록한 해양 학습·시험 일정을 확인할 시간입니다.")
                        .build())
                .build();
        WorkManager.getInstance(context).enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request);
    }

    public static void scheduleVoyageAutoReroute(Context context) {
        Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build();
        PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                VoyageRerouteWorker.class, 24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build();
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                VOYAGE_REROUTE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request);
    }

    public static void cancelDaily(Context context) {
        WorkManager.getInstance(context).cancelUniqueWork(DAILY_WORK);
    }
}
