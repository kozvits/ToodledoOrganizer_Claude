package com.kozvits.toodledo.presentation.ui

/**
 * Фильтры для списка задач.
 * Вынесен в отдельный файл, чтобы быть доступным из любого фрагмента.
 */
sealed class TaskFilter {
    object All : TaskFilter()
    data class ByFolder(val folderId: Long, val name: String) : TaskFilter()
    data class ByContext(val contextId: Long, val name: String) : TaskFilter()
}
