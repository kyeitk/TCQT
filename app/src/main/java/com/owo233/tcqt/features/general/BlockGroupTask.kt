package com.owo233.tcqt.features.general

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.dexkit.DexKitTask
import com.owo233.tcqt.core.hook.hookBefore
import org.luckypray.dexkit.query.FindMethod
import org.luckypray.dexkit.query.base.BaseMatcher

@RegisterAction
object BlockGroupTask : Feature(
    key = "block_group_task",
    name = "屏蔽群待办通知",
    desc = "屏蔽群待办消息的通知。",
), DexKitTask {


    override fun install() {
        requireMethod("BlockGroupTask").hookBefore { param ->
            val j = param.args[1] as? Long ?: return@hookBefore
            val j2 = param.args[2] as? Long ?: return@hookBefore
            if (j == 528L && j2 == 309L) {
                param.result = null
            }
        }
    }

    override fun getQueryMap(): Map<String, BaseMatcher> = mapOf(
        "BlockGroupTask" to FindMethod().apply {
            searchPackages("com.tencent.mobileqq.notification.modularize")
            matcher {
                paramCount = 5
                usingEqStrings(
                    "TianShuOfflineMsgCenter",
                    "deal0x135Msg online:",
                    "convertMsgCommPB fail: "
                )
            }
        }
    )
}
