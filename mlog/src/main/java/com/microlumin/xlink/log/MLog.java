package com.microlumin.xlink.log;

import android.util.Log;
import java.lang.reflect.Method;

/**
 * 封装 Android 日志输出，支持通过 adb 属性动态控制日志级别。
 * 默认显示 Info, Warning, Error 级别的日志。
 * adb shell setprop debug.mlog.level [V/D/I/W/E/NONE]
 */
public class MLog {
    public static final int VERBOSE = 2;
    public static final int DEBUG = 3;
    public static final int INFO = 4;
    public static final int WARN = 5;
    public static final int ERROR = 6;
    public static final int NONE = 7;
    private static final String TAG = "MLog";
    private static final String PROPERTY_KEY = "debug.mlog.level";
    private static int sMinLevel = INFO;

    static {
        updateLevelFromProperty();
    }

    private static void updateLevelFromProperty() {
        String levelStr = getSystemProperty(PROPERTY_KEY, "I");
        sMinLevel = parseLevel(levelStr);
    }

    private static int parseLevel(String levelStr) {
        if (levelStr == null) return INFO;
        switch (levelStr.toUpperCase()) {
            case "V": return VERBOSE;
            case "D": return DEBUG;
            case "I": return INFO;
            case "W": return WARN;
            case "E": return ERROR;
            case "NONE": return NONE;
            default: return INFO;
        }
    }

    public static void setLevel(int level) {
        sMinLevel = level;
    }

    public static void v(String tag, String msg) {
        if (isLoggable(tag, VERBOSE)) {
            Log.v(tag, msg);
        }
    }

    public static void d(String tag, String msg) {
        if (isLoggable(tag, DEBUG)) {
            Log.d(tag, msg);
        }
    }

    public static void i(String tag, String msg) {
        if (isLoggable(tag, INFO)) {
            Log.i(tag, msg);
        }
    }

    public static void w(String tag, String msg) {
        if (isLoggable(tag, WARN)) {
            Log.w(tag, msg);
        }
    }

    public static void e(String tag, String msg) {
        if (isLoggable(tag, ERROR)) {
            Log.e(tag, msg);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (isLoggable(tag, ERROR)) {
            Log.e(tag, msg, tr);
        }
    }

    private static boolean isLoggable(String tag, int level) {
        return level >= sMinLevel;
    }

    /**
     * 重新从系统属性中加载日志级别
     */
    public static void refreshConfig() {
        updateLevelFromProperty();
    }

    private static String getSystemProperty(String key, String defaultValue) {
        try {
            Class<?> systemProperties = Class.forName("android.os.SystemProperties");
            Method getMethod = systemProperties.getMethod("get", String.class, String.class);
            return (String) getMethod.invoke(null, key, defaultValue);
        } catch (Exception e) {
            return defaultValue;
        }
    }
}