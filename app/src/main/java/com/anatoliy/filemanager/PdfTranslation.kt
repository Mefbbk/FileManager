package com.anatoliy.filemanager

import android.content.Context
import android.graphics.Bitmap
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
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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

class PdfTextSource(context: Context, private val file: File) : Closeable {
    private val executor = Executors.newSingleThreadExecutor()
    private val dispatcher = executor.asCoroutineDispatcher()
    private var document: PDDocument? = null

    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    suspend fun pageText(index: Int): String = withContext(dispatcher) {
        val doc = document ?: PDDocument.load(file).also { document = it }
        PDFTextStripper().apply {
            sortByPosition = true
            startPage = index + 1
            endPage = index + 1
        }.getText(doc)
    }

    override fun close() {
        executor.execute { runCatching { document?.close() } }
        executor.shutdown()
    }
}

object PdfTranslation {
    suspend fun recognize(bitmap: Bitmap): String {
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return try {
            recognizer.process(InputImage.fromBitmap(bitmap, 0)).await().text
        } finally {
            recognizer.close()
        }
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

    fun paragraphs(text: String): List<String> {
        val out = mutableListOf<String>()
        val current = StringBuilder()
        for (raw in text.lines()) {
            val line = raw.trim()
            if (line.isEmpty()) {
                if (current.isNotEmpty()) out += current.toString().also { current.clear() }
                continue
            }
            val continues = current.isNotEmpty() &&
                current.last() !in ".!?:;" &&
                line.first().isLowerCase()
            if (current.isNotEmpty() && !continues) out += current.toString().also { current.clear() }
            if (current.isNotEmpty()) current.append(' ')
            current.append(line)
        }
        if (current.isNotEmpty()) out += current.toString()
        return out
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
