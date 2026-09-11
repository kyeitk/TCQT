package com.owo233.tcqt.features.menu

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodReplace
import com.owo233.tcqt.core.hook.isFinal
import com.owo233.tcqt.core.hook.isPublic
import com.owo233.tcqt.core.hook.isStatic
import com.owo233.tcqt.core.hook.paramCount

@RegisterAction
object DisableReactionLimit : Feature(
    key = "disable_reaction_limit",
    name = "禁止过滤反应表情",
    desc = "将更多的表情（Emoji）显示出来。",
    requires = Requires(host = Requires.Host.QQOnly),
) {

    override fun install() {
        load("com.tencent.mobileqq.guild.emoj.api.impl.QQGuildEmojiApiImpl")?.let {
            it.hookMethodReplace({
                name = "getFilterEmojiData"
            }) { null }
            it.hookMethodReplace({
                name = "getFilterSysData"
            }) { null }
        }

        load("com.tencent.mobileqq.aio.msglist.holder.component.msgtail.utils.a")
            ?.declaredMethods
            ?.single {
                it.returnType == Long::class.javaPrimitiveType &&
                        it.paramCount == 0 && it.isPublic &&
                        it.isStatic && it.isFinal
            }?.hookBefore { it.result = 0L }
    }
}
