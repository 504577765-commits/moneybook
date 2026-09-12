package com.mavis.moneybook;

import android.app.Notification;
import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import com.getcapacitor.Bridge;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 监听通知栏的支付类通知
 * v1.3 简化版:Java 端只做通知捕获 + 解析,存 SQLite
 */
public class PaymentNotificationListener extends NotificationListenerService {

    private static final String TAG = "PayListener";

    private static final Set<String> INTERESTED_PACKAGES = new HashSet<>(Arrays.asList(
        "com.eg.android.AlipayGphone",
        "com.tencent.mm",
        "com.unionpay",
        "com.icbc", "com.abchina", "com.bankcomm", "com.cmbchina",
        "com.cib", "com.spdb", "com.cgbchina", "com.everbright",
        "com.pingan", "com.boc", "com.ccb",
        "com.android.mms", "com.google.android.apps.messaging"
    ));

    private static final Set<String> STRONG_KEYWORDS = new HashSet<>(Arrays.asList(
        "付款", "支付", "消费", "支出", "扣款", "成功",
        "收款", "入账", "转入", "退款", "红包",
        "转账", "余额", "提现", "充值", "到账", "账单", "交易",
        "尾号", "信用卡", "借记卡", "POS"
    ));

    private NotificationDbHelper dbHelper;
    private MoneyDbHelper moneyDbHelper;

    @Override
    public void onCreate() {
        super.onCreate();
        dbHelper = new NotificationDbHelper(this);
        moneyDbHelper = new MoneyDbHelper(this);
        // 预热 moneybook.db
        try {
            moneyDbHelper.getWritableDatabase();
            Log.d(TAG, "=== moneybook.db 初始化完成 ===");
        } catch (Exception e) {
            Log.e(TAG, "moneybook.db 初始化失败", e);
        }
        Log.d(TAG, "=== PaymentNotificationListener 启动 ===");
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        Log.d(TAG, "=== 通知监听服务已连接 ===");
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkg = sbn.getPackageName();
        if (!INTERESTED_PACKAGES.contains(pkg)) return;

        // v2.2.4:检查用户设置 — 这个来源是否启用
        String sourceKey = ListenSettings.sourceByPackage(pkg);
        if (!ListenSettings.isEnabled(this, sourceKey)) {
            Log.d(TAG, "[设置] " + pkg + " (" + sourceKey + ") 监听已关闭,跳过");
            return;
        }

        try {
            Notification n = sbn.getNotification();
            if (n == null) return;

            String text = extractText(n);
            if (text == null || text.trim().isEmpty()) return;

            // v2.2.40: 记所有通知原文(不丢!)
            try {
                moneyDbHelper.recordNotification(pkg, text);
            } catch (Exception ignore) {}

            if (!hasStrongKeyword(text)) {
                Log.d(TAG, "[filter] no strong keyword");
                return;
            }

            PaymentParser.Result parsed = PaymentParser.parse(text, pkg);
            Log.d(TAG, "[" + pkg + "] parsed: success=" + parsed.success
                + " type=" + parsed.type + " amount=" + parsed.amount
                + " merchant=" + parsed.merchant + " source=" + parsed.source);

            try {
                long rowId = saveToDb(pkg, text, parsed, sbn.getPostTime());
                Log.d(TAG, "saved pending rowId=" + rowId);
            } catch (Exception e) {
                Log.e(TAG, "saveToDb 失败", e);
                return;
            }

            // ⭐ 关键:直接写入 transactions 表(不依赖 WebView!)
            try {
                SQLiteDatabase moneyDb = moneyDbHelper.getWritableDatabase();
                boolean inserted = moneyDbHelper.saveTransaction(moneyDb, parsed, pkg, text, sbn.getPostTime());
                if (inserted) {
                    Log.d(TAG, "✓ 已直接记账: " + parsed.amount + " 元 " + parsed.type);
                } else {
                    Log.d(TAG, "重复或解析失败,跳过 transactions 写入");
                }
            } catch (Exception e) {
                Log.e(TAG, "saveTransaction 失败", e);
            }

            try {
                pushToWebView(pkg, text, parsed, sbn.getPostTime());
            } catch (Exception e) {
                Log.e(TAG, "push webview failed", e);
            }
        } catch (Exception e) {
            Log.e(TAG, "onNotificationPosted error", e);
        }
    }

