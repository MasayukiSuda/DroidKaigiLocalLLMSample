package com.daasuu.llmsample.data.benchmark

import com.daasuu.llmsample.data.model.TaskType

/**
 * 標準ベンチマークテストケース定義
 */
object BenchmarkTestCases {

    /**
     * チャット機能のテストケース
     */
    val chatTestCases = listOf(
        BenchmarkTestCase(
            name = "簡単な挨拶",
            description = "基本的な挨拶に対する応答を測定",
            taskType = TaskType.CHAT,
            inputText = "こんにちは。今日の天気について教えてください。",
            expectedOutputLength = 100,
            category = BenchmarkCategory.CONVERSATION
        ),
        BenchmarkTestCase(
            name = "技術的な質問",
            description = "技術的な内容への応答を測定",
            taskType = TaskType.CHAT,
            inputText = "Androidアプリ開発でMVVMパターンを使う利点について詳しく説明してください。",
            expectedOutputLength = 300,
            category = BenchmarkCategory.TECHNICAL
        ),
        BenchmarkTestCase(
            name = "創作的な質問",
            description = "創造性を要する質問への応答を測定",
            taskType = TaskType.CHAT,
            inputText = "未来の東京を舞台にしたSF小説の短いあらすじを作ってください。",
            expectedOutputLength = 200,
            category = BenchmarkCategory.CREATIVE
        ),
        BenchmarkTestCase(
            name = "長文生成",
            description = "長い回答を生成する能力を測定",
            taskType = TaskType.CHAT,
            inputText = "日本の四季の特徴と、それぞれの季節の文化的な意味について、詳細に説明してください。",
            expectedOutputLength = 500,
            category = BenchmarkCategory.GENERAL
        ),
        BenchmarkTestCase(
            name = "コーディング支援",
            description = "プログラミング関連の質問への応答を測定",
            taskType = TaskType.CHAT,
            inputText = "Kotlinでシングルトンパターンを実装する方法を、コード例付きで説明してください。",
            expectedOutputLength = 250,
            category = BenchmarkCategory.CODING
        )
    )

    /**
     * 要約機能のテストケース
     */
    val summarizationTestCases = listOf(
        BenchmarkTestCase(
            name = "短文の要約",
            description = "短い文章の要約能力を測定",
            taskType = TaskType.SUMMARIZATION,
            inputText = """
                人工知能（AI）は、コンピューターシステムが人間のような知的行動を実行する能力を指します。
                AIには機械学習、深層学習、自然言語処理などの技術が含まれます。
                近年、AIは医療、金融、製造業など様々な分野で活用されています。
                特に画像認識や音声認識の分野では、人間を上回る性能を示すことも多くなりました。
            """.trimIndent(),
            expectedOutputLength = 80,
            category = BenchmarkCategory.SUMMARIZATION
        ),
        BenchmarkTestCase(
            name = "技術文書の要約",
            description = "技術的な内容の要約能力を測定",
            taskType = TaskType.SUMMARIZATION,
            inputText = """
                Android開発におけるMVVMパターンは、Model-View-ViewModelアーキテクチャの略で、
                アプリケーションの構造を整理し、保守性を向上させるデザインパターンです。
                Modelはデータとビジネスロジックを担当し、データベースやAPIからの情報を管理します。
                Viewはユーザーインターフェースを担当し、画面表示やユーザーとの相互作用を処理します。
                ViewModelはViewとModelの仲介役として機能し、UIロジックを含みながらも
                Viewから独立してテスト可能な形で実装されます。
                このパターンにより、コードの分離、テスタビリティの向上、
                そして設定変更時のデータ保持が実現されます。
            """.trimIndent(),
            expectedOutputLength = 120,
            category = BenchmarkCategory.TECHNICAL
        ),
        BenchmarkTestCase(
            name = "長文記事の要約",
            description = "長い記事の要約能力を測定",
            taskType = TaskType.SUMMARIZATION,
            inputText = """
                気候変動は21世紀最大の課題の一つとされています。地球の平均気温は産業革命以降、
                約1.1度上昇しており、これは主に人間活動による温室効果ガスの排出が原因です。
                
                二酸化炭素をはじめとする温室効果ガスは、太陽からの熱を大気中に閉じ込め、
                地球温暖化を引き起こします。その結果、極地の氷床融解、海面上昇、
                異常気象の頻発などの現象が観測されています。
                
                対策として、再生可能エネルギーの導入、エネルギー効率の改善、
                森林保護と植樹活動、そして個人レベルでの省エネルギー行動が重要です。
                国際社会では、パリ協定に基づいて各国が温室効果ガス削減目標を設定し、
                協力して取り組んでいます。
                
                企業も環境経営やESG投資への注目が高まる中、
                持続可能な事業モデルへの転換を進めています。
                個人も日常生活での選択が環境に与える影響を意識し、
                行動変容を起こすことが求められています。
            """.trimIndent(),
            expectedOutputLength = 150,
            category = BenchmarkCategory.SUMMARIZATION
        )
    )

