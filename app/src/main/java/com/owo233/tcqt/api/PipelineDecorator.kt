package com.owo233.tcqt.api

/**
 * 管线装饰器：由 `features/internal/pipeline/` 下的三条管线按能力驱动。
 *
 * [isAvailable] 只回答"参不参与"，[activate] 负责安装（注册 Action 的装饰器不需要
 * 覆写它）。
 *
 * ```kotlin
 * class RecallHeaderTip : OnAIOViewUpdate {
 *     override fun isAvailable() = featureEnabled && AntiRecallConfig.isTopTipEnabled()
 *     override fun activate() { if (!registered) RecallManager.addListener(::onRecalled) }
 * }
 * ```
 *
 * [Feature] 已经实现了本接口：`isAvailable()` 等于框架启动路径上的
 * `canRun() && onInit()`，`activate()` 保持空实现 —— 注册 Action 的安装由框架走
 * 它自己的 `install()`，管线不能也不该重复触发。
 */
interface PipelineDecorator {

    /**
     * 装配顺序，小的排在前面；同序时按发现顺序稳定排序。
     *
     * **必须显式声明，不要依赖枚举顺序**：装饰器由能力发现从注册表取出，而注册表的
     * 顺序就是 KSP 的类名字典序，依赖它意味着改个类名会静默改变菜单项或视图条的先后。
     */
    val decoratorOrder: Int get() = 1000

    /**
     * 是否参与本次管线装配。
     *
     * **必须是纯函数** —— 它在 `install()` 期间被调用，副作用放 [activate]。
     */
    fun isAvailable(): Boolean = true

    /**
     * 被判定为可用之后调用**一次**。
     *
     * 只有"非注册装饰器"需要覆写它（它们没有 `install()` 可被框架调用）。
     */
    fun activate() = Unit
}
