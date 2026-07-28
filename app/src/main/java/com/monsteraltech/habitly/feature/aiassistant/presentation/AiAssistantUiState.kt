package com.monsteraltech.habitly.feature.aiassistant.presentation

import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiChatSession
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiQuickPrompt
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiRoutineSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiShoppingSuggestion
import com.monsteraltech.habitly.feature.aiassistant.domain.model.FollowUpTarget
import com.monsteraltech.habitly.feature.aiassistant.domain.repository.ModelStatus

data class AiAssistantUiState(
    val chatSession: AiChatSession = AiChatSession(),
    val currentInput: String = "",
    val isGenerating: Boolean = false,
    /** The second turn (routine or shopping extraction) is running: shows "preparing". */
    val isExtractingSuggestions: Boolean = false,
    /** Dynamic error message, e.g. from an exception. For fixed errors use [errorRes]. */
    val error: String? = null,
    /** Localised error by resource id; the screen resolves it with `stringResource`. */
    val errorRes: Int? = null,
    /** One-shot event: how many items were just added to the list, for the snackbar. */
    val addedToListCount: Int? = null,
    val modelStatus: ModelStatus = ModelStatus.NotDownloaded,
    val availableModels: List<AiModelConfig> = emptyList(),
    val selectedModel: AiModelConfig? = null,
    /**
     * Device RAM in bytes, in commercial GB. The screen crosses it with each model's thresholds
     * (`compatibilityWith`) to decide which to offer, which to warn about and which to block.
     */
    val deviceRamBytes: Long = 0L,
    /** Model that is tight on RAM awaiting the user's download confirmation, or `null`. */
    val pendingTightDownloadModel: AiModelConfig? = null,
    /** Ids of the catalog models already downloaded, for the model dropdown. */
    val downloadedModelIds: Set<String> = emptySet(),
    val chatHistory: List<AiChatSession> = emptyList(),
    val quickPrompts: List<AiQuickPrompt> = emptyList(),
    /** Target of the follow-up chip after a proposal with no card ("Yes, create them" / "Yes, to
     *  the list"), or `null` for no chip. The screen localises label, prompt and confirmation. */
    val followUpTarget: FollowUpTarget? = null,
    /** Last generation's metrics (debug builds only): TTFT and decode speed. */
    val lastGenerationStats: String? = null,
    /** Items the AI proposes adding to the list, keyed by message id. */
    val shoppingSuggestions: Map<String, List<AiShoppingSuggestion>> = emptyMap(),
    /** Ids of messages whose suggestions were already added to the list. */
    val addedSuggestionMessageIds: Set<String> = emptySet(),
    /** Id of the message whose list is being added right now, for the button spinner. */
    val addingSuggestionMessageId: String? = null,
    /** Routines the AI proposes creating, keyed by message id. */
    val routineSuggestions: Map<String, List<AiRoutineSuggestion>> = emptyMap(),
    /** Ids of messages whose routines were already created. */
    val addedRoutineMessageIds: Set<String> = emptySet(),
    /** Id of the message whose routines are being created right now. */
    val addingRoutineMessageId: String? = null,
    /** One-shot event: how many routines were just created, for the snackbar. */
    val addedRoutinesCount: Int? = null,
    /** Fraction of the context budget in use `[0f, 1f]`: triggers the compaction hint. */
    val contextUsage: Float = 0f,
    /** Compaction (summarising the old context) is running. */
    val isCompacting: Boolean = false,
    /** One-shot event: the conversation was just compacted, for the snackbar. */
    val contextCompacted: Boolean = false,
    /** One-shot event: outcome of the last answer report (true = sent). */
    val reportResult: Boolean? = null,
    /** Ids of assistant messages already reported, which disables their report action. */
    val reportedMessageIds: Set<String> = emptySet()
)
