package com.daasuu.llmsample

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class LLMSampleApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // Note: モデルのコピーは各ViewModelで必要に応じて実行されます
        // ここでは初期化処理のみ
    }
}