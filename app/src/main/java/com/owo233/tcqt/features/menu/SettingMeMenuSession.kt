package com.owo233.tcqt.features.menu

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.ViewTreeObserver
import android.widget.ImageView
import android.graphics.drawable.Drawable
import android.widget.LinearLayout
import android.widget.TextView
import android.util.TypedValue
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.ModuleComponents
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.Log
import com.owo233.tcqt.host.QQInterfaces
import com.owo233.tcqt.features.menu.settingmecards.NativePanelReflow
import com.owo233.tcqt.features.menu.settingmecards.NativeCoverAdaptation
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Album
import top.yukonga.miuix.kmp.icon.extended.BankCards
import top.yukonga.miuix.kmp.icon.extended.CloudFill
import top.yukonga.miuix.kmp.icon.extended.Favorites
import top.yukonga.miuix.kmp.icon.extended.File
import top.yukonga.miuix.kmp.icon.extended.GridView
import top.yukonga.miuix.kmp.icon.extended.Settings
import top.yukonga.miuix.kmp.icon.extended.Store
import top.yukonga.miuix.kmp.icon.extended.Theme
import java.util.WeakHashMap
import kotlin.math.roundToInt

data class SettingMeUiConfig(
    val widthPercent: Int,
    val cornerRadiusDp: Int,
    val panelClip: Boolean,
    val showTopPunch: Boolean,
    val showTopStatus: Boolean,
    val showTopQqShow: Boolean,
    val showTopReturn: Boolean,
    val menuIconStyle: Int,
    val bottomBarStyle: Int,
    val cardReflow: Boolean = false,
    val showBackground: Boolean = true,
    val cardLayout: String = "",
    val weatherCity: String = "北京,39.9042,116.4074",
    val saveCardLayout: (String) -> Unit = {},
    val readCardLayout: () -> String = { cardLayout },
) {
    companion object {
        const val ICON_NATIVE = 1
        const val ICON_MIUIX = 2
        const val BOTTOM_NATIVE = 1
        const val BOTTOM_MIUIX = 2
    }
}

/**
 * 侧滑栏运行时会话：缓存 QQSettingMe 部件、根视图与底部按钮行，
 * 并为后续整页修改提供统一入口。
 */
object SettingMeMenuSession {

    private const val TAG = "SettingMeMenuSession"
    private const val ENTRY_TAG = "tcqt_module_settings_entry"
    private const val MIUIX_ICON_TAG = "tcqt_miuix_icon"
    private const val BOTTOM_BAR_RESOURCE = "pnv"
    private const val MENU_ICON_IMAGE_RESOURCE = "po5"
    private const val MENU_ICON_LABEL_RESOURCE = "po_"
    private const val BOTTOM_ICON_RESOURCE = "pp2"
    private const val BOTTOM_WEATHER_ICON_RESOURCE = "pqd"
    private const val TOP_BAR_RESOURCE = "pph"
    private const val TOP_PUNCH_RESOURCE = "pp9"
    private const val TOP_STATUS_RESOURCE = "wv0"
    private const val TOP_QQSHOW_RESOURCE = "poy"
    private const val TOP_RETURN_RESOURCE = "pnt"

    private val sessions = WeakHashMap<Any, Session>()
    private val clipListeners = WeakHashMap<View, ViewTreeObserver.OnDrawListener>()
    private val iconDrawables = WeakHashMap<androidx.compose.ui.graphics.vector.ImageVector, Drawable>()
    private var layoutHooked = false

    @Volatile
    private var config: SettingMeUiConfig? = null

    fun configure(config: SettingMeUiConfig) {
        this.config = config
        installLayoutHook()
        if (config.cardReflow) NativeCoverAdaptation.install()
    }

    fun ensureModuleSettingsEntry(part: Any) {
        val session = session(part)
        val root = session.root() ?: return
        val cfg = config ?: return

        applyGeometry(root, cfg)
        attachClipListener(root, session)
        applyTopShortcutVisibility(root, cfg)
        root.postDelayed({ applyMenuIcons(root, cfg) }, 200L)
        if (cfg.cardReflow) root.postDelayed({
            runCatching { NativePanelReflow.attach(part, cfg) }
                .onFailure { Log.w("$TAG 原栏目适配失败，保留原页面: $it") }
        }, 250L)

        val bar = session.bottomBar() ?: run {
            root.postDelayed({ retryEnsure(session, 0) }, 200L)
            return
        }
        session.ensureEntry(bar)
        styleBottomBar(bar, cfg)
        root.postDelayed({ styleBottomBar(bar, cfg) }, 200L)
    }

