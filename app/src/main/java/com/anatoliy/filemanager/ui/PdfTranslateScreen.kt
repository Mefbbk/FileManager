package com.anatoliy.filemanager.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.PdfDocument
import com.anatoliy.filemanager.PdfTextSource
import com.anatoliy.filemanager.PdfTranslation
import com.anatoliy.filemanager.TranslateLanguages
import com.anatoliy.filemanager.TranslatorPool
import java.io.File

private const val PREF_TARGET = "translate_target"

private class PageText(val text: String, val ocr: Boolean)

private class TranslationCache {
    val texts = HashMap<Int, PageText>()
    val translations = HashMap<Triple<Int, String, String>, List<String>>()
    val detected = HashMap<Int, String?>()
}

private sealed interface PageState {
    data class Working(val message: String) : PageState
    data class Failed(val message: String) : PageState
    data object Empty : PageState
    data class Ready(
        val source: String,
        val ocr: Boolean,
        val original: List<String>,
        val sameLanguage: Boolean,
    ) : PageState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfTranslateScreen(file: File, doc: PdfDocument, startPage: Int, onClose: () -> Unit) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }

    var target by remember { mutableStateOf(prefs.getString(PREF_TARGET, null) ?: TranslateLanguages.all.first().first) }
    var sourceOverride by remember { mutableStateOf<String?>(null) }
    var showOriginal by remember { mutableStateOf(false) }

    val textSource = remember(file) { PdfTextSource(context, file) }
    val pool = remember { TranslatorPool() }
    val cache = remember(file) { TranslationCache() }
    DisposableEffect(textSource, pool) {
        onDispose {
            textSource.close()
            pool.close()
        }
    }

    val pagerState = rememberPagerState(initialPage = startPage.coerceIn(0, (doc.pageCount - 1).coerceAtLeast(0))) {
        doc.pageCount
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Переклад")
                        Text(
                            "Сторінка ${pagerState.currentPage + 1} / ${doc.pageCount}",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LanguagePicker(
                    label = sourceOverride?.let { TranslateLanguages.name(it) } ?: "Авто",
                    includeAuto = true,
                    onPick = { sourceOverride = it },
                )
                Icon(Icons.Filled.SwapHoriz, contentDescription = null)
                LanguagePicker(
                    label = TranslateLanguages.name(target),
                    includeAuto = false,
                    onPick = { code ->
                        if (code != null) {
                            target = code
                            prefs.edit().putString(PREF_TARGET, code).apply()
                        }
                    },
                )
                Box(Modifier.weight(1f))
                FilterChip(
                    selected = showOriginal,
                    onClick = { showOriginal = !showOriginal },
                    label = { Text("Оригінал") },
                )
            }
            HorizontalDivider(Modifier.padding(top = 4.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0,
            ) { page ->
                TranslatedPage(
                    page = page,
                    doc = doc,
                    textSource = textSource,
                    pool = pool,
                    cache = cache,
                    sourceOverride = sourceOverride,
                    target = target,
                    showOriginal = showOriginal,
                )
            }
        }
    }
}

@Composable
private fun LanguagePicker(label: String, includeAuto: Boolean, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AssistChip(onClick = { open = true }, label = { Text(label) })
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (includeAuto) {
                DropdownMenuItem(text = { Text("Авто") }, onClick = { open = false; onPick(null) })
            }
            TranslateLanguages.all.forEach { (code, name) ->
                DropdownMenuItem(text = { Text(name) }, onClick = { open = false; onPick(code) })
            }
        }
    }
}

