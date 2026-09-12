package com.mavis.moneybook;

import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

/**
 * APP 启动时扫描最近 24h 短信
 *
 * v1.9.0 重大简化:不再依赖 ForegroundService / ContentObserver
 *
 * 工作方式:
 * - APP 启动时自动调用 scanLast24h()
 * - 读取 content://sms/inbox 最近 24h 的短信
 * - 解析银行交易短信,自动入账
 * - 记录已处理的 lastSmsId,避免重复
 *
 * 优势:
 * - 100% 不会闪退 (没有 Service 启动失败的可能)
 * - 不需要任何特殊权限 (只用 READ_SMS)
 * - 国产 ROM 都不拦 (短信数据库是公开的)
 *
 * 劣势:
 * - 用户必须打开 APP 才会触发 (但配合 WorkManager 每 30 分钟兜底,基本够用)
 */
public class SmsStartupScanner {

    private static final String TAG = "SmsStartupScanner";
    private static final String PREFS = "moneybook_sms_scan";
    private static final String KEY_LAST_ID = "last_sms_id";

    private final Context context;
    private final MoneyDbHelper moneyDbHelper;

    public SmsStartupScanner(Context context) {
        this.context = context.getApplicationContext();
        this.moneyDbHelper = new MoneyDbHelper(this.context);
    }

    /**
     * 扫描所有未处理的新短信 (增量,基于 _id)
     * 时间窗放宽到 7 天,避免 WorkManager 错过短信
     * @return 成功入账的数量
     */
    public int scanLast24h() {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        long lastId = prefs.getLong(KEY_LAST_ID, 0);
        Log.d(TAG, "扫描开始: lastId=" + lastId + " prefs=" + (prefs != null));

        int matched = 0;
        int scanned = 0;
        try {
            // 先检查权限
            int perm = context.checkSelfPermission(android.Manifest.permission.READ_SMS);
            Log.d(TAG, "READ_SMS 权限状态: " + (perm == android.content.pm.PackageManager.PERMISSION_GRANTED ? "已授权" : "未授权(" + perm + ")"));

            Uri uri = Uri.parse("content://sms/inbox");
            String[] projection = {"_id", "address", "body", "date"};
            String selection = "_id > ? AND date > ?";
            long since = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
            String[] selectionArgs = {String.valueOf(lastId), String.valueOf(since)};
            String orderBy = "_id ASC";
            Log.d(TAG, "执行 query: " + uri + " selection=" + selection);
            Cursor c = context.getContentResolver().query(
                uri, projection, selection, selectionArgs, orderBy);
            if (c == null) {
                Log.w(TAG, "读取短信失败: Cursor = null (READ_SMS 权限被拒? ROM 拦截?)");
                return 0;
            }
            int totalCount = c.getCount();
            Log.d(TAG, "短信数据库返回: " + totalCount + " 条");
            if (totalCount == 0) {
                Log.w(TAG, "⚠️ 短信数据库返回 0 条!可能原因:");
                Log.w(TAG, "  1. MIUI 14+ 限制 content://sms/inbox");
                Log.w(TAG, "  2. 系统中确实没有最近 7 天的短信");
                Log.w(TAG, "  3. 短信被云端同步,本地没有");
            }
            long maxId = lastId;
            while (c.moveToNext()) {
                scanned++;
                long id = c.getLong(0);
                if (id > maxId) maxId = id;
                String address = c.getString(1);
                if (address != null) {
                    if (address.startsWith("+86")) {
                        address = address.substring(3);
                    } else if (address.startsWith("86") && address.length() > 11) {
                        address = address.substring(2);
                    }
                }
                String body = c.getString(2);
                long date = c.getLong(3);
                if (body == null || body.trim().isEmpty()) continue;
                if (!isLikelyPayment(address, body)) {
                    Log.v(TAG, "过滤非支付短信 [" + address + "]");
                    continue;
                }
                // v2.2.8:银行短信永远开启(不再受 ListenSettings 控制)
                PaymentParser.Result parsed = PaymentParser.parse(body, "startup_scan");
                if (!parsed.success) {
                    Log.d(TAG, "未识别 [" + address + "]: " + body.substring(0, Math.min(40, body.length())));
                    continue;
                }
                Log.i(TAG, "✓ 识别: " + parsed.amount + "元 " + parsed.type + " [" + address + "]");
                try {
                    android.database.sqlite.SQLiteDatabase db = moneyDbHelper.getWritableDatabase();
                    boolean ok = moneyDbHelper.saveTransaction(db, parsed, "startup_scan", body, date);
                    if (ok) {
                        matched++;
                        Log.i(TAG, "✓✓ 已记账 id=" + id);
                    } else {
                        Log.d(TAG, "⚠️ 重复,跳过 id=" + id);
                    }
                } catch (Throwable t) {
                    Log.e(TAG, "✗ 写库失败: " + t.getMessage(), t);
                }
            }
            c.close();
            Log.d(TAG, "扫描完成: scanned=" + scanned + " matched=" + matched + " maxId=" + maxId);
            if (maxId >= lastId) {
                prefs.edit().putLong(KEY_LAST_ID, maxId).commit();
                Log.d(TAG, "✓ 更新 lastId: " + lastId + " → " + maxId);
            }
        } catch (SecurityException se) {
            Log.e(TAG, "READ_SMS SecurityException: " + se.getMessage(), se);
        } catch (Exception e) {
            Log.e(TAG, "scanLast24h 异常", e);
        }
        return matched;
    }

    private boolean isLikelyPayment(String address, String body) {
        if (address != null) {
            String addrUpper = address.toUpperCase();
            for (String prefix : new String[]{
                "95588", "95559", "95566", "95599", "95533", "95555", "95500",
                "95561", "95568", "95508", "95511", "95577", "95522", "95595",
                "95518", "95516", "1069", "1065", "10086", "10010", "10001",
                "ALIPAY", "ALIPAY"
            }) {
                if (addrUpper.contains(prefix.toUpperCase())) return true;
            }
        }
        if (body.contains("消费") || body.contains("支出") || body.contains("收入") ||
            body.contains("转账") || body.contains("入账") || body.contains("扣款") ||
            body.contains("付款") || body.contains("支付") || body.contains("元") ||
            body.contains("¥") || body.contains("￥")) {
            return true;
        }
        return false;
    }
}
