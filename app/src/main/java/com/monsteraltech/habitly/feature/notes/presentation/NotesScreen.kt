package com.monsteraltech.habitly.feature.notes.presentation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.notes.domain.model.Note
import com.monsteraltech.habitly.feature.notes.domain.model.NoteType
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.theme.LeafCornerLarge
import com.monsteraltech.habitly.ui.theme.habitly

/**
 * The notes board: personal on one tab, the household's on the other. Full screen behind a hidden
 * route, like the settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    onNavigateBack: () -> Unit,
    initialType: NoteType = NoteType.PERSONAL,
    viewModel: NotesViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedTabIndex by rememberSaveable(initialType) {
        mutableIntStateOf(if (initialType == NoteType.PERSONAL) 0 else 1)
    }
    var pendingAfterClose by remember { mutableStateOf<(() -> Unit)?>(null) }

    fun continueAfterClosingEditor(action: () -> Unit) {
        if (uiState.editing == null || viewModel.onRequestCloseEditor()) action()
        else pendingAfterClose = action
    }

    val currentType = if (selectedTabIndex == 0) NoteType.PERSONAL else NoteType.HOUSEHOLD
    val errorMessage = uiState.errorRes?.let { stringResource(it) }
    val deletedMessage = stringResource(R.string.notes_deleted)
    val undoLabel = stringResource(R.string.common_undo)

    BackHandler(enabled = uiState.editing != null) {
        continueAfterClosingEditor { }
    }

    LaunchedEffect(uiState.errorRes, errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onErrorShown()
        }
    }

    LaunchedEffect(uiState.recentlyDeleted?.id, deletedMessage, undoLabel) {
        if (uiState.recentlyDeleted == null) return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = deletedMessage,
            actionLabel = undoLabel,
            duration = SnackbarDuration.Long
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.onRestoreDeletedNote()
        else viewModel.onDeletedMessageShown()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.notes_title), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { continueAfterClosingEditor(onNavigateBack) }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back)
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { continueAfterClosingEditor { viewModel.onNewNote(currentType) } },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.notes_add))
            }
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.fillMaxSize().padding(padding)) {
            val isWide = maxWidth >= 900.dp
            val notes = uiState.notesOf(currentType)
            val listPanel: @Composable (Modifier) -> Unit = { modifier ->
                NotesListPanel(
                    modifier = modifier,
                    selectedTabIndex = selectedTabIndex,
                    onTabSelected = {
                        val index = it
                        continueAfterClosingEditor { selectedTabIndex = index }
                    },
                    notes = notes,
                    isLoading = uiState.isLoading,
                    currentUserId = uiState.currentUserId,
                    memberNicknames = uiState.memberNicknames,
                    searchQuery = uiState.searchQuery,
                    sortOrder = uiState.sortOrder,
                    selectedNoteId = uiState.editing?.note?.id,
                    onSearchChange = viewModel::onSearchChange,
                    onSortChange = viewModel::onSortChange,
                    onOpenNote = { note ->
                        if (uiState.editing?.note?.id != note.id) {
                            continueAfterClosingEditor { viewModel.onEditNote(note) }
                        }
                    },
                    onTogglePinned = viewModel::onTogglePinned,
                    onDelete = viewModel::onDeleteNote
                )
            }

            if (isWide) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .widthIn(max = 1280.dp)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                ) {
                    listPanel(Modifier.weight(0.42f).fillMaxHeight())
                    VerticalDivider()
                    Box(
                        modifier = Modifier.weight(0.58f).fillMaxHeight().padding(24.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        val editor = uiState.editing
                        if (editor == null) {
                            Text(
                                text = stringResource(R.string.notes_select_note),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.align(Alignment.Center)
                            )
                        } else {
                            HabitlyCard(
                                modifier = Modifier.fillMaxWidth(),
                                shape = LeafCornerLarge,
                                contentPadding = PaddingValues(24.dp)
                            ) {
                                NoteEditor(
                                    editor = editor,
                                    isSaving = uiState.isSaving,
                                    onTitleChange = viewModel::onTitleChange,
                                    onBodyChange = viewModel::onBodyChange,
                                    onClose = { continueAfterClosingEditor { } },
                                    onSave = viewModel::onSaveNote
                                )
                            }
                        }
                    }
                }
            } else {
                listPanel(
                    Modifier
                        .fillMaxHeight()
                        .widthIn(max = 760.dp)
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                )

                uiState.editing?.let { editor ->
                    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                    ModalBottomSheet(
                        onDismissRequest = { continueAfterClosingEditor { } },
                        sheetState = sheetState
                    ) {
                        NoteEditor(
                            editor = editor,
                            isSaving = uiState.isSaving,
                            onTitleChange = viewModel::onTitleChange,
                            onBodyChange = viewModel::onBodyChange,
                            onClose = { continueAfterClosingEditor { } },
                            onSave = viewModel::onSaveNote,
                            modifier = Modifier
                                .widthIn(max = 760.dp)
                                .fillMaxWidth()
                                .align(Alignment.CenterHorizontally)
                                .padding(horizontal = 24.dp)
                                .padding(bottom = 32.dp)
                        )
                    }
                }
            }
        }
    }

    if (uiState.showDiscardConfirmation) {
        AlertDialog(
            onDismissRequest = {
                pendingAfterClose = null
                viewModel.onKeepEditing()
            },
            title = { Text(stringResource(R.string.notes_discard_title)) },
            text = { Text(stringResource(R.string.notes_discard_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingAfterClose
                        pendingAfterClose = null
                        viewModel.onDiscardEditorChanges()
                        action?.invoke()
                    }
                ) { Text(stringResource(R.string.notes_discard_confirm)) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingAfterClose = null
                        viewModel.onKeepEditing()
                    }
                ) { Text(stringResource(R.string.notes_keep_editing)) }
            }
        )
    }
}

@Composable
private fun NotesListPanel(
    modifier: Modifier,
    selectedTabIndex: Int,
    onTabSelected: (Int) -> Unit,
    notes: List<Note>,
    isLoading: Boolean,
    currentUserId: String,
    memberNicknames: Map<String, String>,
    searchQuery: String,
    sortOrder: NoteSortOrder,
    selectedNoteId: String?,
    onSearchChange: (String) -> Unit,
    onSortChange: (NoteSortOrder) -> Unit,
    onOpenNote: (Note) -> Unit,
    onTogglePinned: (Note) -> Unit,
    onDelete: (Note) -> Unit
) {
    Column(modifier = modifier) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            listOf(R.string.notes_tab_personal, R.string.notes_tab_household)
                .forEachIndexed { index, titleRes ->
                    Tab(
                        selected = selectedTabIndex == index,
                        onClick = { onTabSelected(index) },
                        text = { Text(stringResource(titleRes), fontWeight = FontWeight.Bold) }
                    )
                }
        }

        NotesSearchAndSort(
            query = searchQuery,
            sortOrder = sortOrder,
            onQueryChange = onSearchChange,
            onSortChange = onSortChange
        )

        when {
            isLoading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            notes.isEmpty() -> Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(
                        if (searchQuery.isBlank()) R.string.notes_empty else R.string.notes_search_empty
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(notes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        selected = note.id == selectedNoteId,
                        currentUserId = currentUserId,
                        memberNicknames = memberNicknames,
                        onClick = { onOpenNote(note) },
                        onTogglePinned = { onTogglePinned(note) },
                        onDelete = { onDelete(note) }
                    )
                }
            }
        }
    }
}

@Composable
private fun NotesSearchAndSort(
    query: String,
    sortOrder: NoteSortOrder,
    onQueryChange: (String) -> Unit,
    onSortChange: (NoteSortOrder) -> Unit
) {
    var sortExpanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.weight(1f),
            placeholder = { Text(stringResource(R.string.notes_search)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = stringResource(R.string.notes_search_clear)
                        )
                    }
                }
            },
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        Box {
            IconButton(onClick = { sortExpanded = true }) {
                Icon(
                    Icons.Filled.Sort,
                    contentDescription = stringResource(R.string.notes_sort)
                )
            }
            DropdownMenu(
                expanded = sortExpanded,
                onDismissRequest = { sortExpanded = false }
            ) {
                NoteSortOrder.entries.forEach { order ->
                    DropdownMenuItem(
                        text = { Text(stringResource(order.labelRes)) },
                        leadingIcon = {
                            if (order == sortOrder) Icon(Icons.Filled.Check, contentDescription = null)
                        },
                        onClick = {
                            sortExpanded = false
                            onSortChange(order)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteEditor(
    editor: NoteEditorState,
    isSaving: Boolean,
    onTitleChange: (String) -> Unit,
    onBodyChange: (String) -> Unit,
    onClose: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(if (editor.note == null) R.string.notes_new else R.string.notes_edit),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.notes_cancel))
            }
        }
        OutlinedTextField(
            value = editor.title,
            onValueChange = onTitleChange,
            label = { Text(stringResource(R.string.notes_field_title)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium
        )
        OutlinedTextField(
            value = editor.body,
            onValueChange = onBodyChange,
            label = { Text(stringResource(R.string.notes_field_body)) },
            placeholder = { Text(stringResource(R.string.notes_hint)) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 8,
            maxLines = 18,
            shape = MaterialTheme.shapes.medium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onClose) { Text(stringResource(R.string.notes_cancel)) }
            Spacer(modifier = Modifier.width(8.dp))
            TextButton(
                onClick = onSave,
                enabled = editor.canSave && !isSaving
            ) { Text(stringResource(R.string.notes_save)) }
        }
    }
}

private val NoteSortOrder.labelRes: Int
    get() = when (this) {
        NoteSortOrder.UPDATED_DESC -> R.string.notes_sort_recent
        NoteSortOrder.UPDATED_ASC -> R.string.notes_sort_oldest
        NoteSortOrder.TITLE -> R.string.notes_sort_title
    }

@Composable
private fun NoteCard(
    note: Note,
    selected: Boolean,
    currentUserId: String,
    memberNicknames: Map<String, String>,
    onClick: () -> Unit,
    onTogglePinned: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { androidx.compose.runtime.mutableStateOf(false) }
    HabitlyCard(
        shape = LeafCornerLarge,
        color = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.habitly.card,
        contentPadding = PaddingValues(16.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (note.isPinned) {
                        Icon(
                            Icons.Filled.PushPin,
                            contentDescription = stringResource(R.string.notes_pinned),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 6.dp)
                        )
                    }
                    Text(
                        text = note.heading,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                if (note.body.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = note.body,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 3
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = note.byline(currentUserId, memberNicknames),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Filled.MoreVert,
                        contentDescription = stringResource(R.string.notes_more_actions)
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.notes_edit)) },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onClick()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (note.isPinned) R.string.notes_unpin else R.string.notes_pin
                                )
                            )
                        },
                        leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                        onClick = {
                            menuExpanded = false
                            onTogglePinned()
                        }
                    )
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(R.string.notes_delete),
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Note.byline(currentUserId: String, memberNicknames: Map<String, String>): String {
    val moment = updatedAt.toShortDateTime()
    val stamp = if (updatedAt > createdAt) {
        stringResource(R.string.notes_edited, moment)
    } else {
        moment
    }

    if (type != NoteType.HOUSEHOLD) return stamp

    val author = if (authorId == currentUserId) {
        stringResource(R.string.notes_author_you)
    } else {
        memberNicknames[authorId] ?: stringResource(R.string.notes_author_unknown)
    }
    return "$author · $stamp"
}

private fun Long.toShortDateTime(): String {
    val dateTime = java.time.Instant.ofEpochMilli(this)
        .atZone(java.time.ZoneId.systemDefault())
        .toLocalDateTime()
    val locale = java.util.Locale.getDefault()
    val monthName = dateTime.month.getDisplayName(java.time.format.TextStyle.SHORT, locale)
    return "%d %s, %02d:%02d".format(
        dateTime.dayOfMonth, monthName, dateTime.hour, dateTime.minute
    )
}
