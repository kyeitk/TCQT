package com.owo233.tcqt.features.menu.settingmecards

import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.owo233.tcqt.core.env.Toasts
import java.lang.ref.WeakReference

/** Scoped to the actual SettingMe Part root; clicks always use the original bound listener. */
internal class NativeCardBridge(root: ViewGroup) {
    private val root = WeakReference(root)

    private fun find(predicate: (View) -> Boolean): View? {
        fun walk(view: View): View? {
            if (view.tag == CARD_PANEL_TAG) return null
            if (predicate(view)) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                walk(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        return root.get()?.let(::walk)
    }

    private fun text(view: View?): String {
        if (view is TextView) return view.text?.toString()?.trim().orEmpty()
        return view?.let {
            runCatching { it.javaClass.getMethod("getText").invoke(it)?.toString()?.trim() }.getOrNull()
        }.orEmpty()
    }

    fun clickLabel(label: String): Boolean = click(find { text(it) == label })

    private fun click(source: View?): Boolean {
        fun clickableChild(view: View): View? {
            if (view.hasOnClickListeners() && view.isEnabled) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                clickableChild(view.getChildAt(i))?.let { return it }
            }
            return null
        }
        var view = source?.let(::clickableChild) ?: source
        val limit = root.get()
        while (view != null && view !== limit) {
            if (view.hasOnClickListeners() && view.isEnabled && view.performClick()) return true
            view = view.parent as? View
        }
        Toasts.info("当前页面尚未加载此入口，请稍后再试")
        return false
    }

    companion object {
        const val CARD_PANEL_TAG = "tcqt_settingme_cards"
        fun resourceName(view: View): String = if (view.id == View.NO_ID) "" else
            runCatching { view.resources.getResourceEntryName(view.id) }.getOrDefault("")
    }
}
