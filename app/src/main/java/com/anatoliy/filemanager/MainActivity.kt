package com.anatoliy.filemanager

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.anatoliy.filemanager.ui.AnalysisScreen
import com.anatoliy.filemanager.ui.DuplicatesScreen
import com.anatoliy.filemanager.ui.FileBrowserScreen
import com.anatoliy.filemanager.ui.ImageViewerScreen
import com.anatoliy.filemanager.ui.PdfViewerScreen
import com.anatoliy.filemanager.ui.PendingTransfer
import com.anatoliy.filemanager.ui.PermissionScreen
import com.anatoliy.filemanager.ui.ScrollMemory
import com.anatoliy.filemanager.ui.TextViewerScreen
import com.anatoliy.filemanager.ui.Tool
import com.anatoliy.filemanager.ui.UnusedFilesScreen
import com.anatoliy.filemanager.ui.ViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class MainActivity : ComponentActivity() {
    private var incoming by mutableStateOf<IncomingState?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            MaterialTheme(
                colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme()
            ) {
                Surface {
                    when (val s = incoming) {
                        null -> App()
                        IncomingState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                        IncomingState.Failed -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Не вдалося відкрити файл")
                        }
                        is IncomingState.Ready -> ViewerFor(s.file, onBack = { finish() })
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri = intent.data ?: return
        incoming = IncomingState.Loading
        lifecycleScope.launch {
            val file = withContext(Dispatchers.IO) { runCatching { localCopy(this@MainActivity, uri) }.getOrNull() }
            incoming = if (file != null) IncomingState.Ready(file) else IncomingState.Failed
        }
    }
}

private sealed interface IncomingState {
    data object Loading : IncomingState
    data object Failed : IncomingState
    data class Ready(val file: File) : IncomingState
}

private fun localCopy(context: Context, uri: Uri): File? {
    if (uri.scheme == "file") return uri.path?.let(::File)?.takeIf { it.canRead() }
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    }?.replace('/', '_') ?: "file"
    val dir = File(context.cacheDir, "incoming").apply {
        deleteRecursively()
        mkdirs()
    }
    val out = File(dir, name)
    context.contentResolver.openInputStream(uri)?.use { input -> out.outputStream().use { input.copyTo(it) } } ?: return null
    return out
}

@Composable
private fun ViewerFor(file: File, onBack: () -> Unit) {
    when {
        file.extension.equals("pdf", ignoreCase = true) -> PdfViewerScreen(file = file, onBack = onBack)
        isImageFile(file) -> ImageViewerScreen(file = file, onBack = onBack)
        else -> TextViewerScreen(file = file, onBack = onBack)
    }
}

sealed interface Screen {
    data object Browser : Screen
    data object Unused : Screen
    data class Analysis(val root: File) : Screen
    data class Duplicates(val root: File) : Screen
    data class Pdf(val file: File) : Screen
    data class Image(val file: File) : Screen
    data class Text(val file: File) : Screen
}

@Composable
fun App() {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(StoragePermission.hasAccess(context)) }
    var roots by remember { mutableStateOf(Storages.list(context)) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasAccess = StoragePermission.hasAccess(context)
                roots = Storages.list(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (!hasAccess) {
        PermissionScreen(onGranted = { hasAccess = true })
        return
    }

    var currentDir by rememberSaveable { mutableStateOf(FileRepository.rootDir.absolutePath) }
    if (!File(currentDir).isDirectory) currentDir = FileRepository.rootDir.absolutePath
    var filter by rememberSaveable { mutableStateOf(FileCategory.ALL) }
    var pending by remember { mutableStateOf<PendingTransfer?>(null) }
    val prefs = remember { context.getSharedPreferences("settings", Context.MODE_PRIVATE) }
    var sort by remember { mutableStateOf(SortOrder.load(prefs)) }
    var showHidden by remember { mutableStateOf(prefs.getBoolean("show_hidden", false)) }
    var viewMode by remember {
        mutableStateOf(runCatching { ViewMode.valueOf(prefs.getString("view_mode", null)!!) }.getOrDefault(ViewMode.LIST))
    }
    val scrollMemory = remember { ScrollMemory() }
    val backStack = remember { mutableStateListOf<Screen>(Screen.Browser) }
    val push: (Screen) -> Unit = { backStack.add(it) }
    val pop: () -> Unit = { if (backStack.size > 1) backStack.removeAt(backStack.lastIndex) }

    val openFile: (FileItem) -> Unit = { item ->
        val f = item.file
        when {
            item.isPdf -> push(Screen.Pdf(f))
            item.isImage -> push(Screen.Image(f))
            isTextFile(f) -> push(Screen.Text(f))
            f.extension.equals("apk", ignoreCase = true) -> installApk(context, f)
            else -> openWithExternalApp(context, f)
        }
    }

    when (val screen = backStack.last()) {
        Screen.Browser -> FileBrowserScreen(
            dir = File(currentDir),
            roots = roots,
            filter = filter,
            onFilterChange = { filter = it },
            sort = sort,
            onSortChange = { sort = it; it.save(prefs) },
            showHidden = showHidden,
            onShowHiddenChange = {
                showHidden = it
                prefs.edit().putBoolean("show_hidden", it).apply()
            },
            viewMode = viewMode,
            onViewModeChange = {
                viewMode = it
                prefs.edit().putString("view_mode", it.name).apply()
            },
            scrollMemory = scrollMemory,
            pending = pending,
            onPendingChange = { pending = it },
            onNavigate = { currentDir = it.absolutePath },
            onOpenFile = openFile,
            onOpenTool = { tool, root ->
                push(
                    when (tool) {
                        Tool.ANALYSIS -> Screen.Analysis(root)
                        Tool.DUPLICATES -> Screen.Duplicates(root)
                        Tool.UNUSED -> Screen.Unused
                    }
                )
            },
        )
        Screen.Unused -> UnusedFilesScreen(
            onBack = pop,
            onOpenPdf = { push(Screen.Pdf(it)) },
            onOpenImage = { push(Screen.Image(it)) },
        )
        is Screen.Analysis -> AnalysisScreen(
            root = screen.root,
            onBack = pop,
            onOpenFolder = {
                currentDir = it.absolutePath
                backStack.retainAll { s -> s == Screen.Browser }
            },
            onOpenFile = openFile,
        )
        is Screen.Duplicates -> DuplicatesScreen(root = screen.root, onBack = pop, onOpenFile = openFile)
        is Screen.Pdf -> PdfViewerScreen(file = screen.file, onBack = pop)
        is Screen.Image -> ImageViewerScreen(file = screen.file, onBack = pop)
        is Screen.Text -> TextViewerScreen(file = screen.file, onBack = pop)
    }
}
