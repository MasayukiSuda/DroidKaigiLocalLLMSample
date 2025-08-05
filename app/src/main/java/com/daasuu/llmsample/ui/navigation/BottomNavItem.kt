package com.daasuu.llmsample.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.ui.graphics.vector.ImageVector

sealed class BottomNavItem(
    val route: String,
    val icon: ImageVector,
    val label: String
) {
    object Chat : BottomNavItem("chat", Icons.AutoMirrored.Filled.Chat, "チャット")
    object Summarize : BottomNavItem("summarize", Icons.Default.Summarize, "要約")
    object Proofread : BottomNavItem("proofread", Icons.Default.Spellcheck, "校正")
}