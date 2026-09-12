package com.mavis.moneybook;

import android.content.Context;
import android.util.Log;

import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import java.util.concurrent.TimeUnit;

/**
 * WorkManager 周期任务 - 兜底扫描
 *
 * v1.9.0 方案:不依赖 ForegroundService
 * - WorkManager 是系统级调度,ROM 友好
 * - 最小周期 15 分钟 (WorkManager 限制)
 * - 兜底:即使 APP 没被打开,每 15 分钟扫一次短信库
 *
 * 为什么用 WorkManager:
 * - Google 官方 API,所有 ROM 都不敢动
 * - 国产 ROM (小米/华为/OPPO/vivo) 都完美支持
 * - APP 被杀也能跑(系统级进程)
 * - 用户无需任何操作,装上就生效
 *
 * 为什么不用 ContentObserver:
 * - 部分国产 ROM 拦截第三方 ContentObserver (即使加了 READ_SMS 权限)
 * - WorkManager 是 Google 官方 API,所有 ROM 都支持
 */
public class SmsScanWorker extends Worker {

    private static final String TAG = "SmsScanWorker";
    private static final String UNIQUE_NAME = "moneybook_sms_scan_worker";

    public SmsScanWorker(Context context, WorkerParameters params) {
        super(context, params);
    }

    @Override
    public Result doWork() {
        try {
            SmsStartupScanner scanner = new SmsStartupScanner(getApplicationContext());
            int found = scanner.scanLast24h();
            Log.i(TAG, "WorkManager 兜底扫描完成,发现 " + found + " 条新账单");
            // 扫完后立即调度下一次 (WorkManager 周期任务最少 15 分钟)
            // 用 OneTimeWorkRequest 触发自己,实现"间隔 N 分钟扫一次"的效果
            scheduleOneTime(getApplicationContext(), 15);
            return Result.success();
        } catch (Throwable t) {
            Log.e(TAG, "doWork err", t);
            return Result.retry();
        }
    }

    /**
     * 调度周期任务 (15 分钟 - WorkManager 最小值)
     */
    public static void schedule(Context context) {
        try {
            Constraints constraints = new Constraints.Builder()
                .setRequiredNetworkType(NetworkType.NOT_REQUIRED)
                .setRequiresBatteryNotLow(false)
                .setRequiresCharging(false)
                .build();
            PeriodicWorkRequest request = new PeriodicWorkRequest.Builder(
                SmsScanWorker.class,
                15, TimeUnit.MINUTES,  // 最小周期
                5, TimeUnit.MINUTES    // flex 5 分钟
            )
                .setConstraints(constraints)
                .addTag("moneybook")
                .build();
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            );
            Log.i(TAG, "✓ WorkManager 周期任务已调度 (每 15 分钟)");
            // 立即触发一次扫描
            scheduleOneTime(context, 0);
        } catch (Throwable t) {
            Log.e(TAG, "schedule 失败: " + t.getMessage(), t);
        }
    }

    /**
     * 调度单次任务 (delayMinutes 分钟后执行)
     */
    public static void scheduleOneTime(Context context, long delayMinutes) {
        try {
            OneTimeWorkRequest request = new OneTimeWorkRequest.Builder(SmsScanWorker.class)
                .setInitialDelay(delayMinutes, TimeUnit.MINUTES)
                .addTag("moneybook_oneshot")
                .build();
            WorkManager.getInstance(context).enqueueUniqueWork(
                "moneybook_oneshot_scan",
                ExistingWorkPolicy.REPLACE,
                request
            );
            Log.i(TAG, "✓ 单次扫描任务已调度 (delay=" + delayMinutes + "min)");
        } catch (Throwable t) {
            Log.e(TAG, "scheduleOneTime 失败: " + t.getMessage(), t);
        }
    }
}

