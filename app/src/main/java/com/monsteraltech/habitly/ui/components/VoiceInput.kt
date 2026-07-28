package com.monsteraltech.habitly.ui.components

import android.content.Context
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.monsteraltech.habitly.R

/**
 * Voice dictation through the system recogniser (`RecognizerIntent`): no new dependencies, no
 * permissions of our own (the recogniser app asks for the microphone) and no model to download.
 */
object VoiceInput {

    /**
     * Not every device ships a recogniser, and launching the intent blindly throws
     * `ActivityNotFoundException`. Needs the `<queries>` entry in the manifest — package
     * visibility must be declared since Android 11.
     */
    fun isAvailable(context: Context): Boolean =
        context.packageManager
            .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
            .isNotEmpty()

    /**
     * Dictation intent in the given language. The tag comes from the Activity configuration
     * wrapped by `LocaleHelper`, so the recogniser follows Habitly's language setting rather than
     * the system one — dictating Spanish into an English recogniser produces garbage.
     */
    fun intent(prompt: String, languageTag: String): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, prompt)
            if (languageTag.isNotBlank()) {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, languageTag)
            }
        }
}

/**
 * Microphone button that **hides itself** when the device has no recogniser, instead of failing on
 * tap. [onSpokenText] receives the highest-confidence transcription.
 */
@Composable
fun VoiceInputButton(
    onSpokenText: (String) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val context = LocalContext.current
    val available = remember(context) { VoiceInput.isAvailable(context) }
    if (!available) return

    val prompt = stringResource(R.string.ai_voice_prompt)
    // Language tag read from the configuration already wrapped by LocaleHelper.
    val languageTag = LocalConfiguration.current.locales[0].toLanguageTag()

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val spoken = result.data
            ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
            ?.firstOrNull()
        if (!spoken.isNullOrBlank()) onSpokenText(spoken)
    }

    IconButton(
        onClick = { runCatching { launcher.launch(VoiceInput.intent(prompt, languageTag)) } },
        modifier = modifier
    ) {
        Icon(
            Icons.Filled.Mic,
            contentDescription = stringResource(R.string.ai_voice_input),
            tint = tint
        )
    }
}
