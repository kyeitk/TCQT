package com.owo233.tcqt.features.menu.settingmecards

import android.app.AlertDialog
import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import android.os.Bundle
import android.os.Build
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.owo233.tcqt.core.env.CommonContextWrapper
import com.owo233.tcqt.ui.component.TCQTDialogLifecycleOwner
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.TabRowDefaults
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.theme.MiuixTheme
import com.owo233.tcqt.features.menu.MiuixIconRenderer
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Sort
import kotlin.math.abs
import kotlin.math.roundToInt

/** All interactions change a draft. Handle gestures stay inside this dialog. */
internal class NativeWidgetEditor(
    hostContext: Context,
    private val saved: CardLayout,
    private val dark: Boolean,
    private val onSave: (CardLayout) -> Unit,
) {
    // QQ may adjust its DisplayMetrics in place. Both themes need the same fresh
    // resources for native rows, Compose controls and the dialog window.
    private val context = CommonContextWrapper(hostContext,
        if (dark) androidx.appcompat.R.style.Theme_AppCompat else androidx.appcompat.R.style.Theme_AppCompat_Light,
        Configuration(hostContext.resources.configuration).apply {
            densityDpi = Resources.getSystem().displayMetrics.densityDpi
            uiMode = (uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
                if (dark) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO
        })
    private val colors = NativeMiuixStyle.colors(dark)
    private val foreground = colors.onSurfaceContainer.toArgb()
    private val secondary = colors.onSurfaceVariantSummary.toArgb()
    private val accent = colors.primary.toArgb()
    private val surface = colors.surfaceContainer.toArgb()
    private val itemSurface = (if (dark) colors.surfaceContainerHighest else colors.surface).toArgb()
    private val moveUpAction = 0x03000000 or View.generateViewId()
    private val moveDownAction = 0x03000000 or View.generateViewId()
    private var draft = saved
    private var dragging: CardKind? = null
    private var targetKind: CardKind? = null
    private var closed = false
    private val rows = linkedMapOf<CardKind, FrameLayout>()
    private val editor = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(8), dp(16), dp(8))
    }
    private val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    private val owner = TCQTDialogLifecycleOwner()
    private val composeViews = mutableListOf<ComposeView>()

    fun show() {
        owner.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        editor.addView(text("拖动名称左侧的图标调整顺序", 12, secondary).apply {
            setPadding(dp(NativeMiuixStyle.ContentInset), 0, dp(NativeMiuixStyle.ContentInset), dp(12))
        })
        editor.addView(ScrollView(context).apply { addView(list) })
        renderRows()
        val dialog = AlertDialog.Builder(context)
            .setCustomTitle(text("编辑小组件", 20, foreground).apply {
                setPadding(dp(NativeMiuixStyle.ContentInset * 2), dp(24), dp(NativeMiuixStyle.ContentInset * 2), dp(8))
            }).setView(editor)
            .setNegativeButton("取消", null)
            .setPositiveButton("保存") { _, _ -> onSave(draft) }
            .create()
        // WindowRecomposer searches the dialog's content root, beyond an individual ComposeView.
        editor.setViewTreeLifecycleOwner(owner)
        editor.setViewTreeSavedStateRegistryOwner(owner)
        dialog.window?.decorView?.let {
            it.setViewTreeLifecycleOwner(owner)
            it.setViewTreeSavedStateRegistryOwner(owner)
        }
        dialog.setOnDismissListener {
            closed = true
            clearDrag(); draft = saved
            composeViews.forEach { it.disposeComposition() }
            owner.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
            owner.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        }
        dialog.show()
        owner.handleLifecycleEvent(Lifecycle.Event.ON_START)
        owner.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        dialog.window?.setBackgroundDrawable(shape(surface, NativeMiuixStyle.DialogCorner))
        val displayWidth = if (Build.VERSION.SDK_INT >= 30)
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).currentWindowMetrics.bounds.width()
        else context.resources.displayMetrics.widthPixels
        dialog.window?.setLayout((displayWidth - dp(48)).coerceAtLeast(dp(160)).coerceAtMost(dp(420)),
            WindowManager.LayoutParams.WRAP_CONTENT)
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(accent)
        dialog.getButton(AlertDialog.BUTTON_NEGATIVE).setTextColor(secondary)
    }

    private fun renderRows() {
        composeViews.forEach { it.disposeComposition() }
        composeViews.clear()
        list.removeAllViews()
        rows.clear()
        draft.cards.filter { it.kind in editableWidgetKinds }.forEach { spec ->
            val item = FrameLayout(context).apply {
                background = shape(itemSurface, NativeMiuixStyle.CardCorner)
                layoutParams = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) }
            }
            val body = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(NativeMiuixStyle.ContentInset), dp(8), dp(NativeMiuixStyle.ContentInset), dp(12))
            }
            val heading = LinearLayout(context).apply { gravity = Gravity.CENTER_VERTICAL }
            heading.addView(handle(spec.kind, item), LinearLayout.LayoutParams(dp(44), dp(48)))
            heading.addView(text(spec.kind.title, NativeMiuixStyle.Headline, foreground), LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                marginStart = dp(NativeMiuixStyle.IconTextGap)
            })
            heading.addView(compose {
                var checked by remember { mutableStateOf(spec.enabled) }
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.CenterEnd) {
                    Switch(checked = checked, onCheckedChange = { next ->
                        if (dragging == null) {
                            checked = next
                            draft = draft.update(spec.kind) { it.copy(enabled = next) }
                        }
                    }, modifier = Modifier.semantics { contentDescription = "显示${spec.kind.title}" })
                }
            }, LinearLayout.LayoutParams(dp(54), dp(48)))
            body.addView(heading)
            val sizes = LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
            }
            val tabs = compose {
                var selected by remember { mutableIntStateOf(if (spec.compact) 1 else 0) }
                TabRowWithContour(tabs = listOf("标准", "紧凑"), selectedTabIndex = selected,
                    colors = TabRowDefaults.tabRowColors(
                        backgroundColor = if (dark) colors.surface else colors.surfaceContainerHighest),
                    onTabSelected = { index ->
                        selected = index
                        draft = draft.update(spec.kind) { it.copy(compact = index == 1) }
                    })
            }
            tabs.contentDescription = "${spec.kind.title}尺寸"
            sizes.addView(tabs, LinearLayout.LayoutParams(-1, -2))
            body.addView(sizes)
            item.addView(body, FrameLayout.LayoutParams(-1, -2))
            rows[spec.kind] = item
            list.addView(item)
        }
    }

    private fun handle(kind: CardKind, item: View): View {
        val handle = object : ImageView(context) {
            override fun onInitializeAccessibilityNodeInfo(info: AccessibilityNodeInfo) {
                super.onInitializeAccessibilityNodeInfo(info)
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(moveUpAction, "向前排列"))
                info.addAction(AccessibilityNodeInfo.AccessibilityAction(moveDownAction, "向后排列"))
            }
            override fun performAccessibilityAction(action: Int, arguments: Bundle?): Boolean {
                if (action != moveUpAction && action != moveDownAction) return super.performAccessibilityAction(action, arguments)
                val editable = draft.cards.filter { it.kind in rows }
                val index = editable.indexOfFirst { it.kind == kind }
                val target = editable.getOrNull(index + if (action == moveUpAction) -1 else 1) ?: return false
                draft = draft.reorderWidget(kind, target.kind, action == moveDownAction)
                renderRows()
                return true
            }
        }.apply {
            setImageDrawable(MiuixIconRenderer.toDrawable(context, MiuixIcons.Sort, dp(22)))
            setColorFilter(secondary)
            setPadding(dp(11), dp(13), dp(11), dp(13))
            contentDescription = "拖动调整${kind.title}顺序"
            isFocusable = true
        }
        var downY = 0f
        var downX = 0f
        var pointerId = -1
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        handle.isClickable = true
        handle.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (dragging != null || closed) return@setOnTouchListener false
                    pointerId = event.getPointerId(0)
                    downY = event.rawY; downX = event.rawX
                    handle.parent.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val index = event.findPointerIndex(pointerId)
                    if (index < 0) { finishDrag(kind, item, commit = false); return@setOnTouchListener true }
                    val deltaY = event.getY(index) + event.rawY - event.y - downY
                    val deltaX = event.getX(index) + event.rawX - event.x - downX
                    if (dragging == null && (abs(deltaY) > slop || abs(deltaX) > slop)) {
                        dragging = kind
                        item.animate().cancel()
                        item.translationZ = dp(8).toFloat()
                        item.alpha = .92f
                        handle.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    }
                    if (dragging == kind) moveDrag(kind, item, deltaY)
                }
                MotionEvent.ACTION_UP -> {
                    pointerId = -1
                    handle.parent.requestDisallowInterceptTouchEvent(false)
                    if (dragging == kind) finishDrag(kind, item, commit = true) else handle.performClick()
                }
                MotionEvent.ACTION_CANCEL -> {
                    pointerId = -1
                    handle.parent.requestDisallowInterceptTouchEvent(false)
                    finishDrag(kind, item, commit = false)
                }
                MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == pointerId) {
                    pointerId = -1
                    handle.parent.requestDisallowInterceptTouchEvent(false)
                    finishDrag(kind, item, commit = false)
                }
            }
            // Retain the gesture from DOWN through UP even when the finger leaves the handle.
            true
        }
        return handle
    }

    private fun moveDrag(kind: CardKind, item: View, deltaY: Float) {
        item.translationY = deltaY.coerceIn(-item.top.toFloat(), (list.height - item.bottom).toFloat())
        val center = item.top + item.height / 2f + deltaY
        val crossed = rows.entries.filter { (candidate, row) ->
            candidate != kind && if (row.top > item.top) center >= row.top + row.height / 2f
                else center <= row.top + row.height / 2f
        }
        val target = (if (deltaY >= 0) crossed.maxByOrNull { it.value.top }
            else crossed.minByOrNull { it.value.top })?.key
        if (target == targetKind) return
        targetKind = target
        rows.forEach { (candidate, row) ->
            if (candidate == kind) return@forEach
            val targetRow = target?.let(rows::get)
            val displaced = targetRow != null && if (targetRow.top > item.top)
                row.top > item.top && row.top <= targetRow.top
            else row.top >= targetRow.top && row.top < item.top
            val shift = if (displaced) {
                val gap = (item.layoutParams as LinearLayout.LayoutParams).bottomMargin
                (item.height + gap) * if (row.top > item.top) -1f else 1f
            } else 0f
            row.animate().translationY(shift).setDuration(180L).start()
        }
        handleFeedback(item)
    }

    private fun handleFeedback(view: View) {
        if (targetKind != null) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    private fun finishDrag(kind: CardKind, item: View, commit: Boolean) {
        if (dragging != kind) return
        val target = if (commit) targetKind?.let(rows::get) else null
        if (target != null) draft = draft.reorderWidget(kind, targetKind!!, target.top > item.top)
        val finalY = target?.let { it.top - item.top }?.toFloat() ?: 0f
        if (target == null) rows.values.filter { it !== item }.forEach {
            it.animate().translationY(0f).setDuration(180L).start()
        }
        item.animate().translationY(finalY).alpha(1f).setDuration(180L).withEndAction {
            clearDrag()
            if (!closed) renderRows()
        }.start()
    }

    private fun clearDrag() {
        dragging = null
        targetKind = null
        rows.values.forEach {
            it.animate().withEndAction(null).cancel()
            it.alpha = 1f; it.translationY = 0f; it.translationZ = 0f
        }
    }

    private fun compose(content: @Composable () -> Unit) = ComposeView(context).apply {
        setViewTreeLifecycleOwner(owner)
        setViewTreeSavedStateRegistryOwner(owner)
        setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
        setContent { MiuixTheme(colors = colors, content = content) }
        composeViews += this
    }

    private fun text(value: String, size: Int, color: Int) = TextView(context).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); gravity = Gravity.CENTER_VERTICAL
    }
    private fun shape(color: Int, radius: Int) = NativeMiuixSurface(color, dp(radius).toFloat())
    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).roundToInt()
}
