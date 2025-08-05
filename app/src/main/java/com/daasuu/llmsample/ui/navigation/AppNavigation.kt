package com.daasuu.llmsample.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.daasuu.llmsample.ui.screens.chat.ChatScreen
import com.daasuu.llmsample.ui.screens.proofread.ProofreadScreen
import com.daasuu.llmsample.ui.screens.summarize.SummarizeScreen

@Composable
fun AppNavigation(
    navController: NavHostController,
    startDestination: String = BottomNavItem.Chat.route
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(BottomNavItem.Chat.route) {
            ChatScreen()
        }
        composable(BottomNavItem.Summarize.route) {
            SummarizeScreen()
        }
        composable(BottomNavItem.Proofread.route) {
            ProofreadScreen()
        }
    }
}