package com.mavis.moneybook;

/**
 * 短信自动记账解析测试 — 正常交易 & 误导性短信区分
 * 运行: java -cp out TestParserSamples
 * 依赖: PaymentParser (真实原文件), android.util.Log stub
 */
public class TestParserSamples {

    private static final String PKG_ALIPAY = "com.eg.android.AlipayGphone";
    private static final String PKG_WECHAT = "com.tencent.mm";
    private static final String PKG_SMS = "sms";

    // desc, pkg, text, expectSuccess, expectType
    private static final String[][] CASES = {
        // ===== 正常消费/收入: 应当识别记账 =====
        { "支付宝付款-美团外卖", PKG_ALIPAY, "支付宝付款成功 ￥38.50 给美团外卖", "true", "expense" },
        { "微信支付凭证-美团", PKG_WECHAT, "微信支付凭证\n美团\n¥45.00", "true", "expense" },
        { "银行信用卡消费-星巴克", PKG_SMS, "您的尾号8888信用卡消费 156.80元 商户:星巴克", "true", "expense" },
        { "微信收款-转账收入", PKG_WECHAT, "微信收到转账 ¥200.00 来自小明", "true", "income" },
        { "银行收款-收入", PKG_SMS, "您尾号6666于10月1日收入888.00元", "true", "income" },
        { "支付宝转账给他人", PKG_ALIPAY, "支付宝你已成功向张三转账 ￥50.00", "true", "expense" },
        { "银行退款-收入", PKG_SMS, "尾号5749卡8月10日14:00退款51.00元", "true", "income" },
        { "云闪付消费", PKG_SMS, "云闪付支付 ￥29.90 商户:肯德基", "true", "expense" },
        { "支付宝红包到账-收入", PKG_ALIPAY, "支付宝红包到账 0.66元 来自王五", "true", "income" },

        // ===== 误导 1: 广电扣费(预告/自动续费,未发生扣款) → 不应记账 =====
        { "广电扣费-预告(待扣)", PKG_SMS, "【广电网络】您的电视套餐将于3月1日自动续费扣款29元,如需退订请回复短信", "false", "-" },
        { "广电扣费-余额不足提醒(未扣)", PKG_SMS, "【广电网络】您账户余额不足,本月费用29元未能扣款,请及时缴费", "false", "-" },

        // ===== 误导 2: 支付宝红包/卡券营销 → 不应记账 =====
        { "支付宝红包-待领取营销", PKG_ALIPAY, "【支付宝】您有1个红包待领取,点击查看详情", "false", "-" },
        { "支付宝红包雨活动", PKG_ALIPAY, "【支付宝】红包雨来了!速领大额红包", "false", "-" },
        { "消费立减优惠券", PKG_ALIPAY, "【支付宝】您有一张满20减5优惠券待使用", "false", "-" },

        // ===== 误导 3: 银行/第三方营销提醒 → 不应记账 =====
        { "信用卡还款提醒", PKG_SMS, "【招商银行】您本期信用卡账单已出,最低还款180元,还款日为3月5日", "false", "-" },
        { "花呗营销推送", PKG_ALIPAY, "【花呗】您本月账单余额可分期,享免息", "false", "-" },
        { "积分提醒", PKG_SMS, "【招商银行】您的信用卡积分将于月底过期,请尽快兑换", "false", "-" },
        { "借款额度营销", PKG_SMS, "【XX银行】您可借款额度已提升至50000元,点击查看", "false", "-" },

        // ===== 对比: 广电真实已扣款 → 应当记账(和预告区分开) =====
        { "广电扣费-已成功扣款", PKG_SMS, "【广电网络】您本月有线电视费已成功扣款35.00元", "true", "expense" }
    };

    public static void main(String[] args) {
        int pass = 0, fail = 0;
        System.out.printf("%-28s | %-8s | %-6s | %-7s | %-7s | 判定%n", "场景", "类型", "金额", "商户", "来源");
        System.out.println("------------------------------------------------------------------------------------------------------");
        for (String[] c : CASES) {
            PaymentParser.Result r = PaymentParser.parse(c[2], c[1]);
            boolean expectSuccess = Boolean.parseBoolean(c[3]);
            String expectType = c[4];

            boolean ok = (r.success == expectSuccess);
            if (expectSuccess) ok = ok && expectType.equals(r.type);

            if (ok) pass++; else fail++;
            String verdict = ok ? "✅" : "❌";
            String s = r.success ? "已记账" : "拒绝";
            System.out.printf("%-28s | %-8s | %-6s | %-7s | %-7s | %s %s%n",
                c[0],
                r.success ? r.type : "-",
                r.success ? String.format("%.2f", r.amount) : "-",
                r.success ? truncate(r.merchant, 7) : "-",
                r.source == null ? "-" : r.source,
                verdict, s);
            if (!ok) {
                System.out.println("    ↳ 期望: success=" + expectSuccess + (expectSuccess ? ", type=" + expectType : "") + "  | 实际: success=" + r.success + (r.success ? ", type=" + r.type + ", amount=" + r.amount + ", merchant=" + r.merchant : ""));
            }
        }
        System.out.println("------------------------------------------------------------------------------------------------------");
        System.out.println("结果: " + pass + "/" + CASES.length + " 通过, " + fail + " 失败");
        if (fail > 0) System.exit(1);
    }

    private static String truncate(String s, int n) {
        if (s == null) return "-";
        return s.length() <= n ? s : s.substring(0, n);
    }
}