    /**
     * 校正機能のテストケース
     */
    val proofreadingTestCases = listOf(
        BenchmarkTestCase(
            name = "誤字脱字の修正",
            description = "基本的な誤字脱字の修正能力を測定",
            taskType = TaskType.PROOFREADING,
            inputText = "私わ今日、あたらしい本を買いまた。とても興味深い内容で、一気に読んでしまいまた。",
            expectedOutputLength = 80,
            category = BenchmarkCategory.PROOFREADING
        ),
        BenchmarkTestCase(
            name = "文法の修正",
            description = "文法的な誤りの修正能力を測定",
            taskType = TaskType.PROOFREADING,
            inputText = "このアプリは使いやすくて、機能も豊富です。ユーザーから評価高いです。開発チームの努力の結果だと思う。",
            expectedOutputLength = 100,
            category = BenchmarkCategory.PROOFREADING
        ),
        BenchmarkTestCase(
            name = "敬語の修正",
            description = "敬語表現の修正能力を測定",
            taskType = TaskType.PROOFREADING,
            inputText = "明日の会議についてお伺いします。田中部長は参加する予定ですか？資料の準備はできてますか？",
            expectedOutputLength = 90,
            category = BenchmarkCategory.PROOFREADING
        ),
        BenchmarkTestCase(
            name = "ビジネス文書の校正",
            description = "ビジネス文書の校正能力を測定",
            taskType = TaskType.PROOFREADING,
            inputText = """
                件名：プロジェクト進捗について
                
                いつもお世話になっております。
                プロジェクトの進捗状況をご報告いたします。
                
                現在、開発フェーズは順調に進んでおり、予定通り来月末には完了する見込みです。
                ただ、テスト環境の構築で若干の遅れが生じており、
                スケジュールの調整が必要になるかもしれません。
                
                また、追加の要件について検討した結果、
                実装可能であることが判明しましたので、提案させていただきます。
                
                何かご不明な点ございましたら、お気軽にお声がけください。
            """.trimIndent(),
            expectedOutputLength = 200,
            category = BenchmarkCategory.PROOFREADING
        )
    )

    /**
     * パフォーマンステスト用のテストケース
     */
    val performanceTestCases = listOf(
        BenchmarkTestCase(
            name = "短時間応答テスト",
            description = "短い応答時間での性能を測定",
            taskType = TaskType.CHAT,
            inputText = "はい",
            expectedOutputLength = 20,
            category = BenchmarkCategory.GENERAL
        ),
        BenchmarkTestCase(
            name = "中程度応答テスト",
            description = "中程度の応答時間での性能を測定",
            taskType = TaskType.CHAT,
            inputText = "今日は良い天気ですね。外に出かける予定はありますか？",
            expectedOutputLength = 100,
            category = BenchmarkCategory.GENERAL
        ),
        BenchmarkTestCase(
            name = "高負荷テスト",
            description = "高負荷時の性能を測定",
            taskType = TaskType.CHAT,
            inputText = """
                以下の要件を満たすAndroidアプリケーションの設計について、
                詳細な説明をお願いします：
                1. MVVM アーキテクチャパターンの採用
                2. Jetpack Compose を使用したUI実装
                3. Room データベースによるローカルデータ管理
                4. Retrofit を使用したREST API通信
                5. Hilt による依存性注入
                6. Coroutines による非同期処理
                7. Navigation Component による画面遷移管理
                それぞれの技術選択の理由と実装上の注意点も含めて説明してください。
            """.trimIndent(),
            expectedOutputLength = 800,
            category = BenchmarkCategory.TECHNICAL
        )
    )

    /**
     * すべてのテストケースを取得
     */
    fun getAllTestCases(): List<BenchmarkTestCase> {
        return chatTestCases + summarizationTestCases + proofreadingTestCases + performanceTestCases
    }


    /**
     * 現在選択されているプロバイダーのみでベンチマークセッションを作成
     */
    fun createCurrentProviderBenchmarkSession(provider: com.daasuu.llmsample.data.model.LLMProvider): BenchmarkSession {
        return BenchmarkSession(
            name = "${provider.displayName} ベンチマーク",
            description = "現在選択されているプロバイダー (${provider.displayName}) の性能測定",
            testCases = listOf(
                chatTestCases[0], // 簡単な挨拶
                chatTestCases[1], // 技術的な質問
                summarizationTestCases[0], // 短文の要約
                proofreadingTestCases[0] // 誤字脱字の修正
            ),
            providers = listOf(provider)
        )
    }

    /**
     * 現在選択されているプロバイダーで包括的ベンチマークセッションを作成
     */
    fun createCurrentProviderComprehensiveBenchmarkSession(provider: com.daasuu.llmsample.data.model.LLMProvider): BenchmarkSession {
        return BenchmarkSession(
            name = "${provider.displayName} 包括的ベンチマーク",
            description = "現在選択されているプロバイダー (${provider.displayName}) での全機能テスト",
            testCases = getAllTestCases(),
            providers = listOf(provider)
        )
    }
}