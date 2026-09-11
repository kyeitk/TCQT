package com.owo233.tcqt.core.reflect

import java.lang.reflect.Constructor
import java.lang.reflect.Field
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

/**
 * 反射成员缓存。
 *
 * 缓存键必须直接持有 [Class] 而非类名：Xposed/插件环境中不同 ClassLoader
 * 可加载同名类，仅以名称作键会串起两个完全不同的成员。
 */
internal object ReflectCache {

    internal interface CacheKey {
        val owner: Class<*>
    }

    private object NotFound

    private val methodCache = ConcurrentHashMap<Any, Any>()
    private val fieldCache = ConcurrentHashMap<Any, Any>()
    private val constructorCache = ConcurrentHashMap<Any, Any>()

    fun getMethod(key: CacheKey, finder: () -> Method?): Method? {
        val value = methodCache.computeIfAbsent(key) { finder() ?: NotFound }
        return if (value === NotFound) null else value as Method
    }

    fun getField(key: CacheKey, finder: () -> Field?): Field? {
        val value = fieldCache.computeIfAbsent(key) { finder() ?: NotFound }
        return if (value === NotFound) null else value as Field
    }

    fun getConstructor(key: CacheKey, finder: () -> Constructor<*>?): Constructor<*>? {
        val value = constructorCache.computeIfAbsent(key) { finder() ?: NotFound }
        return if (value === NotFound) null else value as Constructor<*>
    }

    fun clear() {
        clearMethods()
        clearFields()
        clearConstructors()
    }

    fun clearMethods() = methodCache.clear()

    fun clearFields() = fieldCache.clear()

    fun clearConstructors() = constructorCache.clear()

    fun clear(owner: Class<*>) {
        methodCache.keys.removeAll { (it as? CacheKey)?.owner === owner }
        fieldCache.keys.removeAll { (it as? CacheKey)?.owner === owner }
        constructorCache.keys.removeAll { (it as? CacheKey)?.owner === owner }
    }
}
