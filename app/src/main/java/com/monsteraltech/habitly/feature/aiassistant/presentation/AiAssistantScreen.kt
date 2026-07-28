package com.monsteraltech.habitly.feature.aiassistant.presentation

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiQuickPrompt
import com.monsteraltech.habitly.feature.aiassistant.domain.model.FollowUpTarget
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import com.monsteraltech.habitly.feature.aiassistant.domain.model.QuickPromptId
import com.monsteraltech.habitly.feature.aiassistant.domain.model.compatibilityWith
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus
import com.monsteraltech.habitly.feature.aiassistant.domain.util.AiStructuredBlocks
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.ChatMessageItem
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.ModelBlockedCard
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.ModelDownloadCard
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.PromptInput
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.QuickPromptChip
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.RoutineSuggestionCard
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.ShoppingSuggestionCard
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.SuggestionPreparingCard
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.MeshArrangement
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Above this fraction of used context, the compaction notice appears. */
private const val CONTEXT_WARN_THRESHOLD = 0.7f

/** `2.6 GB` or `850 MB`, using the device language's decimal separator. */
private fun formatModelSize(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1) {
        String.format(Locale.getDefault(), "%.1f GB", gb)
    } else {
        String.format(Locale.getDefault(), "%d MB", bytes / 1_000_000)
    }
}

/**
 * RAM in the gigabytes printed on the phone's spec sheet: binary and without decimals. With the
 * file formatter (decimal GB), 6 GiB of RAM would read as "6.4 GB", which is not what the user has
 * written on the box.
 */
private fun formatRam(bytes: Long): String {
    if (bytes <= 0L) return "—"
    return String.format(Locale.getDefault(), "%d GB", bytes / 1_073_741_824L)
}

/**
 * Floating "scroll to bottom" button. In its own composable, with no ColumnScope or RowScope
 * around it, so [AnimatedVisibility] resolves to the generic overload; the parent's `align`
 * arrives through [modifier].
 */
@Composable
private fun ScrollToBottomFab(
    visible: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        SmallFloatingActionButton(onClick = onClick) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = stringResource(R.string.ai_scroll_to_bottom)
            )
        }
    }
}

/**
 * "Long conversation" notice with a compaction action. The visible history does not change:
 * compacting only summarises the older part that is handed to the model.
 */
