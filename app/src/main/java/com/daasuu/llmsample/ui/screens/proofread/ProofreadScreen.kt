package com.daasuu.llmsample.ui.screens.proofread

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

        if (corrections.isNotEmpty() || isLoading) {
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
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }
                    
                    if (corrections.isNotEmpty()) {
                        Text(
                            text = buildHighlightedText(inputText, corrections),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        Text(
                            text = "修正箇所:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
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
                    } else {
                        Text(
                            text = "校正中...",
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