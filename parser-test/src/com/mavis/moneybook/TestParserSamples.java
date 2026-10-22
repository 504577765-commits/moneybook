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
        // ===== v3.0.9: 支付宝支付信息允许没有商户名 =====
        { "花呗支付-带商户", PKG_ALIPAY, "花呗支付成功 12.00元 商户:瑞幸咖啡", "true", "expense" },
        { "支付宝支付消息-带商户", PKG_ALIPAY, "支付消息 你在瑞幸咖啡有一笔12.00元的支出", "true", "expense" },
        { "使用花呗消费-无商户", PKG_ALIPAY, "你使用花呗消费25.50元", "true", "expense" },
        { "支付宝98元-红包角标干扰", PKG_ALIPAY, "交易提醒 你有一笔98.00元的支出，点击领取2元流量红包。", "true", "expense" },
        { "支付宝碰友节营销", PKG_ALIPAY, "碰友节给商家送福利 领取碰一下后，首笔碰收款奖2元，先去0元领取物料>>", "false", "-" },

        // ===== 误导 1: 广电扣费(预告/自动续费,未发生扣款) → 不应记账 =====
        { "广电扣费-预告(待扣)", PKG_SMS, "【广电网络】您的电视套餐将于3月1日自动续费扣款29元,如需退订请回复短信", "false", "-" },
        { "广电扣费-余额不足提醒(未扣)", PKG_SMS, "【广电网络】您账户余额不足,本月费用29元未能扣款,请及时缴费", "false", "-" },

        // ===== 误导 1.1: 运营商月度话费账单(仅账单结算通知) → 不应记账 =====
        { "广电话费月度账单", PKG_SMS, "【话费账单】尊敬的192****1470客户,您2026年09月共消费39.00元,实际应付39.00元。主要消费项目包括:-套餐及固定费39.00元。查询费用详情,请登录中国广电支付宝小程序/微信小程序/app或拨打全国客服热线10099进行查询。【中国广电】", "false", "-" },

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
        { "广电扣费-已成功扣款", PKG_SMS, "【广电网络】您本月有线电视费已成功扣款35.00元", "true", "expense" },

        // ===== v3.0.4: 真实手机数据中的非支付信息 → 不应记账 =====
        // v3.0.9: 支付宝支付信息常常没有商户名,金额+支出方向明确即可记账
        { "支付宝交易提醒-无商户", PKG_ALIPAY, "交易提醒 你有一笔16.30元的支出，点击领取2个支付宝积分。", "true", "expense" },
        { "支付宝花呗提醒-无商户", PKG_ALIPAY, "交易提醒 你有一笔5.00元的支出，点击领取6个支付宝积分。使用花呗支付，请及时还款。", "true", "expense" },
        { "微信零钱提现-非消费", PKG_WECHAT, "微信支付 [4条]微信支付: 零钱提现发起", "false", "-" },
        { "花呗账单扣款失败", PKG_SMS, "10685511620054000141 【花呗】09月账单扣款失败，151.30元未还，请登录“支付宝-花呗”核对并还款，已还忽略", "false", "-" },
        { "中国广电余额提醒", PKG_SMS, "短信 已隐藏敏感通知内容 中国广电: 【余额提醒】尊敬的客户，您好！截至2026年9月10日4时4分，您的余额已不足15元，为保证您的正常使用，建议您及时充值。更多咨询和办理请拨打10099。【中国广电】", "false", "-" },
        { "美团退款仅受理", PKG_SMS, "【美团】您的「热卖」2双任洗运动鞋/球鞋/板鞋等上门取送包邮(订单号… 33元退款已受理，将原路返还至您的付款账户，请注意查收", "false", "-" },
        { "美团月付开通通知", PKG_SMS, "【美团月付】您已成功开通，使用额度消费先记账。更多信息请点击 mt.cn/HhJGveya 或美团APP搜“月付”，拒收请回复R", "false", "-" },
        { "深度求索余额预警", PKG_SMS, "【深度求索】您好，您的开放平台账户余额已低于预警阈值，当前余额 ¥ 0.72，为保证正常使用，请及时关注。", "false", "-" },
        { "淘宝闪购红包营销", PKG_SMS, "【淘宝闪购】您在渝是乎酸菜鱼(北苑上品店)的订单超时送达，2元慢必赔红包请查收→https://to.ele.me/9OhYzt74_Y", "false", "-" },

        // ===== v3.0.4: 退款到账必须记为收入(括号里带"消费"也不能判成支出) =====
        { "退款到账-括号含消费", PKG_SMS, "短信 已隐藏敏感通知内容 工商银行: 尾号5749卡9月21日18:07收入(退款支付宝-支付宝-消费)22.09元，余额533.52元。【工商银行】", "true", "income" },
        { "退款到账-美团", PKG_SMS, "工商银行: 尾号5749卡8月24日09:42收入(退款财付通-航空总医院)493.34元，余额1,783.65元。【工商银行】", "true", "income" }
    };

    // v3.0.4: 商户名归一化断言(原值 → 期望值)
    private static final String[][] MERCHANT_CASES = {
        { "支付宝-瑞幸咖啡（中国）有限公司", "瑞幸咖啡" },
        { "财付通-瑞幸咖啡", "瑞幸咖啡" },
        { "财付通-西岚兰州牛肉面", "西岚兰州牛肉面" },
        { "缴费财付通-航空总医院", "航空总医院" },
        { "支付宝-上海拉扎斯信息科技有限公司", "饿了么" },
        { "美团支付-厦门三快在线科技有限公司", "美团" },
        { "退款财付通-拾陆两鲜切牛肉自助火锅（新…)", "拾陆两鲜切牛肉自助火锅" },
        { "退款财付通-南焰铁木美式烟熏烤肉自助（…)", "南焰铁木美式烟熏烤肉自助" },
        { "张春光支付宝转账支付宝", "张春光" },
        { "微信零钱提现财付通", "微信零钱提现" },
        { "还款支付宝-还款", "信用卡还款" },
        { "支付宝-好想来零食乐园", "好想来零食乐园" },
        // v3.0.4 历史脏数据: 旧版本把金额/余额/银行名整段写进商户
        { "航空总医院493.34元，余额1,783.65元。工商银行 工商银行", "航空总医院" },
        { "退款支付宝-支付宝-消费29.79元，余额198.22元。工商银行 工商银行", "退款" },
        { "美团App厦门三快在线科技有限98元，余额1,251.48元。工商银行", "美团" },
        // 纯商户名无法判断退款语义,迁移时会结合 raw_text 补成"退款"
        { "财付通91.80元，余额1,493.78元。工商银行 工商银行", "" }
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
        int mp = 0, mf = 0;
        for (String[] c : MERCHANT_CASES) {
            String got = PaymentParser.normalizeMerchant(c[0]);
            boolean ok = c[1].equals(got);
            if (ok) mp++; else mf++;
            System.out.printf("商户归一化 %-30s → %-20s %s%n", c[0], got, ok ? "✅" : "❌ 期望 " + c[1]);
        }
        System.out.println("------------------------------------------------------------------------------------------------------");
        System.out.println("结果: " + (pass + mp) + "/" + (CASES.length + MERCHANT_CASES.length)
                + " 通过, " + (fail + mf) + " 失败");
        if (fail + mf > 0) System.exit(1);
    }

    private static String truncate(String s, int n) {
        if (s == null) return "-";
        return s.length() <= n ? s : s.substring(0, n);
    }
}