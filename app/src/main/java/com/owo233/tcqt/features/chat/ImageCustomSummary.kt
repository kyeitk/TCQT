/**
 * 此 HOOK 与选项均来自 QAuxiliary。
 */

package com.owo233.tcqt.features.chat

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.hook.hookMethodBefore
import com.tencent.qqnt.kernel.nativeinterface.IKernelMsgService
import com.tencent.qqnt.kernel.nativeinterface.MsgElement
import com.tencent.qqnt.kernelpublic.nativeinterface.Contact

@RegisterAction
object ImageCustomSummary : Feature(
    key = "image_custom_summary",
    name = "自定义图片外显文字",
    desc = "自定义消息列表中图片类型消息的外显文字。",
    requires = Requires(ntOnly = true),
) {

    private val mHookType by multiIntOption(
        settingKey = "type",
        name = "外显类型",
        defaultValue = 0,
        options = listOf(
            "表情商城",
            "表情泡泡",
            "纯图片0/图文混排0",
            "动画表情1/表情搜索2/表情消息4/表情推荐7"
        ),
    )

    private val mHookString by stringOption(
        settingKey = "string",
        name = "自定义内容",
        placeholder = "填写内容, e.g: 你干嘛,哎哟,你好烦~",
    )

    override fun install() {
        if (mHookType == 0 || mHookString.isBlank()) {
            return
        }

        IKernelMsgService.CppProxy::class.java.hookMethodBefore({
            name = "sendMsg"
        }) { param ->
            val contact = param.args[1] as Contact
            val elements = param.args[2] as ArrayList<*>

            for (element in elements) {
                val msgElement = (element as MsgElement)

                msgElement.picElement?.let { picElement ->
                    val picSubType = picElement.picSubType

                    if ((mHookType and (1 shl 2)) != 0 && picSubType == 0) {
                        picElement.summary = mHookString
                    }
                    if ((mHookType and (1 shl 3)) != 0 && picSubType != 0) {
                        picElement.summary = mHookString
                        if (contact.chatType != 4) picElement.picSubType = 7
                    }
                }

                msgElement.marketFaceElement?.let { marketFaceElement ->
                    if ((mHookType and (1 shl 0)) != 0) {
                        marketFaceElement.faceName = mHookString
                    }
                }

                msgElement.faceBubbleElement?.let { faceBubbleElement ->
                    if ((mHookType and (1 shl 1)) != 0) {
                        faceBubbleElement.content = mHookString
                    }
                }
            }
        }
    }
}
