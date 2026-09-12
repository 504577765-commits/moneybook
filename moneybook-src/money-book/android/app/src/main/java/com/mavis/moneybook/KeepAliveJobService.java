package com.mavis.moneybook;

import android.app.job.JobInfo;
import android.app.job.JobParameters;
import android.app.job.JobScheduler;
import android.app.job.JobService;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.util.Log;

/**
 * JobScheduler 保活层
 *
 * 为什么需要 JobScheduler:
 * - WorkManager 在国产 ROM 上被限制(15 分钟变 2 小时)
 * - AlarmManager 在 Android 12+ 受 Doze 影响
 * - JobScheduler 是系统级调度,**所有 ROM 都不敢动**(Google 强制)
 *
 * 策略:
 * - 每 15 分钟执行一次(Android 7+ 最小值)
 * - 执行完立即调度下一次,形成循环
 * - 即使 APP 被杀,系统会在 15 分钟内重新拉起
 */
public class KeepAliveJobService extends JobService {

    private static final String TAG = "KeepAliveJobService";
    private static final int JOB_ID = 10001;

    @Override
    public boolean onStartJob(JobParameters params) {
        Log.d(TAG, "🎯 JobScheduler 触发,开始扫描短信");
        try {
            SmsStartupScanner scanner = new SmsStartupScanner(this);
            int found = scanner.scanLast24h();
            Log.i(TAG, "✓ JobScheduler 扫描完成,入账 " + found + " 条");
        } catch (Throwable t) {
            Log.e(TAG, "JobScheduler 扫描失败: " + t.getMessage(), t);
        } finally {
            // 关键:执行完立即调度下一次
            scheduleNext(this);
        }
        // 返回 false 表示工作已完成,不需要保留 wake lock
        return false;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Log.w(TAG, "JobScheduler 被停止,重新调度");
        scheduleNext(this);
        return true; // 返回 true 表示需要重试
    }

    /**
     * 调度下一次 Job
     */
    public static void scheduleNext(Context context) {
        try {
            JobScheduler scheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
            if (scheduler == null) return;

            JobInfo.Builder builder = new JobInfo.Builder(
                JOB_ID,
                new ComponentName(context, KeepAliveJobService.class)
            );

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                // Android 7.0+ 必须用 setMinimumLatency(15 分钟)
                builder.setMinimumLatency(15 * 60 * 1000L);
                builder.setOverrideDeadline(30 * 60 * 1000L); // 最晚 30 分钟内执行
            } else {
                // 旧版本可以用 setPeriodic
                builder.setPeriodic(15 * 60 * 1000L);
            }
            builder.setPersisted(true); // 重启后保持
            builder.setRequiresCharging(false);
            builder.setRequiresDeviceIdle(false);

            int result = scheduler.schedule(builder.build());
            if (result == JobScheduler.RESULT_SUCCESS) {
                Log.d(TAG, "✓ JobScheduler 任务已调度 (15min 后)");
            } else {
                Log.w(TAG, "JobScheduler 调度失败,result=" + result);
            }
        } catch (Throwable t) {
            Log.e(TAG, "scheduleNext 失败: " + t.getMessage(), t);
        }
    }

    /**
     * 启动 JobScheduler 循环
     */
    public static void start(Context context) {
        scheduleNext(context);
    }
}
