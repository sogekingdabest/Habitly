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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DoneAll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.shopping.presentation.add.QuickAddSheet
import com.monsteraltech.habitly.feature.shopping.presentation.components.ItemQuantityLabel
import com.monsteraltech.habitly.feature.shopping.presentation.components.PantryContent
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.components.HabitlySwipeRow
import com.monsteraltech.habitly.ui.components.HabitlyTextField
import com.monsteraltech.habitly.ui.components.MeshArrangement
import com.monsteraltech.habitly.ui.components.VoiceInputButton
import com.monsteraltech.habitly.ui.components.swipeRowSemantics
import com.monsteraltech.habitly.ui.theme.LeafCornerMedium
import com.monsteraltech.habitly.ui.theme.habitly

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
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

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

    LaunchedEffect(uiState.errorRes) {
        uiState.errorRes?.let { res ->
            snackbarHostState.showSnackbar(context.getString(res))
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

    LaunchedEffect(uiState.recentlyDeletedName) {
        uiState.recentlyDeletedName?.let { name ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.shopping_item_deleted, name),
                actionLabel = context.getString(R.string.common_undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.onUndoDelete()
            } else {
                viewModel.onUndoSnackbarShown()
            }
        }
    }

    LaunchedEffect(uiState.recentlyDeletedPantryName) {
        uiState.recentlyDeletedPantryName?.let { name ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.pantry_item_removed, name),
                actionLabel = context.getString(R.string.common_undo),
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
        floatingActionButton = {
            FloatingActionButton(
                onClick = viewModel::onOpenQuickAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.shopping_add_product))
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp, bottom = 8.dp)
                .padding(paddingValues)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // weight: with the mic the header carries three icons; without this, on narrow
                // screens or at large font sizes the title would push them off instead of wrapping.
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        stringResource(R.string.shopping_title),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (uiState.totalItems > 0) {
                        Text(
                            stringResource(R.string.shopping_progress, uiState.checkedCount, uiState.totalItems),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row {
                    // Mic in the header: with your hands busy in the kitchen, dictating "leche,
                    // huevos y pan" is the shortest path to the list. The button does not appear
                    // when the device has no speech recogniser.
                    VoiceInputButton(onSpokenText = viewModel::onVoiceProducts)
                    IconButton(onClick = onNavigateToHistory) {
                        Icon(Icons.Filled.History, contentDescription = stringResource(R.string.shopping_view_history))
                    }
                    IconButton(onClick = { showArchiveDialog = true }, enabled = uiState.allItems.isNotEmpty()) {
                        Icon(Icons.Filled.Archive, contentDescription = stringResource(R.string.shopping_archive))
                    }
                }
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

            SearchField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                onClear = viewModel::onClearSearch
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.totalItems > 0) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Frequents sit above the store filter: they are the most tapped thing on the whole
            // screen and used to be buried underneath it.
            if (uiState.frequentItems.isNotEmpty()) {
                Text(
                    stringResource(R.string.shopping_quick_add),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
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
                Spacer(modifier = Modifier.height(12.dp))
            }

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
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.allItems.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Filled.ShoppingCart,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        stringResource(R.string.shopping_empty_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.shopping_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else if (uiState.isSearching &&
                uiState.filteredPendingItems.isEmpty() &&
                uiState.filteredCompletedItems.isEmpty()
            ) {
                // A search finding nothing is the useful answer: it means the product is not on the
                // list and can be added without fear of duplicating it.
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        stringResource(R.string.shopping_search_empty, uiState.searchQuery),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        stringResource(R.string.shopping_search_empty_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
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
    HabitlyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = LeafCornerMedium,
        contentPadding = PaddingValues(0.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = store,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    pluralStringResource(R.plurals.shopping_products_count, items.size, items.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )
            val rowColor = MaterialTheme.habitly.card
            items.forEach { item ->
                // key by id: without it the gesture state would be reused across rows when the list
                // reorders, and a freshly arrived row would show up already swiped.
                key(item.id) {
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

    HabitlyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = LeafCornerMedium,
        color = cardColor,
        elevation = 6.dp,
        contentPadding = PaddingValues(0.dp)
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
