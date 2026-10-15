package com.owo233.tcqt.features.menu.settingmecards

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.ui.graphics.asAndroidPath
import top.yukonga.miuix.kmp.squircle.addSquircleRect
import top.yukonga.miuix.kmp.theme.darkColorScheme
import top.yukonga.miuix.kmp.theme.lightColorScheme

/** Native host integration: card owns padding; child rows never repeat the horizontal inset. */
internal object NativeMiuixStyle {
    const val PageGutter = 12
    const val ContentInset = 16
    const val CardGap = 12
    const val GroupGap = 16
    const val RelatedGap = 8
    const val IconTextGap = 12
    const val TouchTarget = 44
    const val GlyphSize = 22
    const val CardCorner = 16
    const val ButtonCorner = 16
    const val DialogCorner = 32
    const val Headline = 17
    const val Body = 14
    const val Caption = 11
    private val lightColors = lightColorScheme()
    private val darkColors = darkColorScheme()
    fun colors(dark: Boolean) = if (dark) darkColors else lightColors

    fun path(bounds: Rect, radius: Float): Path {
        val path = androidx.compose.ui.graphics.Path().apply {
            addSquircleRect(bounds.width().toFloat(), bounds.height().toFloat(), radius,
                squircleEnabled = Build.VERSION.SDK_INT >= 33)
        }.asAndroidPath()
        return Path(path).apply { offset(bounds.left.toFloat(), bounds.top.toFloat()) }
    }

    fun outline(outline: Outline, bounds: Rect, radius: Float, path: Path) {
        if (Build.VERSION.SDK_INT >= 33) outline.setPath(path)
        else outline.setRoundRect(bounds, radius)
    }
}

/** Cached Miuix path (extension 1.1); older Android gets a plain rounded fallback. */
internal class NativeMiuixSurface(color: Int, private val radius: Float) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color }
    private var path = Path()
    override fun onBoundsChange(bounds: Rect) { path = NativeMiuixStyle.path(bounds, radius) }
    override fun draw(canvas: Canvas) { canvas.drawPath(path, paint) }
    override fun getOutline(outline: Outline) { NativeMiuixStyle.outline(outline, bounds, radius, path) }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha; invalidateSelf() }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter; invalidateSelf() }
    @Suppress("DEPRECATION")
    override fun getOpacity() = PixelFormat.TRANSLUCENT
}
