package com.owo233.tcqt.features.message

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.HookEnv.requireMinQQVersion
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.owo233.tcqt.core.hook.isPublic
import com.owo233.tcqt.core.hook.paramCount

@RegisterAction
object RemoveEmoReply : Feature(
    key = "remove_emo_reply",
    name = "移除消息表情回应",
    desc = "移除长按消息时出现的表情回应气泡菜单并隐藏消息底部的表情回应视图。",
) {


    override fun install() {
        loadOrThrow("com.tencent.qqnt.aio.api.impl.AIOEmoReplyMenuApiImpl")
            .hookMethodBefore({
                name = if (requireMinQQVersion(QQVersion.QQ_9_1_70))
                    "getSeparateEmoReplyMenuView" else "getEmoReplyMenuView"
            }) { param ->
                param.result = null
            }

        loadOrThrow("com.tencent.mobileqq.aio.msglist.holder.component.msgtail.AIOGeneralMsgTailContentComponent")
            .declaredMethods.first { m ->
                m.isPublic && m.paramCount == 3 && m.returnType == Void.TYPE
                        && m.parameterTypes[0] == Int::class.javaPrimitiveType
                        && m.parameterTypes[2] == List::class.java
            }
            .hookBefore { param -> param.result = null }
    }

}
