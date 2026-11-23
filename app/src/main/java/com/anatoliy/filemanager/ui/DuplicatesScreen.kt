package com.anatoliy.filemanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.anatoliy.filemanager.DuplicateGroup
import com.anatoliy.filemanager.FileItem
import com.anatoliy.filemanager.FileRepository
import com.anatoliy.filemanager.StorageScan
import com.anatoliy.filemanager.formatDate
import com.anatoliy.filemanager.formatSize
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File

class DuplicatesViewModel : ViewModel() {
    var groups by mutableStateOf<List<DuplicateGroup>?>(null)
        private set
    var phase by mutableStateOf("Сканую")
        private set
    var done by mutableIntStateOf(0)
        private set
    var total by mutableIntStateOf(0)
        private set
    var selected by mutableStateOf(setOf<String>())
    private var root: File? = null
    private var job: Job? = null

    fun start(newRoot: File, force: Boolean = false) {
        if (!force && root == newRoot && (groups != null || job?.isActive == true)) return
        root = newRoot
        job?.cancel()
        groups = null
        selected = emptySet()
        job = viewModelScope.launch {
            groups = StorageScan.duplicates(newRoot) { p, d, t -> phase = p; done = d; total = t }
        }
    }

    fun autoSelect() {
        selected = groups.orEmpty().flatMap { g -> g.files.drop(1).map { it.file.absolutePath } }.toSet()
    }

    fun removeDeleted(paths: Set<String>) {
        groups = groups?.map { g -> g.copy(files = g.files.filterNot { it.file.absolutePath in paths }) }
            ?.filter { it.files.size > 1 }
        selected = emptySet()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DuplicatesScreen(
    root: File,
    onBack: () -> Unit,
    onOpenFile: (FileItem) -> Unit,
    vm: DuplicatesViewModel = viewModel(),
) {
    BackHandler(onBack = onBack)
    val context = LocalContext.current
    LaunchedEffect(root) { vm.start(root) }
    var confirmDelete by remember { mutableStateOf(false) }
    val groups = vm.groups
    val selectedItems = groups.orEmpty().flatMap { it.files }.filter { it.file.absolutePath in vm.selected }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (selectedItems.isEmpty()) "Дублікати" else "Вибрано: ${selectedItems.size}") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад") }
                },
                actions = {
                    if (!groups.isNullOrEmpty()) {
                        IconButton(onClick = { vm.autoSelect() }) {
                            Icon(Icons.Filled.AutoFixHigh, contentDescription = "Вибрати всі копії, крім найстарішої")
                        }
                    }
                    if (selectedItems.isNotEmpty()) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Видалити")
                        }
                    } else {
                        IconButton(onClick = { vm.start(root, force = true) }) {
                            Icon(Icons.Filled.Refresh, contentDescription = "Шукати знову")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize()) {
            when {
                groups == null -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(if (vm.total > 0) "${vm.phase}: ${vm.done} з ${vm.total}" else "${vm.phase}: ${vm.done}")
                    if (vm.total > 0) {
                        LinearProgressIndicator(
                            progress = { vm.done.toFloat() / vm.total },
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        )
                    }
                }
                groups.isEmpty() -> Text("Дублікатів не знайдено 🎉", Modifier.align(Alignment.Center))
                else -> LazyColumn {
                    item {
                        Text(
                            "Груп: ${groups.size} · можна звільнити ${formatSize(context, groups.sumOf { it.wasted })}\n" +
                                "Кнопка ✨ вибирає всі копії, крім найстарішої в кожній групі.",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                    groups.forEachIndexed { gi, group ->
                        item(key = "h$gi") {
                            Text(
                                "${group.files.size} копії · ${formatSize(context, group.size)} кожна",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
                            )
                        }
                        items(group.files, key = { "g$gi:" + it.file.path }) { item ->
                            val checked = item.file.absolutePath in vm.selected
                            val toggle = {
                                val p = item.file.absolutePath
                                vm.selected = if (checked) vm.selected - p else vm.selected + p
                            }
                            FileRow(
                                item = item,
                                subtitle = "${item.file.parent} · ${formatDate(item.lastModified)}",
                                onClick = { if (vm.selected.isEmpty()) onOpenFile(item) else toggle() },
                                onLongClick = toggle,
                                selected = checked,
                                trailing = { Checkbox(checked = checked, onCheckedChange = { toggle() }) },
                            )
                        }
                        item(key = "d$gi") { HorizontalDivider() }
                    }
                }
            }
        }
    }

    if (confirmDelete) {
        val whole = groups.orEmpty().count { g -> g.files.all { it.file.absolutePath in vm.selected } }
        DeleteDialog(
            count = selectedItems.size,
            bytes = selectedItems.sumOf { it.size },
            hasFolders = false,
            warning = if (whole > 0) "Увага: у $whole групах вибрано ВСІ копії — файл зникне повністю." else null,
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
