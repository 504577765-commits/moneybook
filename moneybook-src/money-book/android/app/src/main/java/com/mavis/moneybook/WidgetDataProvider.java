package com.mavis.moneybook;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Widget 数据提供器
 *
 * 计算今日统计:
 * - 今日支出
 * - 今日收入
 * - 今日笔数
 * - 最近一笔交易
 */
public class WidgetDataProvider {

    private static final String TAG = "WidgetData";
    private static final String PREFS = "moneybook_widget";

    public static class WidgetData {
        public double todayExpense;
        public double todayIncome;
        public int todayCount;
        public String lastMerchant;
        public double lastAmount;
        public String lastType;
        public long lastTimestamp;
        public String themeColor; // pink/blue/green/purple
        public int opacity; // 20/50/80/100
        public int refreshMinutes; // 0=实时,15,30,60

        public WidgetData() {
            this.themeColor = "pink";
            this.opacity = 100;
            this.refreshMinutes = 15;
        }
    }

    /**
     * 从 SQLite 读今日统计
     */
    public static WidgetData getTodayData(Context context) {
        WidgetData data = new WidgetData();
        data.themeColor = getString(context, "theme", "pink");
        data.opacity = getInt(context, "opacity", 100);
        data.refreshMinutes = getInt(context, "refresh_minutes", 15);

        try {
            MoneyDbHelper db = MoneyDbHelper.getInstance(context);
            String today = new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(new Date());
            String startTs = today + " 00:00:00";
            String endTs = today + " 23:59:59";

            // 查今日所有交易
            JSONArray txs = db.queryTransactions(startTs, endTs, 100, 0);
            for (int i = 0; i < txs.length(); i++) {
                JSONObject tx = txs.getJSONObject(i);
                String type = tx.optString("type", "expense");
                double amount = tx.optDouble("amount", 0);
                if ("expense".equals(type)) {
                    data.todayExpense += amount;
                } else if ("income".equals(type)) {
                    data.todayIncome += amount;
                }
                data.todayCount++;
            }

            // 查最近一笔
            JSONArray recent = db.queryTransactions(null, null, 1, 0);
            if (recent.length() > 0) {
                JSONObject last = recent.getJSONObject(0);
                data.lastMerchant = last.optString("merchant", "未知");
                data.lastAmount = last.optDouble("amount", 0);
                data.lastType = last.optString("type", "expense");
                data.lastTimestamp = last.optLong("occurred_at", 0);
            }
        } catch (Throwable t) {
            android.util.Log.e(TAG, "getTodayData 失败: " + t.getMessage(), t);
        }
        return data;
    }

    /**
     * 保存 Widget 设置
     */
    public static void saveSettings(Context context, String theme, int opacity, int refreshMinutes) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        sp.edit()
            .putString("theme", theme)
            .putInt("opacity", opacity)
            .putInt("refresh_minutes", refreshMinutes)
            .apply();
    }

    public static String getString(Context context, String key, String def) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key, def);
    }

    public static int getInt(Context context, String key, int def) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getInt(key, def);
    }
}
