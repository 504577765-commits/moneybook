package com.mavis.moneybook;

import android.util.Log;
import org.json.JSONObject;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 支付通知解析器 - Java 版
 * 与前端 src/parser/index.js 保持一致
 */
public class PaymentParser {
    private static final String TAG = "PaymentParser";

    public static class Result {
        public String type;       // "expense" / "income"
        public double amount;
        public String merchant;
        public String source;     // "alipay" / "wechat" / "bank" / "unionpay" / "other"
        public boolean isTransfer;
        public String raw;
        public boolean success;

        public JSONObject toJSON() {
            try {
                JSONObject o = new JSONObject();
                o.put("type", type);
                o.put("amount", amount);
                o.put("merchant", merchant);
                o.put("source", source);
                o.put("isTransfer", isTransfer);
                o.put("raw", raw);
                o.put("success", success);
                return o;
            } catch (Exception e) { return null; }
        }
    }

    // ==================== 规则 ====================
    // 顺序很重要:income 在 expense 之前,避免"向你转账"被误判为支出

    private static final Object[][] RULES = {
        // 支付宝 - 转账收入
        { "alipay", "income", "com.eg.android.AlipayGphone", new String[] {
            "(.+?)\\s*向你转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "收到\\s*(.+?)\\s*的转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "收款\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)\\s*来自\\s*(.+?)(?:[:：\\s]|$)",
            "支付宝.*?收款.*?[¥￥]\\s*([\\d,]+\\.?\\d*).*?来自\\s*(.+?)(?:[:：]|\\s|$)",
            "收到.*?转账\\s*[¥￥]\\s*([\\d,]+\\.?\\d*)",
            // v2.2.82: 红包到账是收入,不是支出("支付宝"前缀会被"支付"误抓为支出)
            "红包到账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "红包收入\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "收到\\s*红包\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)"
        }},
        // 微信 - 转账/红包收入
        { "wechat", "income", "com.tencent.mm", new String[] {
            "(.+?)\\s*通过微信向你转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "(.+?)\\s*向你转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "收到\\s*(.+?)\\s*的转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "收到\\s*(.+?)\\s*的微信红包\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "收到\\s*微信红包\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "微信红包\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "收到.*?转账\\s*[¥￥]\\s*([\\d,]+\\.?\\d*)",
            "红包到账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "红包收入\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)"
        }},
        // 银行短信 - 收入
        { "bank", "income", null, new String[] {
            "尾号(\\d{4}).*?收入\\s*([\\d,]+\\.?\\d*)\\s*元",
            "尾号(\\d{4}).*?转入\\s*([\\d,]+\\.?\\d*)\\s*元",
            "尾号(\\d{4}).*?汇款入账\\s*([\\d,]+\\.?\\d*)\\s*元"
        }},
        // 支付宝 - 转账支出
        { "alipay", "expense", "com.eg.android.AlipayGphone", new String[] {
            "你已成功向\\s*(.+?)\\s*转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "你已向\\s*(.+?)\\s*转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "向\\s*(.+?)\\s*转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "转账给\\s*(.+?)\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "成功给\\s*(.+?)\\s*转账\\s*([\\d,]+\\.?\\d*)\\s*元",
            "支付宝.*?向\\s*(.+?)\\s*转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)"
        }},
        // 支付宝 - 消费
        { "alipay", "expense", "com.eg.android.AlipayGphone", new String[] {
            "支付宝.*付款.*?[¥￥]\\s*([\\d,]+\\.?\\d*).*?给\\s*(.+?)(?:[:：]|\\s|$)",
            "付款给\\s*(.+?)\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "向\\s*(.+?)\\s*付款.*?[¥￥]\\s*([\\d,]+\\.?\\d*)",
            "你使用支付宝向\\s*(.+?)\\s*支付.*?[¥￥]\\s*([\\d,]+\\.?\\d*)",
            "成功向\\s*(.+?)\\s*付款.*?([\\d,]+\\.?\\d*)\\s*元",
            "订单金额.*?[¥￥]\\s*([\\d,]+\\.?\\d*).*?商户.*?(.+?)(?:\\s|$)",
            // v3.0.6: 花呗支付(信用支付)也记账 — 必须有商户名
            "使用花呗.*?(?:消费|支付|付款|支出)[^\\d]{0,10}[¥￥]?\\s*([\\d,]+\\.?\\d*)\\s*元.*?(?:商户|收款方|付款给)[:：\\s]*([^,，。\\s]+)",
            "花呗.*?(?:消费|支付|付款|支出)[^\\d]{0,10}[¥￥]?\\s*([\\d,]+\\.?\\d*)\\s*元.*?(?:商户|收款方|付款给)[:：\\s]*([^,，。\\s]+)",
            // v3.0.9: 花呗交易提醒也可能没有商户名
            "花呗.*?(?:消费|支付|付款|支出)[^\\d]{0,10}[¥￥]?\\s*([\\d,]+\\.?\\d*)\\s*元",
            // v3.0.9: 支付宝交易提醒常常没有商户名,只要金额和支出方向明确就记账
            "你在\\s*(.{1,20}?)\\s*有(?:一笔)?\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)\\s*元的?\\s*(?:支出|消费|付款|支付|扣款)",
            "你有一笔\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)\\s*元的?\\s*(?:支出|消费|付款|支付|扣款)"
        }},
        // 微信 - 转账支出
        { "wechat", "expense", "com.tencent.mm", new String[] {
            "你已向\\s*(.+?)\\s*转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "向\\s*(.+?)\\s*转账\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)",
            "转账给\\s*(.+?)\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)"
        }},
        // 微信 - 消费
        // v2.2.39: 覆盖更多微信支付通知格式
        // 例1: "微信支付 51.00元 付款给美团"
        // 例2: "微信支付凭证\n美团\n¥51.00"  (tickerText)
        // 例3: "付款给 美团点评 51.00 元"
        // 例4: "向美团支付 51.00元"
        // 例5: "你已成功付款 51.00元(给XXX)"
        { "wechat", "expense", "com.tencent.mm", new String[] {
            // 微信支付凭证格式 (商家在前面,金额在后面)
            "微信支付凭证\\s*\\n?\\s*(.+?)\\s*[¥￥]\\s*([\\d,]+\\.?\\d*)",
            "微信支付凭证\\s*(.+?)\\s*[¥￥]\\s*([\\d,]+\\.?\\d*)\\s*元",
            // 微信支付 付款给 (商家 + 金额)
            "微信支付.*?付款.*?给\\s*(.+?)\\s*[¥￥]\\s*([\\d,]+\\.?\\d*)",
            "微信支付.*?付款.*?[¥￥]\\s*([\\d,]+\\.?\\d*).*?给\\s*(.+?)(?:\\s|$)",
            // 商户消费
            "微信支付.*?商户消费.*?[¥￥]\\s*([\\d,]+\\.?\\d*).*?商户.*?(.+?)(?:\\s|$)",
            "微信支付.*?消费.*?商户\\s*(.+?)\\s*[¥￥]\\s*([\\d,]+\\.?\\d*)",
            "商户消费\\s*([\\d,]+\\.?\\d*).*?商户\\s*(.+?)(?:\\s|$)",
            // 付款给 + 金额
            "付款给\\s*(.+?)\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)\\s*元",
            "向\\s*(.+?)\\s*付款.*?[¥￥]\\s*([\\d,]+\\.?\\d*)",
            "你已成功付款\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*).*?给\\s*(.+?)(?:\\s|$)",
            "你已成功付款\\s*[¥￥]?\\s*([\\d,]+\\.?\\d*)\\s*元",
            "已付款\\s*([\\d,]+\\.?\\d*).*?给\\s*(.+?)(?:\\s|$)",
            // 微信支付 + 任意(没商家也能抓金额)
            "微信支付.*?[¥￥]\\s*([\\d,]+\\.?\\d*)"
        }},
        // 银行短信 - 消费 (兼容工行/建行/招行/中行/交行 等主流格式)
        // v2.2.24 关键修复: 用 (\\[(.+?)\\]) 抓方括号里的商户信息
        //   例: "尾号5749卡...支出[充值支付宝-张正一]¥0.01元"
        //   group 1: 5749 (尾号)
        //   group 2: 充值支付宝-张正一 (商户信息)
        //   group 3: 0.01 (消费金额)
        { "bank", "expense", null, new String[] {
            // v2.2.36 关键修复: 3 group 抓商户内容!
            //   group 1: 尾号
            //   group 2: [] 或 () 里的商户内容
            //   group 3: 消费金额
            // 例: "尾号5749...支出(消费美团支付-美团骑行)0.99元"
            //   group 1: 5749
            //   group 2: 消费美团支付-美团骑行
            //   group 3: 0.99
            // v2.2.51 修复: 金额支持整数(18元)和小数(0.99元)
            // 工商银行: 方括号格式 (支付宝/微信 通知)
            "尾号(\\d{4}).*?支出\\[([^\\]]+)\\][^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            "尾号(\\d{4}).*?消费\\[([^\\]]+)\\][^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            // 工商银行: 中文圆括号格式 (美团/滴滴 等直接消费)
            "尾号(\\d{4}).*?支出\\(([^\\)]+)\\)[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            "尾号(\\d{4}).*?消费\\(([^\\)]+)\\)[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            // 通用 - 支出/消费 + 数字 + 元
            "尾号(\\d{4}).*?支出[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            "尾号(\\d{4}).*?消费[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            "尾号(\\d{4}).*?POS[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            // 您尾号格式
            "您尾号(\\d{4}).*?消费[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            "您尾号(\\d{4}).*?支出[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            "您尾号(\\d{4}).*?于.*?消费[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            // 建设/招商/中国银行
            "尾号(\\d{4})于\\d.*?消费[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            "尾号(\\d{4})卡\\d.*?消费[^余]*?([\\d,]+(?:\\.\\d+)?).*?元",
            "尾号(\\d{4}).*?信用卡.*?消费[^余]*?([\\d,]+(?:\\.\\d+)?).*?元"
        }},
        // 银行短信 - 收入
        { "bank", "income", null, new String[] {
            "尾号(\\d{4})卡.*?收入\\s*([\\d,]+\\.?\\d*)\\s*元",
            "尾号(\\d{4}).*?收入\\s*([\\d,]+\\.?\\d*)\\s*元",
            "尾号(\\d{4}).*?转入\\s*([\\d,]+\\.?\\d*)\\s*元",
            "尾号(\\d{4})卡.*?入账\\s*([\\d,]+\\.?\\d*)\\s*元"
        }},
        // v2.2.41: 银行短信 - 退款(单独走 income,后续 matchCategory 归 refund)
        { "bank", "income", null, new String[] {
            // 例1: "尾号5749卡8月10日14:00退款51.00元"
            "尾号(\\d{4})卡.*?退款\\s*([\\d,]+\\.?\\d*)\\s*元",
            "尾号(\\d{4}).*?退款\\s*([\\d,]+\\.?\\d*)\\s*元",
            "尾号(\\d{4})卡.*?退费\\s*([\\d,]+\\.?\\d*)\\s*元",
            // 例2: "尾号5749卡8月11日07:14收入(退款美团支付-美团App汉海信息技术（上海）)45.90元"
            //   group 1: 5749 (尾号)
            //   group 2: 退款美团支付-美团App汉海信息技术（上海）  (商户)
            //   group 3: 45.90 (金额)
            // v3.0.4: 捕获商户组,避免商户丢失后兜底抓到整段"退款xxx50元,余额…"
            "尾号(\\d{4}).*?收入\\(([^)]*?退款[^)]*?)\\)\\s*([\\d,]+\\.?\\d*)\\s*元",
            "尾号(\\d{4}).*?收入\\(([^)]*?)\\)\\s*([\\d,]+\\.?\\d*)\\s*元",
            "尾号(\\d{4}).*?收入\\(([^)]*?[（][^)]*?[）][^)]*?)\\)\\s*([\\d,]+\\.?\\d*)\\s*元",  // 支持全角
            "尾号(\\d{4}).*?入账\\(([^)]+)\\)\\s*([\\d,]+\\.?\\d*)\\s*元",
            // 退到卡里
            "退款.*?尾号(\\d{4})\\s*([\\d,]+\\.?\\d*)\\s*元"
        }},
        // v2.2.40: 支付宝 - 退款(收入)
        { "alipay", "income", "com.eg.android.AlipayGphone", new String[] {
            "支付宝.*?退款\\s*([\\d,]+\\.?\\d*)\\s*元",
            "收到.*?退款\\s*([\\d,]+\\.?\\d*)\\s*元",
            "退款成功.*?([\\d,]+\\.?\\d*)\\s*元",
            "支付宝.*?已退款.*?([\\d,]+\\.?\\d*)\\s*元",
            "原路退回.*?([\\d,]+\\.?\\d*)\\s*元"
        }},
        // v2.2.40: 微信 - 退款(收入)
        { "wechat", "income", "com.tencent.mm", new String[] {
            "微信支付.*?退款\\s*([\\d,]+\\.?\\d*)\\s*元",
            "收到.*?微信退款\\s*([\\d,]+\\.?\\d*)\\s*元",
            "微信退款.*?([\\d,]+\\.?\\d*)\\s*元",
            "退款到账\\s*([\\d,]+\\.?\\d*)\\s*元"
        }},
        // 云闪付
        { "unionpay", "expense", "com.unionpay", new String[] {
            "云闪付.*?支付.*?[¥￥]\\s*([\\d,]+\\.?\\d*).*?商户.*?(.+?)(?:\\s|$)",
            "银联.*?消费.*?[¥￥]\\s*([\\d,]+\\.?\\d*)"
        }}
    };

    private static final String[][] FALLBACK_RULES = {
        { "expense", "(?:付款|支付|消费|支出|扣款|转账给)[\\s\\S]{0,40}?([\\d,]+\\.?\\d*)\\s*元" },
        { "expense", "(?:付款|支付|消费|支出|扣款|转账给)[\\s\\S]{0,30}?[¥￥]\\s*([\\d,]+\\.?\\d*)" },
        { "income",  "(?:收到|收款|入账|转入|退款|红包|向你转账)[\\s\\S]{0,30}?([\\d,]+\\.?\\d*)\\s*元" },
        { "income",  "(?:收到|收款|入账|转入|退款|红包)[\\s\\S]{0,30}?[¥￥]\\s*([\\d,]+\\.?\\d*)" }
    };

    public static Result parse(String text, String pkg) {
        Result r = new Result();
        r.raw = text;
        if (text == null || text.isEmpty()) return r;
        String t = text.replaceAll("\\s+", " ").trim();
        // v3.0.4: 话费/宽带等月度账单不是交易,直接忽略(所有入口统一拦截)
        if (isBillStatement(t)) {
            Log.d(TAG, "BILL 忽略: " + t.substring(0, Math.min(40, t.length())));
            return r;
        }
        // v3.0.4: 广告/账单/余额/验证码等非支付信息,直接忽略
        if (isNonPaymentInfo(t)) {
            Log.d(TAG, "NON-PAY 忽略: " + t.substring(0, Math.min(40, t.length())));
            return r;
        }
        boolean isTransfer = t.contains("转账");
        // v3.0.4: "收入(...退款...)" 是退款到账,必须按收入处理,不能被括号里的"消费"误导成支出
        boolean refundIncome = (t.contains("收入(") || t.contains("收入（"))
                && (t.contains("退款") || t.contains("退费"));

        // 1) 规则匹配
        for (Object[] rule : RULES) {
            String source = (String) rule[0];
            String type = (String) rule[1];
            String rulePkg = (String) rule[2];
            String[] patterns = (String[]) rule[3];

            // 退款到账: 跳过所有支出规则,避免 "收入(退款支付宝-支付宝-消费)" 被判成支出
            if (refundIncome && "expense".equals(type)) continue;

            // 如果规则指定了 pkg,只在该 pkg 下匹配
            if (rulePkg != null && pkg != null && !rulePkg.equals(pkg)) continue;

            for (String patStr : patterns) {
                Pattern p = Pattern.compile(patStr);
                Matcher m = p.matcher(t);
                if (m.find()) {
                    Pair am = extractAmountAndMerchant(m, t);
                    if (am.amount > 0) {
                        r.type = type;
                        r.amount = am.amount;
                        r.merchant = am.merchant;
                        r.source = source;
                        r.isTransfer = isTransfer;
                        r.success = true;
                        // 红包特殊处理
                        if (t.contains("微信红包") || t.contains("红包")) {
                            if (am.merchant == null || am.merchant.isEmpty() || "未知商户".equals(am.merchant)) {
                                r.merchant = "微信红包";
                            }
                        }
                        // v2.2.25 兜底:如果 merchant 还是 "未知商户",从原文 [] 抓
                        if (r.merchant == null || r.merchant.isEmpty() || "未知商户".equals(r.merchant)) {
                            String bracket = extractFirstBracketContent(t);
                            if (!bracket.isEmpty()) {
                                r.merchant = cleanMerchant(bracket);
                                Log.d(TAG, "兜底[1]: 从 [] 提取 merchant = " + r.merchant);
                            }
                        }
                        Log.d(TAG, "✓ parsed: " + type + " ¥" + am.amount + " " + r.merchant + " (" + source + ")");
                        sanitizeMerchant(r, t);
                        return r;
                    }
                }
            }
        }

        // 2) 兜底
        // v2.2.59: 兜底规则前先做通知文案过滤 — 红包推送/优惠券等不应当交易!
        if (looksLikeNotification(t)) {
            Log.d(TAG, "FALLBACK 跳过: 文本看起来像通知/推送,不是交易 — " + t.substring(0, Math.min(40, t.length())));
            return r;
        }
        for (String[] fr : FALLBACK_RULES) {
            String type = fr[0];
            String patStr = fr[1];
            Pattern p = Pattern.compile(patStr);
            Matcher m = p.matcher(t);
            if (m.find()) {
                String s = m.group(1);
                s = s.replaceAll("[¥￥,，]", "").replaceAll("[^\\d.]", "");
                try {
                    double v = Double.parseDouble(s);
                    if (v > 0) {
                        r.type = type;
                        r.amount = v;
                        r.source = "other";
                        r.isTransfer = isTransfer;
                        r.success = true;
                        // v2.2.25: FALLBACK 也尝试提取 [] 商户
                        r.merchant = "未知商户";
                        String bracket = extractFirstBracketContent(t);
                        if (!bracket.isEmpty()) {
                            r.merchant = cleanMerchant(bracket);
                            Log.d(TAG, "兜底[2]: fallback 从 [] 提取 merchant = " + r.merchant);
                        }
                        Log.d(TAG, "✓ fallback: " + type + " ¥" + v + " merchant=" + r.merchant);
                        sanitizeMerchant(r, t);
                        return r;
                    }
                } catch (Exception ignore) {}
            }
        }

        Log.d(TAG, "✗ parse failed for: " + (t.length() > 60 ? t.substring(0, 60) + "..." : t));
        return r;
    }

    /**
     * 避免营销文案里的"领取N元红包"被误当成商户名,
     * 否则会绕过"同额跨渠道去重"导致重复记账。
     */
    private static void sanitizeMerchant(Result r, String text) {
        if ("expense".equals(r.type) && r.merchant != null && r.merchant.contains("红包")
                && !text.contains("商户") && !text.contains("收款方") && !text.contains("付款给")) {
            r.merchant = "未知商户";
        }
    }

    private static class Pair {
        double amount;
        String merchant;
        Pair(double a, String m) { amount = a; merchant = m; }
    }

    private static Pair extractAmountAndMerchant(Matcher m, String fullText) {
        // v2.2.21 关键修复: 不再选"最大"金额 — 余额比消费金额大,会被误抓!
        // 改为: 优先选"前后有 ¥/元"的 group,这些才是真正的"金额"
        // 如果都不含货币符号,fallback 到"第一个"非卡号的数字
        int bestIdx = -1;
        double bestAmount = 0;
        int firstNumericIdx = -1;
        double firstNumericValue = 0;

        for (int i = 1; i <= m.groupCount(); i++) {
            String g = m.group(i);
            if (g == null) continue;
            String t = g.trim();
            // 跳过银行卡尾号(4 位纯数字)
            if (t.matches("\\d{4}")) continue;
            // 跳过纯数字"年/月/日" 等无关数字
            String cleaned = t.replaceAll("[¥￥,，]", "").replaceAll("[^\\d.]", "");
            if (cleaned.isEmpty()) continue;
            try {
                double v = Double.parseDouble(cleaned);
                if (v <= 0 || v > 999999999L) continue;
                // 记录第一个数字(兜底用)
                if (firstNumericIdx == -1) {
                    firstNumericIdx = i;
                    firstNumericValue = v;
                }
                // 检查 group 在**原文**里前后是否紧邻 ¥/元
                int start = m.start(i);
                int end = m.end(i);
                String before = start > 0 ? String.valueOf(fullText.charAt(start - 1)) : "";
                String after = end < fullText.length() ? String.valueOf(fullText.charAt(end)) : "";
                boolean hasCurrency = before.matches("[¥￥]") || after.matches("[¥￥元]");
                if (hasCurrency && bestIdx == -1) {
                    bestIdx = i;
                    bestAmount = v;
                    // 不 break — 继续找可能更优的(多个含货币符号的 group,选第一个)
                }
            } catch (Exception ignore) {}
        }

        // 兜底: 如果没找到含货币符号的,选第一个数字(不再选最大)
        if (bestIdx == -1 && firstNumericIdx != -1) {
            bestIdx = firstNumericIdx;
            bestAmount = firstNumericValue;
        }
        if (bestAmount == 0) return new Pair(0, "");

        // 2) 商户:剩下的组里最长的字符串
        String merchant = "";
        for (int i = 1; i <= m.groupCount(); i++) {
            if (i == bestIdx) continue;
            String g = m.group(i);
            if (g == null) continue;
            String t = g.trim();
            if (t.matches("\\d{4}")) continue;
            if (t.matches("[¥￥]?\\d.*") || t.matches("\\d+\\.?\\d*")) continue;
            if (t.length() > merchant.length()) merchant = t;
        }
        merchant = cleanMerchant(merchant);
        return new Pair(bestAmount, merchant.isEmpty() ? "未知商户" : merchant);
    }

    /**
     * v2.2.31: 从原文里扫描所有 [...] 和 (...) 内容,选最长的作为 merchant 候选
     * 用于"规则没抓 [] 或 () 内容"或"FALLBACK"的兜底
     *
     * v2.2.31 关键: 工行短信用中文圆括号 (),不是 []
     */
    private static String extractFirstBracketContent(String text) {
        if (text == null) return "";
        // v3.0.4: 括号内容不能跨越配对的右括号,否则 "收入(退款…)" 会把 "50元,余额…" 一起抓进来
        Pattern p = Pattern.compile("[\\[【\\((（]([^\\]\\[)）]+)[\\]】\\)）]");
        Matcher m = p.matcher(text);
        String best = "";
        while (m.find()) {
            String content = m.group(1).trim();
            if (content.matches("[\\d.,\\s]+")) continue;
            if (content.matches("\\d+\\s*[元角分条个次笔单笔天秒分钟小时]\\s*$")) continue;
            if (content.matches("^\\d+.*\\d+$")) continue;
            if (content.length() < 3) continue;
            if (content.length() > best.length()) best = content;
        }
        return best;
    }

    /**
     * v3.0.4: 运营商/宽带话费"月度账单"文本 — 只是账单结算通知,不是实际支付,直接忽略
     * 例: "【话费账单】尊敬的192****1470客户,您2026年09月共消费39.00元,实际应付39.00元。
     *      主要消费项目包括:-套餐及固定费39.00元 ...【中国广电】"
     * 注意区分: "充值话费50元"/"已成功扣款35元" 这类真实交易不受影响
     */
    public static boolean isBillStatement(String text) {
        if (text == null || text.isEmpty()) return false;
        String t = text.replaceAll("\\s+", " ");
        String[] markers = {
            "【中国广电】", "【话费账单】", "话费账单", "话费月账单",
            "主要消费项目", "消费项目包括",
            "套餐及固定费", "套餐及增值费", "套餐及固定费用"
        };
        for (String m : markers) {
            if (t.contains(m)) return true;
        }
        // "共消费X元,实际应付X元" → 账单结算
        if (t.contains("共消费") && t.contains("应付")) return true;
        // 账单查询引导语
        if (t.contains("查询费用详情")) return true;
        return false;
    }

    /**
     * v2.2.59: 检测文本是否像通知/推送,而非真实交易
     * 返回 true → 跳过 fallback 规则,直接返回解析失败(不记账)
     */
    private static boolean looksLikeNotification(String text) {
        if (text == null || text.isEmpty()) return false;
        // 1. 通知动作开头 (点击/立即/快来/限时/赶紧/马上/点此 等)
        String[] notifyVerbs = {"点击", "立即", "快来", "赶紧", "限时", "马上", "赶快", "点此", "点这里", "戳", "抢", "速领"};
        for (String v : notifyVerbs) {
            if (text.contains(v)) return true;
        }
        // 2. 红包推送通知: 含"红包" 但**不**含"已收/收到/已领" 的真实红包收入
        if (text.contains("红包")) {
            boolean isRealIncome = text.contains("已收") || text.contains("收到") ||
                                   text.contains("已领") || text.contains("领取成功") ||
                                   text.contains("红包收入") || text.contains("红包到账");
            if (!isRealIncome) return true;  // "红包推送/红包活动/红包雨"等
        }
        // 3. 优惠券/卡券推送
        if (text.contains("优惠券") || text.contains("卡券") || text.contains("福利")) {
            return true;
        }
        // 4. "你有一笔/你收到/你已成功" 通知开头
        if (text.contains("你有一笔") || text.contains("你收到") || text.contains("你已成功")) {
            return true;
        }
        // 5. 借呗/备用金推送;花呗仅拦截营销/账单/额度,真实花呗支付必须放行
        if (text.contains("借呗") || text.contains("备用金")) {
            return true;
        }
        if (text.contains("花呗")) {
            boolean realPay = text.contains("花呗支付") || text.contains("花呗付款")
                    || text.contains("花呗消费") || text.contains("花呗支出")
                    || text.contains("使用花呗");
            boolean marketing = text.contains("花呗额度") || text.contains("花呗账单")
                    || text.contains("花呗还款") || text.contains("还花呗")
                    || text.contains("开通花呗") || text.contains("花呗免息")
                    || text.contains("花呗提额") || text.contains("花呗分期");
            if (!realPay || marketing) return true;
        }
        // 6. 还款提醒 (不是真实还款交易)
        if (text.contains("还款提醒") || text.contains("账单日") || text.contains("最低还款")) {
            return true;
        }
        // 7. 签到/领积分
        if (text.contains("签到") || text.contains("领积分")) {
            return true;
        }
        // v2.2.82: 8. 扣费/续费预告 — 尚未实际扣款,不能记账
        //   "将于3月1日自动续费扣款29元" / "余额不足,29元未能扣款" 等
        //   注意与"已成功扣款35元"(真实扣款,应记账)区分 — 真实扣款无以下词
        String[] pendingFees = {"续费", "将于", "即将", "未能扣款", "余额不足", "请保持余额充足", "待扣", "预扣"};
        for (String w : pendingFees) {
            if (text.contains(w)) return true;
        }
        return false;
    }

    private static String cleanMerchant(String s) {
        if (s == null || s.isEmpty()) return "";
        String result = s.replaceAll("[\\[\\]【】()（）]", "")
                .replaceAll("[:：].*$", "")
                .replaceAll("订单号.*$", "")
                .replaceAll("交易号.*$", "")
                .replaceAll("于.*$", "")
                .trim();
        // v2.2.24 去掉常见前缀动词(银行短信 [充值支付宝-张正一] → 支付宝-张正一)
        result = result.replaceAll("^(充值|付款|支付|收款|转账|消费|支出|入账|入帐|提现|体现|转账给|付款给|扫码支付|消费支付|购物|交易|消费)", "");
        // 去掉尾部数字(订单号之类)
        result = result.replaceAll("[-—_]?\\d{4,}$", "");
        // 去掉纯符号
        result = result.replaceAll("^[-—_\\s]+", "").replaceAll("[-—_\\s]+$", "");
        // v2.2.56: 支付宝/微信通知文案检测 — 这些是通知标题/内容,不是商户!
        // 任何包含以下关键词的 merchant 都视为无效,返回空让 fallback 用 "未知商户"
        String[] notifyWords = {
            "你有一笔", "你已成功", "你已向", "你收到", "你收到一笔",
            "新交易", "新到账", "新付款", "新订单", "新消息", "新通知", "新提醒",
            "收款成功", "付款成功", "退款成功", "到账成功", "支付成功", "转账成功",
            "交易成功", "交易完成", "订单完成", "订单已支付", "订单已付款",
            "已到账", "已退款", "已转账", "已收款", "已支付", "已付款",
            "请收款", "请付款", "待收款", "待付款", "待确认", "确认收款", "确认付款",
            "收款通知", "付款通知", "退款通知", "转账通知", "支付通知"
        };
        for (String w : notifyWords) {
            if (result.contains(w)) {
                Log.w("PaymentParser", "cleanMerchant 检测到通知文案 '" + w + "',丢弃 '" + result + "'");
                return "";
            }
        }
        // v3.0.4: 统一归一化商户名(去渠道前缀/别名映射)
        return normalizeMerchant(result);
    }

    /**
     * v3.0.4: 商户名归一化
     * 银行短信里的商户常带支付渠道前缀,如 "支付宝-瑞幸咖啡"、"财付通-西岚兰州牛肉面"、
     * "美团支付-美团骑行"、"缴费财付通-航空总医院"、"退款财付通-xx"。
     * 统一剥离成可聚合、可记忆的商户名;并对常见公司主体做别名映射。
     */
    public static String normalizeMerchant(String s) {
        if (s == null) return "";
        String r = s.trim();
        if (r.isEmpty() || "未知商户".equals(r)) return r;

        // 1) 去掉末尾括号说明(分店名/省略号),但保留括号前的品牌主体
        r = r.replaceAll("[（(][^）)]*[）)]\\s*$", "").trim();

        // 1.5) v3.0.4: 旧版本可能把 "商户51元，余额1,146.59元。工商银行" 整段当商户 → 截断到金额前
        r = r.replaceAll("[\\s,，]*[\\d,，]+(?:\\.\\d+)?\\s*元[，,].*$", "").trim();
        r = r.replaceAll("[\\s,，]*[\\d,，]+(?:\\.\\d+)?\\s*元$", "").trim();

        // 2) 循环剥离前置动词和支付渠道(可能叠加: 缴费 → 财付通-)
        String[] leadVerbs = { "充值", "消费", "缴费", "还款", "退款", "退费", "付款", "支付",
                "收款", "转账", "支出", "收入", "入账", "入帐", "提现", "购物", "交易", "扫码" };
        String[] channels = { "支付宝", "财付通", "微信支付", "微信", "美团支付", "美团App", "美团",
                "抖音支付", "抖音", "网银在线", "拼多多支付", "拼多多", "云闪付", "银联在线", "银联",
                "京东支付", "京东", "快手支付", "快手", "拉卡拉", "易宝支付", "汇付天下" };
        boolean changed = true;
        int guard = 0;
        while (changed && guard++ < 4) {
            changed = false;
            // 先剥渠道前缀;若开头本身就是渠道名(即使后面没分隔符),不能再按动词剥,
            // 否则 "支付宝xxx" 会被 "支付" 切成 "宝xxx"
            boolean startsWithChannel = false;
            for (String ch : channels) {
                if (r.startsWith(ch)) { startsWithChannel = true; break; }
            }
            for (String ch : channels) {
                if (r.startsWith(ch)) {
                    String rest = r.substring(ch.length());
                    if (rest.startsWith("-") || rest.startsWith("－") || rest.startsWith(":") || rest.startsWith("：")) {
                        r = rest.substring(1);
                        changed = true;
                    }
                }
            }
            if (!startsWithChannel) {
                for (String v : leadVerbs) {
                    if (r.startsWith(v)) {
                        r = r.substring(v.length());
                        changed = true;
                        break;
                    }
                }
            }
            String trimmed = r.replaceAll("^[-—_\\s:：]+", "");
            if (!trimmed.equals(r)) {
                r = trimmed;
                changed = true;
            }
        }

        // 3) 去掉尾部渠道说明
        r = r.replaceAll("(支付宝转账支付宝|支付宝转账|微信转账)$", "").trim();
        r = r.replaceAll("财付通$", "").trim();
        r = r.replaceAll("(医保支付|医保)$", "").trim();

        // 4) 常见公司主体 → 用户熟悉的商户名
        String[][] aliases = {
                { "上海拉扎斯信息科技", "饿了么" },
                { "拉扎斯网络科技", "饿了么" },
                { "拉扎斯信息科技", "饿了么" },
                { "拉扎斯", "饿了么" },
                { "厦门三快在线科技", "美团" },
                { "北京三快在线科技", "美团" },
                { "北京三快科技", "美团" },
                { "天津三快信息技术", "美团" },
                { "深圳美团科技", "美团" },
                { "汉海信息技术", "美团" },
                { "北京港佳好邻居连锁便利店", "好邻居便利店" },
                { "瑞幸咖啡（中国）有限公司", "瑞幸咖啡" },
                { "瑞幸咖啡(中国)有限公司", "瑞幸咖啡" },
                { "瑞幸咖啡中国有限公司", "瑞幸咖啡" }
        };
        for (String[] a : aliases) {
            if (r.equals(a[0]) || r.startsWith(a[0])) {
                r = a[1];
                break;
            }
        }
        // v3.0.4: 公司主体常带渠道前缀(如 "美团App厦门三快在线科技有限"),用 contains 兜底
        if (r.contains("三快在线") || r.contains("美团科技") || r.contains("汉海信息技术")) {
            r = "美团";
        } else if (r.contains("拉扎斯")) {
            r = "饿了么";
        } else if (r.contains("瑞幸咖啡")) {
            r = "瑞幸咖啡";
        } else if (r.startsWith("拼多多平台商户")) {
            r = "拼多多";
        }

        // 5) 去尾部残留符号/省略号
        r = r.replaceAll("[…\\.\\s\\-—_]+$", "").trim();
        // 6) 剥空后回退到有意义的通用名称,避免变成"未知商户"
        if (r.isEmpty()) {
            if (s.contains("还款")) return "信用卡还款";
            if (s.contains("退款") || s.contains("退费")) return "退款";
            if (s.contains("微信")) return "微信转账";
            if (s.contains("支付宝")) return "支付宝转账";
            return "";
        }
        if ("还款".equals(r)) return "信用卡还款";
        return r;
    }

    /**
     * v3.0.4: 非支付信息识别 — 账单/欠款提醒/营销广告/余额提醒/服务通知/验证码
     * 这些文本里常带金额,会被 fallback 误记成交易,必须在规则匹配前拦掉。
     */
    public static boolean isNonPaymentInfo(String text) {
        if (text == null || text.isEmpty()) return false;
        String t = text.replaceAll("\\s+", " ");

        // 1) 账单/欠款提醒(不是实际付款) — 优先于交易白名单
        String[] billWords = {
            "账单扣款失败", "本期账单", "账单金额", "最低还款", "应还金额", "应还", "待还", "未还",
            "到期还款", "还款日", "已逾期", "逾期提醒", "账单已出",
            "花呗还款", "还花呗", "自动还款", "花呗账单", "花呗额度", "花呗提额", "开通花呗"
        };
        for (String w : billWords) if (t.contains(w)) return true;

        // 1.5) 真实交易提醒优先:支付宝"你有一笔X元的支出"、微信"已扣费/已支付¥X"
        //      此类文案常附带"点击领取积分/红包"运营引导,不能当营销拦掉(v3.0.6)
        if (isStrongPaymentText(t)) return false;

        // 2) 营销/广告推送(含点击领取/积分类)
        String[] adWords = {
            "点击领取", "点击查看详情", "点击查看", "红包请查收", "慢必赔", "领红包",
            "拒收请回复", "回复R", "回复r", "退订", "已受理",
            "将原路返还", "预计.*到账"
        };
        for (String w : adWords) {
            if (w.contains(".*")) {
                if (Pattern.compile(w).matcher(t).find()) return true;
            } else if (t.contains(w)) return true;
        }

        // 3) 余额/服务提醒
        String[] noticeWords = {
            "余额已不足", "余额已低于", "余额不足", "预警阈值",
            "已成功开通", "服务使用通知", "验证码", "校验码", "动态码", "日报",
            // v3.0.6: 微信内部资金流转/收款请求,不是消费
            "零钱提现", "请收款", "已被接收", "先用后付", "先享后付"
        };
        for (String w : noticeWords) if (t.contains(w)) return true;

        return false;
    }

    /** v3.0.6: 强交易模板 — 明确的一笔支出/扣费,优先于营销词过滤 */
    private static boolean isStrongPaymentText(String t) {
        if (Pattern.compile("你有一笔\\s*[¥￥]?\\s*[\\d,]+(?:\\.\\d+)?\\s*元的?\\s*(?:支出|消费|付款|支付|扣款)").matcher(t).find()) {
            return true;
        }
        if (Pattern.compile("你在.{1,20}有一笔\\s*[¥￥]?\\s*[\\d,]+(?:\\.\\d+)?\\s*元的?\\s*(?:支出|消费|付款|支付|扣款)").matcher(t).find()) {
            return true;
        }
        if (Pattern.compile("(?:已扣费|已支付|付款成功|支付成功)\\s*[¥￥]\\s*[\\d,]+(?:\\.\\d+)?").matcher(t).find()) {
            return true;
        }
        return false;
    }
}
