package com.anatoliy.filemanager

import android.content.SharedPreferences

enum class SortBy(val label: String, val defaultDescending: Boolean) {
    NAME("За назвою", false),
    DATE("За датою", true),
    SIZE("За розміром", true),
    TYPE("За типом", false),
}

data class SortOrder(val by: SortBy = SortBy.NAME, val descending: Boolean = false) {
    fun apply(items: List<FileItem>): List<FileItem> {
        val byName = compareBy<FileItem> { it.name.lowercase() }
        val main: Comparator<FileItem> = when (by) {
            SortBy.NAME -> byName
            SortBy.DATE -> compareBy<FileItem> { it.lastModified }.then(byName)
            SortBy.SIZE -> compareBy<FileItem> { it.size }.then(byName)
            SortBy.TYPE -> compareBy<FileItem> { it.file.extension.lowercase() }.then(byName)
        }
        val ordered = if (descending) main.reversed() else main
        return items.sortedWith(compareByDescending<FileItem> { it.isDirectory }.then(ordered))
    }

    fun save(prefs: SharedPreferences) {
        prefs.edit().putString(KEY_BY, by.name).putBoolean(KEY_DESC, descending).apply()
    }

    companion object {
        private const val KEY_BY = "sort_by"
        private const val KEY_DESC = "sort_desc"

        fun load(prefs: SharedPreferences): SortOrder {
            val by = runCatching { SortBy.valueOf(prefs.getString(KEY_BY, null)!!) }.getOrDefault(SortBy.NAME)
            return SortOrder(by, prefs.getBoolean(KEY_DESC, by.defaultDescending))
        }
    }
}
