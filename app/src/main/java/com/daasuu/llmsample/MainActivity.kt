package com.daasuu.llmsample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.daasuu.llmsample.ui.screens.MainScreen
import com.daasuu.llmsample.ui.theme.DroidKaigiLocalLLMSampleTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DroidKaigiLocalLLMSampleTheme {
                MainScreen()
            }
        }
    }
}