    private fun retryEnsure(session: Session, attempt: Int) {
        if (attempt > 10) return
        val root = session.root() ?: return
        val cfg = config ?: return
        val bar = session.bottomBar() ?: run {
            root.postDelayed({ retryEnsure(session, attempt + 1) }, 200L)
            return
        }
        session.ensureEntry(bar)
        styleBottomBar(bar, cfg)
        applyGeometry(root, cfg)
        attachClipListener(root, session)
        applyTopShortcutVisibility(root, cfg)
        root.postDelayed({ applyMenuIcons(root, cfg) }, 200L)
    }

    private fun session(part: Any): Session =
        sessions[part] ?: Session(part).also { sessions[part] = it }

    private class Session(private val part: Any) {

        private var rootView: View? = null
        private var bottomBar: ViewGroup? = null

        fun root(): View? {
            rootView?.let {
                if (it.isAttachedToWindow) return it
            }
            rootView = findRootFromPart(part)
            return rootView
        }

        fun bottomBar(): ViewGroup? {
            bottomBar?.let {
                if (it.isAttachedToWindow && it.childCount >= 3) return it
            }
            val root = root() ?: return null
            bottomBar = findBottomBar(root)
            return bottomBar
        }

        fun ensureEntry(bar: ViewGroup): View? {
            bar.findViewWithTag<View>(ENTRY_TAG)?.let { return it }
            val template = bar.getChildAt(0)
            val templateLp = template?.layoutParams as? LinearLayout.LayoutParams
            val templateLabel = template?.let {
                findView(it) { child -> child is TextView && child.text.toString().trim() == "设置" } as? TextView
            }
            val templateIcon = template?.let { findView(it) { child -> child is ImageView } as? ImageView }
            val density = bar.resources.displayMetrics.density
            val width = template?.width?.takeIf { it > 0 } ?: (58 * density).roundToInt()
            val height = template?.height?.takeIf { it > 0 } ?: (54 * density).roundToInt()

            val label = TextView(bar.context).apply {
                text = "模块"
                gravity = Gravity.CENTER
                textSize = 14f
                maxLines = 1
                setTextColor(if (HookEnv.isNightMode()) 0xFFF1F1F1.toInt() else 0xFF333333.toInt())
            }
            val image = ImageView(bar.context).apply {
                importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
            }
            val button = LinearLayout(bar.context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                contentDescription = "模块设置"
                tag = ENTRY_TAG
                isClickable = true
                isFocusable = true
                val iconSize = (24 * density).roundToInt()
                image.setImageDrawable(MiuixIconRenderer.toDrawable(context, MiuixIcons.GridView, iconSize))
                addView(image, LinearLayout.LayoutParams(iconSize, iconSize))
                addView(label, LinearLayout.LayoutParams(-2, -2))
                setOnClickListener { openModuleSettings() }
            }
            button.layoutParams = LinearLayout.LayoutParams(width, height).apply {
                leftMargin = templateLp?.leftMargin ?: (19 * bar.resources.displayMetrics.density).roundToInt()
                topMargin = templateLp?.topMargin ?: 0
            }
            bar.addView(button)
            // Bind after native measurement: the first TextView may be an invisible red dot,
            // and QQ may change resource density before its original slots are measured.
            fun syncSlots() {
                val group = template as? ViewGroup ?: return
                val nativeIcon = templateIcon ?: return
                val nativeLabel = templateLabel ?: return
                if (group.width <= 0 || group.height <= 0 || nativeIcon.width <= 0 || nativeLabel.height <= 0) return
                val iconRect = Rect(0, 0, nativeIcon.width, nativeIcon.height)
                val labelRect = Rect(0, 0, nativeLabel.width, nativeLabel.height)
                group.offsetDescendantRectToMyCoords(nativeIcon, iconRect)
                group.offsetDescendantRectToMyCoords(nativeLabel, labelRect)
                val rootLp = button.layoutParams as LinearLayout.LayoutParams
                if (rootLp.width != group.width || rootLp.height != group.height) {
                    rootLp.width = group.width; rootLp.height = group.height
                    button.layoutParams = rootLp
                }
                val iconLp = image.layoutParams as LinearLayout.LayoutParams
                if (iconLp.width != nativeIcon.width || iconLp.height != nativeIcon.height || iconLp.topMargin != iconRect.top) {
                    iconLp.width = nativeIcon.width; iconLp.height = nativeIcon.height; iconLp.topMargin = iconRect.top
                    image.layoutParams = iconLp
                    image.setImageDrawable(MiuixIconRenderer.toDrawable(bar.context, MiuixIcons.GridView, nativeIcon.width))
                }
                label.setTextSize(TypedValue.COMPLEX_UNIT_PX, nativeLabel.textSize)
                label.includeFontPadding = nativeLabel.includeFontPadding
                val labelLp = label.layoutParams as LinearLayout.LayoutParams
                val gap = (labelRect.top - iconRect.bottom).coerceAtLeast(0)
                if (labelLp.height != nativeLabel.height || labelLp.topMargin != gap) {
                    labelLp.height = nativeLabel.height; labelLp.topMargin = gap
                    label.layoutParams = labelLp
                }
            }
            template?.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> syncSlots() }
            button.post { syncSlots() }
            Log.i("$TAG 模块设置入口已注入 bottomBar=${bar.javaClass.name} childCount=${bar.childCount}")
            return button
        }
    }

    private fun installLayoutHook() {
        if (layoutHooked) return
        layoutHooked = true
        View::class.java
            .getMethod("layout", Integer.TYPE, Integer.TYPE, Integer.TYPE, Integer.TYPE)
            .hookBefore { param ->
                val view = param.thisObject as? View ?: return@hookBefore
                if (view.javaClass.name != "com.tencent.mobileqq.widget.QQSettingMeRelativeLayout") return@hookBefore
                val cfg = config ?: return@hookBefore
                if (!cfg.panelClip) return@hookBefore
                val left = param.args[0] as Int
                val right = param.args[2] as Int
                val targetWidth = ((right - left) * cfg.widthPercent / 100).coerceAtLeast(1)
                param.args[2] = left + targetWidth
            }
    }
    private fun attachClipListener(root: View, session: Session) {
        clipListeners[root]?.let { return }
        val listener = ViewTreeObserver.OnDrawListener {
            config?.let { cfg ->
                applyGeometry(root, cfg)
                applyTopShortcutVisibility(root, cfg)
                if (cfg.cardReflow) NativeCoverAdaptation.refreshTopForeground(root)
                applyMenuIcons(root, cfg)
                session.bottomBar()?.let { styleBottomBar(it, cfg) }
            }
        }
        clipListeners[root] = listener
        runCatching { root.viewTreeObserver.addOnDrawListener(listener) }
    }

    private fun applyGeometry(root: View, cfg: SettingMeUiConfig) {
        if (!cfg.panelClip) return
        if (root.width <= 0 || root.height <= 0) return
        val targets = linkedSetOf<View>()
        targets += root
        findViews(root) { view ->
            view.javaClass.name == "com.tencent.mobileqq.widget.QQSettingMeFloatingViewV3" ||
            view.javaClass.name == "com.tencent.mobileqq.widget.QQSettingMeRelativeLayout"
        }.forEach { targets += it }
        targets.forEach { applyGeometryToView(it, cfg) }
    }

    private fun applyGeometryToView(view: View, cfg: SettingMeUiConfig) {
        (view as? ViewGroup)?.let {
            it.clipChildren = true
            it.clipToPadding = true
        }
        if (view.width <= 0 || view.height <= 0) return
        val targetWidth = (view.width * cfg.widthPercent / 100).coerceAtLeast(1)
        val targetHeight = view.height
        val current = view.clipBounds
        if (current == null || current.right != targetWidth || current.bottom != targetHeight) {
            view.clipBounds = Rect(0, 0, targetWidth, targetHeight)
        }
        if (cfg.cornerRadiusDp > 0) {
            val radius = (cfg.cornerRadiusDp * view.resources.displayMetrics.density).roundToInt()
            view.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(v: View, outline: android.graphics.Outline) {
                    outline.setRoundRect(0, 0, targetWidth, v.height, radius.toFloat())
                }
            }
            view.clipToOutline = true
        }
    }

    private fun applyTopShortcutVisibility(root: View, cfg: SettingMeUiConfig) {
        setShortcutVisibilityById(root, TOP_PUNCH_RESOURCE, cfg.showTopPunch)
        setShortcutVisibilityById(root, TOP_STATUS_RESOURCE, cfg.showTopStatus)
        setShortcutVisibilityById(root, TOP_QQSHOW_RESOURCE, cfg.showTopQqShow)
        setShortcutVisibilityById(root, TOP_RETURN_RESOURCE, cfg.showTopReturn)
    }

    private fun setShortcutVisibilityById(root: View, resourceId: String, visible: Boolean) {
        val view = findView(root) { child ->
            child.id != View.NO_ID &&
                    runCatching { child.resources.getResourceEntryName(child.id) }.getOrNull() == resourceId
        } ?: return
        view.visibility = if (visible) View.VISIBLE else View.GONE
    }


    internal fun applyMenuIcons(root: View, cfg: SettingMeUiConfig) {
        if (cfg.menuIconStyle != SettingMeUiConfig.ICON_MIUIX) return
        var replaced = false
        val iconImages = findViews(root) { view ->
            view is ImageView && resourceName(view) == MENU_ICON_IMAGE_RESOURCE
        }
        if (iconImages.isNotEmpty()) {
            iconImages.forEach { view ->
                val image = view as? ImageView ?: return@forEach
                val row = generateSequence(image.parent as? ViewGroup) { it.parent as? ViewGroup }
                    .take(4).firstOrNull { parent ->
                        findView(parent) { it is TextView && resourceName(it) == MENU_ICON_LABEL_RESOURCE } != null
                    } ?: return@forEach
                val label = findView(row) { child ->
                    child is TextView && resourceName(child) == MENU_ICON_LABEL_RESOURCE
                }?.let { (it as? TextView)?.text?.toString()?.trim() } ?: return@forEach
                val icon = MENU_ICON_MAP[label] ?: return@forEach
                replaceIcon(image, icon)
                replaced = true
            }
            if (replaced) return
        }
        MENU_ICON_MAP.forEach { (label, icon) ->
            val labeled = findView(root) { view ->
                (view as? TextView)?.text?.toString()?.trim() == label
            } ?: return@forEach
            val row = generateSequence(labeled.parent as? ViewGroup) { it.parent as? ViewGroup }
                .take(4).firstOrNull { parent ->
                    findView(parent) { it is ImageView } != null &&
                        findViews(parent) { it is TextView && it.text.toString().trim() in MENU_ICON_MAP }.size == 1
                } ?: return@forEach
            val image = findView(row) { it is ImageView } as? ImageView ?: return@forEach
            replaceIcon(image, icon)
        }
    }

    private fun findViews(root: View, predicate: (View) -> Boolean): List<View> {
        val result = mutableListOf<View>()
        fun visit(view: View) {
            if (predicate(view)) result += view
            if (view is ViewGroup) {
                for (i in 0 until view.childCount) visit(view.getChildAt(i))
            }
        }
        visit(root)
        return result
    }

    private fun resourceName(view: View): String {
        if (view.id == View.NO_ID) return "-"
        return runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() ?: "-"
    }

    private fun styleBottomBar(bar: ViewGroup, cfg: SettingMeUiConfig) {
        bar.findViewWithTag<ViewGroup>(ENTRY_TAG)?.let { entry ->
            (entry.getChildAt(1) as? TextView)?.setTextColor(
                if (HookEnv.isNightMode()) 0xFFF1F1F1.toInt() else 0xFF333333.toInt())
            (entry.getChildAt(0) as? ImageView)?.setColorFilter(
                if (HookEnv.isNightMode()) 0xFFDDDDDD.toInt() else 0xFF444444.toInt())
        }
        if (cfg.bottomBarStyle != SettingMeUiConfig.BOTTOM_MIUIX) return
        replaceBottomIcon(bar, "设置", MiuixIcons.Settings)
        replaceBottomIconById(bar, BOTTOM_WEATHER_ICON_RESOURCE, MiuixIcons.CloudFill)
        bar.alpha = 0.96f
    }

    private fun replaceBottomIcon(bar: ViewGroup, label: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        val labeled = findView(bar) { view ->
            (view as? TextView)?.text?.toString()?.trim() == label
        } ?: return
        val row = rowContainer(labeled) ?: return
        val image = findView(row) { child ->
            child is ImageView && resourceName(child) == BOTTOM_ICON_RESOURCE
        } as? ImageView ?: return
        replaceIcon(image, icon)
    }


    private fun replaceBottomIconById(bar: ViewGroup, resourceId: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        val image = findView(bar) { child ->
            child is ImageView && resourceName(child) == resourceId
        } as? ImageView ?: return
        replaceIcon(image, icon)
    }

    private fun replaceIcon(image: ImageView, icon: androidx.compose.ui.graphics.vector.ImageVector) {
        val desired = iconDrawables[icon] ?: run {
            val density = image.resources.displayMetrics.density
            val size = image.width.takeIf { it > 0 } ?: (56 * density).roundToInt()
            val drawable = MiuixIconRenderer.toDrawable(image.context, icon, size)
            iconDrawables[icon] = drawable
            drawable
        }
        desired.setTint(if (HookEnv.isNightMode()) 0xFFDDDDDD.toInt() else 0xFF444444.toInt())
        if (image.drawable !== desired) image.setImageDrawable(desired)
        image.clearColorFilter()
        image.imageTintList = ColorStateList.valueOf(
            if (HookEnv.isNightMode()) 0xFFDDDDDD.toInt() else 0xFF444444.toInt())
    }

    private fun rowContainer(labelView: View): ViewGroup? {
        var parent = labelView.parent
        while (parent is ViewGroup) {
            if (parent.isClickable && parent.childCount > 0) return parent
            parent = parent.parent
        }
        return null
    }

    private fun findRootFromPart(part: Any): View? {
        var cls: Class<*>? = part.javaClass
        var depth = 0
        while (cls != null && cls != Any::class.java && depth < 4) {
            for (field in cls.declaredFields) {
                if (!View::class.java.isAssignableFrom(field.type)) continue
                val value = runCatching {
                    field.isAccessible = true
                    field.get(part)
                }.getOrNull() as? View ?: continue
                var node: View = value
                var parent = node.parent as? ViewGroup
                var up = 0
                while (parent != null && up < 12) {
                    node = parent
                    parent = node.parent as? ViewGroup
                    up++
                }
                return node
            }
            cls = cls.superclass
            depth++
        }
        return null
    }

    private fun findBottomBar(root: View): ViewGroup? {
        findView(root) { view ->
            view.id != View.NO_ID && view is ViewGroup &&
                    runCatching { view.resources.getResourceEntryName(view.id) }.getOrNull() ==
                    BOTTOM_BAR_RESOURCE
        }?.let { return it as? ViewGroup }

        findView(root) { view ->
            view is LinearLayout &&
                    view.childCount == 3 &&
                    hasDescendantText(view, "设置") &&
                    hasDescendantText(view, "夜间")
        }?.let { return it as? ViewGroup }

        findView(root) { view ->
            view.javaClass.simpleName.contains("FloatingView", ignoreCase = true) ||
                    view.javaClass.simpleName.contains("SettingMe", ignoreCase = true)
        }?.let { floating ->
            findView(floating) { view ->
                view is LinearLayout &&
                        view.childCount >= 3 &&
                        hasDescendantText(view, "设置") &&
                        (hasDescendantText(view, "夜间") || hasDescendantText(view, "日间"))
            }?.let { return it as? ViewGroup }
        }
        return null
    }

    private fun hasDescendantText(root: View, expected: String): Boolean =
        findView(root) { view ->
            (view as? TextView)?.text?.toString()?.trim() == expected
        } != null

    private fun findView(root: View, predicate: (View) -> Boolean): View? {
        if (predicate(root)) return root
        if (root is ViewGroup) {
            for (i in 0 until root.childCount) {
                findView(root.getChildAt(i), predicate)?.let { return it }
            }
        }
        return null
    }

    private fun openModuleSettings() {
        runCatching {
            val activity = QQInterfaces.topActivity
            val activityClass =
                HookEnv.moduleClassLoader.loadClass(ModuleComponents.SETTINGS_ACTIVITY)
            activity.startActivity(Intent(activity, activityClass))
        }.onFailure {
            Log.w("$TAG 打开模块设置失败: $it")
        }
    }

    private val MENU_ICON_MAP: Map<String, androidx.compose.ui.graphics.vector.ImageVector> = mapOf(
        "相册" to MiuixIcons.Album,
        "收藏" to MiuixIcons.Favorites,
        "文件" to MiuixIcons.File,
        "钱包" to MiuixIcons.BankCards,
        "会员中心" to MiuixIcons.Store,
        "个性装扮" to MiuixIcons.Theme,
    )
}

