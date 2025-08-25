package com.daasuu.llmsample.ui.screens.summarize

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel

@Composable
fun SummarizeScreen(
    viewModel: SummarizeViewModel = hiltViewModel()
) {
    val inputText by viewModel.inputText.collectAsState()
    val summaryText by viewModel.summaryText.collectAsState()
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
                    placeholder = { Text("要約したいテキストを入力...") },
                    minLines = 5,
                    maxLines = 10
                )
            }
        }

        if (summaryText.isNotBlank() || isLoading) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
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
                            text = "要約結果",
                            style = MaterialTheme.typography.titleMedium
                        )

                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Text(
                        text = if (summaryText.isNotBlank()) summaryText else "要約中...",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = viewModel::clearAll,
                modifier = Modifier.weight(1f)
            ) {
                Text("クリア")
            }

            Button(
                onClick = viewModel::summarize,
                modifier = Modifier.weight(1f),
                enabled = inputText.isNotBlank() && !isLoading
            ) {
                Text("要約する")
            }
        }
    }
}