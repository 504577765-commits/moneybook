package com.mavis.moneybook;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

/**
 * 锁屏广播接收器
 *
 * 锁屏时启动 KeepAliveActivity (1 像素 Activity)
 * 解锁时 KeepAliveActivity 自动 finish
 */
public class ScreenLockReceiver extends BroadcastReceiver {

    private static final String TAG = "ScreenLockReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) return;
        String action = intent.getAction();

        if (Intent.ACTION_SCREEN_OFF.equals(action)) {
            // 屏幕关闭(锁屏)
            Log.d(TAG, "屏幕关闭,启动 1 像素 Activity");
            try {
                Intent i = new Intent(context, KeepAliveActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                i.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
                context.startActivity(i);
            } catch (Exception e) {
                Log.e(TAG, "启动 KeepAliveActivity 失败: " + e.getMessage(), e);
            }
        } else if (Intent.ACTION_SCREEN_ON.equals(action)) {
            // 屏幕点亮(可选:不做事,让 KeepAliveActivity 自己 finish)
            Log.d(TAG, "屏幕点亮");
        } else if (Intent.ACTION_USER_PRESENT.equals(action)) {
            // 用户解锁
            Log.d(TAG, "用户解锁");
        }
    }
}
