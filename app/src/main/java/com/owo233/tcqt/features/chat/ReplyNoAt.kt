package com.owo233.tcqt.features.chat

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.hook.hookReplace
import com.owo233.tcqt.core.hook.invokeOriginal
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.core.reflect.getObjectByTypeOrNull
import com.tencent.mobileqq.aio.event.AIOMsgSendEvent
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.mvi.base.route.MsgIntent
import org.luckypray.dexkit.query.FindClass
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
object ReplyNoAt : Feature(
    key = "reply_no_at",
    name = "移除引用消息自动艾特",
    desc = "引用消息时不添加艾特文本。",
), DexKitTask {


    override fun install() {
        requireClass("reply_no_at").findMethod {
            returnType = void
            paramTypes = arrayOf(MsgIntent::class.java)
        }.hookReplace { param ->
            if (param.args[0] !is AIOMsgSendEvent.MsgOnClickReplyEvent)
                return@hookReplace param.invokeOriginal()

            val aioMsgItem = param.args[0]?.getObjectByTypeOrNull<AIOMsgItem>()
                ?: return@hookReplace param.invokeOriginal()
            val senderUid = aioMsgItem.msgRecord.senderUid

            aioMsgItem.msgRecord.senderUid = ""
            param.invokeOriginal()
            aioMsgItem.msgRecord.senderUid = senderUid
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "reply_no_at" to FindClass().apply {
            searchPackages("com.tencent.mobileqq.aio.input.reply")
            matcher {
                methods {
                    add { name("onDestroy") }
                    add { returnType(Set::class.java) }
                }
            }
        }
    )
}
