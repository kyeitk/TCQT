package com.owo233.tcqt.features.appearance

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.QQVersion
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.doNothing
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction
object BlockChainAniSticker : Feature(
    key = "block_chain_ani_sticker",
    name = "屏蔽全屏动画彩蛋",
    desc = "屏蔽发送或接收超级表情时触发的全屏连锁动画播放。",
    requires = Requires(minQQVersion = QQVersion.QQ_9_0_20),
) {

    override fun install() {
        "com.tencent.mobileqq.aio.animation.api.impl.AioAnimationApiImpl".toClass.findMethod {
            name = "handleNewMsg"
        }.doNothing()
    }
}
