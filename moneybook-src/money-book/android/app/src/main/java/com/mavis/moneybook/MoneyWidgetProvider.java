package com.mavis.moneybook;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;
import android.widget.RemoteViews;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 桌面小组件 v2.2.2
 *
 * 修复 v2.2.0 / v2.2.1 widget 加载失败的两个根因:
 * 1) ❌ layout 里有 <View> 当分隔线 - RemoteViews 不支持,直接 inflate 失败
 * 2) ❌ 用 setImageViewBitmap 画渐变背景 - Bitmap 太大容易 TransactionTooLargeException
 *
 * v2.2.2 方案:
 * - layout 全部用 LinearLayout + TextView(都是 RemoteViews 支持的)
 * - 背景用静态 drawable(gradient shape),5 个主题 = 5 个 drawable
 * - 主题切换:用 setInt(LinearLayout, "setBackgroundResource", R.drawable.xxx)
 *   这是 RemoteViews 标准支持的方法,绝对 work
 */
public class MoneyWidgetProvider extends AppWidgetProvider {

    private static final String TAG = "MoneyWidget";
    public static final String ACTION_REFRESH = "com.mavis.moneybook.WIDGET_REFRESH";
    public static final String ACTION_OPEN_APP = "com.mavis.moneybook.WIDGET_OPEN";

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        Log.d(TAG, "onUpdate, ids=" + (appWidgetIds == null ? 0 : appWidgetIds.length));
        for (int id : appWidgetIds) {
            updateWidget(context, appWidgetManager, id);
        }
    }

    @Override
    public void onEnabled(Context context) {
        super.onEnabled(context);
        Log.d(TAG, "onEnabled");
    }

    @Override
    public void onDisabled(Context context) {
        super.onDisabled(context);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent == null) return;
        String action = intent.getAction();
        if (ACTION_REFRESH.equals(action)) {
            Log.d(TAG, "收到刷新广播");
            refreshAll(context);
        }
    }

    /**
     * 更新单个 widget
     */
    public static void updateWidget(Context context, AppWidgetManager mgr, int widgetId) {
        try {
            WidgetDataProvider.WidgetData data = WidgetDataProvider.getTodayData(context);

            RemoteViews rv = new RemoteViews(context.getPackageName(), R.layout.widget_money);

            // 主题色:用 setBackgroundResource 切换 drawable(RemoteViews 标准支持)
            int bgRes = getThemeDrawable(data.themeColor);
            rv.setInt(R.id.widget_root, "setBackgroundResource", bgRes);

            // 透明度:暂不支持(RemoteViews 不能动态改 drawable alpha)
            // 想要透明度得切 4 套不同透明度的 drawable,先不做

            // 今日支出
            String expText = String.format(Locale.CHINA, "支出 ¥%.2f", data.todayExpense);
            rv.setTextViewText(R.id.tv_expense, expText);

            // 今日收入
            String incText = String.format(Locale.CHINA, "收入 ¥%.2f", data.todayIncome);
            rv.setTextViewText(R.id.tv_income, incText);

            // 今日笔数
            String countText = String.format(Locale.CHINA, "今日 %d 笔", data.todayCount);
            rv.setTextViewText(R.id.tv_count, countText);

            // 最近一笔
            if (data.lastMerchant != null && !data.lastMerchant.isEmpty()) {
                String lastText = String.format(Locale.CHINA, "%s %s ¥%.2f",
                    "income".equals(data.lastType) ? "💰" : "🛒",
                    data.lastMerchant, data.lastAmount);
                rv.setTextViewText(R.id.tv_last, lastText);
            } else {
                rv.setTextViewText(R.id.tv_last, "还没有记账哦~");
            }

            // 时间戳
            String timeText = new SimpleDateFormat("HH:mm 更新", Locale.CHINA).format(new Date());
            rv.setTextViewText(R.id.tv_time, timeText);

            // 点击 → 打开 APP
            Intent openIntent = new Intent(context, MainActivity.class);
            openIntent.setAction(ACTION_OPEN_APP);
            openIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent openPi = PendingIntent.getActivity(context, widgetId, openIntent, piFlags);
            rv.setOnClickPendingIntent(R.id.widget_root, openPi);

            mgr.updateAppWidget(widgetId, rv);
            Log.d(TAG, "✓ 更新 widget " + widgetId + " 成功");
        } catch (Throwable t) {
            Log.e(TAG, "updateWidget err: " + t.getMessage(), t);
        }
    }

    /**
     * 主题 → drawable 资源
     */
    private static int getThemeDrawable(String theme) {
        if (theme == null) return R.drawable.widget_bg_pink;
        switch (theme) {
            case "blue":   return R.drawable.widget_bg_blue;
            case "green":  return R.drawable.widget_bg_green;
            case "purple": return R.drawable.widget_bg_purple;
            case "orange": return R.drawable.widget_bg_orange;
            case "pink":
            default:       return R.drawable.widget_bg_pink;
        }
    }

    /**
     * v2.2.11: 取消 30 分钟兜底定时器
     * 改: widget 完全靠"记账后立即刷新"驱动,不再有定时器
     * 理由: 用户要"实时刷新",30 分钟兜底算"延迟",应该去掉
     * 副作用: APP 异常退出且没新交易时,widget 不会更新 — 但反正"实时"模式就是这样
     */
    public static void scheduleNextRefresh(Context context) {
        // v2.2.11:不再调度任何定时器
        // widget 只在 saveTransaction / addTransaction 触发后立即刷新
        Log.d(TAG, "v2.2.11: 完全实时模式,不再调度定时器");
    }

    /**
     * v2.2.11: 取消已调度的定时器(防止用户从老版本升级后还有遗留的定时器)
     */
    public static void cancelRefresh(Context context) {
        try {
            Intent intent = new Intent(context, MoneyWidgetProvider.class);
            intent.setAction(ACTION_REFRESH);
            int piFlags = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getBroadcast(context, 200, intent, piFlags);
            AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pi);
            Log.d(TAG, "v2.2.11: 取消遗留的 widget 定时器");
        } catch (Throwable t) {
            Log.e(TAG, "cancelRefresh err: " + t.getMessage(), t);
        }
    }

    /**
     * 立即刷新所有 widget
     */
    public static void refreshAll(Context context) {
        try {
            AppWidgetManager mgr = AppWidgetManager.getInstance(context);
            int[] ids = mgr.getAppWidgetIds(new ComponentName(context, MoneyWidgetProvider.class));
            for (int id : ids) {
                updateWidget(context, mgr, id);
            }
            Log.d(TAG, "✓ refreshAll, ids=" + ids.length);
        } catch (Throwable t) {
            Log.e(TAG, "refreshAll err: " + t.getMessage(), t);
        }
    }

    public static void sendRefreshBroadcast(Context context) {
        try {
            Intent intent = new Intent(ACTION_REFRESH);
            intent.setComponent(new ComponentName(context, MoneyWidgetProvider.class));
            context.sendBroadcast(intent);
        } catch (Throwable t) {
            Log.e(TAG, "sendRefreshBroadcast err: " + t.getMessage(), t);
            refreshAll(context);
        }
    }
}
