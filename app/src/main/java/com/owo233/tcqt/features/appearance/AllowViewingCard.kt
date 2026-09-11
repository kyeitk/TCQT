package com.owo233.tcqt.features.appearance

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.getObject
import com.owo233.tcqt.core.reflect.setObject
import com.tencent.mobileqq.data.Card

@RegisterAction
object AllowViewingCard : Feature(
    key = "allow_viewing_card",
    name = "允许查看异常资料卡",
    desc = "忽略账号的异常状态，使其能够正常查看资料卡。",
) {


    override fun install() {
        "com.tencent.mobileqq.profilecard.api.impl.ProfileDataServiceImpl".toHostClass()
            .also { clazz ->
                hookProfileCardMethod(
                    clazz,
                    "getProfileCard",
                    String::class.java,
                    Boolean::class.javaPrimitiveType!!
                )
                hookProfileCardMethod(
                    clazz,
                    "getProfileCardFromCache",
                    String::class.java
                )
            }

        "com.tencent.mobileqq.profilecard.processor.ProfileSecureProcessor".toHostClass()
            .also { clazz ->
                clazz.findMethod {
                    name = "processProfileCard"
                    paramTypes(
                        bundle,
                        "SummaryCard.RespHead".toHostClass(),
                        "SummaryCard.RespSummaryCard".toHostClass()
                    )
                }.hookBefore { param ->
                    val respHead = param.args.getOrNull(1) ?: return@hookBefore
                    val result = respHead.getObject("iResult") as Int
                    if (result == 201 || result == 202) {
                        respHead.setObject("iResult", 0)
                    }
                }
            }
    }

    private fun hookProfileCardMethod(
        clazz: Class<*>,
        methodName: String,
        vararg paramTypes: Class<*>
    ) {
        clazz.findMethod {
            name = methodName
            paramTypes(*paramTypes)
            returnType = Card::class.java
        }.hookAfter { param ->
            val card = param.result as? Card ?: return@hookAfter
            if (card.forbidCode == 201 || card.forbidCode == 202) {
                card.isForbidAccount = false
                card.forbidCode = 0
            }
        }
    }
}
