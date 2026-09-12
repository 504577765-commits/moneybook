package com.mavis.moneybook;

import android.content.BroadcastReceiver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import com.getcapacitor.Bridge;

import org.json.JSONObject;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 实时短信接收器 — v1.9.4 强化版
 *
 * 关键改进:
 * - 用 goAsync() 避免 ANR
 * - 整个流程包在大 try-catch,任何异常都不让 receiver 死
 * - 详细日志,记录每一步
 */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    // v2.2.52: 银行短信号码白名单 (只信任银行,排除运营商/广告/电费)
    private static final Set<String> BANK_SENDER_KEYWORDS = new HashSet<>(Arrays.asList(
        "95588", "95559", "95566", "95599", "95533", "95555", "95500", "95561",
        "95568", "95508", "95511", "95577", "95522", "95595", "95518", "95516",
        "1069", "1065"  // 工行/建行 service 号
        // 删了 10086/10010/10001 (运营商) — 会误识别话费/广告短信
        // 删了 10099 (中国广电) — 不是银行
    ));

    // v2.2.52: 黑名单关键词 (这些内容直接拒收)
    private static final Set<String> SMS_BLACKLIST = new HashSet<>(Arrays.asList(
        "话费账单", "话费", "流量", "宽带", "套餐及固定费", "套餐费",
        "【话费账单】", "【中国广电】", "【中国移动】", "【中国联通】", "【中国电信】",
        "尊敬的", "客服热线", "查询费用",
        "医保", "社保", "公积金", "税务", "发票", "营业执照",
        "验证码", "短信验证码", "登录验证", "动态密码"
    ));

    @Override
    public void onReceive(Context context, Intent intent) {
        // v1.9.4:用 goAsync() 避免 ANR,异步处理
        final PendingResult pendingResult = goAsync();
        try {
            handleSms(context, intent);
        } catch (Throwable t) {
            Log.e(TAG, "onReceive 顶层异常: " + t.getMessage(), t);
        } finally {
            try {
                pendingResult.finish();
            } catch (Exception e) {
                Log.e(TAG, "pendingResult.finish 失败: " + e.getMessage());
            }
        }
    }

    private void handleSms(Context context, Intent intent) {
        if (intent == null || intent.getAction() == null) {
            Log.d(TAG, "intent or action null");
            return;
        }
        if (!intent.getAction().equals("android.provider.Telephony.SMS_RECEIVED")) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            Log.d(TAG, "bundle null");
            return;
        }
        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) {
            Log.d(TAG, "no pdus");
            return;
        }

        StringBuilder body = new StringBuilder();
        String address = "";
        long smsTimestamp = 0;
        for (Object pdu : pdus) {
            try {
                SmsMessage msg = SmsMessage.createFromPdu((byte[]) pdu);
                if (msg == null) continue;
                // 修复 BUG:同时尝试 getDisplayOriginatingAddress 和 getOriginatingAddress
                // 某些 ROM(如小米)getDisplayOriginatingAddress 返回 null,但 getOriginatingAddress 有值
                String a1 = msg.getDisplayOriginatingAddress();
                String a2 = msg.getOriginatingAddress();
                if (a1 != null && !a1.isEmpty()) {
                    address = a1;
                } else if (a2 != null && !a2.isEmpty()) {
                    address = a2;
                }
                body.append(msg.getDisplayMessageBody());
                if (msg.getTimestampMillis() > 0) {
                    smsTimestamp = msg.getTimestampMillis();
                }
            } catch (Exception e) {
                Log.e(TAG, "parse pdu err", e);
            }
        }
        if (smsTimestamp == 0) smsTimestamp = System.currentTimeMillis();
        String text = body.toString().trim();
        if (text.isEmpty()) {
            Log.d(TAG, "empty body");
            return;
        }

        // 修复 BUG:处理 +86 国际号码格式
        if (address.startsWith("+86")) {
            address = address.substring(3);
        } else if (address.startsWith("86") && address.length() > 11) {
            address = address.substring(2);
        }

        Log.d(TAG, "📨 收到短信 [" + address + "]: " + text.substring(0, Math.min(80, text.length())));

        // 过滤非支付短信
        if (!isPaymentRelated(address, text)) {
            Log.d(TAG, "非支付短信,跳过");
            return;
        }

        // v2.2.8:银行短信永远开启(不再受 ListenSettings 控制)
        // 之前是 v2.2.4 加的监听源开关,但用户手滑关掉后不知道,自动记账就废了
        // 现在银行短信是"默认 + 永远"开启,只在 APP 内的监听源设置里控制支付 APP 通知

        // 解析
        PaymentParser.Result parsed = PaymentParser.parse(text, "sms");
        if (!parsed.success) {
            Log.d(TAG, "❌ 解析失败,跳过");
            return;
        }
        Log.d(TAG, "✓ 解析成功: " + parsed.amount + "元 " + parsed.type + " " + parsed.merchant);

        // 写入 moneybook.db
        try {
            MoneyDbHelper db = new MoneyDbHelper(context);
            SQLiteDatabase sqlDb = db.getWritableDatabase();
            boolean ok = db.saveTransaction(sqlDb, parsed, "sms", text, smsTimestamp);
            if (ok) {
                Log.d(TAG, "✓✓ 已记账: " + parsed.amount + "元");
            } else {
                Log.d(TAG, "⚠️ 重复,跳过");
            }
        } catch (Throwable e) {
            Log.e(TAG, "saveToDb failed: " + e.getMessage(), e);
        }

        // 推 WebView(如果在)
        try {
            Bridge bridge = MoneyBookPlugin.getSharedBridge();
            if (bridge != null && bridge.getWebView() != null) {
                JSONObject info = new JSONObject();
                info.put("title", "银行短信");
                info.put("content", text);
                info.put("text", text);
                info.put("packageName", "sms");
                info.put("pkg", "sms");
                info.put("postedAt", smsTimestamp);
                info.put("raw", text);
                if (parsed.success) {
                    info.put("preAmount", parsed.amount);
                    info.put("preType", parsed.type);
                    info.put("preMerchant", parsed.merchant);
                    info.put("preSource", parsed.source);
                }
                final String json = info.toString();
                bridge.getWebView().post(() -> {
                    try { bridge.triggerJSEvent("paymentNotification", "window", json); }
                    catch (Exception e) { Log.e(TAG, "triggerJSEvent failed", e); }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "push webview failed", e);
        }
    }

    private boolean isPaymentRelated(String address, String body) {
        // v2.2.52 强化: 发件人必须是银行白名单 OR 内容必须强匹配
        // 第 1 步: 严格黑名单 — 电话费/广告/验证码直接拒收
        for (String bad : SMS_BLACKLIST) {
            if (body.contains(bad)) {
                Log.d(TAG, "黑名单词 [" + bad + "] 命中,拒收: " + body.substring(0, Math.min(30, body.length())));
                return false;
            }
        }
        // 第 2 步: 发件人是银行白名单 → 信任
        if (address != null) {
            for (String prefix : BANK_SENDER_KEYWORDS) {
                if (address.contains(prefix)) {
                    return true;
                }
            }
        }
        // 第 3 步: 发件人非银行,要求内容强匹配 (有"尾号"+数字+银行名)
        boolean hasBankKeyword = body.contains("尾号") || body.contains("卡号") ||
                                 body.contains("工商银行") || body.contains("建设银行") ||
                                 body.contains("中国银行") || body.contains("农业银行") ||
                                 body.contains("交通银行") || body.contains("招商银行") ||
                                 body.contains("邮储") || body.contains("中信银行") ||
                                 body.contains("浦发") || body.contains("民生银行") ||
                                 body.contains("兴业银行") || body.contains("光大银行") ||
                                 body.contains("华夏银行") || body.contains("广发银行") ||
                                 body.contains("平安银行") || body.contains("北京银行") ||
                                 body.contains("上海银行") || body.contains("南京银行") ||
                                 body.contains("宁波银行") || body.contains("江苏银行") ||
                                 body.contains("杭州银行");
        boolean hasAmount = body.contains("元") || body.contains("¥") || body.contains("￥");
        if (hasBankKeyword && hasAmount) {
            return true;
        }
        // 第 4 步: 否则非支付短信
        return false;
    }
}
