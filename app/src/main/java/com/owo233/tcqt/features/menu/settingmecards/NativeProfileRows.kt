package com.owo233.tcqt.features.menu.settingmecards

import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.FrameLayout
import android.widget.RelativeLayout
import android.widget.TextView

/** One content grid, using QQ's existing profile containers and bound actions. */
internal object NativeProfileRows {
    fun arrange(card: RelativeLayout, avatar: View, details: View, tags: View, width: Int): Int {
        fun dp(value: Int) = (value * card.resources.displayMetrics.density).toInt()
        val inset = dp(NativeMiuixStyle.ContentInset)
        val contentWidth = (width - inset * 2).coerceAtLeast(1)
        fun find(name: String) = descendants(card).firstOrNull { NativeCardBridge.resourceName(it) == name }
        val topInset = dp(24)
        place(card, avatar, RelativeLayout.LayoutParams(dp(64), dp(64)).apply {
            addRule(RelativeLayout.ALIGN_PARENT_START); marginStart = inset; topMargin = topInset
        })
        details.minimumHeight = dp(64)
        place(card, details, RelativeLayout.LayoutParams((contentWidth - dp(76)).coerceAtLeast(1), -2).apply {
            addRule(RelativeLayout.END_OF, avatar.id)
            marginStart = dp(12); marginEnd = inset; topMargin = topInset
        })
        val signature = find("pri")
        val grade = find("pr4")
        // QQ's Parts own these children. Keep the native column and its binding hierarchy.
        for (row in listOfNotNull(signature, grade)) {
            if (row.parent === details && row.layoutParams is LinearLayout.LayoutParams) {
                val old = row.layoutParams as LinearLayout.LayoutParams
                if (old.width != -1 || old.topMargin != dp(8)) {
                    row.layoutParams = LinearLayout.LayoutParams(old).apply {
                        this.width = -1; this.height = -2; topMargin = dp(8)
                    }
                }
            }
        }
        find("prg")?.let { child ->
            (child as? TextView)?.apply {
                gravity = Gravity.START or Gravity.CENTER_VERTICAL
                includeFontPadding = false; isSingleLine = false; maxLines = 2; ellipsize = TextUtils.TruncateAt.END; textSize = 14f
            }
            if (child.parent === signature) {
                val lp = RelativeLayout.LayoutParams(-1, -2).apply {
                    addRule(RelativeLayout.ALIGN_PARENT_START)
                }
                if (!same(child.layoutParams, lp)) child.layoutParams = lp
            }
        }
        (grade as? LinearLayout)?.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        find("prd")?.let { value ->
            (value as? TextView)?.gravity = Gravity.START or Gravity.CENTER_VERTICAL
            val lp = value.layoutParams
            val available = (contentWidth - dp(76)).coerceAtLeast(1)
            val gradeHeight = dp(26)
            if (lp.width != available || lp.height != gradeHeight) {
                lp.width = available; lp.height = gradeHeight; value.layoutParams = lp
            }
        }
        place(card, tags, RelativeLayout.LayoutParams(contentWidth, tags.layoutParams.height).apply {
            addRule(RelativeLayout.BELOW, details.id); addRule(RelativeLayout.ALIGN_PARENT_START)
            leftMargin = inset; marginStart = inset; rightMargin = inset; marginEnd = inset; topMargin = dp(12)
        })
        val height = topInset + maxOf(dp(64), details.measuredHeight) + dp(12) + tags.measuredHeight

        val children = (0 until card.childCount).map(card::getChildAt)
        val notice = children.firstOrNull { descendants(it).filterIsInstance<TextView>().any { text -> text.text.toString() == "通知" } }
        val likes = children.firstOrNull { it.id == 0x7f0a93f8 && it !== notice }
        val show = children.firstOrNull { it.id == 0x7f0a035e || descendants(it).filterIsInstance<TextView>().any { text -> text.text.toString().contains("QQ秀") } }
        val actionHeight = dp(44)
        val leading = notice ?: likes
        val wrapShow = leading != null && show != null &&
            listOfNotNull(notice, likes, show).sumOf { it.measuredWidth } + dp(24) > contentWidth
        fun action(view: View?, start: Boolean = false, end: Boolean = false, after: View? = null) {
            if (view == null) return
            place(card, view, RelativeLayout.LayoutParams(-2, actionHeight).apply {
                addRule(RelativeLayout.BELOW, if (view === show && wrapShow) leading!!.id else tags.id); topMargin = dp(12)
                if (start) { addRule(RelativeLayout.ALIGN_PARENT_START); leftMargin = inset; marginStart = inset }
                if (end) { addRule(RelativeLayout.ALIGN_PARENT_END); rightMargin = inset; marginEnd = inset }
                if (after != null) { addRule(RelativeLayout.END_OF, after.id); leftMargin = dp(12); marginStart = dp(12) }
            })
            // Avoid a legacy centered text field introducing invisible leading space.
            descendants(view).filterIsInstance<TextView>().forEach { it.gravity = Gravity.START or Gravity.CENTER_VERTICAL }
            if (view is ViewGroup) for (i in 0 until view.childCount) {
                val child = view.getChildAt(i)
                when (val old = child.layoutParams) {
                    is RelativeLayout.LayoutParams -> {
                        val lp = RelativeLayout.LayoutParams(old).apply {
                            removeRule(RelativeLayout.ALIGN_PARENT_TOP); removeRule(RelativeLayout.ALIGN_PARENT_BOTTOM)
                            addRule(RelativeLayout.CENTER_VERTICAL)
                            topMargin = 0; bottomMargin = 0
                        }
                        if (!same(old, lp)) child.layoutParams = lp
                    }
                    is FrameLayout.LayoutParams -> {
                        val gravity = (if (old.gravity < 0) Gravity.START else old.gravity and Gravity.VERTICAL_GRAVITY_MASK.inv()) or Gravity.CENTER_VERTICAL
                        if (old.gravity != gravity) { old.gravity = gravity; child.layoutParams = old }
                    }
                }
            }
        }
        action(notice, start = true)
        action(likes, start = notice == null, after = notice)
        action(show, end = true)
        find("pr2")?.let { divider ->
            place(card, divider, RelativeLayout.LayoutParams(dp(1), dp(14)).apply {
                addRule(RelativeLayout.BELOW, tags.id); topMargin = dp(27)
                val leading = notice ?: likes
                if (leading != null) addRule(RelativeLayout.END_OF, leading.id)
                marginStart = dp(6); leftMargin = dp(6)
            })
        }
        return height + dp(12) + actionHeight + inset + if (wrapShow) dp(12) + actionHeight else 0
    }

    private fun place(card: RelativeLayout, view: View, lp: RelativeLayout.LayoutParams) {
        if (view.parent === card && !same(view.layoutParams, lp)) view.layoutParams = lp
    }

    private fun same(old: ViewGroup.LayoutParams, next: RelativeLayout.LayoutParams): Boolean {
        val lp = old as? RelativeLayout.LayoutParams ?: return false
        next.resolveLayoutDirection(lp.layoutDirection)
        return lp.width == next.width && lp.height == next.height && lp.leftMargin == next.leftMargin &&
            lp.rightMargin == next.rightMargin && lp.topMargin == next.topMargin && lp.bottomMargin == next.bottomMargin &&
            lp.marginStart == next.marginStart && lp.marginEnd == next.marginEnd && lp.rules.contentEquals(next.rules)
    }

    private fun descendants(view: View): List<View> = buildList {
        fun visit(child: View) {
            add(child)
            if (child is ViewGroup) for (i in 0 until child.childCount) visit(child.getChildAt(i))
        }
        visit(view)
    }
}
