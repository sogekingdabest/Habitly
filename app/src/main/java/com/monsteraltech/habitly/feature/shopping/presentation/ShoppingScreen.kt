package com.monsteraltech.habitly.feature.shopping.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.shopping.presentation.add.QuickAddSheet
import com.monsteraltech.habitly.feature.shopping.presentation.components.ItemQuantityLabel
import com.monsteraltech.habitly.feature.shopping.presentation.components.PantryContent
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.HabitlyEmptyState
import com.monsteraltech.habitly.ui.components.HabitlyListSection
import com.monsteraltech.habitly.ui.components.HabitlyListSkeleton
import com.monsteraltech.habitly.ui.components.HabitlyRowDivider
import com.monsteraltech.habitly.ui.components.HabitlyTopBar
import com.monsteraltech.habitly.ui.components.HabitlySwipeRow
import com.monsteraltech.habitly.ui.components.HabitlyTextField
import com.monsteraltech.habitly.ui.components.MeshArrangement
import com.monsteraltech.habitly.ui.components.VoiceInputButton
import com.monsteraltech.habitly.ui.components.swipeRowSemantics
import com.monsteraltech.habitly.ui.theme.LeafCornerMedium
import com.monsteraltech.habitly.ui.theme.habitly
import com.monsteraltech.habitly.ui.theme.sizing

