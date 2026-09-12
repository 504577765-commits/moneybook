package com.mavis.moneybook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 闹钟兜底扫描接收器 — v2.1.0 强化
 *
 * 三重保险:
 * 1. 立即调度下一次 3 分钟闹钟
 * 2. 同时调度 5 分钟和 10 分钟的"备份闹钟"(错开时间,防单点失败)
 * 3. 每次 onReceive 立即执行 scanLast24h
 */
public class AlarmScanReceiver extends BroadcastReceiver {

    private static final String TAG = "AlarmScanReceiver";
    public static final String ACTION = "com.mavis.moneybook.ALARM_SCAN";
    // 多个 requestCode 防止 PendingIntent 互相覆盖
    private static final int REQ_PRIMARY = 100;   // 主:3 分钟
    private static final int REQ_BACKUP1 = 101;   // 备份:5 分钟
    private static final int REQ_BACKUP2 = 102;   // 备份:10 分钟

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !ACTION.equals(intent.getAction())) {
            return;
        }
        final android.content.BroadcastReceiver.PendingResult pendingResult = goAsync();
        try {
            Log.d(TAG, "⏰ 闹钟触发,开始扫描短信");
            SmsStartupScanner scanner = new SmsStartupScanner(context);
            int found = scanner.scanLast24h();
            Log.i(TAG, "✓ 闹钟扫描完成,入账 " + found + " 条");
        } catch (Throwable t) {
            Log.e(TAG, "闹钟扫描失败: " + t.getMessage(), t);
        } finally {
            try { pendingResult.finish(); } catch (Exception e) { Log.e(TAG, "finish err", e); }
            // 调度主+备份三个闹钟(防单点失败)
            scheduleAll(context);
        }
    }

    /**
     * 调度三个错开的闹钟 — 防某个被 ROM 拦截
     */
    public static void scheduleAll(Context context) {
        scheduleOne(context, REQ_PRIMARY, 3 * 60 * 1000L);   // 3 分钟
        scheduleOne(context, REQ_BACKUP1, 5 * 60 * 1000L);   // 5 分钟
        scheduleOne(context, REQ_BACKUP2, 10 * 60 * 1000L);  // 10 分钟
    }

    public static void scheduleOne(Context context, int requestCode, long delayMs) {
        try {
            Intent intent = new Intent(context, AlarmScanReceiver.class);
            intent.setAction(ACTION);
            int flags = android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S
                ? android.app.PendingIntent.FLAG_UPDATE_CURRENT | android.app.PendingIntent.FLAG_IMMUTABLE
                : android.app.PendingIntent.FLAG_UPDATE_CURRENT;
            android.app.PendingIntent pi = android.app.PendingIntent.getBroadcast(
                context, requestCode, intent, flags);

            android.app.AlarmManager am = (android.app.AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;

            long triggerAt = System.currentTimeMillis() + delayMs;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
                } else {
                    am.setAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
                }
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
            } else {
                am.set(android.app.AlarmManager.RTC_WAKEUP, triggerAt, pi);
            }
            Log.d(TAG, "✓ 已调度闹钟 req=" + requestCode + " delay=" + (delayMs/1000) + "s");
        } catch (Throwable t) {
            Log.e(TAG, "scheduleOne req=" + requestCode + " 失败: " + t.getMessage(), t);
        }
    }

    /** 兼容旧 API,只调度主闹钟 */
    public static void scheduleNext(Context context) {
        scheduleOne(context, REQ_PRIMARY, 3 * 60 * 1000L);
    }

    public static void start(Context context) {
        scheduleAll(context);
    }
}
