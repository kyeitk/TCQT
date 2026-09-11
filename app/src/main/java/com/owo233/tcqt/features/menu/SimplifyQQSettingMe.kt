package com.owo233.tcqt.features.menu

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.reflect.field
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.reflect.findMethod

@RegisterAction
object SimplifyQQSettingMe : Feature(
    key = "simplify_qq_setting_me",
    name = "侧滑栏精简",
    desc = "对侧滑栏功能入口进行精简隐藏。",
) {

    // 必须声明在 `options` 之前：`options` 的选项标签由它派生，普通 val 按声明顺序初始化。
    private val map: Map<String, String> = linkedMapOf(
        "d_album" to "相册",
        "d_favorite" to "收藏",
        "d_document" to "文件",
        "d_qqwallet" to "钱包",
        "d_vip_identity" to "会员中心",
        "d_decoration" to "个性装扮",
        "d_vip_card" to "免流量"
    )

    private val options by multiIntOption(
        settingKey = "type",
        name = "要精简的项目",
        options = map.values.toList(),
    )

    override fun install() {
        val selectedItemIds = options.let { flags ->
            map.keys.filterIndexedTo(mutableSetOf()) { index, _ ->
                flags.isFlagEnabled(index)
            }
        }
        if (selectedItemIds.isEmpty()) return

        val clazz = "com.tencent.mobileqq.parts.QQSettingMeMenuPanelPartV3".toClass

        clazz.findMethod {
            name = "onInitView"
        }.hookAfter { param ->
            val obj = param.thisObject

            @Suppress("UNCHECKED_CAST")
            val bizDataList = clazz.findField { type = ArrayList::class.java }
                .get(obj) as ArrayList<Any>

            // 移除所有需要隐藏的条目
            bizDataList.removeAll { item ->
                val bean = item.field("a", withSuper = false)
                    ?.get(item) ?: return@removeAll false
                needRemove(bean, selectedItemIds)
            }

            // 取 adapter 并刷新数据
            val listItemAdapter =
                clazz.declaredFields.first { it.type.name.contains("adapter") }
                    .apply { isAccessible = true }
                    .get(obj)

            "com.tencent.biz.richframework.part.adapter.AsyncListDifferDelegationAdapter".toClass
                .findMethod {
                    name = "setItems"
                    paramCount = 1
                }.invoke(listItemAdapter, bizDataList)
        }
    }

    private fun needRemove(bean: Any, selectedItemIds: Set<String>): Boolean =
        bean::class.java.declaredFields
            .filter { it.type == String::class.java }
            .any { f ->
                f.isAccessible = true
                f.get(bean) as? String in selectedItemIds
            }

}
