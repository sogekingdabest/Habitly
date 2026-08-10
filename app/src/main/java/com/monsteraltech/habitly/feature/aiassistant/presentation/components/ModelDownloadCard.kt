package com.monsteraltech.habitly.feature.aiassistant.presentation.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

import com.monsteraltech.habitly.R
import com.monsteraltech.habitly.feature.aiassistant.domain.model.AiModelConfig
import java.util.Locale

@Composable
fun ModelDownloadCard(
    modelConfig: AiModelConfig?,
    progress: Float,
    isDownloading: Boolean,
    onDownload: () -> Unit,
    modifier: Modifier = Modifier,
    onCancel: () -> Unit = {}
) {
    val sizeText = remember(modelConfig) {
        modelConfig?.let { formatBytes(it.sizeBytes) } ?: "~2GB"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.ai_local_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = stringResource(R.string.ai_local_desc),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (isDownloading) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    CircularProgressIndicator(progress = { progress })
                    Text(
                        text = stringResource(R.string.ai_downloading_progress, (progress * 100).toInt()),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = sizeText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    // Cancelling keeps what was downloaded: tapping again resumes.
                    TextButton(onClick = onCancel) {
                        Text(stringResource(R.string.ai_cancel_download))
                    }
                }
            } else {
                Button(onClick = onDownload) {
                    Text(stringResource(R.string.ai_download_model, sizeText))
                }
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val exp = (Math.log(bytes.toDouble()) / Math.log(1024.0)).toInt()
    val prefix = arrayOf("B", "KB", "MB", "GB", "TB")
    val value = bytes / Math.pow(1024.0, exp.toDouble())
    return String.format(Locale.US, "%.1f %s", value, prefix[exp])
}
