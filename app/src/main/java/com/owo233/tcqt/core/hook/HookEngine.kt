package com.owo233.tcqt.core.hook

import java.lang.reflect.Member

fun interface Unhook {

    fun unhook()
}

interface Invoker {

    fun invokeOrigin(thisObject: Any?, vararg args: Any?): Any?
    fun invokeWithMaxPriority(maxPriority: Int, thisObject: Any?, vararg args: Any?): Any?
}

interface HookParam {

    val method: Member

    val thisObject: Any

    var args: Array<Any?>
    var result: Any?
    var throwable: Throwable?
}

interface Chain : HookParam {

    fun proceed(args: Array<Any?> = this.args): Any?
}

interface IHookEngine {

    val apiLevel: Int
    val frameworkName: String
    val frameworkVersion: String
    val frameworkVersionCode: Long
    val bridgeClass: Class<*>?

    /** 当前引擎是否运行在兼容模式（仅 Zygisk 引擎返回 true），默认 false。 */
    val isCompatMode: Boolean get() = false

    fun hookBefore(method: Member, priority: Int = 50, callback: (HookParam) -> Unit): Unhook
    fun hookAfter(method: Member, priority: Int = 50, callback: (HookParam) -> Unit): Unhook
    fun hookReplace(method: Member, priority: Int = 50, callback: (Chain) -> Any?): Unhook

    fun getInvoker(method: Member): Invoker
    fun deoptimize(method: Member): Boolean

    fun log(priority: Int, tag: String?, msg: String, t: Throwable? = null)
}

/**
 * `IHookEngine.frameworkName` 的已知取值。
 *
 * 功能侧判断引擎种类请比较这些常量，不要 `is` 判断具体引擎类（会让 `features` 依赖 `loader`）。
 */
object HookFramework {

    /** `ZygiskHookEngine.frameworkName` 的取值。 */
    const val ZYGISK: String = "Zygisk"
}

object HookEngineManager {

    lateinit var engine: IHookEngine

    val isInitialized: Boolean
        get() = this::engine.isInitialized
}
