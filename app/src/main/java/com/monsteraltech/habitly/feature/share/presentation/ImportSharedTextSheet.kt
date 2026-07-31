package com.monsteraltech.habitly.feature.share.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.EventRepeat
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.shopping.presentation.components.quantityWithUnit
import com.monsteraltech.habitly.ui.components.HabitlyPrimaryButton
import com.monsteraltech.habitly.ui.components.HabitlyTextButton
import com.monsteraltech.habitly.ui.theme.habitly
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * "Share with Habitly": review sheet for what was recognised in text arriving from another app.
 *
 * This is the screen that makes the whole feature safe: the model gets things wrong and shared text
 * is untrusted, so **nothing is saved until the user confirms it here**, with checkboxes to discard
 * and editable quantities.
 *
 * [sharedText] is the intent's raw text; [onDismiss] closes and consumes it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSharedTextSheet(
    sharedText: String,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onImported: (products: Int, routines: Int) -> Unit,
    viewModel: ImportSharedTextViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var showDownloadDialog by remember { mutableStateOf(false) }

    LaunchedEffect(sharedText) {
        viewModel.onTextReceived(sharedText)
    }

    // Immediate close (the gesture already brings its own animation).
    val dismiss: () -> Unit = {
        viewModel.onDismissed()
        onDismiss()
    }

    // Close from a button: the sheet retracts with its animation first, then the state is cleared.
    val hideAndDismiss: () -> Unit = {
        scope.launch { sheetState.hide() }.invokeOnCompletion { dismiss() }
    }

    // Save done: it is announced outside (a snackbar on the screen) and the sheet retracts on its own.
    LaunchedEffect(uiState.stage) {
        if (uiState.stage == ImportStage.DONE) {
            onImported(uiState.savedProducts, uiState.savedRoutines)
            sheetState.hide()
            dismiss()
        }
    }

    ModalBottomSheet(onDismissRequest = dismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Text(
                text = stringResource(R.string.share_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    if (uiState.usedAi) R.string.share_subtitle_ai else R.string.share_subtitle_plain
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.habitly.textSecondary
            )

            Spacer(Modifier.height(16.dp))

            when {
                uiState.stage == ImportStage.ANALYZING -> AnalyzingState()
                uiState.hasNothing -> NothingFoundState()
                else -> ReviewState(
                    uiState = uiState,
                    onToggleProduct = viewModel::onToggleProduct,
                    onChangeQuantity = viewModel::onChangeQuantity,
                    onToggleRoutine = viewModel::onToggleRoutine
                )
            }

            uiState.errorRes?.let { errorRes ->
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(errorRes),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(16.dp))

            // With no model on disk the user is not left stranded: what is shown comes from the
            // plain-text reader, and from here they can download the model for a better read.
            if (uiState.stage != ImportStage.ANALYZING && !uiState.isModelReady) {
                OfferLocalModel(
                    isDownloading = uiState.isDownloading,
                    progress = uiState.downloadProgress,
                    onDownload = { showDownloadDialog = true },
                    onCancel = viewModel::onCancelDownload
                )
                Spacer(Modifier.height(12.dp))
            }

            // Also when nothing was recognised: that is exactly the case where the user has just
            // downloaded the model and needs to be able to retry with it.
            if (uiState.canAnalyzeWithAi) {
                HabitlyTextButton(
                    text = stringResource(R.string.share_analyze_with_ai),
                    onClick = viewModel::onAnalyzeWithAi,
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
                Spacer(Modifier.height(8.dp))
            }

            if (uiState.stage != ImportStage.ANALYZING && !uiState.hasNothing) {
                HabitlyPrimaryButton(
                    text = pluralStringResource(
                        R.plurals.share_add_selected,
                        uiState.selectedCount,
                        uiState.selectedCount
                    ),
                    onClick = viewModel::onConfirm,
                    enabled = uiState.selectedCount > 0,
                    loading = uiState.stage == ImportStage.SAVING,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }

            HabitlyTextButton(
                text = stringResource(R.string.common_cancel),
                onClick = hideAndDismiss,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDownloadDialog) {
        val sizeText = uiState.selectedModel?.let { formatModelSize(it.sizeBytes) } ?: "~2 GB"
        AlertDialog(
            onDismissRequest = { showDownloadDialog = false },
            title = { Text(stringResource(R.string.ai_download_network_title)) },
            text = { Text(stringResource(R.string.ai_download_network_message, sizeText)) },
            confirmButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    viewModel.onDownloadModel(wifiOnly = true)
                }) { Text(stringResource(R.string.ai_download_wifi_only)) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showDownloadDialog = false
                    viewModel.onDownloadModel(wifiOnly = false)
                }) { Text(stringResource(R.string.ai_download_any_network)) }
            }
        )
    }
}

