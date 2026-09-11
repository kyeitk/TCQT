package com.owo233.tcqt.api

import com.owo233.tcqt.core.action.ActionPriority
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.action.ActionSpec

/**
 * 基础设施任务：模块自身的骨架，**不面向用户、不进设置界面、无视开关、永远运行**。
 *
 * 与 [Feature] 的差别只有 `hidden = true` 与 `canRun() = true`；配置项声明、
 * [Requires] 可用性、[install] 的写法与 [Feature] 完全一致。
 *
 * ```kotlin
 * @RegisterAction
 * object MenuBuilder : InfraTask(
 *     key = "MenuBuilder",
 *     priority = ActionPriority.BACKGROUND,
 *     requires = Requires(ntOnly = true),
 * ) {
 *     override fun install() { … }
 * }
 * ```
 */
abstract class InfraTask(
    key: String,
    name: String = "",
    desc: String = "",
    processes: Set<ActionProcess> = ActionSpec.DEFAULT_PROCESSES,
    priority: ActionPriority = ActionPriority.DEFERRED,
    requires: Requires = Requires.None,
) : Feature(
    key = key,
    name = name,
    desc = desc,
    processes = processes,
    priority = priority,
    requires = requires,
) {

    /** 不在设置界面展示。 */
    final override val hidden: Boolean = true

    /** 无视设置开关：基础设施任务没有"关掉"这个状态。 */
    final override fun canRun(): Boolean = true
}
