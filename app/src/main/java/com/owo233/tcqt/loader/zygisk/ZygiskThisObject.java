package com.owo233.tcqt.loader.zygisk;

/**
 * Platform-type bridge: returns the receiver as-is so Kotlin sees a flexible
 * platform type instead of a Kotlin non-null value. That lets
 * {@code HookParam.thisObject} be {@code null} for static methods without a
 * Kotlin null-assertion, matching Xposed semantics.
 */
final class ZygiskThisObject {

    private ZygiskThisObject() {}

    static Object get(Object receiver) {
        return receiver;
    }
}
