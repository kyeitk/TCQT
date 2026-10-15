package com.owo233.tcqt.features.menu.settingmecards

import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.widget.ImageView
import android.view.ViewGroup
import android.view.View
import com.owo233.tcqt.core.env.HookEnv
import androidx.compose.ui.graphics.toArgb
import java.lang.ref.WeakReference

/** Center-crop the loaded image itself, independently of QQ's cover height and image matrix. */
internal class NativeProfileBackground(source: View?, private val radius: Float) : Drawable() {
    private var source = WeakReference(source)
    private var loaded: Drawable? = null
    private var image: Drawable? = null
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var clip = Path()
    var showCover: Boolean = true

    override fun onBoundsChange(bounds: Rect) { clip = NativeMiuixStyle.path(bounds, radius) }
    override fun getOutline(outline: Outline) { NativeMiuixStyle.outline(outline, bounds, radius, clip) }

    fun setSource(view: View?) {
        if (source.get() !== view) source = WeakReference(view)
        refreshSource()
    }

    fun refreshSource() {
        fun imageView(view: View?): ImageView? {
            if (view is ImageView) return view
            if (view is ViewGroup) for (i in 0 until view.childCount) imageView(view.getChildAt(i))?.let { return it }
            return null
        }
        val drawable = imageView(source.get())?.drawable
        val raw = if (drawable?.javaClass?.name == "com.tencent.image.URLDrawable") {
            runCatching { drawable.javaClass.getMethod("getCurrDrawable").invoke(drawable) as? Drawable }.getOrNull()
        } else drawable
        if (raw === loaded) return
        loaded = raw
        // Clone drawable state rather than sampling the already clipped ImageView or rebuilding a bitmap.
        image = raw?.constantState?.newDrawable(source.get()?.resources)?.mutate()
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        val rect = RectF(bounds)
        if (rect.isEmpty) return
        val dark = HookEnv.isNightMode()
        val surface = NativeMiuixStyle.colors(dark).surfaceContainer.toArgb()
        val checkpoint = canvas.save()
        try {
            canvas.clipPath(clip)
            canvas.drawColor(surface)
            val cover = image
            if (showCover && cover != null && cover.intrinsicWidth > 0 && cover.intrinsicHeight > 0) {
                val imageCheckpoint = canvas.save()
                try {
                    val scale = maxOf(rect.width() / cover.intrinsicWidth, rect.height() / cover.intrinsicHeight)
                    canvas.translate(rect.left + (rect.width() - cover.intrinsicWidth * scale) / 2f,
                        rect.top + (rect.height() - cover.intrinsicHeight * scale) / 2f)
                    canvas.scale(scale, scale)
                    cover.setBounds(0, 0, cover.intrinsicWidth, cover.intrinsicHeight)
                    runCatching { cover.draw(canvas) }
                } finally {
                    canvas.restoreToCount(imageCheckpoint)
                }
                paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom,
                    (if (dark) intArrayOf(0x66, 0xCE, 0xF0) else intArrayOf(0x99, 0xE0, 0xF8))
                        .map { (surface and 0xFFFFFF) or (it shl 24) }.toIntArray(),
                    floatArrayOf(0f, .6f, 1f), Shader.TileMode.CLAMP)
                canvas.drawRect(rect, paint)
                paint.shader = null
            }
        } finally {
            canvas.restoreToCount(checkpoint)
        }
    }

    override fun setAlpha(alpha: Int) = Unit
    override fun setColorFilter(colorFilter: ColorFilter?) = Unit
    @Suppress("DEPRECATION")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
