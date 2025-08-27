package com.daasuu.llmsample.ui.screens.model_download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daasuu.llmsample.data.model.ModelInfo

@Composable
fun ModelDownloadScreen(
    viewModel: ModelDownloadViewModel = hiltViewModel(),
) {
    val models by viewModel.models.collectAsState()
    val downloadingModels by viewModel.downloadingModels.collectAsState()

    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(models) { model ->
            ModelItem(
                model = model,
                downloadProgress = downloadingModels[model.id],
                onDownloadClick = { viewModel.downloadModel(model.id) },
                onDeleteClick = { viewModel.deleteModel(model.id) }
            )
        }

        item {
            Spacer(modifier = Modifier.padding(bottom = 32.dp))
        }
    }
}

@Composable
private fun ModelItem(
    model: ModelInfo,
    downloadProgress: Float?,
    onDownloadClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = model.name,
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = model.description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "サイズ: ${formatFileSize(model.fileSize)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                when {
                    downloadProgress != null -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            CircularProgressIndicator(
                                progress = downloadProgress,
                                modifier = Modifier.size(32.dp)
                            )
                            Text(
                                text = "${(downloadProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }

                    model.isDownloaded -> {
                        Button(
                            onClick = onDeleteClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("削除")
                        }
                    }

                    else -> {
                        Button(onClick = onDownloadClick) {
                            Text("ダウンロード")
                        }
                    }
                }
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${bytes / 1024} KB"
        bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
        else -> "%.1f GB".format(bytes.toFloat() / (1024 * 1024 * 1024))
    }
}