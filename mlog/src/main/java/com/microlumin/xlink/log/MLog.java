package com.microlumin.xlink.log;

import android.annotation.SuppressLint;
import android.util.Log;

import com.mlaixr.mlutil.mlog.logger.MLogger;

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
    private static final String sGlobalTagPrefix = "xlink_";
    private static final String PROPERTY_KEY = "debug.mlog.level";

    private static final String DEFAULT_LEVEL = "V";
    private static int sMinLevel = parseLevel(DEFAULT_LEVEL);

    static {
        updateLevelFromProperty();
    }

    private static String formatTag(String tag) {
        if (sGlobalTagPrefix == null || sGlobalTagPrefix.isEmpty()) {
            return tag;
        }
        return sGlobalTagPrefix + tag;
    }

    private static void updateLevelFromProperty() {
        String levelStr = getSystemProperty(PROPERTY_KEY, DEFAULT_LEVEL);
        sMinLevel = parseLevel(levelStr);
    }

    private static int parseLevel(String levelStr) {
        if (levelStr == null) return INFO;
        switch (levelStr.toUpperCase()) {
            case "V":
                return VERBOSE;
            case "D":
                return DEBUG;
            case "I":
                return INFO;
            case "W":
                return WARN;
            case "E":
                return ERROR;
            case "NONE":
                return NONE;
            default:
                return INFO;
        }
    }

    public static void setLevel(int level) {
        sMinLevel = level;
    }

    @SuppressLint("LogTagMismatch")
    public static void v(String tag, String msg) {
        if (isLoggable(VERBOSE)) {
            Log.v(formatTag(tag), msg);
        }
    }

    @SuppressLint("LogTagMismatch")
    public static void d(String tag, String msg) {
        if (isLoggable(DEBUG)) {
            Log.d(formatTag(tag), msg);
//            MLogger.d(tag, msg);
        }
    }

    public static void i(String tag, String msg) {
        if (isLoggable(INFO)) {
            MLogger.i(formatTag(tag), msg);
        }
    }

    public static void w(String tag, String msg) {
        if (isLoggable(WARN)) {
            MLogger.w(formatTag(tag), msg);
        }
    }

    public static void e(String tag, String msg) {
        if (isLoggable(ERROR)) {
            MLogger.e(formatTag(tag), msg);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (isLoggable(ERROR)) {
            MLogger.e(formatTag(tag), msg, tr);
        }
    }

    private static boolean isLoggable(int level) {
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