package com.daasuu.llmsample.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ModelTraining
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.daasuu.llmsample.data.benchmark.BenchmarkMode
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.ui.screens.model_selection.ModelSelectionScreen

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val selectedProvider by viewModel.selectedProvider.collectAsState()
    val isBenchmarkMode by BenchmarkMode.isEnabled.collectAsState()
    val isGpuEnabled by viewModel.isGpuEnabled.collectAsState()
    val shouldShowGpuSettings = selectedProvider == LLMProvider.LITE_RT
    val shouldShowModelSelection = selectedProvider == LLMProvider.LLAMA_CPP

    var showModelSelectionDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            // ベンチマークモードの説明
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isBenchmarkMode) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        Icons.Default.Tune,
                        contentDescription = "ベンチマークモード",
                        modifier = Modifier.padding(end = 12.dp),
                        tint = if (isBenchmarkMode) {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Column {
                        Text(
                            text = if (isBenchmarkMode) "ベンチマークモード: 有効" else "ベンチマークモード: 無効",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isBenchmarkMode) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = if (isBenchmarkMode) {
                                "全プロバイダーで統一プロンプトを使用中。Session Proposalで約束した「同一プロンプト・同一端末でのベンチマーク」を実現します。"
                            } else {
                                "各プロバイダー向けに最適化されたプロンプトを使用中。実用性を重視した設定です。"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isBenchmarkMode) {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        item {
            // Provider selection
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        text = "現在の LLM プロバイダー",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    LLMProvider.entries.forEach { provider ->
                        val isProviderAvailable = viewModel.isProviderAvailable(provider)
                        val unavailableReason = viewModel.getProviderUnavailableReason(provider)

                        Column(
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedProvider == provider,
                                    onClick = {
                                        if (isProviderAvailable) {
                                            viewModel.selectProvider(provider)
                                        }
                                    },
                                    enabled = isProviderAvailable
                                )
                                Column(
                                    modifier = Modifier.padding(start = 8.dp)
                                ) {
                                    Text(
                                        text = provider.displayName,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            color = if (isProviderAvailable) {
                                                MaterialTheme.colorScheme.onSurface
                                            } else {
                                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                            }
                                        )
                                    )

                                    if (!isProviderAvailable && unavailableReason != null) {
                                        Text(
                                            text = unavailableReason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Llama.cpp Model Selection
        if (shouldShowModelSelection) {
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "Llama.cpp モデル選択",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        OutlinedButton(
                            onClick = { showModelSelectionDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.ModelTraining,
                                contentDescription = "モデル選択",
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text("使用するモデルを選択")
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }

                        Text(
                            text = "ダウンロード済みのモデルから使用するモデルを選択できます",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }

        if (shouldShowGpuSettings) {
            item {
                // GPU Settings (only shown when Gemma3/LITE_RT is selected)
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = "GPU 設定",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "GPU アクセラレーション",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "推論処理にGPUを使用します（MediaPipe GPU Delegate）",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            }

                            Switch(
                                checked = isGpuEnabled,
                                onCheckedChange = { viewModel.setGpuEnabled(it) }
                            )
                        }
                    }
                }
            }
        }
    }

    // モデル選択ダイアログ
    if (showModelSelectionDialog) {
        AlertDialog(
            onDismissRequest = { showModelSelectionDialog = false },
            title = {
                Text("Llamaモデル選択")
            },
            text = {
                ModelSelectionScreen()
            },
            confirmButton = {
                TextButton(onClick = { showModelSelectionDialog = false }) {
                    Text("閉じる")
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
