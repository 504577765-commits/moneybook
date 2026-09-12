package android.util;

/** 测试用 Log stub — 避免 android.jar 的 Stub! 异常 */
public class Log {
    public static int d(String tag, String msg) { System.out.println("[D] " + msg); return 0; }
    public static int w(String tag, String msg) { System.out.println("[W] " + msg); return 0; }
    public static int e(String tag, String msg, Throwable t) { System.out.println("[E] " + msg); return 0; }
}