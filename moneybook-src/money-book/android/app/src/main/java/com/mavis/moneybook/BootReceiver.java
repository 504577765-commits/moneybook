package com.mavis.moneybook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * 开机自启 + APP 更新后自启
 *
 * v2.1.0:启动所有保活层
 * - AlarmManager 精准闹钟(3 分钟)
 * - WorkManager 周期任务(15 分钟)
 * - JobScheduler 备用层(15 分钟)
 */
public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null) return;
        String action = intent.getAction();
        if (!action.equals(Intent.ACTION_BOOT_COMPLETED) &&
            !action.equals("android.intent.action.QUICKBOOT_POWERON") &&
            !action.equals(Intent.ACTION_MY_PACKAGE_REPLACED) &&
            !action.equals(Intent.ACTION_PACKAGE_REPLACED)) {
            return;
        }
        Log.d(TAG, "boot/package replaced,启动所有保活层");
        try {
            startAllKeepAlives(context);
        } catch (Exception e) {
            Log.e(TAG, "boot start failed", e);
        }
    }

    /**
     * 启动所有保活层 — 集中管理
     */
    public static void startAllKeepAlives(Context context) {
        // 1) AlarmManager 精准闹钟(主路径,3 分钟)
        AlarmScanReceiver.start(context);
        // 2) WorkManager 周期任务(辅助,15 分钟)
        SmsScanWorker.schedule(context);
        // 3) JobScheduler 备用层(15 分钟,系统级)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            KeepAliveJobService.start(context);
        }
        Log.d(TAG, "✓ 所有保活层已启动");
    }
}
