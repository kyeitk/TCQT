package com.owo233.tcqt.features.message

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.load
import com.owo233.tcqt.core.hook.hookMethodBefore

@RegisterAction
object ShowPreciseBanTime : Feature(
    key = "show_precise_ban_time",
    name = "显示精准禁言时间",
    desc = "禁言状态下在聊天页文字输入框中将替换显示精确的禁言时间，而非只显示单独的<天，分，秒>",
) {


    override fun install() {
        load("com.tencent.qqnt.troop.impl.TroopGagUtils")!!
            .hookMethodBefore({
                name = "remainingTimeToStringCountDown"
                paramTypes = arrayOf(long)
            }) {
                val time = it.args[0] as Long
                if (time <= 0) {
                    it.result = "0秒"
                } else {
                    it.result = formatDuration(time)
                }
            }
    }

    private fun formatDuration(seconds: Long): String {
        val days = seconds / (24 * 3600)
        val hours = (seconds % (24 * 3600)) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return buildString {
            if (days > 0) append("${days}天")
            if (hours > 0) append("${hours}时")
            if (minutes > 0) append("${minutes}分")
            if (secs > 0 || isEmpty()) append("${secs}秒")
        }
    }

}
