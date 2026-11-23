package com.anatoliy.filemanager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType0Font
import com.tom_roush.pdfbox.util.Matrix
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.File

object TranslationOverlay {
    private const val FIT_STEP = 0.92f
    private const val LEADING = 1.15f

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
            fill.color = bg
            canvas.drawRect(r, fill)
            paint.color = textColor(bg)
            val start = block.lineHeight * h * 0.9f
            val minSize = (start * 0.35f).coerceAtLeast(6f)
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
                if (layout.height <= r.height() || size <= minSize) break
                size = (size * FIT_STEP).coerceAtLeast(minSize)
            }
            canvas.save()
            canvas.translate(r.left, r.top)
            layout.draw(canvas)
            canvas.restore()
        }
        return out
    }

    suspend fun exportPdf(
        engine: PageTranslator,
        dest: File,
        sourceOverride: String?,
        target: String,
        onProgress: (Int) -> Unit,
    ) {
        val pdf = withContext(Dispatchers.IO) {
            PDDocument.load(engine.file).apply { isAllSecurityToBeRemoved = true }
        }
        try {
            val font = withContext(Dispatchers.IO) { loadFont(pdf) }
            val glyphs = HashMap<Char, Boolean>()
            for (i in 0 until engine.pageCount) {
                onProgress(i)
                val layout = engine.layout(i)
                if (layout.blocks.isEmpty()) continue
                val source = engine.source(i, sourceOverride) ?: continue
                if (source == target) continue
                val texts = engine.translate(i, source, target)
                val sample = engine.renderPage(i, 600) ?: continue
                val colors = layout.blocks.map { background(sample, it) }
                sample.recycle()
                withContext(Dispatchers.IO) {
                    drawOnPage(pdf, pdf.getPage(i), font, glyphs, layout.blocks, texts, colors)
                }
            }
            onProgress(engine.pageCount)
            withContext(Dispatchers.IO) { pdf.save(dest) }
        } catch (e: Throwable) {
            dest.delete()
            throw e
        } finally {
            withContext(NonCancellable + Dispatchers.IO) { pdf.close() }
        }
    }

    private fun loadFont(pdf: PDDocument): PDFont {
        val candidates = listOf(
            "/system/fonts/Roboto-Regular.ttf",
            "/system/fonts/NotoSans-Regular.ttf",
            "/system/fonts/DroidSans.ttf",
        )
        for (path in candidates) {
            val file = File(path)
            if (!file.exists()) continue
            runCatching { PDType0Font.load(pdf, file) }.getOrNull()?.let { return it }
        }
        error("Не знайдено системний шрифт для PDF")
    }

    private fun clean(text: String, font: PDFont, glyphs: HashMap<Char, Boolean>): String =
        buildString {
            for (c in text) {
                val ch = if (c.isWhitespace()) ' ' else c
                val ok = glyphs.getOrPut(ch) { runCatching { font.encode(ch.toString()) }.isSuccess }
                if (ok) append(ch)
            }
        }

    private fun wrap(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
        fun width(s: String) = font.getStringWidth(s) / 1000f * size
        val lines = mutableListOf<String>()
        var current = ""
        for (word in text.split(' ').filter { it.isNotEmpty() }) {
            val candidate = if (current.isEmpty()) word else "$current $word"
            if (width(candidate) <= maxWidth) {
                current = candidate
                continue
            }
            if (current.isNotEmpty()) lines += current
            current = ""
            var rest = word
            while (width(rest) > maxWidth && rest.length > 1) {
                var cut = rest.length - 1
                while (cut > 1 && width(rest.substring(0, cut)) > maxWidth) cut--
                lines += rest.substring(0, cut)
                rest = rest.substring(cut)
            }
            current = rest
        }
        if (current.isNotEmpty()) lines += current
        return lines
    }

    private fun drawOnPage(
        pdf: PDDocument,
        page: PDPage,
        font: PDFont,
        glyphs: HashMap<Char, Boolean>,
        blocks: List<TextBlock>,
        texts: List<String>,
        colors: List<Int>,
    ) {
        val (dw, dh) = pageDisplaySize(page)
        val crop = page.cropBox
        val llx = crop.lowerLeftX
        val lly = crop.lowerLeftY
        val w = crop.width
        val h = crop.height
        val toPage = when (((page.rotation % 360) + 360) % 360) {
            90 -> Matrix(0f, 1f, -1f, 0f, w + llx, lly)
            180 -> Matrix(-1f, 0f, 0f, -1f, w + llx, h + lly)
            270 -> Matrix(0f, -1f, 1f, 0f, llx, h + lly)
            else -> Matrix(1f, 0f, 0f, 1f, llx, lly)
        }
        PDPageContentStream(pdf, page, PDPageContentStream.AppendMode.APPEND, true, true).use { cs ->
            cs.transform(toPage)
            blocks.forEachIndexed { i, block ->
                val text = clean(texts.getOrNull(i) ?: return@forEachIndexed, font, glyphs).trim()
                val r = coverRect(block, dw, dh)
                val bg = colors[i]
                cs.setNonStrokingColor(Color.red(bg) / 255f, Color.green(bg) / 255f, Color.blue(bg) / 255f)
                cs.addRect(r.left, dh - r.bottom, r.width(), r.height())
                cs.fill()
                if (text.isEmpty()) return@forEachIndexed

                val start = block.lineHeight * dh * 0.9f
                val minSize = (start * 0.35f).coerceAtLeast(3f)
                var size = start
                var lines: List<String>
                while (true) {
                    lines = wrap(text, font, size, r.width())
                    if (lines.size * size * LEADING <= r.height() || size <= minSize) break
                    size = (size * FIT_STEP).coerceAtLeast(minSize)
                }
                val fg = textColor(bg)
                cs.setNonStrokingColor(Color.red(fg) / 255f, Color.green(fg) / 255f, Color.blue(fg) / 255f)
                cs.beginText()
                cs.setFont(font, size)
                var y = dh - r.top - size * 0.9f
                for (line in lines) {
                    cs.setTextMatrix(Matrix.getTranslateInstance(r.left, y))
                    cs.showText(line)
                    y -= size * LEADING
                }
                cs.endText()
            }
        }
    }
}
