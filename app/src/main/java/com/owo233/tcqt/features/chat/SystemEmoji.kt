package com.owo233.tcqt.features.chat

// 思路参考自 QAuxiliary: me.singleneuron.hook.SystemEmoji
// https://github.com/cinit/QAuxiliary

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.isStatic
import java.lang.reflect.Method

@RegisterAction
object SystemEmoji : Feature(
    key = "system_emoji",
    name = "强制使用系统 Emoji",
    desc = "禁用 QQ 内置 Emoji 映射，让文本中的 Emoji 使用系统字体渲染。",
) {


    override fun install() {
        val emotcationConstants = loadOrThrow("com.tencent.mobileqq.text.EmotcationConstants")

        listOf(
            emotcationConstants.findUniqueIntMethod(paramCount = 1),
            emotcationConstants.findUniqueIntMethod(paramCount = 2)
        ).forEach { method ->
            method.hookBefore { param ->
                param.result = -1
            }
        }
    }

    private fun Class<*>.findUniqueIntMethod(paramCount: Int): Method {
        val candidates = declaredMethods.filter { method ->
            method.isStatic &&
                method.returnType == Integer.TYPE &&
                method.parameterTypes.size == paramCount &&
                method.parameterTypes.all { it == Integer.TYPE }
        }

        return candidates.single().apply { isAccessible = true }
    }
}
