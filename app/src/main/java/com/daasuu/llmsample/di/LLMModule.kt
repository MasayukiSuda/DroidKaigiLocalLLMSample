package com.daasuu.llmsample.di

import com.daasuu.llmsample.data.llm.gemini.GeminiNanoRepository
import com.daasuu.llmsample.data.llm.llamacpp.LlamaCppRepository
import com.daasuu.llmsample.data.llm.task.TaskRepository
import com.daasuu.llmsample.data.model.LLMProvider
import com.daasuu.llmsample.domain.LLMRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LlamaCppRepo

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LiteRTRepo

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GeminiNanoRepo

@Module
@InstallIn(SingletonComponent::class)
object LLMModule {
    
    @Provides
    @Singleton
    @LlamaCppRepo
    fun provideLlamaCppRepository(
        repository: LlamaCppRepository
    ): LLMRepository = repository
    
    @Provides
    @Singleton
    @LiteRTRepo
    fun provideLiteRTRepository(
        repository: TaskRepository
    ): LLMRepository = repository
    
    @Provides
    @Singleton
    @GeminiNanoRepo
    fun provideGeminiNanoRepository(
        repository: GeminiNanoRepository
    ): LLMRepository = repository
    
    @Provides
    @Singleton
    fun provideLLMRepositories(
        @LlamaCppRepo llamaCppRepo: LLMRepository,
        @LiteRTRepo liteRTRepo: LLMRepository,
        @GeminiNanoRepo geminiNanoRepo: LLMRepository
    ): Map<LLMProvider, LLMRepository> {
        return mapOf(
            LLMProvider.LLAMA_CPP to llamaCppRepo,
            LLMProvider.LITE_RT to liteRTRepo,
            LLMProvider.GEMINI_NANO to geminiNanoRepo
        )
    }
}