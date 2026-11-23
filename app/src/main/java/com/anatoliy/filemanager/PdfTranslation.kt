package com.anatoliy.filemanager

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

object TranslateLanguages {
    val all = listOf(
        TranslateLanguage.UKRAINIAN to "Українська",
        TranslateLanguage.ENGLISH to "English",
        TranslateLanguage.GERMAN to "Deutsch",
        TranslateLanguage.POLISH to "Polski",
        TranslateLanguage.FRENCH to "Français",
        TranslateLanguage.SPANISH to "Español",
        TranslateLanguage.ITALIAN to "Italiano",
        TranslateLanguage.CZECH to "Čeština",
        TranslateLanguage.RUSSIAN to "Русский",
    )

    fun name(code: String): String =
        all.firstOrNull { it.first == code }?.second ?: java.util.Locale(code).displayLanguage
}

suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { cont ->
    addOnSuccessListener { cont.resume(it) }
    addOnFailureListener { cont.resumeWithException(it) }
    addOnCanceledListener { cont.cancel() }
}

class TextBlock(val text: String, val box: RectF, val lineHeight: Float)

class PageLayout(val blocks: List<TextBlock>, val ocr: Boolean) {
    val text: String get() = blocks.joinToString("\n\n") { it.text }
}

private class Line(val text: String, val box: RectF)

fun pageDisplaySize(page: PDPage): Pair<Float, Float> {
    val box = page.cropBox
    return if (page.rotation % 180 != 0) box.height to box.width else box.width to box.height
}

private class LayoutStripper : PDFTextStripper() {
    val lines = mutableListOf<Line>()
    private val words = mutableListOf<Line>()

    init {
        sortByPosition = true
    }

    override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
        if (text.isBlank() || textPositions.isEmpty()) return
        val left = textPositions.minOf { it.x }
        val right = textPositions.maxOf { it.x + it.width }
        val bottom = textPositions.maxOf { it.y }
        val top = textPositions.minOf { it.y - max(it.height, it.fontSizeInPt * 0.7f) }
        words += Line(text, RectF(left, top, right, bottom))
    }

    override fun writeLineSeparator() = flush()

    override fun writePageEnd() = flush()

    private fun flush() {
        if (words.isEmpty()) return
        var segment = mutableListOf(words.first())
        for (word in words.drop(1)) {
            val prev = segment.last()
            val gap = word.box.left - prev.box.right
            if (gap > 2.5f * max(prev.box.height(), word.box.height())) {
                lines += merge(segment)
                segment = mutableListOf()
            }
            segment += word
        }
        lines += merge(segment)
        words.clear()
    }

    private fun merge(parts: List<Line>) = Line(
        parts.joinToString(" ") { it.text },
        RectF(parts.minOf { it.box.left }, parts.minOf { it.box.top }, parts.maxOf { it.box.right }, parts.maxOf { it.box.bottom }),
    )
}

private fun groupLines(lines: List<Line>, width: Float, height: Float): List<TextBlock> {
    val groups = mutableListOf<MutableList<Line>>()
    for (line in lines) {
        val h = line.box.height()
        val target = groups.asReversed().take(8).firstOrNull { group ->
            val last = group.last()
            val lh = last.box.height()
            val gap = line.box.top - last.box.bottom
            val similar = abs(h - lh) < 0.35f * max(h, lh)
            val overlap = min(line.box.right, last.box.right) - max(line.box.left, last.box.left) > 0
            similar && overlap && gap > -0.5f * h && gap < 0.9f * max(h, lh)
        }
        if (target != null) target += line else groups += mutableListOf(line)
    }
    return groups.map { group ->
        val text = StringBuilder()
        for (line in group) {
            val t = line.text.trim()
            if (text.endsWith("-") && t.firstOrNull()?.isLowerCase() == true) {
                text.setLength(text.length - 1)
            } else if (text.isNotEmpty()) {
                text.append(' ')
            }
            text.append(t)
        }
        val lineHeight = group.map { it.box.height() }.average().toFloat()
        TextBlock(
            text.toString(),
            RectF(
                group.minOf { it.box.left } / width,
                group.minOf { it.box.top } / height,
                group.maxOf { it.box.right } / width,
                group.maxOf { it.box.bottom } / height,
            ),
            lineHeight / height,
        )
    }
}

