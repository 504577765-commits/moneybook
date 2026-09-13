package com.mavis.moneybook;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import com.getcapacitor.Bridge;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import org.json.JSONArray;
import org.json.JSONObject;

@CapacitorPlugin(name = "MoneyNotifier")
public class MoneyNotifier extends Plugin {

    private static final String TAG = "MoneyNotifier";

    @Override
    public void load() {
        MoneyBookPlugin.setSharedBridge(this.bridge);
        Log.d(TAG, "load: bridge saved");
    }

    @PluginMethod
    public void openAccessSettings(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) { call.reject("Activity is null"); return; }
        try {
            // 跳到"通知使用权"页面 (Android 4.3+,所有 ROM 都有)
            Intent intent = new Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity.startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            // 兜底:跳到应用详情页
            try {
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(android.net.Uri.parse("package:" + activity.getPackageName()));
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                activity.startActivity(intent);
                call.resolve();
            } catch (Exception e2) {
                call.reject("无法打开设置: " + e2.getMessage());
            }
        }
    }

    @PluginMethod
    public void checkNotificationAccess(PluginCall call) {
        boolean granted = isNotificationServiceEnabled();
        JSObject ret = new JSObject();
        ret.put("granted", granted);
        call.resolve(ret);
    }

    @PluginMethod
    public void openExactAlarmSettings(PluginCall call) {
        // Android 12+ 引导用户开启 SCHEDULE_EXACT_ALARM
        // Android 13+ 在记账类 APP 不显示"闹钟和提醒"选项(只有日历/闹钟类才显示)
        // 所以我们打开 APP 详情页,让用户自己找"特殊权限"或"省电策略"
        try {
            // 直接打开 APP 详情页(一定能跳)
            Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getActivity().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void openKeepAliveSettings(PluginCall call) {
        // 修正:Android 13+ 取消了闹钟特殊权限,改用 4 步走方案
        // 1) 打开应用详情页
        // 2) 用户自己找"省电策略" → 无限制
        // 3) 引导用户去后台任务长按锁定
        try {
            String brand = android.os.Build.BRAND.toLowerCase();
            Intent intent = null;
            if (brand.contains("xiaomi") || brand.contains("redmi")) {
                // 小米:省电策略
                try {
                    intent = new Intent();
                    intent.setComponent(new android.content.ComponentName(
                        "com.miui.powerkeeper",
                        "com.miui.powerkeeper.ui.HiddenAppsConfigActivity"));
                    intent.putExtra("package_name", getActivity().getPackageName());
                    intent.putExtra("package_label", "记账本");
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getActivity().startActivity(intent);
                    call.resolve();
                    return;
                } catch (Exception e) {}
            } else if (brand.contains("huawei") || brand.contains("honor")) {
                // 华为:启动管理
                try {
                    intent = new Intent();
                    intent.setComponent(new android.content.ComponentName(
                        "com.huawei.systemmanager",
                        "com.huawei.systemmanager.startupmgr.ui.StartupNormalAppListActivity"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getActivity().startActivity(intent);
                    call.resolve();
                    return;
                } catch (Exception e) {}
            } else if (brand.contains("oppo")) {
                try {
                    intent = new Intent();
                    intent.setComponent(new android.content.ComponentName(
                        "com.coloros.safecenter",
                        "com.coloros.safecenter.permission.startup.StartupAppListActivity"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getActivity().startActivity(intent);
                    call.resolve();
                    return;
                } catch (Exception e) {}
            } else if (brand.contains("vivo")) {
                try {
                    intent = new Intent();
                    intent.setComponent(new android.content.ComponentName(
                        "com.vivo.permissionmanager",
                        "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    getActivity().startActivity(intent);
                    call.resolve();
                    return;
                } catch (Exception e) {}
            }
            // 兜底:打开 APP 详情页(一定能跳转)
            intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
            intent.setData(android.net.Uri.parse("package:" + getActivity().getPackageName()));
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            getActivity().startActivity(intent);
            call.resolve();
        } catch (Exception e) {
            // 最后兜底:打开应用详情
            try {
                Intent i = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                i.setData(android.net.Uri.parse("package:" + getActivity().getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                getActivity().startActivity(i);
                call.resolve();
            } catch (Exception e2) {
                call.reject(e2.getMessage());
            }
        }
    }

    @PluginMethod
    public void getRecentLogs(PluginCall call) {
        // v2.2.8:返回详细的诊断信息
        try {
            org.json.JSONArray logs = new org.json.JSONArray();
            logs.put("=== v2.2.8 自检 ===");
            logs.put("MoneyDbHelper: 写库逻辑已修复 (v2.2.8)");
            logs.put("SmsReceiver: 用 goAsync() 防 ANR,不再受 ListenSettings 控制");
            logs.put("SmsStartupScanner: lastId=" + getLastSmsId());
            logs.put("AlarmScanReceiver: 3/5/10 分钟循环");
            // 关键:权限状态
            logs.put("短信权限: " + (getActivity() != null &&
                getActivity().checkSelfPermission("android.permission.READ_SMS") == PackageManager.PERMISSION_GRANTED
                ? "✅ 已授权" : "❌ 未授权"));
            logs.put("通知监听: " + (isNotificationServiceEnabled() ? "✅ 已开启" : "❌ 未开启"));
            logs.put("精准闹钟权限: " + canScheduleExact());
            logs.put("WorkManager: 已调度");
            // 监听源设置(关键!)
            java.util.Map<String, Boolean> ls = ListenSettings.getAll(getContext());
            logs.put("--- 监听源设置 ---");
            for (java.util.Map.Entry<String, Boolean> e : ls.entrySet()) {
                logs.put("  " + e.getKey() + ": " + (e.getValue() ? "✅ ON" : "❌ OFF"));
            }
            // 数据库统计
            try {
                MoneyDbHelper db = new MoneyDbHelper(getActivity());
                int txCount = db.getTransactionCount();
                logs.put("--- 数据库 ---");
                logs.put("交易数: " + txCount);
                org.json.JSONArray recent = db.getAllTransactions();
                int show = Math.min(5, recent.length());
                for (int i = 0; i < show; i++) {
                    org.json.JSONObject t = recent.getJSONObject(i);
                    logs.put("  最近" + (i+1) + ": " + t.optString("merchant", "未知") +
                        " ¥" + t.optDouble("amount", 0) +
                        " 源=" + t.optString("source", "?") +
                        " 时间=" + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.CHINA)
                            .format(new java.util.Date(t.optLong("occurred_at", 0))));
                }
            } catch (Exception e) {
                logs.put("数据库错误: " + e.getMessage());
            }
            // 立即触发一次扫描
            try {
                SmsStartupScanner scanner = new SmsStartupScanner(getActivity());
                int found = scanner.scanLast24h();
                logs.put(">>> 手动扫描: 新发现 " + found + " 条");
            } catch (Exception e) {
                logs.put(">>> 手动扫描失败: " + e.getMessage());
            }
            JSObject ret = new JSObject();
            ret.put("logs", logs);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    private long getLastSmsId() {
        try {
            return getActivity().getSharedPreferences("moneybook_sms_scan", Context.MODE_PRIVATE)
                .getLong("last_sms_id", 0);
        } catch (Exception e) {
            return -1;
        }
    }

    private String canScheduleExact() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                android.app.AlarmManager am = (android.app.AlarmManager) getActivity()
                    .getSystemService(Context.ALARM_SERVICE);
                return am != null && am.canScheduleExactAlarms() ? "已授权" : "未授权";
            }
            return "不需要(Android 11-)";
        } catch (Exception e) {
            return "检测失败";
        }
    }

    /**
     * 用户在系统设置里勾选/取消通知使用权后,系统不会自动重启我们的 Service
     * 这里用反射强行重新绑定(N_REBIND_BY_USER 是隐藏 API)
     */
    @PluginMethod
    public void checkPermission(PluginCall call) {
        boolean granted = isNotificationServiceEnabled();
        JSObject ret = new JSObject();
        ret.put("granted", granted);
        call.resolve(ret);
    }

    @PluginMethod
    public void requestSmsPermission(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) { call.reject("Activity null"); return; }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // 先检查是否已经授权
            int readGranted = activity.checkSelfPermission("android.permission.READ_SMS");
            if (readGranted == PackageManager.PERMISSION_GRANTED) {
                // 已经授权,直接返回
                JSObject ret = new JSObject();
                ret.put("granted", true);
                call.resolve(ret);
                return;
            }
            // 申请权限
            activity.requestPermissions(
                new String[]{"android.permission.READ_SMS", "android.permission.RECEIVE_SMS"},
                1001
            );
        }
        // Android 6 以下默认授权
        JSObject ret = new JSObject();
        ret.put("granted", Build.VERSION.SDK_INT < Build.VERSION_CODES.M);
        ret.put("pending", Build.VERSION.SDK_INT >= Build.VERSION_CODES.M);
        call.resolve(ret);
    }

    @PluginMethod
    public void checkSmsPermission(PluginCall call) {
        Activity activity = getActivity();
        if (activity == null) { call.reject("Activity null"); return; }
        boolean granted = false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            granted = activity.checkSelfPermission("android.permission.READ_SMS")
                == PackageManager.PERMISSION_GRANTED;
        } else {
            granted = true;
        }
        JSObject ret = new JSObject();
        ret.put("granted", granted);
        call.resolve(ret);
    }

    /**
     * 拉取未消费的通知(从 NotificationListenerService 缓存的 SQLite)
     * 同时把 consumed=1
     */
    @PluginMethod
    public void getPendingNotifications(PluginCall call) {
        try {
            Activity activity = getActivity();
            if (activity == null) { call.reject("Activity null"); return; }
            // 用 try-catch 多次尝试不同路径
            JSONArray arr = tryReadNotifications(activity);

            JSObject ret = new JSObject();
            ret.put("notifications", arr);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e(TAG, "getPendingNotifications err", e);
            call.reject(e.getMessage());
        }
    }

    /**
     * 尝试从多个位置读取通知(兼容不同 Android 版本和路径)
     */
    private JSONArray tryReadNotifications(Activity activity) {
        JSONArray result = new JSONArray();
        // 1. 标准路径
        String[] paths = {
            activity.getDatabasePath("moneybook_notif.db").getAbsolutePath(),
            "/data/data/" + activity.getPackageName() + "/databases/moneybook_notif.db",
            "moneybook_notif.db"
        };
        for (String p : paths) {
            try {
                SQLiteDatabase db;
                if (p.equals("moneybook_notif.db")) {
                    db = activity.openOrCreateDatabase(p, Activity.MODE_PRIVATE, null);
                } else {
                    java.io.File f = new java.io.File(p);
                    if (!f.exists()) continue;
                    db = SQLiteDatabase.openDatabase(p, null, SQLiteDatabase.OPEN_READWRITE);
                }
                Log.d(TAG, "尝试读取 DB: " + p + " (path=" + db.getPath() + ")");
                readFromDb(db, result);
                db.close();
                if (result.length() > 0) {
                    Log.d(TAG, "✓ 从 " + p + " 读到 " + result.length() + " 条");
                    return result;
                }
            } catch (Exception e) {
                Log.w(TAG, "路径 " + p + " 失败: " + e.getMessage());
            }
        }
        return result;
    }

    private void readFromDb(SQLiteDatabase db, JSONArray arr) {
        try {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS pending_notifications (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  pkg TEXT, title TEXT, content TEXT, raw TEXT," +
                "  posted_at INTEGER, consumed INTEGER DEFAULT 0," +
                "  parsed INTEGER DEFAULT 0, parsed_type TEXT," +
                "  parsed_amount REAL, parsed_merchant TEXT, parsed_source TEXT)"
            );
            db.beginTransaction();
            try {
                Cursor c = db.rawQuery(
                    "SELECT id, pkg, title, content, raw, posted_at, " +
                    "parsed, parsed_type, parsed_amount, parsed_merchant, parsed_source " +
                    "FROM pending_notifications WHERE consumed=0 ORDER BY id ASC LIMIT 100",
                    null
                );
                while (c.moveToNext()) {
                    JSONObject o = new JSONObject();
                    o.put("id", c.getLong(0));
                    o.put("packageName", c.getString(1));
                    o.put("pkg", c.getString(1));
                    o.put("title", c.getString(2));
                    o.put("content", c.getString(3));
                    o.put("text", c.getString(3));
                    o.put("raw", c.getString(4));
                    o.put("postedAt", c.getLong(5));
                    int parsed = c.getInt(6);
                    o.put("preParsed", parsed == 1);
                    if (parsed == 1) {
                        o.put("preType", c.getString(7));
                        o.put("preAmount", c.getDouble(8));
                        o.put("preMerchant", c.getString(9));
                        o.put("preSource", c.getString(10));
                    }
                    arr.put(o);
                }
                c.close();
                db.execSQL("UPDATE pending_notifications SET consumed=1 WHERE consumed=0");
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            Log.e(TAG, "readFromDb error", e);
        }
    }

    /**
     * 检查通知使用权是否开启(简化版,只看包名)
     */
    private boolean isNotificationServiceEnabled() {
        Activity activity = getActivity();
        if (activity == null) return false;
        String pkgName = activity.getPackageName();
        final String flat = Settings.Secure.getString(activity.getContentResolver(),
                "enabled_notification_listeners");
        return !TextUtils.isEmpty(flat) && flat.contains(pkgName);
    }

    /**
     * ⭐ JS 端启动时调这个:拉取 Java 端直接写入的交易记录
     * 这是真正的"不依赖 JS 拉取"的关键:数据已经在库里
     */
    @PluginMethod
    public void getJavaTransactions(PluginCall call) {
        try {
            Activity activity = getActivity();
            if (activity == null) { call.reject("Activity null"); return; }
            MoneyDbHelper db = new MoneyDbHelper(activity);
            org.json.JSONArray txs = db.getAllTransactions();
            org.json.JSONArray cats = db.getAllCategories();
            int count = db.getTransactionCount();

            JSObject ret = new JSObject();
            ret.put("transactions", txs);
            ret.put("categories", cats);
            ret.put("count", count);
            Log.d("MoneyNotifier", "getJavaTransactions: count=" + count);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "getJavaTransactions err", e);
            call.reject(e.getMessage());
        }
    }

    // ====== v2.2.3:JS 端统一数据源(全部走 Java 端) ======

    @PluginMethod
    public void listTransactionsJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            Long startTs = call.hasOption("startTs") ? call.getLong("startTs") : null;
            Long endTs = call.hasOption("endTs") ? call.getLong("endTs") : null;
            int limit = call.getInt("limit", 500);
            // 转换:JS 端 endTs 是 < endTs,Java 端是 <= endTs;为兼容 JS 端语义
            String startStr = startTs != null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date(startTs)) : null;
            String endStr = endTs != null ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                .format(new java.util.Date(endTs)) : null;
            org.json.JSONArray txs = db.queryTransactions(startStr, endStr, limit, 0);
            JSObject ret = new JSObject();
            ret.put("transactions", txs);
            ret.put("count", txs.length());
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "listTransactionsJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void insertTransactionJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.insertTransactionJs(
                call.getString("id"),
                call.getString("type", "expense"),
                call.getDouble("amount", 0.0),
                call.getString("category_id", null),
                call.getString("source", "other"),
                call.getString("merchant", ""),
                call.getString("note", ""),
                call.getString("raw_text", ""),
                call.getLong("occurred_at", System.currentTimeMillis()),
                call.getLong("account_id", 0L),
                call.getLong("to_account_id", 0L)
            );
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "insertTransactionJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void updateTransactionJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.updateTransactionJs(
                call.getString("id"),
                call.getString("type", "expense"),
                call.getDouble("amount", 0.0),
                call.getString("category_id", null),
                call.getString("merchant", ""),
                call.getString("note", ""),
                call.getLong("occurred_at", System.currentTimeMillis()),
                call.getLong("account_id", 0L),
                call.getLong("to_account_id", 0L)
            );
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "updateTransactionJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void deleteTransactionJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.deleteTransactionJs(call.getString("id"));
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "deleteTransactionJs err", e);
            call.reject(e.getMessage());
        }
    }

    // ========== v2.3.0: JS 桥接扩展(账户/预算/映射/批量/拆分/upsert) ==========

    @PluginMethod
    public void upsertTransactionJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.upsertTransactionJs(
                call.getString("id"),
                call.getString("type", "expense"),
                call.getDouble("amount", 0.0),
                call.getString("category_id", null),
                call.getString("source", "other"),
                call.getString("merchant", ""),
                call.getString("note", ""),
                call.getString("raw_text", ""),
                call.getLong("occurred_at", System.currentTimeMillis()),
                call.getLong("account_id", 0L),
                call.getLong("to_account_id", 0L)
            );
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "upsertTransactionJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void deleteTransactionsJs(PluginCall call) {
        try {
            org.json.JSONArray arr = call.getArray("ids");
            java.util.List<String> ids = new java.util.ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i);
                    if (s != null && !s.isEmpty()) ids.add(s);
                }
            }
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            int n = db.deleteTransactionsJs(ids.toArray(new String[0]));
            JSObject ret = new JSObject();
            ret.put("success", n > 0);
            ret.put("removed", n);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "deleteTransactionsJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void batchSetCategoryJs(PluginCall call) {
        try {
            org.json.JSONArray arr = call.getArray("ids");
            java.util.List<String> ids = new java.util.ArrayList<>();
            if (arr != null) {
                for (int i = 0; i < arr.length(); i++) {
                    String s = arr.optString(i);
                    if (s != null && !s.isEmpty()) ids.add(s);
                }
            }
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            int n = db.batchSetCategoryJs(ids.toArray(new String[0]), call.getString("category_id", null));
            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("updated", n);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "batchSetCategoryJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void saveTxSplitsJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.saveTxSplitsJs(call.getString("tx_id"), call.getArray("splits"));
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "saveTxSplitsJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void getAllAccountsJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            org.json.JSONArray arr = db.getAllAccounts();
            JSObject ret = new JSObject();
            ret.put("accounts", arr);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "getAllAccountsJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void insertAccountJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            long id = db.insertAccountJs(
                call.getString("name", ""),
                call.getString("type", "other"),
                call.getString("icon", "💰")
            );
            JSObject ret = new JSObject();
            ret.put("success", id > 0);
            ret.put("id", id);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "insertAccountJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void updateAccountJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.updateAccountJs(
                call.getLong("id", 0L),
                call.getString("name", ""),
                call.getString("type", "other"),
                call.getString("icon", "💰"),
                call.getDouble("initial_balance", 0.0)
            );
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "updateAccountJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void deleteAccountJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.deleteAccountJs(call.getLong("id", 0L));
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "deleteAccountJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void getBudgetsJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            org.json.JSONArray arr = db.getBudgetsJs(call.getString("month", ""));
            JSObject ret = new JSObject();
            ret.put("budgets", arr);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "getBudgetsJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void setBudgetJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.setBudgetJs(
                call.getString("category_id", ""),
                call.getString("month", ""),
                call.getDouble("amount", 0.0)
            );
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "setBudgetJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void getMerchantAccountIdJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            long id = db.getMerchantAccountId(call.getString("merchant", ""));
            JSObject ret = new JSObject();
            ret.put("account_id", id);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "getMerchantAccountIdJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void setMerchantAccountJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.setMerchantAccountJs(
                call.getString("merchant", ""),
                call.getLong("account_id", 0L)
            );
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "setMerchantAccountJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void getMerchantCategoryJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            String cid = db.getMerchantCategory(call.getString("merchant", ""));
            JSObject ret = new JSObject();
            ret.put("category_id", cid);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "getMerchantCategoryJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void setMerchantCategoryJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            boolean ok = db.setMerchantCategoryJs(
                call.getString("merchant", ""),
                call.getString("category_id", null)
            );
            JSObject ret = new JSObject();
            ret.put("success", ok);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "setMerchantCategoryJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void sumByCategoryJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            long startTs = call.getLong("startTs", 0L);
            long endTs = call.getLong("endTs", System.currentTimeMillis());
            org.json.JSONArray result = db.sumByCategoryJs(startTs, endTs);
            JSObject ret = new JSObject();
            ret.put("items", result);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "sumByCategoryJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void cleanDuplicatesJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            int removed = db.cleanDuplicates();
            JSObject ret = new JSObject();
            ret.put("removed", removed);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "cleanDuplicatesJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void getRecentRawTextsJs(PluginCall call) {
        // v2.2.40: 获取最近 50 条通知原文(供 APP 显示)
        try {
            int limit = call.getInt("limit", 50);
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            JSONArray arr = db.getRecentRawTexts(limit);
            JSObject ret = new JSObject();
            ret.put("items", arr);
            ret.put("count", arr.length());
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "getRecentRawTextsJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void forceCleanJunkMerchantsJs(PluginCall call) {
        // v2.2.43: 强力清理所有 merchant 里含垃圾关键词的交易
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            int cleaned = db.forceCleanJunkMerchants();
            JSObject ret = new JSObject();
            ret.put("cleaned", cleaned);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "forceCleanJunkMerchantsJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void clearNotificationLogJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            int n = db.clearNotificationLog();
            JSObject ret = new JSObject();
            ret.put("cleared", n);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void resetAllMerchantsJs(PluginCall call) {
        // v2.2.37: 把所有商户重置为"未知商户",为重新识别做准备
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            int reset = db.resetAllMerchants();
            JSObject ret = new JSObject();
            ret.put("reset", reset);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "resetAllMerchantsJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void reExtractMerchantsJs(PluginCall call) {
        // v2.2.34: 重新识别所有商户,返回详细统计
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            int[] stats = db.reExtractAllMerchantsWithStats();
            JSObject ret = new JSObject();
            ret.put("updated", stats[0]);
            ret.put("total", stats[1]);
            ret.put("skippedEmptyRaw", stats[2]);
            ret.put("sameAsBefore", stats[3]);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "reExtractMerchantsJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void debugParseJs(PluginCall call) {
        // v2.2.30: 实时调试 — 粘贴一条文本,显示每步解析结果
        try {
            String text = call.getString("text", "");
            String pkg = call.getString("pkg", "sms");
            if (text.isEmpty()) {
                call.reject("text 不能为空");
                return;
            }
            JSObject result = new JSObject();
            result.put("inputText", text);
            result.put("inputPkg", pkg);

            // 1. PaymentParser 解析
            com.mavis.moneybook.PaymentParser.Result parsed =
                com.mavis.moneybook.PaymentParser.parse(text, pkg);
            JSObject parseResult = new JSObject();
            parseResult.put("success", parsed.success);
            parseResult.put("amount", parsed.amount);
            parseResult.put("type", parsed.type == null ? "" : parsed.type);
            parseResult.put("merchant", parsed.merchant == null ? "" : parsed.merchant);
            parseResult.put("source", parsed.source == null ? "" : parsed.source);
            result.put("parserResult", parseResult);

            // 2. extractFirstBracket 兜底
            String bracketMer = MoneyDbHelper.extractFirstBracket(text);
            result.put("bracketMerchant", bracketMer);

            // 3. 最终 merchant(模拟入库逻辑)
            String finalMer = parsed.merchant;
            if (parsed.merchant == null || parsed.merchant.isEmpty()
                || "未知商户".equals(parsed.merchant) || "未知".equals(parsed.merchant)) {
                if (!bracketMer.isEmpty()) finalMer = bracketMer;
            }
            result.put("finalMerchant", finalMer == null ? "" : finalMer);

            // 4. matchCategory
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            String cat = db.matchCategoryForDebug(parsed, text);
            result.put("finalCategory", cat);

            call.resolve(result);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "debugParseJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void dailySumJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            long startTs = call.getLong("startTs", 0L);
            long endTs = call.getLong("endTs", System.currentTimeMillis());
            org.json.JSONArray result = db.dailySumJs(startTs, endTs);
            JSObject ret = new JSObject();
            ret.put("items", result);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "dailySumJs err", e);
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void getCategoriesJs(PluginCall call) {
        try {
            MoneyDbHelper db = new MoneyDbHelper(getContext());
            String type = call.getString("type", null);
            org.json.JSONArray cats;
            if (type != null && !type.isEmpty()) {
                // 按 type 过滤(JS 端要 expense 或 income)
                org.json.JSONArray all = db.getAllCategories();
                cats = new org.json.JSONArray();
                for (int i = 0; i < all.length(); i++) {
                    org.json.JSONObject cat = all.getJSONObject(i);
                    if (type.equals(cat.optString("type"))) {
                        cats.put(cat);
                    }
                }
            } else {
                cats = db.getAllCategories();
            }
            JSObject ret = new JSObject();
            ret.put("categories", cats);
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "getCategoriesJs err", e);
            call.reject(e.getMessage());
        }
    }

    // ====== 桌面小组件 (v2.2.0) ======

    @PluginMethod
    public void saveWidgetSettings(PluginCall call) {
        try {
            String theme = call.getString("theme", "pink");
            int opacity = call.getInt("opacity", 100);
            int refreshMinutes = call.getInt("refreshMinutes", 15);
            WidgetDataProvider.saveSettings(getContext(), theme, opacity, refreshMinutes);
            // 立即刷新所有 widget
            MoneyWidgetProvider.refreshAll(getContext());
            // 重新调度兜底刷新周期
            MoneyWidgetProvider.scheduleNextRefresh(getContext());
            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("theme", theme);
            ret.put("opacity", opacity);
            ret.put("refreshMinutes", refreshMinutes);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void getWidgetSettings(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            ret.put("theme", WidgetDataProvider.getString(getContext(), "theme", "pink"));
            ret.put("opacity", WidgetDataProvider.getInt(getContext(), "opacity", 100));
            ret.put("refreshMinutes", WidgetDataProvider.getInt(getContext(), "refresh_minutes", 15));
            // 是否已经有 widget
            android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(getContext());
            int[] ids = mgr.getAppWidgetIds(new android.content.ComponentName(getContext(), MoneyWidgetProvider.class));
            ret.put("hasWidget", ids != null && ids.length > 0);
            ret.put("widgetCount", ids == null ? 0 : ids.length);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void refreshWidget(PluginCall call) {
        try {
            // v2.2.12: 详细返回,告诉用户 widget 真的更新了
            android.appwidget.AppWidgetManager mgr = android.appwidget.AppWidgetManager.getInstance(getContext());
            int[] ids = mgr.getAppWidgetIds(new android.content.ComponentName(getContext(), MoneyWidgetProvider.class));
            MoneyWidgetProvider.refreshAll(getContext());
            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("widgetCount", ids == null ? 0 : ids.length);
            if (ids == null || ids.length == 0) {
                ret.put("message", "⚠️ 桌面上没有小组件!长按桌面 → 小组件 → 找'记账本'添加");
            } else {
                ret.put("message", "✅ 已更新 " + ids.length + " 个 widget");
            }
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    // ====== v2.2.4:监听源设置 ======

    @PluginMethod
    public void getListenSettings(PluginCall call) {
        try {
            java.util.Map<String, Boolean> all = ListenSettings.getAll(getContext());
            JSObject ret = new JSObject();
            for (java.util.Map.Entry<String, Boolean> e : all.entrySet()) {
                ret.put(e.getKey(), e.getValue());
            }
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    // ====== v2.2.9:实时运行状态 ======

    @PluginMethod
    public void getRunStatus(PluginCall call) {
        try {
            JSObject ret = new JSObject();
            // 短信权限
            int smsPerm = getActivity() != null ?
                getActivity().checkSelfPermission("android.permission.READ_SMS") : 0;
            ret.put("smsPermission", smsPerm == android.content.pm.PackageManager.PERMISSION_GRANTED);

            // 通知监听
            ret.put("notifListener", isNotificationServiceEnabled());

            // 数据库
            try {
                MoneyDbHelper db = new MoneyDbHelper(getActivity());
                ret.put("txCount", db.getTransactionCount());

                // 最近 1 小时的交易数
                long oneHourAgo = System.currentTimeMillis() - 3600 * 1000;
                org.json.JSONArray recent = db.queryTransactions(
                    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                        .format(new java.util.Date(oneHourAgo)),
                    null, 100, 0);
                ret.put("lastHourCount", recent.length());

                // 最近一笔
                org.json.JSONArray all = db.getAllTransactions();
                if (all.length() > 0) {
                    org.json.JSONObject last = all.getJSONObject(0);
                    JSObject lastObj = new JSObject();
                    lastObj.put("merchant", last.optString("merchant"));
                    lastObj.put("amount", last.optDouble("amount", 0));
                    lastObj.put("source", last.optString("source"));
                    lastObj.put("occurred_at", last.optLong("occurred_at", 0));
                    lastObj.put("auto", last.optInt("auto", 0));
                    ret.put("lastTx", lastObj);
                }
            } catch (Exception e) {
                ret.put("dbError", e.getMessage());
            }

            // lastId
            try {
                long lastId = getActivity().getSharedPreferences("moneybook_sms_scan", Context.MODE_PRIVATE)
                    .getLong("last_sms_id", 0);
                ret.put("lastSmsId", lastId);
            } catch (Exception ignore) {}

            // SmsStartupScanner 立即跑一次,看能不能读到数据
            try {
                SmsStartupScanner scanner = new SmsStartupScanner(getActivity());
                int found = scanner.scanLast24h();
                ret.put("scanFound", found);
            } catch (Exception e) {
                ret.put("scanError", e.getMessage());
            }

            // 总结
            boolean running = smsPerm == android.content.pm.PackageManager.PERMISSION_GRANTED;
            ret.put("running", running);
            ret.put("summary", running ?
                "✅ 短信权限已授权,自动记账应该工作" :
                "❌ 短信权限未授权!这是自动记账不工作的最常见原因");

            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    @PluginMethod
    public void saveListenSettings(PluginCall call) {
        try {
            java.util.Map<String, Boolean> settings = new java.util.HashMap<>();
            String[] keys = new String[]{
                ListenSettings.SRC_SMS,
                ListenSettings.SRC_ALIPAY,
                ListenSettings.SRC_WECHAT,
                ListenSettings.SRC_UNIONPAY,
                ListenSettings.SRC_BANK_APP,
                ListenSettings.SRC_OTHER
            };
            for (String k : keys) {
                if (call.hasOption(k)) {
                    settings.put(k, call.getBoolean(k, false));
                }
            }
            ListenSettings.save(getContext(), settings);
            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("saved", settings);
            call.resolve(ret);
        } catch (Exception e) {
            call.reject(e.getMessage());
        }
    }

    /**
     * v2.2.62: 导出所有交易为 CSV 文件,保存到 Download/moneybook-YYYYMMDD-HHmmss.csv
     * 返回 {success, filePath, count}
     */
    @PluginMethod
    public void exportTransactionsToCsv(PluginCall call) {
        try {
            Context ctx = getContext();
            MoneyDbHelper db = new MoneyDbHelper(ctx);
            org.json.JSONArray txs = db.getAllTransactions();
            org.json.JSONArray cats = db.getAllCategories();

            // cat id → name map
            java.util.Map<String, String> catMap = new java.util.HashMap<>();
            for (int i = 0; i < cats.length(); i++) {
                org.json.JSONObject c = cats.getJSONObject(i);
                catMap.put(c.getString("id"), c.getString("name"));
            }

            StringBuilder sb = new StringBuilder();
            // BOM 让 Excel 正确识别 UTF-8
            sb.append('\ufeff');
            // 表头
            sb.append("时间,类型,金额,分类,商户,来源,备注\n");
            for (int i = 0; i < txs.length(); i++) {
                org.json.JSONObject t = txs.getJSONObject(i);
                long ts = t.optLong("occurred_at", 0);
                String time = ts > 0 ? new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.CHINA)
                    .format(new java.util.Date(ts)) : "";
                String type = t.optString("type", "");
                double amount = t.optDouble("amount", 0);
                String catId = t.optString("category_id", "");
                String catName = catMap.getOrDefault(catId, catId);
                String merchant = t.optString("merchant", "");
                String source = t.optString("source", "");
                String note = t.optString("note", "");
                // CSV 转义
                sb.append(escapeCsv(time)).append(',')
                  .append(type).append(',')
                  .append(String.format("%.2f", amount)).append(',')
                  .append(escapeCsv(catName)).append(',')
                  .append(escapeCsv(merchant)).append(',')
                  .append(escapeCsv(source)).append(',')
                  .append(escapeCsv(note)).append('\n');
            }

            // 文件名
            String fname = "moneybook-" + new java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.CHINA)
                .format(new java.util.Date()) + ".csv";
            java.io.File out;
            // 优先下载到公共 Download 目录
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS);
            java.io.File dlDir = ctx.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS);
            if (dlDir == null) dlDir = ctx.getFilesDir();
            if (!dlDir.exists()) dlDir.mkdirs();
            out = new java.io.File(dlDir, fname);
            java.io.FileOutputStream fos = new java.io.FileOutputStream(out);
            fos.write(sb.toString().getBytes("UTF-8"));
            fos.close();

            // 通知媒体扫描,让用户在文件管理器/相册能看到
            try {
                android.content.Intent mediaScan = new android.content.Intent(android.content.Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
                mediaScan.setData(android.net.Uri.fromFile(out));
                ctx.sendBroadcast(mediaScan);
            } catch (Exception ignore) {}

            JSObject ret = new JSObject();
            ret.put("success", true);
            ret.put("filePath", out.getAbsolutePath());
            ret.put("count", txs.length());
            call.resolve(ret);
        } catch (Exception e) {
            Log.e("MoneyNotifier", "exportTransactionsToCsv err", e);
            call.reject(e.getMessage());
        }
    }

    private String escapeCsv(String s) {
        if (s == null) return "";
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            return "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
