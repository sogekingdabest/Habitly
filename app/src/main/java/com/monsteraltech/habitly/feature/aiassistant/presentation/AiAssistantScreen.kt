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
import com.monsteraltech.habitly.feature.aiassistant.domain.model.MessageRole
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus
import com.monsteraltech.habitly.feature.aiassistant.domain.util.AiStructuredBlocks
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.ChatMessageItem
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.ModelDownloadCard
import com.monsteraltech.habitly.feature.aiassistant.presentation.components.PromptInput
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

/** A partir de esta fracción de contexto ocupado se muestra el aviso de compactar. */
private const val CONTEXT_WARN_THRESHOLD = 0.7f

/** `2,6 GB` o `850 MB`, con el separador decimal del idioma del dispositivo. */
private fun formatModelSize(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1) {
        String.format(Locale.getDefault(), "%.1f GB", gb)
    } else {
        String.format(Locale.getDefault(), "%d MB", bytes / 1_000_000)
    }
}

/**
 * Botón flotante "ir al final". En su propio composable (sin ColumnScope/RowScope alrededor)
 * para que [AnimatedVisibility] resuelva a la sobrecarga genérica; el `align` del padre llega
 * por [modifier]. Es la pieza de scroll de la fase 3 (equivalente al ScrollToBottomButton del
 * AI Edge Gallery).
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
 * Aviso de "conversación larga" con acción de compactar (fase 6). El historial visible no
 * cambia: compactar solo resume la parte antigua que se le pasa al modelo.
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
    // Seguimiento del final durante el streaming. Se rompe cuando el usuario sube a releer
    // (gesto hacia arriba) y se vuelve a enganchar al llegar de nuevo al final.
    val autoFollow = remember { mutableStateOf(true) }
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // available.y > 0 = el dedo arrastra hacia abajo = se revela lo anterior: el
                // usuario quiere releer, así que dejamos de seguir la cola del stream.
                if (available.y > 0f) autoFollow.value = false
                return Offset.Zero
            }
        }
    }
    var showScrollToBottom by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    // Copiar mensajes al portapapeles. En Android 13+ el sistema ya muestra su propia
    // confirmación al copiar, así que el snackbar solo se lanza por debajo de esa versión.
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

    // Dictado por voz con el reconocedor del sistema: sin permisos propios ni modelos extra.
    // El texto reconocido se deja en el campo para que el usuario lo revise antes de enviar.
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

    // Al enviar un mensaje nuevo, baja al final y vuelve a enganchar el seguimiento.
    val userMessageCount = uiState.chatSession.messages.count { it.role is MessageRole.User }
    LaunchedEffect(userMessageCount) {
        if (userMessageCount > 0) {
            autoFollow.value = true
            val total = listState.layoutInfo.totalItemsCount
            if (total > 0) listState.animateScrollToItem(total - 1)
        }
    }

    // Sigue la cola de la respuesta durante el streaming, solo mientras autoFollow siga vivo
    // (si el usuario subió a releer, no se le interrumpe). El objetivo es el item ancla del
    // final: sobre el mensaje —que es un item muy alto— scrollToItem fijaría su INICIO, no la
    // cola que crece. Sin animación: relanzarla en cada refresco del stream la corta a tirones.
    val streamingLength = uiState.chatSession.messages.lastOrNull()?.content?.length ?: 0
    LaunchedEffect(uiState.isGenerating, streamingLength) {
        if (!uiState.isGenerating || !autoFollow.value) return@LaunchedEffect
        val total = listState.layoutInfo.totalItemsCount
        if (total > 0) listState.scrollToItem(total - 1)
    }

    // Botón "ir al final" + re-enganche del seguimiento. canScrollForward = queda contenido
    // por debajo; con 500 ms de margen el botón no parpadea mientras se sigue el stream.
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
                            // Sin icono por fila: no distingue nada entre chats y solo mete
                            // ruido; la papelera al final ya marca la zona de acción.
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
                        // El modelo y su estado se quedan en el desplegable: con el modelo
                        // listo el subtítulo no aportaba nada, y cuando no lo está ya manda la
                        // tarjeta de descarga. Aquí solo restaba altura a la conversación.
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
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(model.name)
                                                Text(
                                                    text = if (isDownloaded) {
                                                        stringResource(
                                                            R.string.ai_model_size_downloaded,
                                                            formatModelSize(model.sizeBytes)
                                                        )
                                                    } else {
                                                        formatModelSize(model.sizeBytes)
                                                    },
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
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
            // Sin padding horizontal aquí: lo pone cada bloque por su cuenta, para que la fila
            // de chips pueda ocupar el ancho completo y deslizarse hasta el borde.
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    // Sube el campo de texto con el teclado; el resto de la app lo hace
                    // igual (login/registro) y aquí faltaba.
                    .imePadding()
            ) {
                when (uiState.modelStatus) {
                    is ModelStatus.NotDownloaded -> {
                        ModelDownloadCard(
                            modelConfig = uiState.selectedModel,
                            progress = 0f,
                            isDownloading = false,
                            // Antes de encolar GB se pregunta con qué red (Wi-Fi o datos).
                            onDownload = { showDownloadDialog = true },
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
                                    // fillParentMaxHeight y no weight: dentro de un item de
                                    // LazyColumn el weight se resolvía contra el Column de
                                    // fuera, la lista lo ignoraba y esto nunca llegó a centrarse.
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

                            // key por id: durante el streaming solo recompone el mensaje que
                            // crece, en vez de rehacer todas las filas en cada refresco.
                            items(uiState.chatSession.messages, key = { it.id }) { message ->
                                Column {
                                    ChatMessageItem(
                                        message = message,
                                        // El último mensaje del asistente mientras se genera:
                                        // sin botón de copiar y, si sigue vacío, con puntos.
                                        isStreaming = uiState.isGenerating &&
                                            message.id == uiState.chatSession.messages.lastOrNull()?.id &&
                                            message.role is MessageRole.Assistant,
                                        onCopy = onCopyMessage
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

                            // Si el stream ya va por el bloque @@…@@ oculto, el texto visible
                            // deja de crecer un buen rato: se avisa de que vienen sugerencias.
                            // Ya no hay spinner: el mensaje se completa solo y los puntos de la
                            // burbuja cubren la espera hasta el primer token.
                            if (uiState.isGenerating || uiState.isExtractingSuggestions) {
                                val streamingTail = uiState.chatSession.messages.lastOrNull()
                                    ?.takeIf { it.role is MessageRole.Assistant }
                                    ?.content.orEmpty()
                                if (uiState.isExtractingSuggestions ||
                                    AiStructuredBlocks.hasPendingStructuredBlock(streamingTail)) {
                                    item(key = "preparing") { SuggestionPreparingCard() }
                                }
                            }

                            // Ancla del final: objetivo del scroll de seguimiento. Sobre el
                            // mensaje (item muy alto) scrollToItem fijaría su inicio, no la cola.
                            // Solo con mensajes, para no hacer scrollable la pantalla vacía.
                            if (uiState.chatSession.messages.isNotEmpty()) {
                                item(key = "bottom-anchor") { Spacer(Modifier.height(1.dp)) }
                            }
                        }

                        // "Ir al final": solo cuando el usuario se ha quedado arriba.
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

                        // Métricas de la última respuesta: solo llegan en builds debug.
                        uiState.lastGenerationStats?.let { stats ->
                            Text(
                                text = stats,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }

                        // Aviso de conversación larga con acción de compactar (fase 6).
                        if (uiState.contextUsage >= CONTEXT_WARN_THRESHOLD && !uiState.isGenerating) {
                            ContextCompactBanner(
                                usagePercent = (uiState.contextUsage * 100).toInt(),
                                isCompacting = uiState.isCompacting,
                                onCompact = { viewModel.onCompactContext() }
                            )
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
                            // El chip de seguimiento ("Sí, créalas" / "Sí, a la lista") va
                            // delante de los fijos; aquí solo se pinta, su destino lo enruta
                            // el ViewModel al reconocer el prompt en onQuickPrompt.
                            quickPrompts = listOfNotNull(
                                uiState.followUpPrompt?.let { AiQuickPrompt(it.label, it.prompt) }
                            ) + uiState.quickPrompts,
                            onQuickPrompt = { viewModel.onQuickPrompt(it) }
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
