package com.anatoliy.filemanager.ui

import android.graphics.Bitmap
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.PdfDocument
import com.anatoliy.filemanager.openWithExternalApp
import com.anatoliy.filemanager.shareFiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private const val MAX_ZOOM = 5f
private const val DOUBLE_TAP_ZOOM = 2.5f

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfViewerScreen(file: File, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current

    val document = remember(file) { runCatching { PdfDocument(file) } }
    DisposableEffect(document) {
        onDispose { document.getOrNull()?.close() }
    }

    val listState = rememberLazyListState()
    val currentPage by remember { derivedStateOf { listState.firstVisibleItemIndex + 1 } }
    val pageCount = document.getOrNull()?.pageCount ?: 0

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(file.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
                    }
                },
                actions = {
                    if (pageCount > 0) {
                        Text("$currentPage / $pageCount", Modifier.padding(end = 4.dp))
                    }
                    IconButton(onClick = { shareFiles(context, listOf(file)) }) {
                        Icon(Icons.Filled.Share, contentDescription = "Поділитися")
                    }
                    IconButton(onClick = { openWithExternalApp(context, file) }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Відкрити в іншому додатку")
                    }
                },
            )
        },
    ) { padding ->
        val doc = document.getOrNull()
        if (doc == null) {
            Box(Modifier.padding(padding).fillMaxSize().padding(24.dp)) {
                Text(
                    "Не вдалося відкрити PDF: ${document.exceptionOrNull()?.message ?: "невідома помилка"}",
                    Modifier.align(Alignment.Center),
                )
            }
        } else {
            BoxWithConstraints(
                Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clipToBounds()
            ) {
                val density = LocalDensity.current
                val pagePadding = 8.dp
                val widthPx = with(density) { (maxWidth - pagePadding * 2).roundToPx() }
                val viewW = with(density) { maxWidth.toPx() }
                val viewH = with(density) { maxHeight.toPx() }
                val scope = rememberCoroutineScope()

                var scale by remember { mutableFloatStateOf(1f) }
                var offset by remember { mutableStateOf(Offset.Zero) }
                var renderFactor by remember { mutableIntStateOf(1) }

                LaunchedEffect(scale) {
                    delay(300)
                    renderFactor = if (scale > 1.25f) 2 else 1
                }

                fun clamp(o: Offset, s: Float) = Offset(
                    o.x.coerceIn(-(viewW * (s - 1f)), 0f),
                    o.y.coerceIn(-(viewH * (s - 1f)), 0f),
                )

                fun zoomTo(newScale: Float, focus: Offset) {
                    val s = newScale.coerceIn(1f, MAX_ZOOM)
                    offset = clamp(focus - (focus - offset) * (s / scale), s)
                    scale = s
                }

                fun pan(delta: Offset) {
                    val scrollWanted = -delta.y / scale
                    val consumed = listState.dispatchRawDelta(scrollWanted)
                    val leftover = scrollWanted - consumed
                    offset = clamp(Offset(offset.x + delta.x, offset.y - leftover * scale), scale)
                }

                LazyColumn(
                    state = listState,
                    userScrollEnabled = scale == 1f,
                    contentPadding = PaddingValues(pagePadding),
                    verticalArrangement = Arrangement.spacedBy(pagePadding),
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = { tap ->
                                val from = scale
                                val to = if (from > 1.05f) 1f else DOUBLE_TAP_ZOOM
                                scope.launch {
                                    animate(from, to) { value, _ -> zoomTo(value, tap) }
                                }
                            })
                        }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown(requireUnconsumed = false)
                                do {
                                    val event = awaitPointerEvent(PointerEventPass.Initial)
                                    val fingers = event.changes.count { it.pressed }
                                    if (fingers >= 2 || scale > 1f) {
                                        val zoom = event.calculateZoom()
                                        val centroid = event.calculateCentroid(useCurrent = true)
                                        if (fingers >= 2 && zoom != 1f && centroid != Offset.Unspecified) {
                                            zoomTo(scale * zoom, centroid)
                                        }
                                        pan(event.calculatePan())
                                        event.changes.forEach { if (it.positionChanged()) it.consume() }
                                    }
                                } while (event.changes.any { it.pressed })
                                if (scale < 1.02f) {
                                    scale = 1f
                                    offset = Offset.Zero
                                }
                            }
                        }
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                            transformOrigin = TransformOrigin(0f, 0f)
                        },
                ) {
                    items(count = doc.pageCount) { index ->
                        PdfPage(doc, index, widthPx * renderFactor)
                    }
                }
            }
        }
    }
}

@Composable
private fun PdfPage(doc: PdfDocument, index: Int, widthPx: Int) {
    var bitmap by remember(index) { mutableStateOf<Bitmap?>(null) }
    LaunchedEffect(index, widthPx) {
        doc.renderPage(index, widthPx)?.let { bitmap = it }
    }

    val bmp = bitmap
    if (bmp != null) {
        Image(
            bitmap = bmp.asImageBitmap(),
            contentDescription = "Сторінка ${index + 1}",
            modifier = Modifier.fillMaxWidth(),
            contentScale = ContentScale.FillWidth,
        )
    } else {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f / 1.414f)
                .background(MaterialTheme.colorScheme.surface)
        ) {
            CircularProgressIndicator(Modifier.align(Alignment.Center))
        }
    }
}