@Composable
private fun ContextCompactBanner(
    usagePercent: Int,
    isCompacting: Boolean,
    onCompact: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.ai_context_long, usagePercent),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f)
            )
            if (isCompacting) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp
                )
                Text(
                    text = stringResource(R.string.ai_compacting),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            } else {
                TextButton(onClick = onCompact) {
                    Text(stringResource(R.string.ai_compact))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    // Follows the tail while streaming. Breaks when the user scrolls up to re-read, and latches on
    // again once they return to the bottom.
    val autoFollow = remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // available.y > 0 means the finger is dragging down, revealing earlier content: the
                // user wants to re-read, so stop following the stream's tail.
                if (available.y > 0f) autoFollow.value = false
                return Offset.Zero
            }
        }
    }
    var showScrollToBottom by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Copy messages to the clipboard. Android 13+ shows its own copy confirmation, so the snackbar
    // only fires below that version.
    val clipboard = LocalClipboardManager.current
    val copiedMessage = stringResource(R.string.ai_copied)
    val onCopyMessage: (String) -> Unit = { text ->
        clipboard.setText(AnnotatedString(text))
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            scope.launch { snackbarHostState.showSnackbar(copiedMessage) }
        }
    }
    var expandedModelMenu by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<AiChatSession?>(null) }
    var showDownloadDialog by remember { mutableStateOf(false) }
    var modelToDelete by remember { mutableStateOf<AiModelConfig?>(null) }
    // Id of the assistant message whose report is awaiting confirmation.
    var messageToReport by remember { mutableStateOf<String?>(null) }

    // Voice dictation through the system recogniser: no permissions of our own, no extra models.
    // The transcription is left in the field so the user can review it before sending.
    val voicePromptText = stringResource(R.string.ai_voice_prompt)
    val voiceUnavailableText = stringResource(R.string.ai_voice_unavailable)
    val speechLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) viewModel.onInputChange(spoken)
    }

    // Sending a new message scrolls to the bottom and re-latches the follow.
    val userMessageCount = uiState.chatSession.messages.count { it.role is MessageRole.User }
    LaunchedEffect(userMessageCount) {
        if (userMessageCount > 0) {
            autoFollow.value = true
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.animateScrollToItem(total - 1)
        }
    }

    // Follows the answer's tail while streaming, but only while autoFollow holds — a user who
    // scrolled up to re-read is not interrupted. The target is the anchor item at the end: aimed
    // at the message itself, a very tall item, scrollToItem would pin its **start** rather than the
    // growing tail. Unanimated, because relaunching the animation on every refresh makes it stutter.
    val streamingLength = uiState.chatSession.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(uiState.isGenerating, streamingLength) {
        if (!uiState.isGenerating || !autoFollow.value) return@LaunchedEffect
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.scrollToItem(total - 1)
    }

    // "Scroll to bottom" button plus follow re-latching. canScrollForward means there is content
    // below; a 500 ms margin keeps the button from flickering while the stream is being followed.
    LaunchedEffect(listState) {
        snapshotFlow { listState.canScrollForward }.collectLatest { canScroll ->
            if (!canScroll) {
                autoFollow.value = true
                showScrollToBottom = false
            } else {
                delay(500)
                showScrollToBottom = true
            }
        }
    }

    // The error arrives either as dynamic text (uiState.error) or as a localised resource id
    // (uiState.errorRes); resolved here, in the Activity context that follows the language.
    val errorResText = uiState.errorRes?.let { stringResource(it) }
    LaunchedEffect(uiState.error, uiState.errorRes) {
        val message = uiState.error ?: errorResText
        if (message != null) {
            snackbarHostState.showSnackbar(message)
            viewModel.onDismissError()
        }
    }

    val addedCount = uiState.addedToListCount
    if (addedCount != null) {
        val addedMessage = pluralStringResource(R.plurals.ai_added_to_list, addedCount, addedCount)
        LaunchedEffect(addedCount) {
            snackbarHostState.showSnackbar(addedMessage)
            viewModel.onAddedToListShown()
        }
    }

    val addedRoutines = uiState.addedRoutinesCount
    if (addedRoutines != null) {
        val addedRoutinesMessage = pluralStringResource(R.plurals.ai_routines_created, addedRoutines, addedRoutines)
        LaunchedEffect(addedRoutines) {
            snackbarHostState.showSnackbar(addedRoutinesMessage)
            viewModel.onAddedRoutinesShown()
        }
    }

    val contextCompactedMessage = stringResource(R.string.ai_compacted)
    LaunchedEffect(uiState.contextCompacted) {
        if (uiState.contextCompacted) {
            snackbarHostState.showSnackbar(contextCompactedMessage)
            viewModel.onContextCompactedShown()
        }
    }

    val reportSuccessMessage = stringResource(R.string.ai_report_success)
    val reportErrorMessage = stringResource(R.string.ai_report_error)
    LaunchedEffect(uiState.reportResult) {
        uiState.reportResult?.let { ok ->
            snackbarHostState.showSnackbar(if (ok) reportSuccessMessage else reportErrorMessage)
            viewModel.onReportResultShown()
        }
    }

    val dateFormatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                Text(
                    stringResource(R.string.ai_chat_history),
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleLarge
                )
                HorizontalDivider()
                
                Button(
                    onClick = {
                        viewModel.onNewChat()
                        scope.launch { drawerState.close() }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.ai_new_conversation))
                }

                LazyColumn {
                    items(uiState.chatHistory) { session ->
                        val isSelected = session.id == uiState.chatSession.id
                        NavigationDrawerItem(
                            label = { 
                                Column {
                                    Text(
                                        text = session.title,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = dateFormatter.format(Date(session.timestamp)),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            },
                            selected = isSelected,
                            onClick = {
                                viewModel.onLoadChat(session.id)
                                scope.launch { drawerState.close() }
                            },
                            // No per-row icon: it distinguishes nothing between chats and only adds
                            // noise; the bin at the end already marks the action area.
                            badge = {
                                IconButton(onClick = { chatToDelete = session }) {
                                    Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.cd_delete), modifier = Modifier.size(20.dp))
                                }
                            },
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    ) {
        HabitlyBackground(arrangement = MeshArrangement.Chat) {
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                    title = {
                        // The model and its state stay in the dropdown: with the model ready the
                        // subtitle added nothing, and when it is not ready the download card
                        // already leads. Here it only stole height from the conversation.
                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable(
                                        onClickLabel = stringResource(R.string.ai_change_model)
                                    ) { expandedModelMenu = true }
                                    .padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(stringResource(R.string.ai_title))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                            }

                            DropdownMenu(
                                expanded = expandedModelMenu,
                                onDismissRequest = { expandedModelMenu = false }
                            ) {
                                uiState.availableModels.forEach { model ->
                                    val isDownloaded = model.id in uiState.downloadedModelIds
                                    // A model that does not fit is shown dimmed and inert: hiding
                                    // it would leave the user wondering what is missing, and
                                    // leaving it active would lead them straight into a crash.
                                    val compatibility = model.compatibilityWith(uiState.deviceRamBytes)
                                    val isUsable = compatibility.canUse
                                    DropdownMenuItem(
                                        enabled = isUsable,
                                        text = {
                                            Column {
                                                Text(model.name)
                                                Text(
                                                    text = when {
                                                        !isUsable -> stringResource(
                                                            R.string.ai_model_ram_required,
                                                            formatRam(model.minRamBytes)
                                                        )
                                                        isDownloaded -> stringResource(
                                                            R.string.ai_model_size_downloaded,
                                                            formatModelSize(model.sizeBytes)
                                                        )
                                                        else -> formatModelSize(model.sizeBytes)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isUsable) {
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                    } else {
                                                        MaterialTheme.colorScheme.error
                                                    }
                                                )
                                            }
                                        },
                                        onClick = {
                                            viewModel.onSelectModel(model.id)
                                            expandedModelMenu = false
                                        },
                                        trailingIcon = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (model.id == uiState.selectedModel?.id) {
                                                    Icon(
                                                        Icons.Default.Check,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                                if (isDownloaded) {
                                                    IconButton(onClick = {
                                                        expandedModelMenu = false
                                                        modelToDelete = model
                                                    }) {
                                                        Icon(
                                                            Icons.Default.Delete,
                                                            contentDescription = stringResource(R.string.ai_delete_model),
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                            Icon(Icons.Default.Menu, contentDescription = stringResource(R.string.ai_menu))
                        }
                    },
                    actions = {
                        if (uiState.chatSession.messages.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onNewChat() }) {
                                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.ai_new_chat))
                            }
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) }
        ) { innerPadding ->
            // No horizontal padding here: each block adds its own, so the chip row can take the
            // full width and scroll all the way to the edge.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Lifts the text field with the keyboard, matching the rest of the app.
                    .imePadding()
            ) {
                when (uiState.modelStatus) {
                    is ModelStatus.NotDownloaded -> {
                        ModelDownloadCard(
                            modelConfig = uiState.selectedModel,
                            progress = 0f,
                            isDownloading = false,
                            // Before queueing gigabytes, ask which network: Wi-Fi or mobile data.
                            onDownload = { showDownloadDialog = true },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        )
                    }
                    is ModelStatus.Unsupported -> {
                        val model = uiState.selectedModel
                        ModelBlockedCard(
                            title = stringResource(R.string.ai_model_unsupported_title),
                            message = stringResource(
                                R.string.ai_model_unsupported_message,
                                model?.name.orEmpty(),
                                formatRam(model?.minRamBytes ?: 0L),
                                formatRam(uiState.deviceRamBytes)
                            ),
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        )
                    }
                    is ModelStatus.LoadCrashed -> {
                        ModelBlockedCard(
                            title = stringResource(R.string.ai_model_load_crashed_title),
                            message = stringResource(
                                R.string.ai_model_load_crashed_message,
                                uiState.selectedModel?.name.orEmpty()
                            ),
                            actionLabel = stringResource(R.string.ai_model_load_crashed_retry),
                            onAction = { viewModel.onRetryAfterLoadCrash() },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        )
                    }
                    is ModelStatus.Downloading -> {
                        val progress = (uiState.modelStatus as ModelStatus.Downloading).progress
                        ModelDownloadCard(
                            modelConfig = uiState.selectedModel,
                            progress = progress,
                            isDownloading = true,
                            onDownload = {},
                            onCancel = { viewModel.onCancelDownload() },
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
                        )
                    }
                    is ModelStatus.Error -> {
                        val error = (uiState.modelStatus as ModelStatus.Error).message
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = stringResource(R.string.ai_error_loading),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                                Button(
                                    onClick = { viewModel.onRetryDownload() },
                                    modifier = Modifier.padding(top = 16.dp)
                                ) {
                                    Text(stringResource(R.string.ai_retry))
                                }
                            }
                        }
                    }
                    is ModelStatus.Ready -> {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .nestedScroll(nestedScrollConnection),
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                        ) {
                            if (uiState.chatSession.messages.isEmpty()) {
                                item {
                                    // fillParentMaxHeight rather than weight: inside a LazyColumn
                                    // item the weight resolved against the outer Column, the list
                                    // ignored it and this never actually centred.
                                    Box(
                                        modifier = Modifier
                                            .fillParentMaxHeight()
                                            .fillMaxWidth(),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Icon(
                                                Icons.Default.SmartToy,
                                                contentDescription = null,
                                                modifier = Modifier.padding(16.dp),
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                text = stringResource(R.string.ai_empty_title),
                                                style = MaterialTheme.typography.headlineSmall,
                                                fontWeight = FontWeight.Bold,
                                                textAlign = TextAlign.Center
                                            )
                                            Text(
                                                text = stringResource(R.string.ai_empty_subtitle),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                textAlign = TextAlign.Center
                                            )
                                        }
                                    }
                                }
                            }

                            // Keyed by id: while streaming, only the growing message recomposes
                            // instead of every row being rebuilt on each refresh.
                            items(uiState.chatSession.messages, key = { it.id }) { message ->
                                Column {
                                    ChatMessageItem(
                                        message = message,
                                        // The last assistant message while generating: no copy
                                        // button, and dots while it is still empty.
                                        isStreaming = uiState.isGenerating &&
                                            message.id == uiState.chatSession.messages.lastOrNull()?.id &&
                                            message.role is MessageRole.Assistant,
                                        onCopy = onCopyMessage,
                                        isReported = message.id in uiState.reportedMessageIds,
                                        onReport = { messageToReport = message.id }
                                    )
                                    val suggestions = uiState.shoppingSuggestions[message.id]
                                    if (!suggestions.isNullOrEmpty()) {
                                        ShoppingSuggestionCard(
                                            count = suggestions.size,
                                            isAdded = message.id in uiState.addedSuggestionMessageIds,
                                            isLoading = uiState.addingSuggestionMessageId == message.id,
                                            onAdd = { viewModel.onAddSuggestionsToList(message.id) }
                                        )
                                    }
                                    val routineSuggestions = uiState.routineSuggestions[message.id]
                                    if (!routineSuggestions.isNullOrEmpty()) {
                                        RoutineSuggestionCard(
                                            routines = routineSuggestions,
                                            isAdded = message.id in uiState.addedRoutineMessageIds,
                                            isLoading = uiState.addingRoutineMessageId == message.id,
                                            onAdd = { type -> viewModel.onAddRoutineSuggestions(message.id, type) }
                                        )
                                    }
                                }
                            }

                            // Once the stream reaches the hidden @@…@@ block the visible text stops
                            // growing for a while, so the user is told suggestions are coming. No
                            // spinner: the message completes on its own and the bubble's dots cover
                            // the wait until the first token.
                            if (uiState.isGenerating || uiState.isExtractingSuggestions) {
                                val streamingTail = uiState.chatSession.messages.lastOrNull()
                                    ?.takeIf { it.role is MessageRole.Assistant }
                                    ?.content.orEmpty()
                                if (uiState.isExtractingSuggestions ||
                                    AiStructuredBlocks.hasPendingStructuredBlock(streamingTail)) {
                                    item(key = "preparing") { SuggestionPreparingCard() }
                                }
                            }

                            // Tail anchor: the target of the follow scroll. Aimed at the message,
                            // a very tall item, scrollToItem would pin its start rather than the
                            // tail. Only present with messages, so the empty screen stays unscrollable.
                            if (uiState.chatSession.messages.isNotEmpty()) {
                                item(key = "bottom-anchor") { Spacer(Modifier.height(1.dp)) }
                            }
                        }

                        // "Scroll to bottom": only when the user has stayed above.
                        ScrollToBottomFab(
                            visible = showScrollToBottom,
                            onClick = {
                                autoFollow.value = true
                                scope.launch {
                                    val total = listState.layoutInfo.totalItemsCount
                                    if (total > 0) listState.animateScrollToItem(total - 1)
                                }
                            },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 8.dp)
                        )
                        }

                        // Metrics for the last answer: only produced in debug builds.
                        uiState.lastGenerationStats?.let { stats ->
                            Text(
                                text = stats,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        // Long-conversation notice with a compaction action.
                        if (uiState.contextUsage >= CONTEXT_WARN_THRESHOLD && !uiState.isGenerating) {
                            ContextCompactBanner(
                                usagePercent = (uiState.contextUsage * 100).toInt(),
                                isCompacting = uiState.isCompacting,
                                onCompact = { viewModel.onCompactContext() }
                            )
                        }

                        // Localised chips: the follow-up one, if any, goes ahead of the fixed ones.
                        // Texts are resolved here, in the Activity context that follows the
                        // Settings language, and each chip carries its own action.
                        val followUpAck = stringResource(R.string.ai_follow_up_ack)
                        val quickPromptChips = listOfNotNull(
                            uiState.followUpTarget?.let { target ->
                                val userText = followUpUserText(target)
                                QuickPromptChip(followUpLabel(target)) {
                                    viewModel.onFollowUpChipTapped(userText, followUpAck)
                                }
                            }
                        ) + uiState.quickPrompts.map { qp ->
                            val promptText = quickPromptText(qp)
                            QuickPromptChip(quickPromptLabel(qp.id)) {
                                viewModel.onQuickPrompt(promptText)
                            }
                        }

                        PromptInput(
                            input = uiState.currentInput,
                            onInputChange = { viewModel.onInputChange(it) },
                            onSend = { viewModel.onSendMessage() },
                            isGenerating = uiState.isGenerating,
                            onStop = { viewModel.onStopGeneration() },
                            onVoiceInput = {
                                val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(
                                        RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                        RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                    )
                                    putExtra(RecognizerIntent.EXTRA_PROMPT, voicePromptText)
                                }
                                try {
                                    speechLauncher.launch(intent)
                                } catch (e: ActivityNotFoundException) {
                                    scope.launch { snackbarHostState.showSnackbar(voiceUnavailableText) }
                                }
                            },
                            quickPrompts = quickPromptChips
                        )

                        // AI disclaimer: the model can be wrong. Not mandatory, but standard
                        // practice; sits discreetly under the input.
                        Text(
                            text = stringResource(R.string.ai_disclaimer),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
        }
    }

    if (showDownloadDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text(stringResource(R.string.ai_download_network_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ai_download_network_message,
                        formatModelSize(uiState.selectedModel?.sizeBytes ?: 0L)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    viewModel.onDownloadModel(wifiOnly = true)
                }) {
                    Text(stringResource(R.string.ai_download_wifi_only))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    viewModel.onDownloadModel(wifiOnly = false)
                }) {
                    Text(stringResource(R.string.ai_download_any_network))
                }
            }
        )
    }

    modelToDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { modelToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.ai_delete_model_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ai_delete_model_confirm_message,
                        model.name,
                        formatModelSize(model.sizeBytes)
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeleteModel(model.id)
                        modelToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.cd_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { modelToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    // A model that barely fits: warn and let the user decide. Blocking here would be too much — it
    // may run fine on an otherwise idle phone — and saying nothing too little, given hundreds of
    // megabytes and a possible crash.
    uiState.pendingTightDownloadModel?.let { model ->
        AlertDialog(
            onDismissRequest = { viewModel.onDismissTightDownload() },
            icon = { Icon(Icons.Default.Memory, contentDescription = null) },
            title = { Text(stringResource(R.string.ai_model_tight_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ai_model_tight_message,
                        model.name,
                        formatRam(model.recommendedRamBytes),
                        formatRam(uiState.deviceRamBytes)
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.onConfirmTightDownload() }) {
                    Text(stringResource(R.string.ai_model_tight_proceed))
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onDismissTightDownload() }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    messageToReport?.let { messageId ->
        AlertDialog(
            onDismissRequest = { messageToReport = null },
            icon = { Icon(Icons.Outlined.Flag, contentDescription = null) },
            title = { Text(stringResource(R.string.ai_report_confirm_title)) },
            text = { Text(stringResource(R.string.ai_report_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.onReportMessage(messageId)
                    messageToReport = null
                }) {
                    Text(stringResource(R.string.ai_report_send))
                }
            },
            dismissButton = {
                TextButton(onClick = { messageToReport = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }

    chatToDelete?.let { session ->
        AlertDialog(
            onDismissRequest = { chatToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.ai_delete_chat_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.ai_delete_chat_confirm_message,
                        session.title.ifBlank { stringResource(R.string.ai_new_conversation) }
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.onDeleteChat(session.id)
                        chatToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.cd_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { chatToDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

// Localised chip texts. Resolved with stringResource in the Activity context so they respect the
// language chosen in Settings; the domain layer only supplies the id.

@Composable
private fun quickPromptLabel(id: QuickPromptId): String = stringResource(
    when (id) {
        QuickPromptId.WEEKLY_MENU -> R.string.ai_qp_weekly_menu
        QuickPromptId.COOK_FROM_PANTRY -> R.string.ai_qp_cook_from_pantry
        QuickPromptId.CLEANING_PLAN -> R.string.ai_qp_cleaning_plan
        QuickPromptId.RECIPES_FROM_LIST -> R.string.ai_qp_recipes_from_list
        QuickPromptId.WEEKLY_LIST -> R.string.ai_qp_weekly_list
        QuickPromptId.ORGANIZE_DAY -> R.string.ai_qp_organize_day
        QuickPromptId.QUICK_DINNER -> R.string.ai_qp_quick_dinner
        QuickPromptId.ROUTINE_IDEAS -> R.string.ai_qp_routine_ideas
        QuickPromptId.CLEANING_TIPS -> R.string.ai_qp_cleaning_tips
    }
)

@Composable
private fun quickPromptText(qp: AiQuickPrompt): String = when (qp.id) {
    QuickPromptId.WEEKLY_MENU -> stringResource(
        R.string.ai_qp_weekly_menu_prompt,
        pluralStringResource(R.plurals.ai_people, qp.memberCount, qp.memberCount)
    )
    QuickPromptId.COOK_FROM_PANTRY -> stringResource(R.string.ai_qp_cook_from_pantry_prompt)
    QuickPromptId.CLEANING_PLAN -> stringResource(R.string.ai_qp_cleaning_plan_prompt)
    QuickPromptId.RECIPES_FROM_LIST -> stringResource(R.string.ai_qp_recipes_from_list_prompt)
    QuickPromptId.WEEKLY_LIST -> stringResource(R.string.ai_qp_weekly_list_prompt)
    QuickPromptId.ORGANIZE_DAY -> stringResource(R.string.ai_qp_organize_day_prompt)
    QuickPromptId.QUICK_DINNER -> stringResource(R.string.ai_qp_quick_dinner_prompt)
    QuickPromptId.ROUTINE_IDEAS -> stringResource(R.string.ai_qp_routine_ideas_prompt)
    QuickPromptId.CLEANING_TIPS -> stringResource(R.string.ai_qp_cleaning_tips_prompt)
}

@Composable
private fun followUpLabel(target: FollowUpTarget): String = stringResource(
    when (target) {
        FollowUpTarget.ROUTINES -> R.string.ai_follow_up_routines_label
        FollowUpTarget.SHOPPING -> R.string.ai_follow_up_shopping_label
        FollowUpTarget.BOTH -> R.string.ai_follow_up_both_label
    }
)

@Composable
private fun followUpUserText(target: FollowUpTarget): String = stringResource(
    when (target) {
        FollowUpTarget.ROUTINES -> R.string.ai_follow_up_routines_prompt
        FollowUpTarget.SHOPPING -> R.string.ai_follow_up_shopping_prompt
        FollowUpTarget.BOTH -> R.string.ai_follow_up_both_prompt
    }
)
