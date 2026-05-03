package com.monsteraltech.habitly.feature.shopping.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ShoppingScreen(
    onNavigateToHistory: () -> Unit = {},
    viewModel: ShoppingViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    var newItemName by remember { mutableStateOf("") }
    var newItemQuantity by remember { mutableIntStateOf(1) }
    var showAddStoreDialog by remember { mutableStateOf(false) }
    var newStoreName by remember { mutableStateOf("") }
    var showQuantitySelector by remember { mutableStateOf(false) }

    val units = listOf("unidad", "kg", "g", "L", "ml", "docena", "paquete")
    var selectedUnit by remember { mutableStateOf("unidad") }

    if (showAddStoreDialog) {
        AlertDialog(
            onDismissRequest = { showAddStoreDialog = false },
            title = { Text("Añadir Supermercado") },
            text = {
                OutlinedTextField(
                    value = newStoreName,
                    onValueChange = { newStoreName = it },
                    label = { Text("Nombre del supermercado") },
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
                }) { Text("Añadir") }
            },
            dismissButton = {
                TextButton(onClick = { showAddStoreDialog = false }) { Text("Cancelar") }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "Lista de la Compra",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                if (uiState.totalItems > 0) {
                    Text(
                        "${uiState.checkedCount}/${uiState.totalItems} productos",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row {
                IconButton(onClick = onNavigateToHistory) {
                    Icon(Icons.Filled.History, contentDescription = "Ver historial")
                }
                IconButton(onClick = { viewModel.onArchiveList() }) {
                    Icon(Icons.Filled.Archive, contentDescription = "Guardar en historial")
                }
            }
        }

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
                    label = { Text("Nuevo") },
                    leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp)) }
                )
            }
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        if (uiState.frequentItems.isNotEmpty()) {
            Text(
                "Añade rápido:",
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

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newItemName,
                onValueChange = { newItemName = it },
                modifier = Modifier.weight(1f),
                label = { Text("Añadir a ${uiState.selectedStore}...") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { showQuantitySelector = !showQuantitySelector }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Cantidad")
                    }
                }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (newItemName.isNotBlank()) {
                        viewModel.onAddItem(newItemName, newItemQuantity, selectedUnit)
                        newItemName = ""
                        newItemQuantity = 1
                        showQuantitySelector = false
                    }
                },
                modifier = Modifier.height(56.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir")
            }
        }

        AnimatedVisibility(
            visible = showQuantitySelector,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Cantidad:", style = MaterialTheme.typography.labelMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(1, 2, 3, 5).forEach { qty ->
                            FilterChip(
                                selected = newItemQuantity == qty,
                                onClick = { newItemQuantity = qty },
                                label = { Text("${qty}x") },
                                modifier = Modifier.width(48.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    Text("Unidad:", style = MaterialTheme.typography.labelMedium)
                    units.take(4).forEach { unit ->
                        FilterChip(
                            selected = selectedUnit == unit,
                            onClick = { selectedUnit = unit },
                            label = { Text(unit) },
                            modifier = Modifier.width(72.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    "Tu lista está vacía",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Añade tu primer producto para empezar",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                uiState.pendingItemsByStore.forEach { (store, items) ->
                    stickyHeader {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = store,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    "${items.size} producto${if (items.size != 1) "s" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    items(items, key = { it.id }) { item ->
                        ShoppingItemCard(
                            item = item,
                            onToggle = { viewModel.onToggleItem(item.id, !item.isChecked) },
                            onDelete = { viewModel.onDeleteItem(item.id) }
                        )
                    }
                }

                if (uiState.completedItems.isNotEmpty()) {
                    stickyHeader {
                        Surface(
                            color = MaterialTheme.colorScheme.background,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = { viewModel.onToggleCompletedSection() }) {
                                    Text(
                                        "Completados (${uiState.completedItems.size})",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Icon(
                                        if (uiState.showCompletedSection) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.showCompletedSection) {
                        uiState.completedItemsByStore.forEach { (store, items) ->
                            item {
                                Text(
                                    store,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp)
                                )
                            }
                            items(items, key = { it.id }) { item ->
                                ShoppingItemCard(
                                    item = item,
                                    onToggle = { viewModel.onToggleItem(item.id, !item.isChecked) },
                                    onDelete = { viewModel.onDeleteItem(item.id) },
                                    isCompleted = true
                                )
                            }
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    if (uiState.pendingItems.isNotEmpty() || uiState.completedItems.isNotEmpty()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { viewModel.onCheckAll() },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.pendingItems.isNotEmpty()
                            ) {
                                Icon(Icons.Outlined.DoneAll, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Marcar todo")
                            }
                            OutlinedButton(
                                onClick = { viewModel.onDeleteChecked() },
                                modifier = Modifier.weight(1f),
                                enabled = uiState.completedItems.isNotEmpty(),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Limpiar")
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
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
                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f))
            }
        }
    }
}
