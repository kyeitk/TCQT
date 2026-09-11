package com.owo233.tcqt.features.general

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookMethodBefore

@RegisterAction
object ArkClickable : Feature(
    key = "ark_clickable",
    name = "允许打开Ark消息",
    desc = "仅TIM可用，绕过部分Ark卡片消息禁止访问（请到最新版本QQ使用）的限制。",
    requires = Requires(host = Requires.Host.TimOnly),
) {

    override fun install() {
        load("com.tencent.mobileqq.aio.msglist.holder.component.ark.d")
            ?.hookMethodBefore("a", String::class.java, String::class.java) {
                it.result = true
            }
    }
}
