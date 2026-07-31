package com.monsteraltech.habitly.feature.share.presentation

import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.AiAssistantRepository
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiItemsToShoppingListUseCase
import com.monsteraltech.habitly.feature.aiassistant.domain.usecase.AddAiRoutinesUseCase
import com.monsteraltech.habitly.feature.routines.domain.model.RoutineType
import com.monsteraltech.habitly.feature.share.domain.usecase.ExtractSharedTextUseCase
import com.monsteraltech.habitly.feature.share.domain.usecase.SharedTextExtraction
import com.monsteraltech.habitly.feature.share.domain.util.SharedTextGuard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where the shared-text import currently stands. */
enum class ImportStage { ANALYZING, REVIEW, SAVING, DONE }

/** A review row: the proposal and whether the user wants it. */
data class ImportProductRow(val product: AiShoppingSuggestion, val selected: Boolean = true)

data class ImportRoutineRow(val routine: AiRoutineSuggestion, val selected: Boolean = true)

data class ImportSharedUiState(
    val stage: ImportStage = ImportStage.ANALYZING,
    val products: List<ImportProductRow> = emptyList(),
    val routines: List<ImportRoutineRow> = emptyList(),
    /** Whether the proposals came from the local model or the plain-text reader. */
    val usedAi: Boolean = false,
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val selectedModel: AiModelConfig? = null,
    val savedProducts: Int = 0,
    val savedRoutines: Int = 0,
    @StringRes val errorRes: Int? = null
) {
    val selectedProducts: List<AiShoppingSuggestion>
        get() = products.filter { it.selected }.map { it.product }

    val selectedRoutines: List<AiRoutineSuggestion>
        get() = routines.filter { it.selected }.map { it.routine }

    val selectedCount: Int
        get() = selectedProducts.size + selectedRoutines.size

    val isModelReady: Boolean
        get() = modelStatus is ModelStatus.Ready

    val isDownloading: Boolean
        get() = modelStatus is ModelStatus.Downloading

    val downloadProgress: Float
        get() = (modelStatus as? ModelStatus.Downloading)?.progress ?: 0f

    /** Nothing to review: no products and no routines. */
    val hasNothing: Boolean
        get() = products.isEmpty() && routines.isEmpty()

    /**
     * Offer "analyse with AI": the model is ready but what is shown came from the plain-text reader
     * (because it was not ready when the text arrived, or because the model found nothing).
     */
    val canAnalyzeWithAi: Boolean
        get() = isModelReady && !usedAi && stage == ImportStage.REVIEW
}

/**
 * "Share with Habitly": takes text from another app, proposes what it recognised, and **saves
 * nothing** until the user confirms it in the review.
 *
 * The text comes from outside and is untrusted, so:
 *  - it is sanitised and capped in [SharedTextGuard] before the model is touched,
 *  - the model sees it delimited as data, never as instructions,
 *  - and the user triggers the creation from the review screen, with editable checkboxes and
 *    quantities. However much the text "asks" to create something, nothing is created on its own.
 */
