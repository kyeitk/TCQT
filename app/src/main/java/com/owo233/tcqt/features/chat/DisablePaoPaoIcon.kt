package com.owo233.tcqt.features.chat

import android.annotation.SuppressLint
import android.widget.ImageView
import android.widget.LinearLayout
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction
object DisablePaoPaoIcon : Feature(
    key = "disable_pao_pao_icon",
    name = "禁用泡泡图标",
    desc = "将聊天界面中的泡泡图标替换为红包图标。",
    requires = Requires(host = Requires.Host.QQOnly),
) {

    @SuppressLint("DiscouragedApi")
    override fun install() {
        "com.tencent.qqnt.aio.shortcutbar.PanelIconLinearLayout".toHostClass().also { clazz ->
            clazz.findMethod {
                paramTypes(int, string, null)
            }.hookAfter { param ->
                val layout = param.thisObject as LinearLayout
                val icon = layout.findViewWithTag<ImageView>(1016)

                if (icon != null) {
                    val hbId = HookEnv.hostAppContext.resources
                        .getIdentifier(
                            "qui_red_envelope_aio_oversized_light_selector",
                            "drawable",
                            HookEnv.hostAppPackageName
                        )

                    icon.tag = 1004
                    icon.contentDescription = "红包"
                    icon.setImageResource(hbId)
                }
            }
        }
    }
}
