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


import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun ProofreadScreen(
    viewModel: ProofreadViewModel = hiltViewModel()
) {
    val inputText by viewModel.inputText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val correctedText by viewModel.correctedText.collectAsState()

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

                    if (!isLoading) {
                        // 校正後の文章表示
                        if (correctedText.isNotBlank()) {
                            Text(
                                text = "校正後の文章",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = correctedText,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        } else {
                            Text(
                                text = "校正できませんでした。",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        
                        // 校正前の文章表示（引数の値をそのまま）
                        Text(
                            text = "校正前の文章",
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

