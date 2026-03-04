package com.microlumin.xlink.log;

import org.junit.Test;
import static org.junit.Assert.*;

public class MLogTest {

    @Test
    public void testLevelParsing() throws Exception {
        java.lang.reflect.Method parseLevel = MLog.class.getDeclaredMethod("parseLevel", String.class);
        parseLevel.setAccessible(true);

        assertEquals(MLog.VERBOSE, parseLevel.invoke(null, "V"));
        assertEquals(MLog.DEBUG, parseLevel.invoke(null, "d"));
        assertEquals(MLog.INFO, parseLevel.invoke(null, "I"));
        assertEquals(MLog.WARN, parseLevel.invoke(null, "W"));
        assertEquals(MLog.ERROR, parseLevel.invoke(null, "E"));
        assertEquals(MLog.NONE, parseLevel.invoke(null, "NONE"));
        assertEquals(MLog.INFO, parseLevel.invoke(null, "UNKNOWN"));
        assertEquals(MLog.INFO, parseLevel.invoke(null, (Object) null));
    }

    @Test
    public void testIsLoggable() throws Exception {
        java.lang.reflect.Method isLoggable = MLog.class.getDeclaredMethod("isLoggable", String.class, int.class);
        isLoggable.setAccessible(true);
        String testTag = "TestTag";

        // 由于 Log.isLoggable 在本地测试环境中默认行为（依赖系统属性）可能不可控，
        // 我们主要验证 sMinLevel 的过滤逻辑
        MLog.setLevel(MLog.INFO);
        // INFO 及以上级别，取决于 Log.isLoggable 的默认值 (通常是 INFO)
        // 在本地单元测试中，Log.isLoggable 可能会返回默认值或者抛错（如果没 mock）
        // 但 MLog 使用了 Android SDK 的 Log 类，单元测试时通常需要 mock 或者使用 Robolectric
        // 这里假设测试环境下能够执行，我们主要看 sMinLevel 的拦截
        
        assertFalse((Boolean) isLoggable.invoke(null, testTag, MLog.DEBUG));
        assertFalse((Boolean) isLoggable.invoke(null, testTag, MLog.VERBOSE));

        MLog.setLevel(MLog.DEBUG);
        // 如果 sMinLevel 是 DEBUG，那么 DEBUG 级别通过第一关，进入 Log.isLoggable
        // assertFalse((Boolean) isLoggable.invoke(null, testTag, MLog.VERBOSE));
    }
}