    private String extractText(Notification n) {
        Bundle extras = n.extras;
        if (extras == null) return null;
        StringBuilder sb = new StringBuilder();
        String title = extras.getString(Notification.EXTRA_TITLE, "");
        String text = extras.getString(Notification.EXTRA_TEXT, "");
        String big = extras.getString(Notification.EXTRA_BIG_TEXT, "");
        String sub = extras.getString(Notification.EXTRA_SUB_TEXT, "");
        if (title != null && !title.isEmpty()) sb.append(title).append(" ");
        if (text != null && !text.isEmpty()) sb.append(text).append(" ");
        if (big != null && !big.isEmpty() && big.length() > text.length()) sb.append(big).append(" ");
        if (sub != null && !sub.isEmpty()) sb.append(sub).append(" ");
        CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
        if (lines != null) {
            for (CharSequence line : lines) {
                if (line != null) sb.append(line).append(" ");
            }
        }
        if (n.tickerText != null) sb.append(n.tickerText).append(" ");

        // v2.2.39: 扫描所有 extras key — 微信/支付宝常把商家名放自定义字段
        // (例如: "android.summary", "android.subText" 之外,微信有 "com.tencent.mm" 私有 keys)
        try {
            for (String key : extras.keySet()) {
                if (key == null) continue;
                // 跳过标准 key(已经提取过了)
                if (key.equals(Notification.EXTRA_TITLE) || key.equals("android.title")) continue;
                if (key.equals(Notification.EXTRA_TEXT) || key.equals("android.text")) continue;
                if (key.equals(Notification.EXTRA_BIG_TEXT) || key.equals("android.bigText")) continue;
                if (key.equals(Notification.EXTRA_SUB_TEXT) || key.equals("android.subText")) continue;
                if (key.equals(Notification.EXTRA_TEXT_LINES) || key.equals("android.textLines")) continue;
                Object v = extras.get(key);
                if (v == null) continue;
                String s = v.toString().trim();
                if (s.isEmpty() || s.length() > 500) continue;
                // 只关心看起来像商户/描述的字符串
                if (s.matches(".*(商户|商家|付款方|收款方|付款给|消费).*")) {
                    sb.append(s).append(" ");
                    Log.d(TAG, "[extras] key=" + key + " val=" + s);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "extras scan err: " + e.getMessage());
        }

        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    private boolean hasStrongKeyword(String text) {
        // v2.2.19 修复: 只用强关键词,不再"含¥/两位小数"就放行
        // 之前的 text.matches(".*\\d+\\.\\d{2}.*") 会让快递单号、商品价格等都进入解析流程
        for (String kw : STRONG_KEYWORDS) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    private long saveToDb(String pkg, String text, PaymentParser.Result parsed, long postTime) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues v = new ContentValues();
        v.put("pkg", pkg);
        v.put("title", "");
        v.put("content", text);
        v.put("raw", text);
        v.put("posted_at", postTime);
        v.put("consumed", 0);
        v.put("parsed", parsed.success ? 1 : 0);
        v.put("parsed_type", parsed.type == null ? "" : parsed.type);
        v.put("parsed_amount", parsed.amount);
        v.put("parsed_merchant", parsed.merchant == null ? "" : parsed.merchant);
        v.put("parsed_source", parsed.source == null ? "" : parsed.source);
        return db.insert("pending_notifications", null, v);
    }

    private void pushToWebView(String pkg, String text, PaymentParser.Result parsed, long postTime) {
        Bridge bridge = MoneyBookPlugin.getSharedBridge();
        if (bridge == null || bridge.getWebView() == null) return;
        try {
            JSONObject info = new JSONObject();
            info.put("title", "");
            info.put("content", text);
            info.put("text", text);
            info.put("packageName", pkg);
            info.put("pkg", pkg);
            info.put("postedAt", postTime);
            info.put("raw", text);
            if (parsed.success) {
                info.put("preAmount", parsed.amount);
                info.put("preType", parsed.type);
                info.put("preMerchant", parsed.merchant);
                info.put("preSource", parsed.source);
            }
            final String json = info.toString();
            bridge.getWebView().post(() -> {
                try {
                    bridge.triggerJSEvent("paymentNotification", "window", json);
                } catch (Exception e) {
                    Log.e(TAG, "triggerJSEvent failed", e);
                }
            });
        } catch (Exception e) {
            Log.e(TAG, "JSON error", e);
        }
    }

    private static class NotificationDbHelper extends SQLiteOpenHelper {
        private static final String DB_NAME = "moneybook_notif.db";
        private static final int VER = 2;

        NotificationDbHelper(Context ctx) {
            super(ctx, DB_NAME, null, VER);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS pending_notifications (" +
                "  id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "  pkg TEXT, title TEXT, content TEXT, raw TEXT," +
                "  posted_at INTEGER, consumed INTEGER DEFAULT 0," +
                "  parsed INTEGER DEFAULT 0," +
                "  parsed_type TEXT, parsed_amount REAL," +
                "  parsed_merchant TEXT, parsed_source TEXT)"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldV, int newV) {
            if (oldV < 2) {
                try { db.execSQL("ALTER TABLE pending_notifications ADD COLUMN parsed INTEGER DEFAULT 0"); } catch (Exception ignore) {}
                try { db.execSQL("ALTER TABLE pending_notifications ADD COLUMN parsed_type TEXT"); } catch (Exception ignore) {}
                try { db.execSQL("ALTER TABLE pending_notifications ADD COLUMN parsed_amount REAL"); } catch (Exception ignore) {}
                try { db.execSQL("ALTER TABLE pending_notifications ADD COLUMN parsed_merchant TEXT"); } catch (Exception ignore) {}
                try { db.execSQL("ALTER TABLE pending_notifications ADD COLUMN parsed_source TEXT"); } catch (Exception ignore) {}
            }
        }
    }
}