/**
 * [openQuickAdd] arrives `true` when entering from the launcher icon's "Add to shopping" shortcut:
 * the tab opens with the quick-add sheet already unfolded.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    onNavigateToHistory: () -> Unit = {},
    openQuickAdd: Boolean = false,
    onQuickAddHandled: () -> Unit = {},
    viewModel: ShoppingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val errorMessage = uiState.errorRes?.let { stringResource(it) }
    val undoLabel = stringResource(R.string.common_undo)
    val deletedItemMessage = uiState.recentlyDeletedName?.let { name ->
        stringResource(R.string.shopping_item_deleted, name)
    }
    val deletedPantryMessage = uiState.recentlyDeletedPantryName?.let { name ->
        stringResource(R.string.pantry_item_removed, name)
    }

    LaunchedEffect(openQuickAdd) {
        if (openQuickAdd) {
            viewModel.onOpenQuickAdd()
            onQuickAddHandled()
        }
    }

    var showAddStoreDialog by remember { mutableStateOf(false) }
    var newStoreName by remember { mutableStateOf("") }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    // The search box no longer sits permanently between the header and the list: it is one of the
    // header actions, and it unfolds when it is asked for.
    var showSearch by remember { mutableStateOf(false) }
    var showOverflow by remember { mutableStateOf(false) }

    // Inline "add": typing a name and pressing + is the most repeated action of the screen, and it
    // used to cost a trip to a floating button and a sheet. The sheet is still one tap away for
    // when the product needs a store, a quantity or a note.
    var quickAddText by remember { mutableStateOf("") }
    var quickAddFocused by remember { mutableStateOf(false) }

    // skipPartiallyExpanded: the sheet opens fully, keyboard and all; a halfway state would only
    // cover the field you have to type in.
    val quickAddSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    if (uiState.quickAdd.isOpen) {
        QuickAddSheet(
            state = uiState.quickAdd,
            availableStores = uiState.availableStores,
            pantryQuantity = uiState.quickAddPantryMatch?.quantity,
            pantryUnit = uiState.quickAddPantryMatch?.unit,
            duplicate = uiState.quickAddDuplicate,
            sheetState = quickAddSheetState,
            onNameChange = viewModel::onQuickAddNameChange,
            onQuantityChange = viewModel::onQuickAddQuantityChange,
            onUnitChange = viewModel::onQuickAddUnitChange,
            onStoreChange = viewModel::onQuickAddStoreChange,
            onCategoryChange = viewModel::onQuickAddCategoryChange,
            onNotesChange = viewModel::onQuickAddNotesChange,
            onToggleOptions = viewModel::onToggleQuickAddOptions,
            onSave = viewModel::onQuickAddSave,
            onVoiceInput = viewModel::onQuickAddVoice,
            onDismiss = viewModel::onDismissQuickAdd
        )
    }

    LaunchedEffect(uiState.errorRes, errorMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(errorMessage)
            viewModel.onErrorShown()
        }
    }

    // Dictation confirmation: when several are added at once without opening any sheet, the count
    // is the only sign that it was understood correctly.
    val voiceAdded = uiState.voiceAddedCount
    if (voiceAdded != null) {
        val voiceAddedMessage = pluralStringResource(R.plurals.addproduct_added_count, voiceAdded, voiceAdded)
        LaunchedEffect(voiceAdded) {
            snackbarHostState.showSnackbar(voiceAddedMessage)
            viewModel.onVoiceAddedShown()
        }
    }

    LaunchedEffect(uiState.recentlyDeletedName, deletedItemMessage, undoLabel) {
        uiState.recentlyDeletedName?.let {
            val result = snackbarHostState.showSnackbar(
                message = checkNotNull(deletedItemMessage),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDelete()
            } else {
                viewModel.onUndoSnackbarShown()
            }
        }
    }

    LaunchedEffect(uiState.recentlyDeletedPantryName, deletedPantryMessage, undoLabel) {
        uiState.recentlyDeletedPantryName?.let {
            val result = snackbarHostState.showSnackbar(
                message = checkNotNull(deletedPantryMessage),
                actionLabel = undoLabel,
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDeletePantry()
            } else {
                viewModel.onUndoPantrySnackbarShown()
            }
        }
    }

    if (showArchiveDialog) {
        var stockPantry by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showArchiveDialog = false },
            icon = { Icon(Icons.Filled.Archive, contentDescription = null) },
            title = { Text(stringResource(R.string.shopping_archive_confirm_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.shopping_archive_confirm_message))
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .toggleable(
                                value = stockPantry,
                                onValueChange = { stockPantry = it },
                                role = Role.Checkbox
                            )
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(checked = stockPantry, onCheckedChange = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.shopping_archive_stock_pantry),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showArchiveDialog = false
                    viewModel.onArchiveList(stockPantry)
                }) { Text(stringResource(R.string.shopping_archive)) }
            },
            dismissButton = {
                TextButton(onClick = { showArchiveDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(stringResource(R.string.shopping_clear_confirm_title)) },
            text = { Text(stringResource(R.string.shopping_clear_confirm_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.onDeleteChecked()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text(stringResource(R.string.shopping_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    if (showAddStoreDialog) {
        AlertDialog(
            onDismissRequest = { showAddStoreDialog = false },
            title = { Text(stringResource(R.string.shopping_add_store_title)) },
            text = {
                OutlinedTextField(
                    value = newStoreName,
                    onValueChange = { newStoreName = it },
                    label = { Text(stringResource(R.string.shopping_store_name)) },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newStoreName.isNotBlank()) {
                        viewModel.onAddCustomStore(newStoreName)
                        newStoreName = ""
                        showAddStoreDialog = false
                    }
                }) { Text(stringResource(R.string.shopping_add)) }
            },
            dismissButton = {
                TextButton(onClick = { showAddStoreDialog = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        )
    }

    HabitlyBackground(arrangement = MeshArrangement.Shopping) {
    Scaffold(
        containerColor = Color.Transparent,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            HabitlyTopBar(
                title = stringResource(R.string.shopping_title),
                subtitle = if (uiState.totalItems > 0) {
                    pluralStringResource(
                        R.plurals.shopping_progress,
                        uiState.totalItems,
                        uiState.checkedCount,
                        uiState.totalItems
                    )
                } else {
                    null
                },
                actions = {
                    // Mic in the header: with your hands busy in the kitchen, dictating "leche,
                    // huevos y pan" is the shortest path to the list. The button does not appear
                    // when the device has no speech recogniser.
                    VoiceInputButton(onSpokenText = viewModel::onVoiceProducts)
                    IconButton(onClick = {
                        showSearch = !showSearch
                        if (!showSearch) viewModel.onClearSearch()
                    }) {
                        Icon(
                            if (showSearch) Icons.Filled.SearchOff else Icons.Filled.Search,
                            contentDescription = stringResource(R.string.shopping_search)
                        )
                    }
                    // History and archiving are occasional: they cost a tap here and give the
                    // title back the width it was losing to three icons.
                    Box {
                        IconButton(onClick = { showOverflow = true }) {
                            Icon(
                                Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.shopping_more_actions)
                            )
                        }
                        DropdownMenu(
                            expanded = showOverflow,
                            onDismissRequest = { showOverflow = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_view_history)) },
                                leadingIcon = { Icon(Icons.Filled.History, contentDescription = null) },
                                onClick = {
                                    showOverflow = false
                                    onNavigateToHistory()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.shopping_archive)) },
                                leadingIcon = { Icon(Icons.Filled.Archive, contentDescription = null) },
                                enabled = uiState.allItems.isNotEmpty(),
                                onClick = {
                                    showOverflow = false
                                    showArchiveDialog = true
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                // Caps the content and centres it: on a tablet a product row would otherwise run
                // the whole width, with the checkbox at one edge and the name at the other.
                .wrapContentWidth()
                .widthIn(max = MaterialTheme.sizing.contentMaxWidth)
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        ) {
            // The progress reads as the underline of the header, not as one more block of its own.
            if (uiState.totalItems > 0) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = uiState.selectedTab == ShoppingTab.LIST,
                    onClick = { viewModel.onSelectTab(ShoppingTab.LIST) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(stringResource(R.string.shopping_tab_list)) }
                SegmentedButton(
                    selected = uiState.selectedTab == ShoppingTab.PANTRY,
                    onClick = { viewModel.onSelectTab(ShoppingTab.PANTRY) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(stringResource(R.string.shopping_tab_pantry)) }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.selectedTab == ShoppingTab.PANTRY) {
                PantryContent(
                    itemsByCategory = uiState.pantryByCategory,
                    onAdjustQuantity = { itemId, delta -> viewModel.onAdjustPantryQuantity(itemId, delta) },
                    onDelete = { itemId -> viewModel.onDeletePantryItem(itemId) }
                )
                return@Column
            }

            if (showSearch) {
                SearchField(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChange,
                    onClear = viewModel::onClearSearch
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Adding, inline and always in reach: it is the most repeated action of the screen and
            // it used to cost a trip to a floating button and a sheet. The sheet still handles the
            // product that needs a store, a quantity or a note.
            val submitQuickAdd = {
                if (quickAddText.isNotBlank()) {
                    viewModel.onQuickAdd(quickAddText.trim())
                    quickAddText = ""
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HabitlyTextField(
                    value = quickAddText,
                    onValueChange = { quickAddText = it },
                    label = stringResource(R.string.shopping_add_hint),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { quickAddFocused = it.isFocused },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submitQuickAdd() }),
                    trailingIcon = {
                        IconButton(
                            onClick = submitQuickAdd,
                            enabled = quickAddText.isNotBlank()
                        ) {
                            Icon(
                                Icons.Filled.Add,
                                contentDescription = stringResource(R.string.shopping_add_product)
                            )
                        }
                    }
                )
                IconButton(onClick = viewModel::onOpenQuickAdd) {
                    Icon(
                        Icons.Filled.Tune,
                        contentDescription = stringResource(R.string.shopping_add_options),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Frequents belong to the act of adding, so they show while that field has the focus
            // instead of standing between the header and the list all day long.
            if (uiState.frequentItems.isNotEmpty() && (quickAddFocused || quickAddText.isNotBlank())) {
                Spacer(modifier = Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(uiState.frequentItems) { itemName ->
                        AssistChip(
                            onClick = { viewModel.onQuickAdd(itemName) },
                            label = { Text(itemName, style = MaterialTheme.typography.bodySmall) },
                            leadingIcon = {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items(uiState.availableStores, key = { it }) { store ->
                    FilterChip(
                        selected = uiState.selectedStore == store,
                        // While searching the store filter has no place: the search deliberately
                        // sweeps the whole list.
                        enabled = !uiState.isSearching,
                        onClick = { viewModel.onSelectStore(store) },
                        label = { Text(store) }
                    )
                }
                item {
                    AssistChip(
                        onClick = { showAddStoreDialog = true },
                        label = { Text(stringResource(R.string.shopping_new_store)) },
                        leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading) {
                HabitlyListSkeleton(loadingLabel = stringResource(R.string.cd_loading))
            } else if (uiState.allItems.isEmpty()) {
                HabitlyEmptyState(
                    icon = Icons.Outlined.ShoppingCart,
                    title = stringResource(R.string.shopping_empty_title),
                    description = stringResource(R.string.shopping_empty_subtitle)
                )
            } else if (uiState.isSearching &&
                uiState.filteredPendingItems.isEmpty() &&
                uiState.filteredCompletedItems.isEmpty()
            ) {
                // A search finding nothing is the useful answer: it means the product is not on the
                // list and can be added without fear of duplicating it.
                HabitlyEmptyState(
                    icon = Icons.Outlined.SearchOff,
                    title = stringResource(R.string.shopping_search_empty, uiState.searchQuery),
                    description = stringResource(R.string.shopping_search_empty_subtitle)
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    uiState.pendingItemsByStore.forEach { (store, items) ->
                        item(key = "pending-$store") {
                            StoreSectionCard(
                                store = store,
                                items = items,
                                onToggle = { itemId -> viewModel.onToggleItem(itemId, true) },
                                onDelete = { itemId -> viewModel.onDeleteItem(itemId) }
                            )
                        }
                    }

                    if (uiState.filteredCompletedItems.isNotEmpty()) {
                        item(key = "completed-section") {
                            CompletedSectionCard(
                                completedItemsByStore = uiState.completedItemsByStore,
                                showCompletedSection = uiState.isCompletedSectionExpanded,
                                onToggleSection = { viewModel.onToggleCompletedSection() },
                                onToggle = { itemId -> viewModel.onToggleItem(itemId, false) },
                                onDelete = { itemId -> viewModel.onDeleteItem(itemId) }
                            )
                        }
                    }

                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                        if (uiState.filteredPendingItems.isNotEmpty() || uiState.filteredCompletedItems.isNotEmpty()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { viewModel.onCheckAll() },
                                    modifier = Modifier.weight(1f),
                                    enabled = uiState.filteredPendingItems.isNotEmpty()
                                ) {
                                    Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.shopping_check_all))
                                }
                                OutlinedButton(
                                    onClick = { showClearDialog = true },
                                    modifier = Modifier.weight(1f),
                                    enabled = uiState.filteredCompletedItems.isNotEmpty(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(stringResource(R.string.shopping_clear))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
    }
}

/**
 * The list's search box. It filters over pending **and** completed items: adding the rice twice
 * happens precisely because what is already bought is folded away out of sight.
 */
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit
) {
    HabitlyTextField(
        value = query,
        onValueChange = onQueryChange,
        label = stringResource(R.string.shopping_search),
        modifier = Modifier.fillMaxWidth(),
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = onClear) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.shopping_search_clear)
                    )
                }
            }
        }
    )
}