/**
 * The local model's first inference is slow (engine load + prefill), so there is progress and an
 * explicit notice: a still screen reads as frozen.
 */
@Composable
private fun AnalyzingState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = stringResource(R.string.share_analyzing),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.share_analyzing_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.habitly.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

/** A text with no products creates nothing, and it says so plainly. */
@Composable
private fun NothingFoundState() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = stringResource(R.string.share_nothing_found),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        Text(
            text = stringResource(R.string.share_nothing_found_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.habitly.textSecondary,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ReviewState(
    uiState: ImportSharedUiState,
    onToggleProduct: (Int) -> Unit,
    onChangeQuantity: (Int, Int) -> Unit,
    onToggleRoutine: (Int) -> Unit
) {
    // Height-capped: a long recipe must not push the save button off the sheet.
    LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
        if (uiState.products.isNotEmpty()) {
            item(key = "products-header") {
                SectionHeader(
                    icon = Icons.Filled.ShoppingCart,
                    text = pluralStringResource(
                        R.plurals.ai_suggestion_count,
                        uiState.products.size,
                        uiState.products.size
                    )
                )
            }
            itemsIndexed(uiState.products, key = { _, row -> "p-${row.product.name}" }) { index, row ->
                ProductReviewRow(
                    row = row,
                    onToggle = { onToggleProduct(index) },
                    onChangeQuantity = { delta -> onChangeQuantity(index, delta) }
                )
            }
        }

        if (uiState.routines.isNotEmpty()) {
            item(key = "routines-header") {
                if (uiState.products.isNotEmpty()) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                SectionHeader(
                    icon = Icons.Filled.EventRepeat,
                    text = pluralStringResource(
                        R.plurals.ai_routine_count,
                        uiState.routines.size,
                        uiState.routines.size
                    )
                )
            }
            itemsIndexed(uiState.routines, key = { _, row -> "r-${row.routine.title}" }) { index, row ->
                RoutineReviewRow(row = row, onToggle = { onToggleRoutine(index) })
            }
        }
    }
}

@Composable
private fun SectionHeader(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.habitly.accentText,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.habitly.accentText
        )
    }
}

@Composable
private fun ProductReviewRow(
    row: ImportProductRow,
    onToggle: () -> Unit,
    onChangeQuantity: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(8.dp))
                .toggleable(value = row.selected, onValueChange = { onToggle() }, role = Role.Checkbox)
                .heightIn(min = 48.dp)
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(checked = row.selected, onCheckedChange = null)
            Spacer(Modifier.size(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = row.product.name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = quantityWithUnit(row.product.quantity, row.product.unit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.habitly.textSecondary
                )
            }
        }

        IconButton(
            onClick = { onChangeQuantity(-1) },
            enabled = row.selected && row.product.quantity > 1
        ) {
            Icon(
                Icons.Filled.Remove,
                contentDescription = stringResource(R.string.share_quantity_less, row.product.name)
            )
        }
        IconButton(onClick = { onChangeQuantity(1) }, enabled = row.selected) {
            Icon(
                Icons.Filled.Add,
                contentDescription = stringResource(R.string.share_quantity_more, row.product.name)
            )
        }
    }
}

@Composable
private fun RoutineReviewRow(row: ImportRoutineRow, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .toggleable(value = row.selected, onValueChange = { onToggle() }, role = Role.Checkbox)
            .heightIn(min = 48.dp)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = row.selected, onCheckedChange = null)
        Spacer(Modifier.size(8.dp))
        Text(
            text = row.routine.title,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
    }
}

/** Invitation to download the local model, with its progress if already under way. */
@Composable
private fun OfferLocalModel(
    isDownloading: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            Icons.Filled.AutoAwesome,
            contentDescription = null,
            tint = MaterialTheme.habitly.accentText,
            modifier = Modifier.size(18.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.share_model_offer),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.habitly.textSecondary
            )
            if (isDownloading) {
                Text(
                    text = stringResource(R.string.ai_downloading_progress, (progress * 100).toInt()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.habitly.textSecondary
                )
            }
        }
        if (isDownloading) {
            HabitlyTextButton(text = stringResource(R.string.ai_cancel_download), onClick = onCancel)
        } else {
            HabitlyTextButton(text = stringResource(R.string.share_model_download), onClick = onDownload)
        }
    }
}

/** The model size in GB/MB, in the same format as the assistant screen. */
private fun formatModelSize(bytes: Long): String {
    if (bytes <= 0L) return "~2 GB"
    val gb = bytes / 1_000_000_000.0
    return if (gb >= 1) String.format(Locale.getDefault(), "%.1f GB", gb)
    else String.format(Locale.getDefault(), "%.0f MB", bytes / 1_000_000.0)
}
