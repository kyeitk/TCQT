package com.owo233.tcqt.features.general

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.paramCount
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.getObjectByType
import com.owo233.tcqt.core.reflect.setObjectByType
import com.tencent.mobileqq.data.troop.TroopInfo
import com.tencent.mobileqq.troop.troopsetting.vm.TroopSettingViewModel

@RegisterAction
object AllowOpenBlockedGroup : Feature(
    key = "allow_open_blocked_group",
    name = "允许打开被封禁群组",
    desc = "解除被封禁群组无法进入聊天页面的限制。",
    requires = Requires(host = Requires.Host.QQOnly),
) {

    override fun install() {
        TroopInfo::class.java.apply {
            findMethod {
                name = "isUnreadableBlock"
                returnType = boolean
            }.hookBefore { param ->
                param.result = false
            }
            findMethod {
                name = "isNeedInterceptOnBlockTroop"
                returnType = boolean
            }.hookBefore { param ->
                param.result = false
            }
        }

        TroopSettingViewModel::class.java.declaredMethods.single { m ->
            m.paramCount == 3 &&
            m.parameterTypes[0] == m.declaringClass &&
            m.parameterTypes[1] == String::class.java &&
            m.parameterTypes[2].simpleName != "TroopSearchWay"
        }.hookBefore { param ->
            if (param.args[2]!!.getObjectByType<Int>() == 72) { // 群已解散/已不是群成员
                param.args[2]!!.setObjectByType<Int>(0)
            }
        }
    }
}