@HiltViewModel
class ImportSharedTextViewModel @Inject constructor(
    private val extractSharedTextUseCase: ExtractSharedTextUseCase,
    private val addAiItemsToShoppingListUseCase: AddAiItemsToShoppingListUseCase,
    private val addAiRoutinesUseCase: AddAiRoutinesUseCase,
    private val repository: AiAssistantRepository
) : ViewModel() {

    private val tag = "ImportSharedText"

    private val _uiState = MutableStateFlow(ImportSharedUiState())
    val uiState: StateFlow<ImportSharedUiState> = _uiState.asStateFlow()

    /** The already-sanitised text in use (so it can be re-analysed with AI). */
    private var sanitizedText: String = ""

    private var extractionJob: Job? = null

    init {
        viewModelScope.launch {
            repository.observeModelStatus().collectLatest { status ->
                _uiState.update { it.copy(modelStatus = status) }
            }
        }
        viewModelScope.launch {
            repository.observeSelectedModel().collectLatest { model ->
                _uiState.update { it.copy(selectedModel = model) }
            }
        }
    }

    /**
     * Entry point: the raw text that arrived via `ACTION_SEND`. With the model on disk it is
     * analysed with AI; without it the line-by-line parse is proposed right away (it is instant) and
     * the screen offers to download it.
     */
    fun onTextReceived(raw: String) {
        if (sanitizedText.isNotBlank()) return // Already working on this text.

        sanitizedText = SharedTextGuard.sanitize(raw)
        if (sanitizedText.isBlank()) {
            _uiState.update { it.copy(stage = ImportStage.REVIEW, usedAi = false) }
            return
        }

        if (_uiState.value.isModelReady) {
            analyzeWithAi()
        } else {
            showExtraction(extractSharedTextUseCase.withoutAi(sanitizedText))
        }
    }

    /** Re-runs the extraction with the local model (after downloading it, or if the simple parse failed). */
    fun onAnalyzeWithAi() {
        if (!_uiState.value.isModelReady || sanitizedText.isBlank()) return
        analyzeWithAi()
    }

    private fun analyzeWithAi() {
        extractionJob?.cancel()
        _uiState.update { it.copy(stage = ImportStage.ANALYZING, errorRes = null) }
        extractionJob = viewModelScope.launch {
            try {
                showExtraction(extractSharedTextUseCase.withAi(sanitizedText))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(tag, "Failed to analyse the shared text", e)
                // There is a way out without AI: propose the line-by-line parse and flag the failure.
                showExtraction(extractSharedTextUseCase.withoutAi(sanitizedText))
                _uiState.update { it.copy(errorRes = R.string.share_error_analyze) }
            }
        }
    }

    private fun showExtraction(extraction: SharedTextExtraction) {
        _uiState.update { state ->
            state.copy(
                stage = ImportStage.REVIEW,
                products = extraction.products.map { ImportProductRow(it) },
                routines = extraction.routines.map { ImportRoutineRow(it) },
                usedAi = extraction.usedAi
            )
        }
    }

    fun onToggleProduct(index: Int) {
        _uiState.update { state ->
            state.copy(
                products = state.products.mapIndexed { i, row ->
                    if (i == index) row.copy(selected = !row.selected) else row
                }
            )
        }
    }

    fun onToggleRoutine(index: Int) {
        _uiState.update { state ->
            state.copy(
                routines = state.routines.mapIndexed { i, row ->
                    if (i == index) row.copy(selected = !row.selected) else row
                }
            )
        }
    }

    /** Adjusts a proposal's quantity: the model gets it wrong and the user is in charge. */
    fun onChangeQuantity(index: Int, delta: Int) {
        _uiState.update { state ->
            state.copy(
                products = state.products.mapIndexed { i, row ->
                    if (i != index) row else {
                        val quantity = (row.product.quantity + delta).coerceIn(1, MAX_QUANTITY)
                        row.copy(product = row.product.copy(quantity = quantity))
                    }
                }
            )
        }
    }

    /** Creates only what is still checked. */
    fun onConfirm() {
        val state = _uiState.value
        if (state.stage == ImportStage.SAVING || state.selectedCount == 0) return

        val products = state.selectedProducts
        val routines = state.selectedRoutines

        _uiState.update { it.copy(stage = ImportStage.SAVING, errorRes = null) }
        viewModelScope.launch {
            var addedProducts = 0
            var addedRoutines = 0
            var failed = false

            if (products.isNotEmpty()) {
                addAiItemsToShoppingListUseCase(products)
                    .onSuccess { addedProducts = it }
                    .onFailure {
                        failed = true
                        Log.e(tag, "Could not add the shared products", it)
                    }
            }
            if (routines.isNotEmpty()) {
                // Shared with the household: text arriving from outside is usually the flat's chore
                // split, not a personal routine.
                addAiRoutinesUseCase(routines, RoutineType.HOUSEHOLD)
                    .onSuccess { addedRoutines = it }
                    .onFailure {
                        failed = true
                        Log.e(tag, "Could not create the shared routines", it)
                    }
            }

            _uiState.update {
                it.copy(
                    stage = if (addedProducts + addedRoutines > 0) ImportStage.DONE else ImportStage.REVIEW,
                    savedProducts = addedProducts,
                    savedRoutines = addedRoutines,
                    errorRes = if (failed) R.string.share_error_save else null
                )
            }
        }
    }

    fun onDownloadModel(wifiOnly: Boolean) {
        viewModelScope.launch {
            try {
                repository.downloadModel(wifiOnly)
            } catch (e: Exception) {
                Log.e(tag, "Error enqueueing the model download", e)
                _uiState.update { it.copy(errorRes = R.string.ai_error_download) }
            }
        }
    }

    fun onCancelDownload() {
        repository.cancelDownload()
    }

    fun onErrorShown() {
        _uiState.update { it.copy(errorRes = null) }
    }

    /** On closing the sheet: cancel the in-flight inference and forget the received text. */
    fun onDismissed() {
        extractionJob?.cancel()
        extractionJob = null
        sanitizedText = ""
        _uiState.update {
            ImportSharedUiState(modelStatus = it.modelStatus, selectedModel = it.selectedModel)
        }
    }

    private companion object {
        const val MAX_QUANTITY = 99
    }
}
