package com.anatoliy.filemanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anatoliy.filemanager.FileCategory
import com.anatoliy.filemanager.FileItem
import com.anatoliy.filemanager.FileRepository
import com.anatoliy.filemanager.SpaceReport
import com.anatoliy.filemanager.StorageScan
import com.anatoliy.filemanager.formatSize
import com.anatoliy.filemanager.shareFiles
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class AnalysisViewModel : ViewModel() {
    var report by mutableStateOf<SpaceReport?>(null)
        private set
    var scanned by mutableIntStateOf(0)
        private set
    var selected by mutableStateOf(setOf<String>())
    private var root: File? = null
    private var job: Job? = null

    fun start(newRoot: File, force: Boolean = false) {
        if (!force && root == newRoot && (report != null || job?.isActive == true)) return
        root = newRoot
        job?.cancel()
        report = null
        selected = emptySet()
        job = viewModelScope.launch {
            scanned = 0
            report = StorageScan.analyze(newRoot) { scanned = it }
        }
    }

    fun removeDeleted(paths: Set<String>) {
        val r = report ?: return
        report = r.copy(largestFiles = r.largestFiles.filterNot { it.file.absolutePath in paths })
        selected = emptySet()
    }
}

private val categoryColors = mapOf(
    FileCategory.IMAGES to Color(0xFF42A5F5),
    FileCategory.VIDEO to Color(0xFFEF5350),
    FileCategory.AUDIO to Color(0xFFAB47BC),
    FileCategory.DOCUMENTS to Color(0xFF26A69A),
    FileCategory.FB2 to Color(0xFF8D6E63),
    FileCategory.ARCHIVES to Color(0xFFFFA726),
    FileCategory.APK to Color(0xFF66BB6A),
    FileCategory.OTHER to Color(0xFF90A4AE),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(
    root: File,
    onBack: () -> Unit,
    onOpenFolder: (File) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    vm: AnalysisViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    LaunchedEffect(root) { vm.start(root) }
    var confirmDelete by remember { mutableStateOf(false) }
    val report = vm.report
    val selectedItems = report?.largestFiles.orEmpty().filter { it.file.absolutePath in vm.selected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedItems.isEmpty()) "Аналіз пам'яті" else "Вибрано: ${selectedItems.size}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    if (selectedItems.isNotEmpty()) {
                        IconButton(onClick = { shareFiles(context, selectedItems.map { it.file }) }) {
                            Icon(Icons.Filled.Share, contentDescription = "Поділитися")
                        }
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Видалити")
                        }
                    } else {
                        IconButton(onClick = { vm.start(root, force = true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Сканувати знову")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            if (report == null) {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text("Переглянуто файлів: ${vm.scanned}")
                }
                return@Box
            }
            LazyColumn {
                item { StorageHeader(report) }
                item { SectionHeader("За типами") }
                items(report.byCategory, key = { it.first.name }) { (cat, bytes, count) ->
                    BarRow(
                        label = cat.label,
                        detail = "${formatSize(context, bytes)} · файлів: $count",
                        fraction = bytes.toFloat() / report.scannedBytes.coerceAtLeast(1),
                        color = categoryColors[cat] ?: MaterialTheme.colorScheme.primary,
                    )
                }
                item { SectionHeader("Найбільші папки") }
                items(report.topFolders, key = { "f:" + it.first.path }) { (folder, bytes) ->
                    ListItem(
                        modifier = Modifier.clickable { onOpenFolder(folder) },
                        leadingContent = { Icon(Icons.Filled.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                        headlineContent = { Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            Column {
                                LinearProgressIndicator(
                                    progress = { bytes.toFloat() / report.scannedBytes.coerceAtLeast(1) },
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                )
                                Text(formatSize(context, bytes))
                            }
                        },
                    )
                }
                item { SectionHeader("Найбільші файли") }
                items(report.largestFiles, key = { "l:" + it.file.path }) { item ->
                    val checked = item.file.absolutePath in vm.selected
                    val toggle = {
                        val p = item.file.absolutePath
                        vm.selected = if (checked) vm.selected - p else vm.selected + p
                    }
                    FileRow(
                        item = item,
                        subtitle = "${formatSize(context, item.size)} · ${item.file.parent}",
                        onClick = { if (vm.selected.isEmpty()) onOpenFile(item) else toggle() },
                        onLongClick = toggle,
                        selected = checked,
                        trailing = { Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }

    if (confirmDelete) {
        DeleteDialog(
            count = selectedItems.size,
            bytes = selectedItems.sumOf { it.size },
            hasFolders = false,
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                val files = selectedItems.map { it.file }
                vm.viewModelScope.launch {
                    val failed = FileRepository.delete(files)
                    vm.removeDeleted(files.filterNot { it.exists() }.map { it.absolutePath }.toSet())
                    Toast.makeText(context, if (failed == 0) "Видалено: ${files.size}" else "Не вдалося: $failed", Toast.LENGTH_SHORT).show()
                }
            },
        )
    }
}

@Composable
private fun StorageHeader(r: SpaceReport) {
    val context = LocalContext.current
    val used = r.totalBytes - r.freeBytes
    val other = (used - r.scannedBytes).coerceAtLeast(0)
    Column(Modifier.padding(16.dp)) {
        Text("Зайнято ${formatSize(context, used)} з ${formatSize(context, r.totalBytes)}", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().height(18.dp).clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            r.byCategory.forEach { (cat, bytes, _) ->
                val w = bytes.toFloat() / r.totalBytes.coerceAtLeast(1)
                if (w > 0.002f) Box(Modifier.weight(w).fillMaxSize().background(categoryColors[cat] ?: Color.Gray))
            }
            val o = other.toFloat() / r.totalBytes.coerceAtLeast(1)
            if (o > 0.002f) Box(Modifier.weight(o).fillMaxSize().background(MaterialTheme.colorScheme.outline))
            val free = r.freeBytes.toFloat() / r.totalBytes.coerceAtLeast(1)
            if (free > 0.002f) Spacer(Modifier.weight(free))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Вільно ${formatSize(context, r.freeBytes)} · файли ${formatSize(context, r.scannedBytes)} (${r.fileCount}) · " +
                "система й додатки ${formatSize(context, other)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun BarRow(label: String, detail: String, fraction: Float, color: Color) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).background(color, CircleShape))
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
            Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color))
        }
        Spacer(Modifier.width(1.dp))
    }
}
