package com.owo233.tcqt.features.message

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction
object PokeNoCoolDown : Feature(
    key = "poke_no_cool_down",
    name = "戳一戳无冷却",
    desc = "移除戳一戳冷却时间(每天上限200次)。",
) {


    override fun install() {
        "com.tencent.mobileqq.paiyipai.PaiYiPaiHandler".toHostClass().findMethod {
            returnType = boolean
            visibility = private
            paramTypes = arrayOf(string)
            paramCount = 1
        }.hookBefore { param -> param.result = true }
    }
}
