package com.anatoliy.filemanager.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.anatoliy.filemanager.FileItem
import com.anatoliy.filemanager.FileRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class UnusedFilesViewModel : ViewModel() {
    var days by mutableIntStateOf(90)
        private set
    var scanning by mutableStateOf(false)
        private set
    var scannedCount by mutableIntStateOf(0)
        private set
    var results by mutableStateOf<List<FileItem>>(emptyList())
        private set
    var selected by mutableStateOf(setOf<String>())
        private set

    private var scanJob: Job? = null
    private var hasScanned = false

    fun scanIfNeeded() {
        if (!hasScanned) scan()
    }

    fun setPeriod(newDays: Int) {
        if (newDays == days && hasScanned) return
        days = newDays
        scan()
    }

    fun scan() {
        hasScanned = true
        scanJob?.cancel()
        scanJob = viewModelScope.launch {
            scanning = true
            scannedCount = 0
            selected = emptySet()
            try {
                results = FileRepository.findUnused(FileRepository.rootDir, days) { scannedCount = it }
            } finally {
                scanning = false
            }
        }
    }

    fun toggle(path: String, checked: Boolean) {
        selected = if (checked) selected + path else selected - path
    }

    fun toggleAll() {
        selected = if (selected.size == results.size) emptySet()
        else results.map { it.file.absolutePath }.toSet()
    }

    fun selectedItems(): List<FileItem> = results.filter { it.file.absolutePath in selected }

    fun deleteSelected(onDone: (Int, Int) -> Unit) {
        val toDelete = selectedItems().map { it.file }
        viewModelScope.launch {
            val failed = FileRepository.delete(toDelete)
            results = results.filter { it.file.exists() }
            selected = emptySet()
            onDone(toDelete.size - failed, failed)
        }
    }
}
