package com.kozvits.toodledo.presentation.viewmodel

import androidx.lifecycle.*
import com.kozvits.toodledo.domain.model.*
import com.kozvits.toodledo.domain.usecase.*
import com.kozvits.toodledo.presentation.ui.TaskFilter
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// ─── Task List ────────────────────────────────────────────────────────────────

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
@HiltViewModel
class TaskListViewModel @Inject constructor(
    private val getTasksUseCase: GetTasksUseCase,
    private val getCompletedTasksUseCase: GetCompletedTasksUseCase,
    private val getFoldersUseCase: GetFoldersUseCase,
    private val getContextsUseCase: GetContextsUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase,
    private val deleteTaskUseCase: DeleteTaskUseCase,
    private val searchTasksUseCase: SearchTasksUseCase,
    private val getTasksByFolderUseCase: GetTasksByFolderUseCase,
    private val getTasksByContextUseCase: GetTasksByContextUseCase
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _activeFilter = MutableStateFlow<TaskFilter>(TaskFilter.All)
    private val _showCompleted = MutableStateFlow(false)

    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    val activeFilter: StateFlow<TaskFilter> = _activeFilter.asStateFlow()
    val showCompleted: StateFlow<Boolean> = _showCompleted.asStateFlow()

    val tasks: StateFlow<List<Task>> = combine(
        _searchQuery.debounce(300),
        _activeFilter
    ) { query, filter -> Pair(query, filter) }
        .flatMapLatest { (query, filter) ->
            when {
                query.isNotBlank() -> searchTasksUseCase(query)
                filter is TaskFilter.ByFolder -> getTasksByFolderUseCase(filter.folderId)
                filter is TaskFilter.ByContext -> getTasksByContextUseCase(filter.contextId)
                else -> getTasksUseCase()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val completedTasks: StateFlow<List<Task>> = getCompletedTasksUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val folders: StateFlow<List<Folder>> = getFoldersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contexts: StateFlow<List<TaskContext>> = getContextsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSearch(query: String) { _searchQuery.value = query }
    fun clearSearch() { _searchQuery.value = "" }
    fun setFilter(filter: TaskFilter) { _activeFilter.value = filter }
    fun toggleShowCompleted() { _showCompleted.value = !_showCompleted.value }
    fun completeTask(id: Long) = viewModelScope.launch { completeTaskUseCase(id) }
    fun deleteTask(id: Long) = viewModelScope.launch { deleteTaskUseCase(id) }
}

// ─── Edit Task ────────────────────────────────────────────────────────────────

@HiltViewModel
class EditTaskViewModel @Inject constructor(
    private val getTaskByIdUseCase: GetTaskByIdUseCase,
    private val addTaskUseCase: AddTaskUseCase,
    private val updateTaskUseCase: UpdateTaskUseCase,
    private val getFoldersUseCase: GetFoldersUseCase,
    private val getContextsUseCase: GetContextsUseCase
) : ViewModel() {

    private val _task = MutableLiveData(Task())
    val task: LiveData<Task> = _task

    private val _saveComplete = MutableLiveData(false)
    val saveComplete: LiveData<Boolean> = _saveComplete

    val folders: StateFlow<List<Folder>> = getFoldersUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val contexts: StateFlow<List<TaskContext>> = getContextsUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun loadTask(id: Long) = viewModelScope.launch {
        if (id > 0L) getTaskByIdUseCase(id)?.let { _task.value = it }
    }

    fun updateTitle(v: String)                { _task.value = _task.value?.copy(title = v) }
    fun updateNote(v: String)                 { _task.value = _task.value?.copy(note = v) }
    fun updatePriority(v: Priority)           { _task.value = _task.value?.copy(priority = v) }
    fun updateStatus(v: TaskStatus)           { _task.value = _task.value?.copy(status = v) }
    fun updateFolder(id: Long, name: String)  { _task.value = _task.value?.copy(folderId = id, folderName = name) }
    fun updateContext(id: Long, name: String) { _task.value = _task.value?.copy(contextId = id, contextName = name) }
    fun updateDueDate(ts: Long)               { _task.value = _task.value?.copy(dueDate = ts) }
    fun updateStartDate(ts: Long)             { _task.value = _task.value?.copy(startDate = ts) }
    fun updateRepeat(v: RepeatType)           { _task.value = _task.value?.copy(repeat = v) }
    fun updateStar(v: Boolean)                { _task.value = _task.value?.copy(star = v) }
    fun updateHot(v: Boolean)                 { _task.value = _task.value?.copy(isHot = v) }
    fun updateTag(v: String)                  { _task.value = _task.value?.copy(tag = v) }
    fun updateLength(v: Int)                  { _task.value = _task.value?.copy(length = v) }
    fun updateRemind(v: Long)                 { _task.value = _task.value?.copy(remind = v) }

    fun save() = viewModelScope.launch {
        val t = _task.value ?: return@launch
        if (t.title.isBlank()) return@launch
        if (t.id == 0L) addTaskUseCase(t) else updateTaskUseCase(t)
        _saveComplete.value = true
    }
}

// ─── Hot List ────────────────────────────────────────────────────────────────

@HiltViewModel
class HotListViewModel @Inject constructor(
    private val getSortedHotListUseCase: GetSortedHotListUseCase,
    private val completeTaskUseCase: CompleteTaskUseCase
) : ViewModel() {

    private val _sort1 = MutableStateFlow(SortConfig(SortField.PRIORITY, SortOrder.DESC))
    private val _sort2 = MutableStateFlow<SortConfig?>(SortConfig(SortField.DUE_DATE, SortOrder.ASC))
    private val _sort3 = MutableStateFlow<SortConfig?>(null)

    val sort1: StateFlow<SortConfig> = _sort1.asStateFlow()
    val sort2: StateFlow<SortConfig?> = _sort2.asStateFlow()
    val sort3: StateFlow<SortConfig?> = _sort3.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val hotList: StateFlow<List<Task>> = combine(_sort1, _sort2, _sort3) { s1, s2, s3 ->
        Triple(s1, s2, s3)
    }.flatMapLatest { (s1, s2, s3) ->
        getSortedHotListUseCase(s1, s2, s3)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setSort1(field: SortField, order: SortOrder) { _sort1.value = SortConfig(field, order) }
    fun setSort2(field: SortField?, order: SortOrder) { _sort2.value = field?.let { SortConfig(it, order) } }
    fun setSort3(field: SortField?, order: SortOrder) { _sort3.value = field?.let { SortConfig(it, order) } }

    fun completeTask(id: Long) = viewModelScope.launch { completeTaskUseCase(id) }
}

// ─── Sync ─────────────────────────────────────────────────────────────────────

@HiltViewModel
class SyncViewModel @Inject constructor(
    private val syncNowUseCase: SyncNowUseCase,
    private val getSyncSettingsUseCase: GetSyncSettingsUseCase,
    private val saveSyncSettingsUseCase: SaveSyncSettingsUseCase,
    private val observeSyncStateUseCase: ObserveSyncStateUseCase
) : ViewModel() {

    val syncState: StateFlow<SyncState> = observeSyncStateUseCase()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncState.Idle)

    private val _settings = MutableLiveData(SyncSettings())
    val settings: LiveData<SyncSettings> = _settings

    init {
        viewModelScope.launch { _settings.value = getSyncSettingsUseCase() }
    }

    fun syncNow() = viewModelScope.launch { syncNowUseCase() }

    fun saveSettings(settings: SyncSettings) = viewModelScope.launch {
        saveSyncSettingsUseCase(settings)
        _settings.value = settings
    }
}
