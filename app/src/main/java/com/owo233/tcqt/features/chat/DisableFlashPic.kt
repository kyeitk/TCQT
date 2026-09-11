package com.owo233.tcqt.features.chat

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.launchWithCatch
import com.owo233.tcqt.core.hook.hookMethodAfter
import com.owo233.tcqt.core.sync.ModuleScope
import com.owo233.tcqt.host.service.ContactHelper
import com.owo233.tcqt.host.service.LocalGrayTips
import com.tencent.mobileqq.aio.msg.AIOMsgItem
import com.tencent.qqnt.kernel.nativeinterface.JsonGrayBusiId
import com.tencent.qqnt.kernel.nativeinterface.MsgConstant
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import io.fastkv.FastKV

@RegisterAction
object DisableFlashPic : Feature(
    key = "disable_flash_pic",
    name = "将闪照视为正常图片",
    desc = "好友发送的闪照将作为正常图片显示并添加灰条提示。",
    requires = Requires(ntOnly = true),
) {

    private val warnedKv: FastKV by lazy {
        FastKV.Builder(
            "${HookEnv.moduleDataPath}/global/flash_pic_warned",
            "FlashPicWarned"
        ).build()
    }

    override fun install() {
        AIOMsgItem::class.java.hookMethodAfter({
            name = "getMsgRecord"
        }) { param ->
            val msgRecord = param.result as? MsgRecord ?: return@hookMethodAfter

            if (msgRecord.chatType == MsgConstant.KCHATTYPEC2C &&
                msgRecord.sendType == MsgConstant.KSENDTYPERECV // 只处理对方发送的消息
            ) {
                val subMsgType = msgRecord.subMsgType // 位掩码（Bitmask）
                // 8192 (闪照标记) + 2 (图片基础类型) = 8194
                if ((subMsgType and 8192) != 0) { // 带有闪照属性
                    msgRecord.subMsgType = subMsgType and 8192.inv() // 移除闪照属性

                    val warnedKey = "${msgRecord.senderUin}_${msgRecord.msgSeq}"
                    if (!warnedKv.contains(warnedKey)) {
                        warnedKv.putBoolean(warnedKey, true)
                        ModuleScope.launchWithCatch {
                            val contact = ContactHelper.generateContact(
                                MsgConstant.KCHATTYPEC2C,
                                msgRecord.senderUin.toString()
                            )
                            LocalGrayTips.addLocalGrayTip(
                                contact,
                                JsonGrayBusiId.AIO_AV_C2C_NOTICE,
                                LocalGrayTips.Align.CENTER
                            ) {
                                text("对方发送了一张闪照")
                                msgRef("消息", msgRecord.msgSeq)
                                text("(seq=${msgRecord.msgSeq})")
                            }
                        }
                    }
                }
            }
        }
    }
}
