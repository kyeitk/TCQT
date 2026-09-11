package com.owo233.tcqt.features.cleanup

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction
object HideGuildAD : Feature(
    key = "hide_guild_ad",
    name = "隐藏频道广告",
    desc = "隐藏频道中的广告，或许还能隐藏一些其他广告。",
) {


    override fun install() {
        "com.tencent.gdtad.aditem.GdtAd".toClass.findMethod {
            name = "isValid"
            returnType = boolean
        }.hookBefore {
            it.result = false
        }
    }
}
