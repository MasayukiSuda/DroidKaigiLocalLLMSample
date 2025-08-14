package com.daasuu.llmsample.ui.screens.proofread

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProofreadScreen(
    viewModel: ProofreadViewModel = hiltViewModel()
) {
    val inputText by viewModel.inputText.collectAsState()
    val corrections by viewModel.corrections.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val correctedText by viewModel.correctedText.collectAsState()
    val rawOutput by viewModel.rawOutput.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "入力テキスト",
                    style = MaterialTheme.typography.titleMedium
                )

                OutlinedTextField(
                    value = inputText,
                    onValueChange = viewModel::updateInputText,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("校正したいテキストを入力...") },
                    minLines = 5,
                    maxLines = 10
                )
            }
        }

        if (inputText.isNotBlank()) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "校正結果",
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (isLoading) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    text = "校正中...",
                                    style = MaterialTheme.typography.bodySmall
                                )
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }

                    if (!isLoading && (corrections.isNotEmpty() || correctedText.isNotBlank())) {
                        if (correctedText.isNotBlank()) {
                            Text(
                                text = "修正後の文章",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = correctedText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }

                        Text(
                            text = "修正前（ハイライト表示）",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = buildHighlightedText(inputText, corrections),
                            style = MaterialTheme.typography.bodyMedium
                        )

                        corrections.forEach { correction ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Text(
                                        text = "${correction.type}: ${correction.original} → ${correction.suggested}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (correction.explanation.isNotBlank()) {
                                        Text(
                                            text = correction.explanation,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    } else if (!isLoading) {
                        if (rawOutput.isNotBlank()) {
                            Text(
                                text = "モデル出力 (解析できませんでした)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = rawOutput,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                        Text(
                            text = "修正は見つかりませんでした。",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            text = "修正前の文章",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = inputText,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
        }

        Button(
            onClick = viewModel::proofread,
            modifier = Modifier.fillMaxWidth(),
            enabled = inputText.isNotBlank() && !isLoading
        ) {
            Text("校正する")
        }
    }
}

@Composable
fun buildHighlightedText(
    text: String,
    corrections: List<ProofreadCorrection>
): AnnotatedString {
    return buildAnnotatedString {
        append(text)

        corrections.forEach { correction ->
            val start = text.indexOf(correction.original)
            if (start != -1) {
                addStyle(
                    style = SpanStyle(
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        background = Color.Yellow.copy(alpha = 0.3f)
                    ),
                    start = start,
                    end = start + correction.original.length
                )
            }
        }
    }
}