package com.daasuu.llmsample

import android.os.Bundle
import android.view.WindowManager
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
        
        // 画面がOFFにならないようにする（Wake状態を維持）
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        
        enableEdgeToEdge()
        setContent {
            DroidKaigiLocalLLMSampleTheme {
                MainScreen()
            }
        }
    }
}