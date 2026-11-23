package com.anatoliy.filemanager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

object TranslationOverlay {
    private const val FIT_STEP = 0.95f
    private const val MIN_SHRINK = 0.6f

    fun coverRect(block: TextBlock, width: Float, height: Float): RectF {
        val pad = block.lineHeight * height * 0.2f
        return RectF(
            block.box.left * width - pad,
            block.box.top * height - pad,
            block.box.right * width + pad,
            block.box.bottom * height + pad,
        )
    }

    fun background(page: Bitmap, block: TextBlock): Int {
        val r = coverRect(block, page.width.toFloat(), page.height.toFloat())
        val counts = HashMap<Int, Int>()
        val samples = HashMap<Int, Int>()
        val steps = 8
        for (i in 0..steps) {
            val fx = r.left + r.width() * i / steps
            val fy = r.top + r.height() * i / steps
            listOf(fx to r.top - 2, fx to r.bottom + 2, r.left - 2 to fy, r.right + 2 to fy).forEach { (x, y) ->
                val px = x.toInt().coerceIn(0, page.width - 1)
                val py = y.toInt().coerceIn(0, page.height - 1)
                val c = page.getPixel(px, py)
                val key = (Color.red(c) shr 4 shl 8) or (Color.green(c) shr 4 shl 4) or (Color.blue(c) shr 4)
                counts[key] = (counts[key] ?: 0) + 1
                samples[key] = c
            }
        }
        val best = counts.maxByOrNull { it.value }?.key ?: return Color.WHITE
        return samples[best] ?: Color.WHITE
    }

    fun textColor(background: Int): Int {
        val lum = 0.299 * Color.red(background) + 0.587 * Color.green(background) + 0.114 * Color.blue(background)
        return if (lum > 140) Color.BLACK else Color.WHITE
    }

    fun render(page: Bitmap, blocks: List<TextBlock>, texts: List<String>): Bitmap {
        val out = page.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)
        val w = out.width.toFloat()
        val h = out.height.toFloat()
        val fill = Paint()
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG)
        blocks.forEachIndexed { i, block ->
            val text = texts.getOrNull(i) ?: return@forEachIndexed
            val r = coverRect(block, w, h)
            val bg = background(page, block)
            paint.color = textColor(bg)
            val room = roomBelow(block, blocks, w, h) - r.top
            val start = block.fontSize * h
            val minSize = (start * MIN_SHRINK).coerceAtLeast(6f)
            val boxW = r.width().toInt().coerceAtLeast(1)
            var size = start
            var layout: StaticLayout
            while (true) {
                paint.textSize = size
                layout = StaticLayout.Builder.obtain(text, 0, text.length, paint, boxW)
                    .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                    .setLineSpacing(0f, 1f)
                    .setIncludePad(false)
                    .build()
                if (layout.height <= room || size <= minSize) break
                size = (size * FIT_STEP).coerceAtLeast(minSize)
            }
            fill.color = bg
            canvas.drawRect(r.left, r.top, r.right, max(r.bottom, r.top + layout.height + size * 0.2f), fill)
            canvas.save()
            canvas.translate(r.left, r.top)
            layout.draw(canvas)
            canvas.restore()
        }
        return out
    }

    private fun roomBelow(block: TextBlock, blocks: List<TextBlock>, w: Float, h: Float): Float {
        val r = coverRect(block, w, h)
        val next = blocks.filter { other ->
            other !== block &&
                other.box.top >= block.box.bottom - block.lineHeight * 0.3f &&
                other.box.left < block.box.right && other.box.right > block.box.left
        }.minOfOrNull { coverRect(it, w, h).top }
        return max(r.bottom, next ?: (h * 0.98f))
    }
}
