package com.daasuu.llmsample

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Rule

/**
 * Unit テスト用のベースクラス
 * - コルーチンのテストディスパッチャーをセットアップ
 * - LiveData のテスト環境をセットアップ
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class TestBase {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    open fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    open fun tearDown() {
        Dispatchers.resetMain()
    }
}
