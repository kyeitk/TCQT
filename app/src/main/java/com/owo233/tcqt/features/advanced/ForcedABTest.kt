package com.owo233.tcqt.features.advanced

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.reflect.getObject
import com.owo233.tcqt.core.reflect.invoke
import com.owo233.tcqt.core.reflect.setObject

@RegisterAction
object ForcedABTest : Feature(
    key = "forced_to_ab",
    name = "AB测试强制转组",
    desc = "在AB测试中强制转到指定组，想优先体验某些灰度功能时可以尝试本功能，或者留在对照组。",
    processes = setOf(ActionProcess.ALL),
) {

    private val mode by intOption(
        settingKey = "mode",
        name = "强制模式",
        defaultValue = 1,
        options = listOf("强制A组（对照组）", "强制B组（实验组）"),
    )

    override fun install() {
        val controllerClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ABTestController")
        val expEntityClz = loadOrThrow("com.tencent.mobileqq.utils.abtest.ExpEntityInfo")

        expEntityClz.hookMethodBefore(
            "isExpHit",
            String::class.java
        ) { param ->
            when (mode) {
                1 -> param.result = false
                2 -> param.result = true
            }
        }

        expEntityClz.hookMethodBefore("getAssignment") { param ->
            val expName = param.thisObject.invoke("getExpName") as String
            if (!expName.isEmpty()) {
                when (mode) {
                    1 -> param.result = "${expName}_A"
                    2 -> param.result = "${expName}_B"
                }
            }
        }

        expEntityClz.hookMethodBefore(
            "isExperiment",
            String::class.java
        ) { param ->
            when (mode) {
                1 -> param.result = false
                2 -> param.result = true
            }
        }

        expEntityClz.hookMethodBefore(
            "isContrast",
            String::class.java
        ) { param ->
            when (mode) {
                1 -> param.result = true
                2 -> param.result = false
            }
        }

        controllerClz.hookMethodAfter(
            "getExpEntityInner",
            String::class.java,
            String::class.java,
            Boolean::class.java
        ) { param ->
            val entity = param.result
            val mAssignment = entity!!.getObject("mAssignment") as String
            when (mode) {
                1 -> entity.setObject("mAssignment", "${mAssignment}_A")
                2 -> entity.setObject("mAssignment", "${mAssignment}_B")
            }
            param.result = entity
        }
    }
}
