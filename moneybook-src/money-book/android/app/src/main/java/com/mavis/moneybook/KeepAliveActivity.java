package com.mavis.moneybook;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.PowerManager;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

/**
 * 1 像素 Activity 保活
 *
 * 原理:
 * - 在锁屏时启动一个 1 像素的 Activity,让进程处于可见状态
 * - 进程优先级提升,不容易被回收
 * - 在屏幕解锁时 finish 自己
 *
 * 关键:
 * - 必须在 AndroidManifest 注册
 * - 监听锁屏/解锁广播
 */
public class KeepAliveActivity extends Activity {

    private static final String TAG = "KeepAliveActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 设置 1 像素
        Window window = getWindow();
        window.setGravity(Gravity.START | Gravity.TOP);
        WindowManager.LayoutParams params = window.getAttributes();
        params.x = 0;
        params.y = 0;
        params.height = 1;
        params.width = 1;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
            | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
            | WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
        window.setAttributes(params);

        // 设置透明背景
        View view = new View(this);
        view.setBackgroundColor(Color.TRANSPARENT);
        setContentView(view);

        Log.d(TAG, "KeepAliveActivity onCreate");

        // 注册解锁广播
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(mReceiver, filter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 如果屏幕是亮的,直接 finish(只在锁屏时保活)
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && pm.isScreenOn()) {
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        try {
            unregisterReceiver(mReceiver);
        } catch (Exception ignore) {}
        super.onDestroy();
        Log.d(TAG, "KeepAliveActivity onDestroy");
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_USER_PRESENT.equals(action)) {
                // 用户解锁,退出
                finish();
            }
        }
    };
}
