package com.mavis.moneybook;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * 监听源设置 (v2.2.4)
 *
 * 解决问题:支付宝/微信/银联通知 + 银行短信会重复记账
 *
 * 策略:
 * - 每个来源可以独立开关
 * - 默认:银行短信 ON, 支付宝/微信/银联 OFF(因为银行短信已经记录了同一笔)
 * - 用户可以自由组合
 * - 智能去重:即使两个都开,60秒内同金额同商户只记一次
 */
public class ListenSettings {

    private static final String PREFS = "moneybook_listen";

    // 各种来源
    public static final String SRC_SMS = "sms";                  // 银行短信
    public static final String SRC_ALIPAY = "alipay";            // 支付宝
    public static final String SRC_WECHAT = "wechat";            // 微信
    public static final String SRC_UNIONPAY = "unionpay";        // 银联
    public static final String SRC_BANK_APP = "bank_app";        // 银行 APP(工银/招行等)
    public static final String SRC_OTHER = "other";              // 其他

    /**
     * 读所有设置
     */
    public static java.util.Map<String, Boolean> getAll(Context ctx) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        java.util.Map<String, Boolean> result = new java.util.HashMap<>();
        result.put(SRC_SMS, sp.getBoolean(SRC_SMS, true));                  // 默认开
        result.put(SRC_ALIPAY, sp.getBoolean(SRC_ALIPAY, false));           // 默认关
        result.put(SRC_WECHAT, sp.getBoolean(SRC_WECHAT, false));           // 默认关
        result.put(SRC_UNIONPAY, sp.getBoolean(SRC_UNIONPAY, false));       // 默认关
        result.put(SRC_BANK_APP, sp.getBoolean(SRC_BANK_APP, false));       // 默认关
        result.put(SRC_OTHER, sp.getBoolean(SRC_OTHER, false));             // 默认关
        return result;
    }

    /**
     * 判断某个来源是否启用
     */
    public static boolean isEnabled(Context ctx, String source) {
        if (source == null) return false;
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // 默认值
        boolean def;
        switch (source) {
            case SRC_SMS: def = true; break;
            default: def = false;
        }
        return sp.getBoolean(source, def);
    }

    /**
     * 保存所有设置
     */
    public static void save(Context ctx, java.util.Map<String, Boolean> settings) {
        SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = sp.edit();
        for (java.util.Map.Entry<String, Boolean> e : settings.entrySet()) {
            editor.putBoolean(e.getKey(), e.getValue());
        }
        editor.apply();
    }

    /**
     * 根据包名判断属于哪个来源
     * v2.2.15: 修复 — 系统短信 APP(mms/messaging) 不应该算 bank_app
     * 因为 SRC_BANK_APP 默认是 OFF,系统短信 APP 通知会被拦截,导致收不到银行短信
     * 修复: 系统短信 APP 走 SRC_SMS(永远开),这样默认就能收到
     */
    public static String sourceByPackage(String pkg) {
        if (pkg == null) return SRC_OTHER;
        // v2.2.15: 系统短信 APP 发来的通知(银行短信会通过它显示)→ 走 SMS 通道
        if (pkg.contains("mms") || pkg.contains("messaging") || pkg.contains("telephony")) return SRC_SMS;
        if (pkg.contains("AlipayGphone")) return SRC_ALIPAY;
        if (pkg.contains("tencent.mm")) return SRC_WECHAT;
        if (pkg.contains("unionpay")) return SRC_UNIONPAY;
        // 银行 APP 通知(工银/招行等)
        if (pkg.contains("icbc") || pkg.contains("abchina") || pkg.contains("bankcomm")
            || pkg.contains("cmbchina") || pkg.contains("cib") || pkg.contains("spdb")
            || pkg.contains("cgbchina") || pkg.contains("everbright") || pkg.contains("pingan")
            || pkg.contains("boc") || pkg.contains("ccb") || pkg.contains("mobiebank")
            || pkg.contains("android.bankabc") || pkg.contains("com.bank")
            || pkg.contains("com.hxb")) return SRC_BANK_APP;
        return SRC_OTHER;
    }
}
