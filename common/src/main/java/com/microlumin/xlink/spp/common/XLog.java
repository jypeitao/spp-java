package com.microlumin.xlink.spp.common;

import android.util.Log;

/**
 * 封装 Android Log，方便统一管理和扩展。
 */
public class XLog {
    private static boolean sEnabled = true;
    private static String sGlobalTagPrefix = "";

    public static void setEnabled(boolean enabled) {
        sEnabled = enabled;
    }

    public static void setGlobalTagPrefix(String prefix) {
        sGlobalTagPrefix = prefix;
    }

    private static String formatTag(String tag) {
        if (sGlobalTagPrefix == null || sGlobalTagPrefix.isEmpty()) {
            return tag;
        }
        return sGlobalTagPrefix + tag;
    }

    public static void v(String tag, String msg) {
        if (sEnabled) {
            Log.v(formatTag(tag), msg);
        }
    }

    public static void v(String tag, String msg, Throwable tr) {
        if (sEnabled) {
            Log.v(formatTag(tag), msg, tr);
        }
    }

    public static void d(String tag, String msg) {
        if (sEnabled) {
            Log.d(formatTag(tag), msg);
        }
    }

    public static void d(String tag, String msg, Throwable tr) {
        if (sEnabled) {
            Log.d(formatTag(tag), msg, tr);
        }
    }

    public static void i(String tag, String msg) {
        if (sEnabled) {
            Log.i(formatTag(tag), msg);
        }
    }

    public static void i(String tag, String msg, Throwable tr) {
        if (sEnabled) {
            Log.i(formatTag(tag), msg, tr);
        }
    }

    public static void w(String tag, String msg) {
        if (sEnabled) {
            Log.w(formatTag(tag), msg);
        }
    }

    public static void w(String tag, String msg, Throwable tr) {
        if (sEnabled) {
            Log.w(formatTag(tag), msg, tr);
        }
    }

    public static void e(String tag, String msg) {
        if (sEnabled) {
            Log.e(formatTag(tag), msg);
        }
    }

    public static void e(String tag, String msg, Throwable tr) {
        if (sEnabled) {
            Log.e(formatTag(tag), msg, tr);
        }
    }
}