@Composable
private fun TranslatedPage(
    page: Int,
    doc: PdfDocument,
    textSource: PdfTextSource,
    pool: TranslatorPool,
    cache: TranslationCache,
    sourceOverride: String?,
    target: String,
    showOriginal: Boolean,
) {
    var state by remember(page) { mutableStateOf<PageState>(PageState.Working("Читання тексту…")) }
    val translated = remember(page) { mutableStateListOf<String>() }
    var attempt by remember(page) { mutableStateOf(0) }

    LaunchedEffect(page, sourceOverride, target, attempt) {
        translated.clear()
        try {
            val pageText = cache.texts[page] ?: run {
                state = PageState.Working("Читання тексту…")
                val layer = runCatching { textSource.pageText(page) }.getOrDefault("")
                if (layer.isNotBlank()) {
                    PageText(layer, ocr = false)
                } else {
                    state = PageState.Working("Розпізнавання тексту зі зображення…")
                    val bitmap = doc.renderPage(page, 2000)
                    val recognized = bitmap?.let {
                        try { PdfTranslation.recognize(it) } finally { it.recycle() }
                    }.orEmpty()
                    PageText(recognized, ocr = true)
                }
            }.also { cache.texts[page] = it }

            if (pageText.text.isBlank()) {
                state = PageState.Empty
                return@LaunchedEffect
            }

            val original = PdfTranslation.paragraphs(pageText.text)
            val source = sourceOverride ?: if (cache.detected.containsKey(page)) {
                cache.detected[page]
            } else {
                state = PageState.Working("Визначення мови…")
                PdfTranslation.detect(pageText.text).also { cache.detected[page] = it }
            }

            if (source == null) {
                state = PageState.Failed("Не вдалося визначити мову сторінки. Виберіть мову оригіналу вгорі.")
                return@LaunchedEffect
            }

            val ready = PageState.Ready(source, pageText.ocr, original, sameLanguage = source == target)
            if (source == target) {
                state = ready
                return@LaunchedEffect
            }

            val key = Triple(page, source, target)
            cache.translations[key]?.let {
                translated.addAll(it)
                state = ready
                return@LaunchedEffect
            }

            if (!pool.isReady(source, target)) {
                state = PageState.Working(
                    "Завантаження мовних моделей (${TranslateLanguages.name(source)} → ${TranslateLanguages.name(target)}, ~30 МБ кожна). Потрібен інтернет лише перший раз…"
                )
                pool.prepare(source, target)
            }

            state = ready
            for (paragraph in original) {
                translated += pool.translate(source, target, paragraph)
            }
            cache.translations[key] = translated.toList()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            state = PageState.Failed("Помилка: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    Box(Modifier.fillMaxSize()) {
        when (val s = state) {
            is PageState.Working -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                CircularProgressIndicator()
                Text(s.message, style = MaterialTheme.typography.bodyMedium)
            }

            is PageState.Failed -> Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text(s.message)
                OutlinedButton(onClick = { attempt++ }) { Text("Спробувати ще") }
            }

            PageState.Empty -> Text(
                "На цій сторінці немає тексту.",
                Modifier.align(Alignment.Center).padding(32.dp),
            )

            is PageState.Ready -> ReadyPage(
                state = s,
                translated = translated,
                showOriginal = showOriginal,
            )
        }
    }
}

@Composable
private fun ReadyPage(
    state: PageState.Ready,
    translated: List<String>,
    showOriginal: Boolean,
) {
    val clipboard = LocalClipboardManager.current
    val showTranslation = !state.sameLanguage && !showOriginal
    val paragraphs = if (showTranslation) translated else state.original
    val stillTranslating = showTranslation && translated.size < state.original.size

    LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    buildString {
                        append("Мова оригіналу: ")
                        append(TranslateLanguages.name(state.source))
                        if (state.ocr) append(" · розпізнано з зображення")
                        if (state.sameLanguage) append(" · перекладати не потрібно")
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { clipboard.setText(AnnotatedString(paragraphs.joinToString("\n\n"))) }) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Копіювати текст", Modifier.size(20.dp))
                }
            }
        }
        itemsIndexed(paragraphs) { _, paragraph ->
            SelectionContainer {
                Text(
                    paragraph,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
        }
        if (stillTranslating) {
            item {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Text(
                        "Переклад ${translated.size} / ${state.original.size}…",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}
