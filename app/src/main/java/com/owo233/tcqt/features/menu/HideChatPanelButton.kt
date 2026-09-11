package com.owo233.tcqt.features.menu

// 思路参考自 QAuxiliary: https://github.com/cinit/QAuxiliary

import android.widget.ImageView
import android.widget.LinearLayout
import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.HookEnv.toHostClass
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction
object HideChatPanelButton : Feature(
    key = "hide_chat_panel_button",
    name = "净化输入框下的快捷按钮",
    desc = "移除聊天输入框下方的语音、相册、拍照等快捷按钮，并让剩余按钮自动重新排版。",
) {

    // 必须声明在 `items` 之前：`items` 的 options 要用到它，普通 val 按声明顺序初始化。
    private val PANEL_ITEMS = listOf(
        // QQ 9.2.95: PanelIconLinearLayout.e(int, String, h) adds direct ImageView children
        // from AIOPanelIconItem(tag, contentDescription, drawable, resId).
        PanelItem("语音", tags = setOf(1000)),
        PanelItem("相册", titles = setOf("相册", "图片"), tags = setOf(1003)),
        PanelItem("拍照", tags = setOf(1005)),
        PanelItem("红包", tags = setOf(1004)),
        PanelItem("滤镜视频", titles = setOf("滤镜视频", "泡泡"), tags = setOf(1016)),
        PanelItem("表情", tags = setOf(1001))
    )

    private val items by multiIntOption(
        settingKey = "items",
        name = "净化项目",
        defaultValue = 0,
        options = PANEL_ITEMS.map { it.label },
    )

    private val activeItems: Set<PanelItem> by lazy {
        PANEL_ITEMS.filterIndexed { index, _ -> items.isFlagEnabled(index) }.toSet()
    }

    override fun install() {
        if (!HookEnv.isQQ() || activeItems.isEmpty()) return

        "com.tencent.qqnt.aio.shortcutbar.PanelIconLinearLayout".toHostClass()
            .findMethod { paramTypes(int, string, null) }
            .hookAfter { param ->
                val layout = param.thisObject as? LinearLayout ?: return@hookAfter

                layout.removeMatchedShortcuts()
            }
    }

    private fun LinearLayout.removeMatchedShortcuts() {
        for (index in childCount - 1 downTo 0) {
            val child = getChildAt(index)
            if (child is ImageView && activeItems.any { it.matches(child) }) {
                removeViewAt(index)
            }
        }
    }

    private data class PanelItem(
        val label: String,
        val titles: Set<String> = setOf(label),
        val tags: Set<Int> = emptySet()
    ) {

        fun matches(icon: ImageView): Boolean {
            val description = icon.contentDescription?.toString() ?: return false
            val tag = icon.tag as? Int
            return (tag != null && tag in tags) || description.trim() in titles
        }
    }
}
