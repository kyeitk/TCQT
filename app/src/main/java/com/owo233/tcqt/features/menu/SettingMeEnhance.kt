package com.owo233.tcqt.features.menu

import com.owo233.tcqt.annotations.RegisterAction
import com.owo233.tcqt.api.Feature
import com.owo233.tcqt.api.Requires
import com.owo233.tcqt.core.action.ActionProcess
import com.owo233.tcqt.core.env.isFlagEnabled
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookAfter
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.core.reflect.field
import com.owo233.tcqt.core.reflect.findField
import com.owo233.tcqt.core.reflect.findMethod
import com.owo233.tcqt.features.menu.settingmecards.WalletBalanceSource

/**
 * 侧滑栏增强：保留历史「侧滑栏精简」的 key 与精简行为，并提供「旧精简 / 全功能」模式。
 *
 * - 旧精简：只执行原有隐藏行为，不显示模块设置入口。
 * - 全功能：保留原有隐藏行为，并注入模块设置入口；里程碑 1 负责整页宽度、顶部快捷项、
 *   菜单 MiuixIcons 替换和底部栏 MIUIX 风格化。
 */
@RegisterAction
object SimplifyQQSettingMe : Feature(
    key = "simplify_qq_setting_me",
    name = "侧滑栏增强",
    desc = "侧滑栏精简/增强：旧精简模式保留原隐藏行为，全功能模式提供 MIUIX 风格整页改造。",
    requires = Requires(host = Requires.Host.QQOnly, ntOnly = true),
    processes = setOf(ActionProcess.MAIN, ActionProcess.TOOL),
) {

    private const val TAG = "SettingMeEnhance"
    private const val MODE_LEGACY = 1
    private const val MODE_FULL = 2
    private const val ICON_NATIVE = 1
    private const val ICON_MIUIX = 2
    private const val BOTTOM_NATIVE = 1
    private const val BOTTOM_MIUIX = 2

    // 必须声明在 `options` 之前：`options` 的选项标签由它派生，普通 val 按声明顺序初始化。
    private val map: Map<String, String> = linkedMapOf(
        "d_album" to "相册",
        "d_favorite" to "收藏",
        "d_document" to "文件",
        "d_qqwallet" to "钱包",
        "d_vip_identity" to "会员中心",
        "d_decoration" to "个性装扮",
        "d_vip_card" to "免流量",
    )

    private val mode by intOption(
        settingKey = "mode",
        name = "模式",
        defaultValue = MODE_LEGACY,
        options = listOf("旧精简", "全功能"),
    )

    private val options by multiIntOption(
        settingKey = "type",
        name = "要精简的项目",
        options = map.values.toList(),
    )

    private val panelClip by multiIntOption(
        settingKey = "panel_clip",
        name = "面板缩限",
        desc = "开启后才调整侧滑栏宽度与圆角。",
        options = listOf("开启"),
    )

    private val panelWidth by sliderOption(
        settingKey = "panel_width",
        name = "面板宽度",
        defaultValue = 84,
        min = 70,
        max = 92,
        step = 2,
        suffix = "%",
    )

    private val cornerRadius by sliderOption(
        settingKey = "corner_radius",
        name = "圆角",
        defaultValue = 28,
        min = 0,
        max = 40,
        step = 4,
        suffix = "dp",
    )

    private val topShortcuts by multiIntOption(
        settingKey = "top_shortcuts",
        name = "顶部快捷项",
        defaultValue = 0b1111,
        options = listOf("打卡", "状态", "QQ秀", "返回"),
    )

    private val menuIconStyle by intOption(
        settingKey = "menu_icon_style",
        name = "菜单图标",
        defaultValue = ICON_MIUIX,
        options = listOf("原生", "MiuixIcons"),
    )

    private val bottomBarStyle by intOption(
        settingKey = "bottom_bar_style",
        name = "底部栏",
        defaultValue = BOTTOM_MIUIX,
        options = listOf("原生", "MIUIX"),
    )

    private val cardStyle by intOption(
        settingKey = "card_style",
        name = "内容布局",
        defaultValue = 2,
        options = listOf("原生", "Miuix 卡片"),
        desc = "基于原生栏目适配实际宽度，加入可编辑的天气和钱包卡片，保留原生资料内容。",
    )

    private val cardBackground by multiIntOption(
        settingKey = "card_background",
        name = "卡片面板背景",
        defaultValue = 1,
        options = listOf("保留原生背景图"),
    )

    private var cardLayout by stringOption(
        settingKey = "card_layout",
        name = "卡片布局",
        isHide = true,
    )

    private var weatherCity by stringOption(
        settingKey = "weather_city",
        name = "天气城市",
        defaultValue = "北京,39.9042,116.4074",
        desc = "格式：城市名,纬度,经度。手动指定城市，不自动获取定位。",
        placeholder = "北京,39.9042,116.4074",
    )

    override fun install() {
        val addModuleSettings = mode == MODE_FULL
        if (addModuleSettings && cardStyle == 2) WalletBalanceSource.install()
        if (currentProcess != ActionProcess.MAIN) return
        val selectedItemIds = options.let { flags ->
            map.keys.filterIndexedTo(mutableSetOf()) { index, _ ->
                flags.isFlagEnabled(index)
            }
        }
        if (!addModuleSettings && selectedItemIds.isEmpty()) return

        if (addModuleSettings) {
            SettingMeMenuSession.configure(
                SettingMeUiConfig(
                    widthPercent = panelWidth,
                    cornerRadiusDp = cornerRadius,
                    panelClip = panelClip.isFlagEnabled(0),
                    showTopPunch = topShortcuts.isFlagEnabled(0),
                    showTopStatus = topShortcuts.isFlagEnabled(1),
                    showTopQqShow = topShortcuts.isFlagEnabled(2),
                    showTopReturn = topShortcuts.isFlagEnabled(3),

                    menuIconStyle = menuIconStyle,
                    bottomBarStyle = bottomBarStyle,
                    cardReflow = cardStyle == 2,
                    showBackground = cardBackground.isFlagEnabled(0),
                    cardLayout = cardLayout,
                    weatherCity = weatherCity,
                    saveCardLayout = { cardLayout = it },
                    readCardLayout = { cardLayout },
                )
            )
        }

        val clazz = "com.tencent.mobileqq.parts.QQSettingMeMenuPanelPartV3".toClass

        clazz.findMethod {
            name = "onInitView"
        }.hookAfter { param ->
            val obj = param.thisObject ?: return@hookAfter

            if (addModuleSettings) {
                runCatching { SettingMeMenuSession.ensureModuleSettingsEntry(obj) }
                    .onFailure { Log.w("$TAG 侧滑栏全功能应用失败: $it") }
            }

            if (selectedItemIds.isNotEmpty()) {
                removeSelectedItems(clazz, obj, selectedItemIds)
            }
        }
    }

    private fun removeSelectedItems(clazz: Class<*>, obj: Any, selectedItemIds: Set<String>) {
        @Suppress("UNCHECKED_CAST")
        val bizDataList = clazz.findField { type = ArrayList::class.java }
            .get(obj) as ArrayList<Any>

        bizDataList.removeAll { item ->
            val bean = item.field("a", withSuper = false)
                ?.get(item) ?: return@removeAll false
            needRemove(bean, selectedItemIds)
        }

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

    private fun needRemove(bean: Any, selectedItemIds: Set<String>): Boolean =
        bean::class.java.declaredFields
            .filter { it.type == String::class.java }
            .any { f ->
                f.isAccessible = true
                f.get(bean) as? String in selectedItemIds
            }
}
