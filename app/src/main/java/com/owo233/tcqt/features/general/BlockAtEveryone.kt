package com.owo233.tcqt.features.general

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.loadOrThrow
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.reflect.findMethod
import com.tencent.qqnt.kernel.nativeinterface.NotificationCommonInfo
import com.tencent.qqnt.kernel.nativeinterface.RecentContactInfo
import mqq.app.AppRuntime

@RegisterAction
object BlockAtEveryone : Feature(
    key = "block_at_everyone",
    name = "屏蔽艾特全体成员通知",
    desc = "屏蔽艾特全体消息的通知。",
) {


    override fun install() {
        loadOrThrow("com.tencent.qqnt.notification.NotificationFacade").findMethod {
            paramCount = 4
            paramTypes = arrayOf(
                AppRuntime::class.java,
                RecentContactInfo::class.java,
                NotificationCommonInfo::class.java,
                boolean
            )
        }.hookBefore { param ->
            val info = param.args[1] as? RecentContactInfo ?: return@hookBefore
            if (info.chatType != CHAT_TYPE_GROUP) return@hookBefore

            val isAtAll = (info.atType and AT_ALL_FLAG) != 0 ||
                    info.abstractContent.orEmpty().any { it.content == "@全体成员" }

            if (isAtAll) param.result = null
        }
    }


    // atType 1 艾特全体成员 2 艾特群成员 6 艾特自己
    // chatType 2 群组 1 好友
    private const val AT_ALL_FLAG = 1
    private const val CHAT_TYPE_GROUP = 2
}
