package com.owo233.tcqt.features.menu.settingmecards

import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import android.widget.TextView
import android.text.TextUtils
import android.view.ViewOutlineProvider
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.features.menu.SettingMeUiConfig
import com.owo233.tcqt.features.menu.SettingMeMenuSession
import java.util.WeakHashMap
import java.lang.ref.WeakReference

/** Adjust the existing columns. Native profile, drawer, background and actions stay in place. */
internal object NativePanelReflow {
    private val sessions = WeakHashMap<ViewGroup, WeakReference<Session>>()
    private fun adaptTagRow(row: ViewGroup) {
        if (row.width <= 0 || row.childCount == 0) return
        val gap = (6 * row.resources.displayMetrics.density).toInt()
        val items = (0 until row.childCount).map(row::getChildAt)
        // QQ's last child is its original “更多” action. Reserve room for it.
        val end = items.last()
        val available = row.width - row.paddingLeft - row.paddingRight
        if (available <= 0) return
        items.forEach { item ->
            val height = item.layoutParams.height
            item.measure(View.MeasureSpec.makeMeasureSpec(item.layoutParams.width.coerceIn(1, available),
                View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(
                if (height > 0) height else row.height.coerceAtLeast(row.measuredHeight).coerceAtLeast(1),
                if (height > 0) View.MeasureSpec.EXACTLY else View.MeasureSpec.AT_MOST))
        }
        var left = row.paddingLeft
        items.dropLast(1).forEach { item ->
            if (left + item.measuredWidth + gap + end.measuredWidth <= row.width - row.paddingRight) {
                item.layout(left, row.paddingTop, left + item.measuredWidth, row.paddingTop + item.measuredHeight)
                left += item.measuredWidth + gap
            } else item.layout(0, 0, 0, 0)
        }
        end.layout(left, row.paddingTop, left + end.measuredWidth, row.paddingTop + end.measuredHeight)
    }

    fun attach(part: Any, config: SettingMeUiConfig, attempt: Int = 0) {
        val panel = nativePanel(part) ?: return
        if (sessions[panel]?.get() != null) return
        val menu = descendants(panel).filterIsInstance<ViewGroup>().firstOrNull {
            it.javaClass.simpleName.contains("RecyclerView") &&
            descendants(it).filterIsInstance<TextView>().count { text ->
                text.text.toString() in setOf("相册", "收藏", "钱包", "文件", "会员中心", "个性装扮")
            } >= 2
        } ?: run {
            if (attempt < 10) panel.postDelayed({ attach(part, config, attempt + 1) }, 300L)
            else Log.w("SettingMeNativeReflow 原生菜单尚未加载，本次保留原布局")
            return
        }
        val content = menu.parent as? RelativeLayout ?: return
        val profile = (0 until content.childCount).map(content::getChildAt)
            .filterIsInstance<FrameLayout>().firstOrNull { frame ->
                descendants(frame).any { it.javaClass.simpleName == "QQProAvatarView" }
            } ?: return
        sessions[panel] = WeakReference(Session(panel, content, profile, menu, config))
    }

    private class Session(
        private val panel: ViewGroup,
        private val content: RelativeLayout,
        private val profile: FrameLayout,
        private val menu: ViewGroup,
        private val config: SettingMeUiConfig,
    ) {
        private val widgets = NativeWidgets(panel.context, config, NativeCardBridge(panel))
        private var pending = false
        private var lastGeometry: List<Int>? = null
        private val adaptNextFrame = Runnable {
            pending = false
            if (!panel.isAttachedToWindow) return@Runnable
            val signature = listOf(panel.rootView.width, panel.height, if (HookEnv.isNightMode()) 1 else 0) +
                descendants(profile).filter { NativeCardBridge.resourceName(it) in setOf("pq6", "prg", "pr4", "xft") }
                    .flatMap { listOf(it.measuredWidth, it.measuredHeight, it.visibility) }
            if (lastGeometry != signature) { lastGeometry = signature; adapt() }
        }
        private val listener = ViewTreeObserver.OnGlobalLayoutListener {
            if (!pending) { pending = true; panel.postOnAnimation(adaptNextFrame) }
        }
        private val backgrounds = WeakHashMap<View, NativeProfileBackground>()
        private var coverSpacer: View? = null
        private var spacerHeight = 0
        private val imageListener = ViewTreeObserver.OnDrawListener { backgrounds.values.forEach { it.refreshSource() } }
        private val tagsListener = ViewTreeObserver.OnPreDrawListener {
            if (HookEnv.versionCode == 16410L) descendants(profile)
                .filterIsInstance<ViewGroup>().filter { it.javaClass.simpleName == "FlowLayoutContainer" }
                .forEach { fitTags(it); adaptTagRow(it) }
            true
        }

        init {
            widgets.id = View.generateViewId()
            content.addView(widgets, RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                addRule(RelativeLayout.BELOW, profile.id)
                topMargin = dp(NativeMiuixStyle.GroupGap)
                leftMargin = dp(NativeMiuixStyle.PageGutter)
                rightMargin = dp(NativeMiuixStyle.PageGutter)
            })
            (menu.layoutParams as? RelativeLayout.LayoutParams)?.let {
                it.addRule(RelativeLayout.BELOW, widgets.id)
                it.topMargin = dp(12)
                menu.layoutParams = it
            }
            panel.viewTreeObserver.addOnGlobalLayoutListener(listener)
            panel.viewTreeObserver.addOnDrawListener(imageListener)
            panel.viewTreeObserver.addOnPreDrawListener(tagsListener)
            panel.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) {
                    panel.viewTreeObserver.addOnGlobalLayoutListener(listener)
                    panel.viewTreeObserver.addOnDrawListener(imageListener)
                    panel.viewTreeObserver.addOnPreDrawListener(tagsListener)
                }
                override fun onViewDetachedFromWindow(view: View) {
                    panel.removeCallbacks(adaptNextFrame); pending = false; lastGeometry = null
                    panel.viewTreeObserver.removeOnGlobalLayoutListener(listener)
                    panel.viewTreeObserver.removeOnDrawListener(imageListener)
                    panel.viewTreeObserver.removeOnPreDrawListener(tagsListener)
                }
            })
            adapt()
            Log.i("SettingMeNativeReflow 已在原生资料卡与菜单之间接入组件，保留原生栏目")
        }

        private fun adapt() {
            val screenWidth = panel.rootView.width.takeIf { it > 0 } ?: return
            val width = if (config.panelClip) screenWidth * config.widthPercent / 100 else panel.width
            if (width <= 0) return
            resize(content, width)
            NativeCoverAdaptation.adaptTopBar(panel, width)
            SettingMeMenuSession.applyMenuIcons(menu, config)
            // Preserve the existing background cropping; only the content column gets a real width.
            val covers = descendants(panel).filter { it.javaClass.simpleName == "DrawerRandomCoverView" }
            covers.forEach { it.alpha = if (config.showBackground && backgrounds.isEmpty()) 1f else 0f }
            descendants(menu).filter { it.javaClass.simpleName == "TianshuRedTouch" }
                .forEach { if (it.visibility != View.GONE) it.visibility = View.GONE }
            descendants(menu).filterIsInstance<TextView>().filter {
                it.text.toString() in setOf("限时推广", "广告", "商业推广")
            }.forEach { if (it.visibility != View.GONE) it.visibility = View.GONE }

            // This mapping is from the connected 9.3.70 device, not the older research report.
            if (HookEnv.versionCode != 16410L) return
            val cardWidth = (width - dp(NativeMiuixStyle.PageGutter * 2)).coerceAtLeast(1)
            descendants(profile).filter { NativeCardBridge.resourceName(it) == "pp7" && it is RelativeLayout }
                .forEach { card ->
                    val avatar = descendants(card).firstOrNull { NativeCardBridge.resourceName(it) == "pr9" }
                        ?: return@forEach
                    resize(card, cardWidth)
                    (card.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
                        if (lp.marginStart != dp(NativeMiuixStyle.PageGutter) || lp.marginEnd != dp(NativeMiuixStyle.PageGutter)) {
                            lp.marginStart = dp(NativeMiuixStyle.PageGutter); lp.marginEnd = dp(NativeMiuixStyle.PageGutter)
                            card.layoutParams = lp
                        }
                    }
                    // QQ's pager uses custom layout params and ignores child margins.
                    // Offset only the current page's small inset; never pull another page into view.
                    val cardPoint = IntArray(2).also { card.getLocationInWindow(it) }
                    val profilePoint = IntArray(2).also { profile.getLocationInWindow(it) }
                    val inset = cardPoint[0] - profilePoint[0] - card.translationX
                    if (inset in 0f..dp(32).toFloat()) card.translationX = dp(NativeMiuixStyle.PageGutter) - inset
                    val background = backgrounds.getOrPut(card) {
                        card.outlineProvider = ViewOutlineProvider.BACKGROUND
                        card.clipToOutline = true
                        NativeProfileBackground(covers.firstOrNull(), dp(NativeMiuixStyle.CardCorner).toFloat())
                    }
                    background.setSource(covers.firstOrNull())
                    background.showCover = config.showBackground
                    if (card.background !== background) card.background = background
                    resize(avatar, dp(64), dp(64))
                    val details = descendants(card).firstOrNull { NativeCardBridge.resourceName(it) == "pq6" }
                    details?.let { view ->
                        resize(view, (cardWidth - dp(64 + 44)).coerceAtLeast(dp(80)))
                        val detailsWidth = (cardWidth - dp(64 + 44)).coerceAtLeast(dp(80))
                        val accountText = descendants(view).filterIsInstance<TextView>().firstOrNull { it.text.toString() == "切换账号" }
                        val nameRow = descendants(view).firstOrNull { NativeCardBridge.resourceName(it) == "pqz" } as? LinearLayout
                        if (nameRow != null && accountText != null) {
                            nameRow.orientation = LinearLayout.VERTICAL
                            resize(nameRow, (detailsWidth - dp(32)).coerceAtLeast(dp(48)), ViewGroup.LayoutParams.WRAP_CONTENT)
                            (nameRow.parent as? View)?.let { resize(it, it.layoutParams.width, ViewGroup.LayoutParams.WRAP_CONTENT) }
                            accountText.maxLines = 1
                            accountText.isSingleLine = true
                            if (accountText.textSize != 12f * accountText.resources.displayMetrics.scaledDensity) accountText.textSize = 12f
                            resize(accountText, ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                            var accountControl: View = accountText
                            while (accountControl.parent is View && accountControl.parent !== nameRow) accountControl = accountControl.parent as View
                            if (accountControl.parent === nameRow) resize(accountControl, ViewGroup.LayoutParams.WRAP_CONTENT, dp(32))
                        }
                        val nicknameWidth = if (nameRow != null && accountText != null) ViewGroup.LayoutParams.MATCH_PARENT
                            else (detailsWidth - dp(88)).coerceAtLeast(dp(48))
                        descendants(view).forEach { child ->
                            when (NativeCardBridge.resourceName(child)) {
                                "pr8" -> {
                                    (child.layoutParams as? LinearLayout.LayoutParams)?.let { lp ->
                                        if (lp.weight != 0f) { lp.weight = 0f; child.layoutParams = lp }
                                    }
                                    resize(child, nicknameWidth)
                                }
                                "pr7" -> {
                                    resize(child, ViewGroup.LayoutParams.MATCH_PARENT)
                                    (child as? TextView)?.let {
                                        it.maxLines = 1; it.ellipsize = TextUtils.TruncateAt.END
                                        if (it.textSize != 18f * it.resources.displayMetrics.scaledDensity) it.textSize = 18f
                                    }
                                }
                                "prg" -> {
                                    (child.parent as? View)?.let { parent ->
                                        resize(parent, parent.layoutParams.width, ViewGroup.LayoutParams.WRAP_CONTENT)
                                    }
                                    (child as? TextView)?.let {
                                        it.maxLines = 2; it.ellipsize = TextUtils.TruncateAt.END
                                        if (it.textSize != 14f * it.resources.displayMetrics.scaledDensity) it.textSize = 14f
                                    }
                                }
                            }
                        }
                    }
                    descendants(card).filter { it.javaClass.simpleName == "FlowLayoutContainer" }
                        .forEach { tags ->
                            resize(tags, (cardWidth - dp(32)).coerceAtLeast(1))
                            fitTags(tags)
                            (tags as? ViewGroup)?.let(::adaptTagRow)
                            val height = if (details != null) NativeProfileRows.arrange(card as RelativeLayout, avatar, details, tags, cardWidth)
                                else dp(196)
                            resize(card, cardWidth, height)
                            resizeProfileParents(card, height)
                        }
                }
            if (backgrounds.isNotEmpty()) {
                covers.forEach { it.alpha = 0f }
                collapseCoverSpacer()
            }
        }

        private fun resizeProfileParents(card: View, height: Int) {
            var parent = card.parent as? View
            while (parent != null) {
                val current = parent
                resize(current, current.layoutParams?.width ?: break, height)
                if (current === profile) break
                parent = current.parent as? View
            }
        }

        private fun fitTags(tags: View) {
            descendants(tags).filterIsInstance<TextView>().forEach { text ->
                val textWidth = kotlin.math.ceil(text.paint.measureText(text.text.toString())).toInt() +
                    text.paddingLeft + text.paddingRight + dp(2)
                resize(text, textWidth)
                (text.parent as? LinearLayout)?.let { item ->
                    val siblings = (0 until item.childCount).map(item::getChildAt)
                        .filter { it !== text && it.visibility != View.GONE }
                        .sumOf { it.measuredWidth.coerceAtLeast(0) }
                    resize(item, textWidth + siblings + item.paddingLeft + item.paddingRight + dp(12))
                }
            }
        }

        private fun collapseCoverSpacer() {
            // QQ's scroll child uses a zero-width View as the cover-height anchor.
            // Identify its BELOW-linked sibling structurally instead of guessing another obfuscated id.
            var child: View = content
            for (parent in generateSequence(content.parent as? ViewGroup) { it.parent as? ViewGroup }.take(8)) {
                if (parent.javaClass.simpleName.contains("ScrollView")) break
                val lp = child.layoutParams as? RelativeLayout.LayoutParams
                val anchor = lp?.getRule(RelativeLayout.BELOW)?.takeIf { it > 0 }
                val spacer = coverSpacer?.takeIf { it.parent === parent } ?: (0 until parent.childCount).map(parent::getChildAt).firstOrNull {
                    it.javaClass == View::class.java && it.id == anchor &&
                        it.layoutParams.width == 0 && it.layoutParams.height > dp(96)
                }
                if (spacer != null) {
                    coverSpacer = spacer
                    // Use local, fixed chrome geometry, never coordinates affected by bounce.
                    val bar = descendants(panel).firstOrNull { NativeCardBridge.resourceName(it) == "pqu" }
                    if (spacerHeight == 0 && bar != null && bar.height > 0) spacerHeight = bar.bottom + dp(24)
                    if (spacerHeight > 0) resize(spacer, 0, spacerHeight)
                    return
                }
                child = parent
            }
        }

        private fun resize(view: View, width: Int, height: Int? = null) {
            val lp = view.layoutParams ?: return
            if (lp.width == width && (height == null || lp.height == height)) return
            lp.width = width
            if (height != null) lp.height = height
            view.layoutParams = lp
        }
        private fun dp(value: Int) = (value * panel.resources.displayMetrics.density).toInt()
    }

    private fun nativePanel(part: Any): ViewGroup? {
        for (cls in generateSequence(part.javaClass) { it.superclass }.take(12)) {
            for (field in cls.declaredFields) {
                if (!View::class.java.isAssignableFrom(field.type)) continue
                val view = runCatching { field.isAccessible = true; field.get(part) as? View }.getOrNull() ?: continue
                for (ancestor in generateSequence(view) { it.parent as? View }.take(12)) {
                    val group = ancestor as? ViewGroup ?: continue
                    if (group.javaClass.simpleName == "DrawerFrame") break
                    if ((0 until group.childCount).any {
                            group.getChildAt(it).javaClass.simpleName == "QQSettingMeFloatingViewV3"
                        }) return group
                }
            }
        }
        return null
    }

    private fun descendants(root: View): List<View> {
        val result = mutableListOf<View>()
        fun visit(view: View) {
            if (view.tag == NativeCardBridge.CARD_PANEL_TAG) return
            result += view
            if (view is ViewGroup) for (i in 0 until view.childCount) visit(view.getChildAt(i))
        }
        visit(root)
        return result
    }
}
