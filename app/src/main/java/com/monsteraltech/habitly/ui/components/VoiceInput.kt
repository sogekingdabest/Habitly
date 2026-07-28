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
 * Dictado por voz con el **reconocedor del sistema** (`RecognizerIntent`): cero dependencias
 * nuevas, cero permisos propios (el permiso de micrófono lo pide la app del reconocedor) y
 * ningún modelo extra que descargar.
 */
object VoiceInput {

    /**
     * ¿Hay alguna app que resuelva el dictado? No todos los dispositivos traen reconocedor
     * (algunos Android sin servicios de Google, por ejemplo), y lanzar el intent a ciegas
     * revienta con `ActivityNotFoundException`. Requiere el `<queries>` del manifiesto: desde
     * Android 11 la visibilidad de paquetes hay que declararla.
     */
    fun isAvailable(context: Context): Boolean =
        context.packageManager
            .queryIntentActivities(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH), 0)
            .isNotEmpty()

    /**
     * Intent de dictado en el idioma indicado. El idioma sale de la configuración de la
     * Activity, que es la que envuelve `LocaleHelper`: así el reconocedor sigue el idioma de
     * **Ajustes de Habitly** y no el del sistema. Dictar en español con el reconocedor en
     * inglés produce basura.
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
 * Botón de micrófono que **desaparece** si el dispositivo no tiene reconocedor, en vez de
 * fallar al pulsarlo. [onSpokenText] recibe la transcripción con más confianza.
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
    // El tag de idioma se lee de la configuración ya envuelta por LocaleHelper (idioma de Ajustes).
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
