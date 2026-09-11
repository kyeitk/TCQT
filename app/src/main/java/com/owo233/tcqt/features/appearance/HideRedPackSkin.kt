package com.owo233.tcqt.features.appearance

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.paramCount

@RegisterAction
object HideRedPackSkin : Feature(
    key = "hide_red_pack_skin",
    name = "隐藏红包推荐皮肤",
    desc = "隐藏点击红包按钮后出现的红包皮肤推荐。",
    requires = Requires(host = Requires.Host.QQOnly),
) {

    override fun install() {
        loadOrThrow("com.tencent.mobileqq.qwallet.hb.panel.recommend.SkinRecommendViewModel")
            .declaredMethods
            .single {
                it.paramCount == 2 && it.parameterTypes[0] == Int::class.javaPrimitiveType
                        && it.parameterTypes[1].name == "kotlin.jvm.functions.Function1"
            }
            .hookBefore {
                it.result = Unit
            }
    }
}
