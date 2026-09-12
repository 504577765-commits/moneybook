package com.mavis.moneybook;

import android.os.Bundle;
import android.util.Log;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    private static final String TAG = "MainActivity";

    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(MoneyNotifier.class);
        super.onCreate(savedInstanceState);
        // v2.1.0:四重保活
        // - 启动扫描:立即扫一次最近 24h 短信
        // - AlarmManager:3 分钟一次精准闹钟
        // - WorkManager:15 分钟一次周期任务
        // - JobScheduler:15 分钟一次备用层
        try {
            SmsStartupScanner scanner = new SmsStartupScanner(this);
            int found = scanner.scanLast24h();
            Log.i(TAG, "启动扫描 found=" + found);
            // v2.2.5:启动时检查并补种默认分类(防止升级后分类表空)
            ensureCategoriesSeeded();
            // 启动所有保活层
            BootReceiver.startAllKeepAlives(this);
            Log.i(TAG, "所有保活层已启动");
            // v2.2.11: 取消老版本遗留的 widget 30分钟兜底定时器
            MoneyWidgetProvider.cancelRefresh(this);
            // 刷新桌面小组件
            MoneyWidgetProvider.refreshAll(this);
        } catch (Throwable t) {
            Log.e(TAG, "启动扫描失败: " + t.getMessage(), t);
        }
    }

    /**
     * v2.2.5:确保分类表已 seed(升级用户也能补)
     */
    private void ensureCategoriesSeeded() {
        try {
            MoneyDbHelper db = new MoneyDbHelper(this);
            android.database.sqlite.SQLiteDatabase sqlDb = db.getWritableDatabase();
            java.lang.reflect.Method m = MoneyDbHelper.class.getDeclaredMethod("seedDefaultCategories", android.database.sqlite.SQLiteDatabase.class);
            m.setAccessible(true);
            m.invoke(db, sqlDb);
        } catch (Throwable t) {
            Log.e(TAG, "ensureCategoriesSeeded err: " + t.getMessage());
        }
    }
}
