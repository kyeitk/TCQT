package com.owo233.tcqt.features.menu.settingmecards

import android.graphics.drawable.GradientDrawable
import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.ImageView
import com.owo233.tcqt.core.env.HookEnv
import com.owo233.tcqt.core.env.toClass
import com.owo233.tcqt.core.hook.hookBefore
import com.owo233.tcqt.core.log.Log
import androidx.compose.ui.graphics.toArgb
import java.util.WeakHashMap

/** Only the verified native card mode uses these cover and shortcut adjustments. */
internal object NativeCoverAdaptation {
    private val blurViews = WeakHashMap<View, Boolean>()
    private val lightForegroundFilter = PorterDuffColorFilter(0xFF333333.toInt(), PorterDuff.Mode.SRC_IN)
    private val darkForegroundFilter = PorterDuffColorFilter(0xFFF1F1F1.toInt(), PorterDuff.Mode.SRC_IN)
    private var installed = false

    fun install() {
        if (installed || HookEnv.versionCode != 16410L) return
        installed = true
        runCatching {
            // The fused background reads the raw drawable independently. Leave QQ's
            // position observers and restore animation intact, even when the cover is hidden.
            "com.tencent.qui.quiblurview.QQBlurViewWrapper".toClass
                .getDeclaredMethod("setEnableBlur", Boolean::class.javaPrimitiveType).hookBefore {
                    if (blurViews.containsKey(it.thisObject as? View)) it.args[0] = false
                }
            "com.tencent.qui.quiblurview.QQBlurViewWrapper".toClass
                .getDeclaredMethod("setOverlayDrawable", android.graphics.drawable.Drawable::class.java).hookBefore {
                    if (blurViews.containsKey(it.thisObject as? View)) it.args[0] = null
                }
        }.onFailure { Log.w("SettingMeNativeCover 原生封面适配未安装: ${it.javaClass.simpleName}") }
    }

    fun adaptTopBar(panel: View, width: Int) {
        if (HookEnv.versionCode != 16410L) return
        fun descendants(view: View): Sequence<View> = sequence {
            yield(view)
            if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
        }
        // 9.3.70 NavigationPart binds this inset RelativeLayout; pph is an unrelated menu icon.
        val bar = descendants(panel).firstOrNull { NativeCardBridge.resourceName(it) == "pqu" }
            ?: return
        bar.layoutParams?.let { lp ->
            val margins = lp as? ViewGroup.MarginLayoutParams
            val available = (width - (margins?.leftMargin ?: 0) - (margins?.rightMargin ?: 0)).coerceAtLeast(1)
            if (lp.width != available) { lp.width = available; bar.layoutParams = lp }
        }
        val dark = HookEnv.isNightMode()
        val color = if (dark) 0xFF303030.toInt() else NativeMiuixStyle.colors(false).surfaceContainerHigh.toArgb()
        descendants(bar).filter { it.javaClass.simpleName == "QQBlurViewWrapper" }.forEach { blur ->
            if (blurViews[blur] == dark) return@forEach
            blurViews[blur] = dark
            runCatching {
                blur.javaClass.getMethod("setEnableBlur", Boolean::class.javaPrimitiveType).invoke(blur, false)
                blur.javaClass.getMethod("setOverlayDrawable", android.graphics.drawable.Drawable::class.java)
                    .invoke(blur, null)
                blur.javaClass.getMethod("setDisableDrawable", android.graphics.drawable.Drawable::class.java)
                    .invoke(blur, GradientDrawable().apply { setColor(color) })
            }.onFailure { Log.w("SettingMeNativeCover 顶部背景适配失败: ${it.javaClass.simpleName}") }
        }
        refreshTopForeground(panel)
    }

    fun refreshTopForeground(panel: View) {
        if (HookEnv.versionCode != 16410L) return
        fun visit(view: View, block: (View) -> Unit) {
            block(view)
            if (view is ViewGroup) for (index in 0 until view.childCount) visit(view.getChildAt(index), block)
        }
        var bar: View? = null
        visit(panel) { if (NativeCardBridge.resourceName(it) == "pqu") bar = it }
        val foreground = if (HookEnv.isNightMode()) 0xFFF1F1F1.toInt() else 0xFF333333.toInt()
        val filter = if (HookEnv.isNightMode()) darkForegroundFilter else lightForegroundFilter
        bar?.let { root -> visit(root) { child ->
            when (child) {
                is TextView -> {
                    if (child.currentTextColor != foreground) child.setTextColor(foreground)
                    if (child.compoundDrawableTintList?.defaultColor != foreground)
                        child.compoundDrawableTintList = ColorStateList.valueOf(foreground)
                    child.compoundDrawablesRelative.filterNotNull().forEach { drawable ->
                        if (drawable.colorFilter != filter) drawable.colorFilter = filter
                    }
                }
                is ImageView -> {
                    if (child.imageTintList?.defaultColor != foreground)
                        child.imageTintList = ColorStateList.valueOf(foreground)
                    if (child.colorFilter != filter) child.colorFilter = filter
                    child.drawable?.let { drawable ->
                        if (drawable.colorFilter != filter) drawable.colorFilter = filter
                    }
                }
            }
        } }
    }
}