@Composable
fun StoreSectionCard(
    store: String,
    items: List<com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem>,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    HabitlyListSection(
        modifier = Modifier.fillMaxWidth(),
        shape = LeafCornerMedium,
        title = store,
        trailing = {
            Text(
                pluralStringResource(R.plurals.shopping_products_count, items.size, items.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.habitly.textSecondary
            )
        }
    ) {
        val rowColor = MaterialTheme.habitly.card
        items.forEach { item ->
            // key by id: without it the gesture state would be reused across rows when the list
            // reorders, and a freshly arrived row would show up already swiped.
            key(item.id) {
                HabitlyRowDivider()
                ShoppingItemRow(
                    item = item,
                    onToggle = { onToggle(item.id) },
                    onDelete = { onDelete(item.id) },
                    containerColor = rowColor
                )
            }
        }
    }
}

@Composable
fun CompletedSectionCard(
    completedItemsByStore: Map<String, List<com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem>>,
    showCompletedSection: Boolean,
    onToggleSection: () -> Unit,
    onToggle: (String) -> Unit,
    onDelete: (String) -> Unit
) {
    // Opaque on purpose: the rows inside paint this same colour to cover the gesture background,
    // and with a translucent card the colours would not match.
    val cardColor = MaterialTheme.colorScheme.surfaceVariant

    HabitlyListSection(
        modifier = Modifier.fillMaxWidth(),
        shape = LeafCornerMedium,
        color = cardColor
    ) {
        Column {
            TextButton(
                onClick = onToggleSection,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        stringResource(R.string.shopping_completed),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        val totalCompleted = completedItemsByStore.values.sumOf { it.size }
                        Text(
                            "($totalCompleted)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(end = 4.dp)
                        )
                        Icon(
                            if (showCompletedSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )
            AnimatedVisibility(
                visible = showCompletedSection,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column {
                    completedItemsByStore.forEach { (store, items) ->
                        Text(
                            store,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                        )
                        items.forEach { item ->
                            key(item.id) {
                                ShoppingItemRow(
                                    item = item,
                                    onToggle = { onToggle(item.id) },
                                    onDelete = { onDelete(item.id) },
                                    containerColor = cardColor
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * A product row. Swipe right to tick it off, swipe left to delete it (with the undo snackbar): in
 * the supermarket, one hand on the trolley, a gesture lands better than a small icon. The delete
 * button left the row for exactly that reason — it was a destructive target sitting right next to
 * the most repeated gesture.
 *
 * [containerColor] must be the opaque colour of the card containing it: were the row transparent,
 * the gesture's coloured background would always show through.
 */
@Composable
fun ShoppingItemRow(
    item: com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    containerColor: Color
) {
    val toggleLabel = stringResource(
        if (item.isChecked) R.string.shopping_a11y_uncheck else R.string.shopping_a11y_check
    )
    val deleteLabel = stringResource(R.string.cd_delete)

    HabitlySwipeRow(
        onPrimaryAction = onToggle,
        onDelete = onDelete,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(containerColor)
                .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(8.dp))
                    .toggleable(
                        value = item.isChecked,
                        onValueChange = { onToggle() },
                        role = Role.Checkbox
                    )
                    .swipeRowSemantics(
                        primaryLabel = toggleLabel,
                        onPrimaryAction = onToggle,
                        deleteLabel = deleteLabel,
                        onDelete = onDelete
                    )
                    // 48 dp minimum height: the gesture must not steal size from the tap target.
                    .heightIn(min = 48.dp)
                    .padding(vertical = 6.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = item.isChecked,
                    onCheckedChange = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None,
                        color = if (item.isChecked)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            MaterialTheme.colorScheme.onSurface
                    )
                    ItemQuantityLabel(quantity = item.quantity, unit = item.unit)
                }
            }
        }
    }
}
