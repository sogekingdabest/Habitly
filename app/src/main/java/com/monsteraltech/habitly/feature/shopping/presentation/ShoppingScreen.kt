package com.monsteraltech.habitly.feature.shopping.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import com.monsteraltech.habitly.feature.shopping.presentation.components.PantryContent
import com.monsteraltech.habitly.ui.components.HabitlyBackground
import com.monsteraltech.habitly.ui.components.HabitlyCard
import com.monsteraltech.habitly.ui.components.MeshArrangement
import com.monsteraltech.habitly.ui.theme.LeafCornerMedium

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    onNavigateToHistory: () -> Unit = {},
    onNavigateToAddProduct: () -> Unit = {},
    viewModel: ShoppingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddStoreDialog by remember { mutableStateOf(false) }
    var newStoreName by remember { mutableStateOf("") }
    var showArchiveDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.errorRes) {
        uiState.errorRes?.let { res ->
            snackbarHostState.showSnackbar(context.getString(res))
            viewModel.onErrorShown()
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
                onClick = onNavigateToAddProduct,
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
                Column {
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

            if (uiState.totalItems > 0) {
                LinearProgressIndicator(
                    progress = { uiState.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    color = MaterialTheme.colorScheme.primary
                )
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
                Spacer(modifier = Modifier.height(8.dp))
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
                                showCompletedSection = uiState.showCompletedSection,
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
            items.forEach { item ->
                ShoppingItemRow(
                    item = item,
                    onToggle = { onToggle(item.id) },
                    onDelete = { onDelete(item.id) }
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
    HabitlyCard(
        modifier = Modifier.fillMaxWidth(),
        shape = LeafCornerMedium,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
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
                            ShoppingItemRow(
                                item = item,
                                onToggle = { onToggle(item.id) },
                                onDelete = { onDelete(item.id) },
                                isCompleted = true
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShoppingItemRow(
    item: com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    isCompleted: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
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
                if (item.quantity > 1 || item.unit != "unidad") {
                    Text(
                        text = "${item.quantity} ${item.unit}${if (item.quantity > 1 && item.unit != "unidad") "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        }
        IconButton(onClick = onDelete, modifier = Modifier.size(48.dp)) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = stringResource(R.string.cd_delete),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun ShoppingItemCard(
    item: com.monsteraltech.habitly.feature.shopping.domain.model.ShoppingItem,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    isCompleted: Boolean = false
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isCompleted)
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(if (isCompleted) 0.dp else 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = { onToggle() }
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
                if (item.quantity > 1 || item.unit != "unidad") {
                    Text(
                        text = "${item.quantity} ${item.unit}${if (item.quantity > 1 && item.unit != "unidad") "s" else ""}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.cd_delete), tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}