class PdfTextSource(context: Context, private val file: File) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private var document: PDDocument? = null

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun pageLayout(index: Int): PageLayout = withContext(dispatcher) {
        val doc = document ?: PDDocument.load(file).also { document = it }
        val (width, height) = pageDisplaySize(doc.getPage(index))
        val stripper = LayoutStripper().apply {
            startPage = index + 1
            endPage = index + 1
        }
        stripper.getText(doc)
        PageLayout(groupLines(stripper.lines, width, height), ocr = false)
    }

    override fun close() {
        executor.execute { runCatching { document?.close() } }
        executor.shutdown()
    }
}

object PdfTranslation {
    suspend fun recognize(bitmap: Bitmap): PageLayout {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val result = try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()
        } finally {
            recognizer.close()
        }
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val blocks = result.textBlocks.mapNotNull { block ->
            val rect = block.boundingBox ?: return@mapNotNull null
            val lines = block.lines.ifEmpty { return@mapNotNull null }
            TextBlock(
                lines.joinToString(" ") { it.text },
                RectF(rect.left / w, rect.top / h, rect.right / w, rect.bottom / h),
                rect.height().toFloat() / lines.size / h,
            )
        }
        return PageLayout(blocks, ocr = true)
    }

    suspend fun detect(text: String): String? {
        val client = LanguageIdentification.getClient()
        return try {
            val tag = client.identifyLanguage(text.take(3000)).await()
            if (tag == "und") null else TranslateLanguage.fromLanguageTag(tag)
        } finally {
            client.close()
        }
    }
}

class TranslatorPool : Closeable {
    private val translators = HashMap<Pair<String, String>, Translator>()
    private val ready = HashSet<Pair<String, String>>()

    @Synchronized
    private fun get(source: String, target: String): Translator =
        translators.getOrPut(source to target) {
            Translation.getClient(
                TranslatorOptions.Builder().setSourceLanguage(source).setTargetLanguage(target).build()
            )
        }

    fun isReady(source: String, target: String) = (source to target) in ready

    suspend fun prepare(source: String, target: String) {
        get(source, target).downloadModelIfNeeded(DownloadConditions.Builder().build()).await()
        ready += source to target
    }

    suspend fun translate(source: String, target: String, text: String): String =
        get(source, target).translate(text).await()

    @Synchronized
    override fun close() {
        translators.values.forEach { it.close() }
        translators.clear()
    }
}

class PageTranslator(context: Context, val file: File, private val doc: PdfDocument) : Closeable {
    private val textSource = PdfTextSource(context, file)
    private val pool = TranslatorPool()
    private val layouts = HashMap<Int, PageLayout>()
    private val detected = HashMap<Int, String?>()
    private val translations = HashMap<Triple<Int, String, String>, List<String>>()

    val pageCount get() = doc.pageCount

    suspend fun renderPage(page: Int, width: Int): Bitmap? = doc.renderPage(page, width)

    suspend fun layout(page: Int, onStatus: (String) -> Unit = {}): PageLayout {
        layouts[page]?.let { return it }
        onStatus("Читання тексту…")
        val fromText = runCatching { textSource.pageLayout(page) }.getOrNull()
        val layout = if (fromText != null && fromText.blocks.isNotEmpty()) {
            fromText
        } else {
            onStatus("Розпізнавання тексту зі зображення…")
            val bitmap = doc.renderPage(page, 2000)
            bitmap?.let { try { PdfTranslation.recognize(it) } finally { it.recycle() } }
                ?: PageLayout(emptyList(), ocr = true)
        }
        layouts[page] = layout
        return layout
    }

    suspend fun source(page: Int, override: String?, onStatus: (String) -> Unit = {}): String? {
        if (override != null) return override
        if (detected.containsKey(page)) return detected[page]
        onStatus("Визначення мови…")
        return PdfTranslation.detect(layout(page).text).also { detected[page] = it }
    }

    suspend fun translate(
        page: Int,
        source: String,
        target: String,
        onStatus: (String) -> Unit = {},
        onBlock: (String) -> Unit = {},
    ): List<String> {
        val key = Triple(page, source, target)
        translations[key]?.let { done -> done.forEach(onBlock); return done }
        if (!pool.isReady(source, target)) {
            onStatus(
                "Завантаження мовних моделей (${TranslateLanguages.name(source)} → ${TranslateLanguages.name(target)}, ~30 МБ кожна). Інтернет потрібен лише перший раз…"
            )
            pool.prepare(source, target)
        }
        val result = layout(page).blocks.map { block ->
            pool.translate(source, target, block.text).also(onBlock)
        }
        translations[key] = result
        return result
    }

    override fun close() {
        textSource.close()
        pool.close()
    }
}
