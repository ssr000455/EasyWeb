// b站：绝望彻彻底底的绝望 [android/app/src/main/java/com/easyweb/app/engine/Render.kt]

package com.easyweb.app.engine

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import kotlin.math.roundToInt

// 渲染器
class Renderer(
    private val canvas: Canvas,
    private val scrollX: Float = 0.0f,
    private val scrollY: Float = 0.0f,
    private val viewportWidth: Float,
    private val viewportHeight: Float,
    private val scaleFactor: Float = 1.0f
) {
    private val bgPaint = Paint()
    private val borderPaint = Paint()
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 渲染整个布局树
    fun render(layoutRoot: LayoutBox) {
        // 清空画布为白色背景
        canvas.drawColor(Color.WHITE)
        // 应用缩放
        canvas.save()
        canvas.scale(scaleFactor, scaleFactor)
        // 递归渲染盒子
        renderBox(layoutRoot)
        // 恢复画布
        canvas.restore()
    }

    // 渲染单个盒子
    private fun renderBox(box: LayoutBox) {
        // 跳过屏幕外的盒子
        val screenRect = Rect(-scrollX, -scrollY, viewportWidth, viewportHeight)
        if (!rectsOverlap(box.rect, screenRect)) return

        // 渲染背景和边框
        renderBackground(box)
        renderBorder(box)

        // 渲染文本
        if (box.boxType == BoxType.Text) {
            renderText(box)
        }

        // 渲染子元素
        for (child in box.children) {
            renderBox(child)
        }
    }

    // 渲染背景
    private fun renderBackground(box: LayoutBox) {
        val style = box.style ?: return
        val bg = style.color("background-color", 0L)
        val alpha = alphaOf(bg)
        if (alpha > 0) {
            val pa = paddingArea(box)
            val x = (pa.x - scrollX).roundToInt()
            val y = (pa.y - scrollY).roundToInt()
            val w = pa.width.roundToInt()
            val h = pa.height.roundToInt()
            if (w > 0 && h > 0) {
                bgPaint.color = Color.argb(
                    alpha.coerceIn(0, 255),
                    redOf(bg).coerceIn(0, 255),
                    greenOf(bg).coerceIn(0, 255),
                    blueOf(bg).coerceIn(0, 255)
                )
                bgPaint.style = Paint.Style.FILL
                canvas.drawRect(x.toFloat(), y.toFloat(), (x + w).toFloat(), (y + h).toFloat(), bgPaint)
            }
        }
    }

    // 渲染边框
    private fun renderBorder(box: LayoutBox) {
        if (box.border.top == 0.0f && box.border.right == 0.0f
            && box.border.bottom == 0.0f && box.border.left == 0.0f) return

        val borderColor = box.style?.color("border-color", toArgb(0, 0, 0, 255)) ?: toArgb(0, 0, 0, 255)
        borderPaint.color = Color.argb(
            alphaOf(borderColor).coerceIn(0, 255),
            redOf(borderColor).coerceIn(0, 255),
            greenOf(borderColor).coerceIn(0, 255),
            blueOf(borderColor).coerceIn(0, 255)
        )
        borderPaint.style = Paint.Style.FILL

        val r = box.rect
        val x = (r.x - scrollX).toFloat()
        val y = (r.y - scrollY).toFloat()
        val w = r.width
        val h = r.height
        val bTop = box.border.top
        val bRight = box.border.right
        val bBottom = box.border.bottom
        val bLeft = box.border.left

        // 上边框
        if (bTop > 0.0f) {
            canvas.drawRect(x, y, x + w, y + bTop, borderPaint)
        }
        // 下边框
        if (bBottom > 0.0f) {
            canvas.drawRect(x, y + h - bBottom, x + w, y + h, borderPaint)
        }
        // 左边框
        if (bLeft > 0.0f) {
            canvas.drawRect(x, y, x + bLeft, y + h, borderPaint)
        }
        // 右边框
        if (bRight > 0.0f) {
            canvas.drawRect(x + w - bRight, y, x + w, y + h, borderPaint)
        }
    }

    // 渲染文本
    private fun renderText(box: LayoutBox) {
        val node = box.domNode ?: return
        if (node.kind != NodeKind.Text) return
        val text = node.text?.trim() ?: return
        if (text.isEmpty()) return

        val color = box.style?.color("color", toArgb(0, 0, 0, 255)) ?: toArgb(0, 0, 0, 255)
        val fontSize = box.style?.length("font-size", 16.0f) ?: 16.0f

        textPaint.color = Color.argb(
            alphaOf(color).coerceIn(0, 255),
            redOf(color).coerceIn(0, 255),
            greenOf(color).coerceIn(0, 255),
            blueOf(color).coerceIn(0, 255)
        )
        textPaint.textSize = fontSize
        textPaint.typeface = Typeface.DEFAULT

        val x = box.rect.x - scrollX
        val y = box.rect.y - scrollY

        // 使用 Android Canvas 绘制文本
        canvas.drawText(text, x, y + fontSize, textPaint)
    }
}

// 判断两个矩形是否重叠
private fun rectsOverlap(a: Rect, b: Rect): Boolean {
    return a.x < b.x + b.width && a.x + a.width > b.x
        && a.y < b.y + b.height && a.y + a.height > b.y
}