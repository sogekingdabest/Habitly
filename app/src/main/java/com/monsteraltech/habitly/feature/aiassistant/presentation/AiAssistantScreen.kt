package com.monsteraltech.habitly.feature.aiassistant.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.SmartToy
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.ChatMessageItem
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.ModelDownloadCard
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.PromptInput
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.ShoppingSuggestionCard
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiAssistantScreen(
    viewModel: AiAssistantViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var expandedModelMenu by remember { mutableStateOf(false) }
    var chatToDelete by remember { mutableStateOf<AiChatSession?>(null) }

    LaunchedEffect(uiState.chatSession.messages.count { it.role == com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole.User }) {
        val userMessageCount = uiState.chatSession.messages.count { it.role == com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole.User }
        if (userMessageCount > 0) {
            listState.animateScrollToItem(uiState.chatSession.messages.size - 1)
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
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
                            icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
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
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column(
                            modifier = Modifier.clickable { expandedModelMenu = true }
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(stringResource(R.string.ai_title))
                                Icon(Icons.Default.ArrowDropDown, contentDescription = stringResource(R.string.ai_change_model))
                            }
                            val statusText = when (uiState.modelStatus) {
                                is ModelStatus.Ready -> stringResource(R.string.ai_status_ready)
                                is ModelStatus.Downloading -> stringResource(R.string.ai_status_downloading)
                                else -> stringResource(R.string.ai_status_not_downloaded)
                            }
                            val modelName = uiState.selectedModel?.name ?: stringResource(R.string.ai_selecting)
                            Text(
                                text = "$modelName • $statusText",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        DropdownMenu(
                            expanded = expandedModelMenu,
                            onDismissRequest = { expandedModelMenu = false }
                        ) {
                            uiState.availableModels.forEach { model ->
                                DropdownMenuItem(
                                    text = { Text(model.name) },
                                    onClick = {
                                        viewModel.onSelectModel(model.id)
                                        expandedModelMenu = false
                                    },
                                    trailingIcon = {
                                        if (model.id == uiState.selectedModel?.id) {
                                            Icon(Icons.Default.SmartToy, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                )
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
            ) {
                when (uiState.modelStatus) {
                    is ModelStatus.NotDownloaded -> {
                        ModelDownloadCard(
                            modelConfig = uiState.selectedModel,
                            progress = 0f,
                            isDownloading = false,
                            onDownload = { viewModel.onDownloadModel() },
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                    is ModelStatus.Downloading -> {
                        val progress = (uiState.modelStatus as ModelStatus.Downloading).progress
                        ModelDownloadCard(
                            modelConfig = uiState.selectedModel,
                            progress = progress,
                            isDownloading = true,
                            onDownload = {},
                            modifier = Modifier.padding(top = 16.dp)
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
                        LazyColumn(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                            state = listState,
                            reverseLayout = false
                        ) {
                            if (uiState.chatSession.messages.isEmpty()) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f),
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
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = stringResource(R.string.ai_empty_subtitle),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }

                            items(uiState.chatSession.messages) { message ->
                                Column {
                                    ChatMessageItem(message = message)
                                    val suggestions = uiState.shoppingSuggestions[message.id]
                                    if (!suggestions.isNullOrEmpty()) {
                                        ShoppingSuggestionCard(
                                            count = suggestions.size,
                                            isAdded = message.id in uiState.addedSuggestionMessageIds,
                                            isLoading = uiState.addingSuggestionMessageId == message.id,
                                            onAdd = { viewModel.onAddSuggestionsToList(message.id) }
                                        )
                                    }
                                }
                            }

                            if (uiState.isGenerating) {
                                item {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        CircularProgressIndicator()
                                    }
                                }
                            }
                        }

                        PromptInput(
                            input = uiState.currentInput,
                            onInputChange = { viewModel.onInputChange(it) },
                            onSend = { viewModel.onSendMessage() },
                            quickPrompts = uiState.quickPrompts,
                            onQuickPrompt = { viewModel.onQuickPrompt(it) }
                        )
                    }
                }
            }
        }
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
