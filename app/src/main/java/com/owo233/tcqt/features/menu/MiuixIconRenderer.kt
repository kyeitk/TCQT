package com.owo233.tcqt.features.menu

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.VectorGroup
import androidx.compose.ui.graphics.vector.VectorPath
import androidx.compose.ui.graphics.vector.toPath

/**
 * 把 MiuixIcons 的 ImageVector 手动光栅化成原生 View 可用的 BitmapDrawable。
 * 只使用公开的 ImageVector / VectorGroup / VectorPath 结构，不触碰 Compose 内部类。
 */
internal object MiuixIconRenderer {

    private const val DEFAULT_TINT = 0xFF444444.toInt()

    fun toDrawable(context: Context, icon: ImageVector, sizePx: Int): Drawable {
        val safeSize = sizePx.coerceAtLeast(1)
        val bitmap = Bitmap.createBitmap(safeSize, safeSize, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val viewport = maxOf(icon.viewportWidth, icon.viewportHeight, 1f)
        val scale = safeSize / viewport

        canvas.scale(scale, scale)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = DEFAULT_TINT
        }
        drawGroup(canvas, icon.root, paint)
        return BitmapDrawable(context.resources, bitmap)
    }

    private fun drawGroup(canvas: Canvas, group: VectorGroup, paint: Paint) {
        canvas.save()
        canvas.translate(group.translationX, group.translationY)
        canvas.translate(group.pivotX, group.pivotY)
        if (group.rotation != 0f) canvas.rotate(group.rotation)
        canvas.scale(group.scaleX, group.scaleY)
        canvas.translate(-group.pivotX, -group.pivotY)

        for (node in group) {
            when (node) {
                is VectorGroup -> drawGroup(canvas, node, paint)
                is VectorPath -> drawPath(canvas, node, paint)
            }
        }
        canvas.restore()
    }

    private fun drawPath(canvas: Canvas, vectorPath: VectorPath, paint: Paint) {
        val path = vectorPath.pathData.toPath(androidx.compose.ui.graphics.Path()).asAndroidPath()

        if (vectorPath.fill != null) {
            paint.style = Paint.Style.FILL
            paint.color = DEFAULT_TINT
            canvas.drawPath(path, paint)
        }

        if (vectorPath.stroke != null) {
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = vectorPath.strokeLineWidth.takeIf { it > 0f } ?: 1f
            canvas.drawPath(path, paint)
        }
    }
}