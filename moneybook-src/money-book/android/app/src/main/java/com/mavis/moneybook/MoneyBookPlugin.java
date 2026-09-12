package com.mavis.moneybook;

import com.getcapacitor.Bridge;

/**
 * 桥接器:让 NotificationListenerService / SmsReceiver
 * 能够拿到 Capacitor 的 Bridge,向 WebView 推事件
 */
public class MoneyBookPlugin {
    private static Bridge sharedBridge;

    public static synchronized void setSharedBridge(Bridge b) {
        sharedBridge = b;
    }

    public static Bridge getSharedBridge() {
        return sharedBridge;
    }
}
