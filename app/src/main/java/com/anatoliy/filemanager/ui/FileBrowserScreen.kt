package com.anatoliy.filemanager.ui

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderZip
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Unarchive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anatoliy.filemanager.FileCategory
import com.anatoliy.filemanager.FileItem
import com.anatoliy.filemanager.FileRepository
import com.anatoliy.filemanager.SortOrder
import com.anatoliy.filemanager.StorageRoot
import com.anatoliy.filemanager.Storages
import com.anatoliy.filemanager.Zip
import com.anatoliy.filemanager.formatSize
import com.anatoliy.filemanager.shareFiles
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileBrowserScreen(
    dir: File,
    roots: List<StorageRoot>,
    filter: FileCategory,
    onFilterChange: (FileCategory) -> Unit,
    sort: SortOrder,
    onSortChange: (SortOrder) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    viewMode: ViewMode,
    onViewModeChange: (ViewMode) -> Unit,
    scrollMemory: ScrollMemory,
    pending: PendingTransfer?,
    onPendingChange: (PendingTransfer?) -> Unit,
    onNavigate: (File) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onOpenTool: (Tool, File) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshKey by remember { mutableIntStateOf(0) }
    var refreshing by remember { mutableStateOf(false) }
    var items by remember(dir) { mutableStateOf<List<FileItem>?>(null) }

    LaunchedEffect(dir, refreshKey, showHidden) {
        items = withContext(Dispatchers.IO) { FileRepository.list(dir, showHidden) }
        refreshing = false
    }

    val root = remember(dir, roots) { Storages.rootOf(dir, roots) }
    val parent = dir.parentFile
    val canGoUp = root != null && dir.path != root.dir.path && parent != null
    BackHandler(enabled = canGoUp) { parent?.let(onNavigate) }

    var query by remember(dir) { mutableStateOf<String?>(null) }
    var results by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var searchRunning by remember { mutableStateOf(false) }
    val searchText = query?.trim().orEmpty()
    val showingResults = query != null && searchText.length >= 2
    LaunchedEffect(showingResults, searchText, dir, refreshKey, showHidden) {
        if (!showingResults) {
            results = emptyList()
            searchRunning = false
            return@LaunchedEffect
        }
        delay(300)
        searchRunning = true
        results = emptyList()
        FileRepository.search(dir, searchText, showHidden).collect { results = it }
        searchRunning = false
        refreshing = false
    }
    BackHandler(enabled = query != null) { query = null }
    val raw: List<FileItem>? = if (showingResults) results else items
    val shown: List<FileItem>? = remember(raw, sort) { raw?.let(sort::apply) }

    var selected by remember(dir, filter, showingResults, searchText) { mutableStateOf(setOf<String>()) }
    val selecting = selected.isNotEmpty()
    val toggle: (FileItem) -> Unit = { item ->
        val path = item.file.absolutePath
        selected = if (path in selected) selected - path else selected + path
    }
    BackHandler(enabled = selecting) { selected = emptySet() }

    var confirmDelete by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var renaming by remember { mutableStateOf<File?>(null) }
    var creatingFolder by remember { mutableStateOf(false) }
    var properties by remember { mutableStateOf<List<File>?>(null) }
    var extracting by remember { mutableStateOf<File?>(null) }

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    val listState = remember(dir) {
        val (i, o) = scrollMemory.get("list:${dir.path}")
        LazyListState(i, o)
    }
    val gridState = remember(dir) {
        val (i, o) = scrollMemory.get("grid:${dir.path}")
        LazyGridState(i, o)
    }
    DisposableEffect(dir) {
        onDispose {
            scrollMemory.put("list:${dir.path}", listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
            scrollMemory.put("grid:${dir.path}", gridState.firstVisibleItemIndex, gridState.firstVisibleItemScrollOffset)
        }
    }
    val searchListState = remember(searchText) { LazyListState() }
    val searchGridState = remember(searchText) { LazyGridState() }

    fun runBusy(label: String, work: suspend () -> String) {
        busy = label
        scope.launch {
            val message = work()
            busy = null
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            selected = emptySet()
            refreshKey++
        }
    }

    fun compress(files: List<File>) = runBusy("Стискаю в ZIP…") {
        val zip = Zip.compress(files, dir)
        if (zip != null) "Створено ${zip.name}" else "Не вдалося створити архів"
    }

    val open: (FileItem) -> Unit = { item ->
        when {
            item.isDirectory -> onNavigate(item.file)
            Zip.isZip(item.file) -> extracting = item.file
            else -> onOpenFile(item)
        }
    }

    val actionsFor: (FileItem) -> List<MenuAction> = { item ->
        buildList {
            if (Zip.isZip(item.file)) add(MenuAction("Розпакувати", Icons.Filled.Unarchive) { extracting = item.file })
            add(MenuAction("Перейменувати", Icons.Filled.Edit) { renaming = item.file })
            if (item.isDirectory) add(MenuAction("Стиснути в ZIP", Icons.Filled.FolderZip) { compress(listOf(item.file)) })
            add(MenuAction("Властивості", Icons.Filled.Info) { properties = listOf(item.file) })
        }
    }

    val counts = remember(shown) {
        shown.orEmpty().filterNot { it.isDirectory }.groupingBy { it.category }.eachCount()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            AppDrawerContent(
                roots = roots,
                currentRoot = root,
                currentDir = dir,
                showHidden = showHidden,
                onShowHiddenChange = onShowHiddenChange,
                onOpenDir = {
                    scope.launch { drawerState.close() }
                    onNavigate(it)
                },
                onOpenTool = {
                    scope.launch { drawerState.close() }
                    onOpenTool(it, root?.dir ?: FileRepository.rootDir)
                },
            )
        },
    ) {
        Scaffold(
            floatingActionButton = {
                if (query == null && !selecting && items != null) {
                    FloatingActionButton(onClick = { creatingFolder = true }) {
                        Icon(Icons.Filled.CreateNewFolder, contentDescription = "Нова папка")
                    }
                }
            },
            bottomBar = {
                if (pending != null) {
                    PasteBar(
                        pending = pending,
                        onCancel = { onPendingChange(null) },
                        onPaste = {
                            runBusy(if (pending.move) "Переміщення…" else "Копіювання…") {
                                val r = FileRepository.transfer(pending.files, dir, pending.move)
                                onPendingChange(null)
                                buildString {
                                    append(if (pending.move) "Переміщено: " else "Скопійовано: ")
                                    append(r.done)
                                    if (r.failed > 0) append(", помилок: ${r.failed}")
                                    if (r.skipped > 0) append(", пропущено: ${r.skipped}")
                                }
                            }
                        },
                    )
                }
            },
            topBar = {
                Column {
                    when {
                        selecting -> SelectionTopBar(
                            count = selected.size,
                            onClose = { selected = emptySet() },
                            onSelectAll = {
                                val all = visibleItems(shown.orEmpty(), filter).map { it.file.absolutePath }.toSet()
                                selected = if (selected.containsAll(all)) emptySet() else all
                            },
                            onCopy = {
                                onPendingChange(PendingTransfer(selectedFiles(shown, selected), move = false))
                                selected = emptySet()
                            },
                            onMove = {
                                onPendingChange(PendingTransfer(selectedFiles(shown, selected), move = true))
                                selected = emptySet()
                            },
                            onDelete = { confirmDelete = true },
                            onRename = { renaming = selectedFiles(shown, selected).firstOrNull() },
                            onShare = {
                                val files = selectedFiles(shown, selected).filter { it.isFile }
                                if (files.isEmpty()) {
                                    Toast.makeText(context, "Папками поділитися не можна", Toast.LENGTH_SHORT).show()
                                } else {
                                    shareFiles(context, files)
                                }
                            },
                            onCompress = { compress(selectedFiles(shown, selected)) },
                            onProperties = { properties = selectedFiles(shown, selected) },
                        )
                        query != null -> SearchTopBar(
                            query = query.orEmpty(),
                            hint = if (canGoUp) "Пошук у «${dir.name}»" else "Пошук файлів",
                            onQueryChange = { query = it },
                            onClose = { query = null },
                        )
                        else -> TopAppBar(
                            title = {
                                Text(
                                    if (canGoUp) dir.name else root?.label ?: dir.path,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                    Icon(Icons.Filled.Menu, contentDescription = "Меню")
                                }
                            },
                            actions = {
                                IconButton(onClick = { query = "" }) {
                                    Icon(Icons.Filled.Search, contentDescription = "Пошук")
                                }
                                SortMenu(sort, onSortChange)
                                IconButton(onClick = {
                                    onViewModeChange(if (viewMode == ViewMode.LIST) ViewMode.GRID else ViewMode.LIST)
                                }) {
                                    if (viewMode == ViewMode.LIST) {
                                        Icon(Icons.Filled.GridView, contentDescription = "Показати сіткою")
                                    } else {
                                        Icon(Icons.AutoMirrored.Filled.ViewList, contentDescription = "Показати списком")
                                    }
                                }
                            },
                        )
                    }
                    if (!showingResults && query == null) Breadcrumbs(dir, root, onNavigate)
                    FilterBar(selected = filter, counts = counts, onSelect = onFilterChange)
                    HorizontalDivider()
                }
            },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = refreshing,
                onRefresh = {
                    refreshing = true
                    refreshKey++
                },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                val list = shown
                if (list == null) {
                    CircularProgressIndicator(Modifier.align(Alignment.Center))
                    return@PullToRefreshBox
                }
                val visible = visibleItems(list, filter)
                val folders = visible.filter { it.isDirectory }
                val files = visible.filter { !it.isDirectory }
                val onTap: (FileItem) -> Unit = { if (selecting) toggle(it) else open(it) }
                val isSelected: (FileItem) -> Boolean = { it.file.absolutePath in selected }
                val lState = if (showingResults) searchListState else listState
                val gState = if (showingResults) searchGridState else gridState

                when {
                    visible.isEmpty() -> EmptyState(
                        if (showingResults) { if (searchRunning) "Шукаю…" else "Нічого не знайдено" }
                        else if (filter == FileCategory.ALL) "Папка порожня"
                        else "Тут немає файлів типу «${filter.label}»",
                    )
                    filter.isVisual -> MediaGrid(
                        folders, files, gState, selecting, isSelected, onTap, toggle, showingResults, actionsFor,
                    )
                    viewMode == ViewMode.GRID -> FileGrid(visible, gState, selecting, isSelected, onTap, toggle)
                    else -> LazyColumn(
                        state = lState,
                        contentPadding = PaddingValues(bottom = 88.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.file.absolutePath }) { item ->
                            FileListRow(
                                item, selecting, isSelected(item), onTap, toggle,
                                showPath = showingResults,
                                actions = actionsFor,
                            )
                            HorizontalDivider()
                        }
                    }
                }
                if (searchRunning) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter))
            }
        }
    }

    busy?.let { BusyDialog(it) }

    properties?.let { PropertiesDialog(it, onDismiss = { properties = null }) }

    extracting?.let { zip ->
        AlertDialog(
            onDismissRequest = { extracting = null },
            icon = { Icon(Icons.Filled.Unarchive, contentDescription = null) },
            title = { Text("Розпакувати архів?") },
            text = { Text("«${zip.name}» (${formatSize(context, zip.length())}) буде розпаковано в нову папку поруч.") },
            confirmButton = {
                TextButton(onClick = {
                    extracting = null
                    runBusy("Розпаковую…") {
                        val folder = Zip.extract(zip)
                        if (folder != null) "Розпаковано в «${folder.name}»" else "Не вдалося розпакувати"
                    }
                }) { Text("Розпакувати") }
            },
            dismissButton = { TextButton(onClick = { extracting = null }) { Text("Скасувати") } },
        )
    }

    if (creatingFolder) {
        NewFolderDialog(
            dir = dir,
            onDismiss = { creatingFolder = false },
            onConfirm = { name ->
                creatingFolder = false
                scope.launch {
                    if (FileRepository.createFolder(dir, name) == null) {
                        Toast.makeText(context, "Не вдалося створити папку", Toast.LENGTH_SHORT).show()
                    }
                    refreshKey++
                }
            },
        )
    }

    renaming?.let { file ->
        RenameDialog(
            file = file,
            onDismiss = { renaming = null },
            onConfirm = { newName ->
                renaming = null
                scope.launch {
                    if (FileRepository.rename(file, newName) == null) {
                        Toast.makeText(context, "Не вдалося перейменувати", Toast.LENGTH_SHORT).show()
                    }
                    selected = emptySet()
                    refreshKey++
                }
            },
        )
    }

    if (confirmDelete) {
        val chosen = shown.orEmpty().filter { it.file.absolutePath in selected }
        DeleteDialog(
            count = chosen.size,
            bytes = chosen.sumOf { it.size },
            hasFolders = chosen.any { it.isDirectory },
            onDismiss = { confirmDelete = false },
            onConfirm = {
                confirmDelete = false
                val toDelete = chosen.map { it.file }
                runBusy("Видалення…") {
                    val failed = FileRepository.delete(toDelete)
                    if (failed == 0) "Видалено: ${toDelete.size}"
                    else "Видалено: ${toDelete.size - failed}, не вдалося: $failed"
                }
            },
        )
    }
}

@Composable
fun DeleteDialog(count: Int, bytes: Long, hasFolders: Boolean, onDismiss: () -> Unit, onConfirm: () -> Unit, warning: String? = null) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Filled.Delete, contentDescription = null) },
        title = { Text("Видалити вибране?") },
        text = {
            Text(
                buildString {
                    append("Буде видалено: $count")
                    if (bytes > 0) append(" (${formatSize(context, bytes)})")
                    append(".")
                    if (hasFolders) append("\nПапки видаляються разом з усім вмістом.")
                    if (warning != null) append("\n$warning")
                    append("\nЦю дію не можна скасувати.")
                }
            )
        },
        confirmButton = { TextButton(onClick = onConfirm) { Text("Видалити") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Скасувати") } },
    )
}
