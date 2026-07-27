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

/** En qué punto va la importación del texto compartido. */
enum class ImportStage { ANALYZING, REVIEW, SAVING, DONE }

/** Una fila de la revisión: la propuesta y si el usuario la quiere. */
data class ImportProductRow(val product: AiShoppingSuggestion, val selected: Boolean = true)

data class ImportRoutineRow(val routine: AiRoutineSuggestion, val selected: Boolean = true)

data class ImportSharedUiState(
    val stage: ImportStage = ImportStage.ANALYZING,
    val products: List<ImportProductRow> = emptyList(),
    val routines: List<ImportRoutineRow> = emptyList(),
    /** Si lo propuesto lo sacó el modelo local o el lector de texto plano. */
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

    /** Nada que revisar: ni productos ni rutinas. */
    val hasNothing: Boolean
        get() = products.isEmpty() && routines.isEmpty()

    /**
     * Ofrecer "analizar con la IA": el modelo está listo pero lo que se ve salió del lector de
     * texto plano (porque no lo estaba al recibir el texto, o porque el modelo no encontró nada).
     */
    val canAnalyzeWithAi: Boolean
        get() = isModelReady && !usedAi && stage == ImportStage.REVIEW
}

/**
 * "Compartir con Habitly": recibe el texto de otra app, propone lo que ha reconocido y **no
 * guarda nada** hasta que el usuario lo confirma en la revisión.
 *
 * El texto llega de fuera y no es fiable, así que:
 *  - se sanea y acota en [SharedTextGuard] antes de tocar el modelo,
 *  - el modelo lo ve delimitado como datos, nunca como instrucciones,
 *  - y el alta la dispara el usuario desde la pantalla de revisión, con casillas y cantidades
 *    editables. Por mucho que el texto "pida" crear algo, aquí no se da de alta solo.
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

    /** Texto ya saneado del que se está tirando (para poder reanalizarlo con IA). */
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

    fun onTextReceived(raw: String) {
        if (sanitizedText.isNotBlank()) return

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
                Log.e(tag, "Failed to analyze shared text", e)
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

    /** Ajusta la cantidad de una propuesta: el modelo se equivoca y el usuario manda. */
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

    /** Da de alta solo lo que sigue marcado. */
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
                        Log.e(tag, "No se pudieron añadir los productos compartidos", it)
                    }
            }
            if (routines.isNotEmpty()) {
                // Compartidas con la casa: un texto que llega de fuera suele ser el reparto de
                // tareas del piso, no una rutina personal.
                addAiRoutinesUseCase(routines, RoutineType.HOUSEHOLD)
                    .onSuccess { addedRoutines = it }
                    .onFailure {
                        failed = true
                        Log.e(tag, "No se pudieron crear las rutinas compartidas", it)
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
                Log.e(tag, "Error al encolar la descarga del modelo", e)
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

    /** Al cerrar la hoja: se corta la inferencia en vuelo y se olvida el texto recibido. */
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
