# モデルファイルのセットアップガイド

このアプリは3つのオンデバイスLLMプロバイダーをサポートしており、実際のモデルファイルをダウンロードして使用できます。

## 対応モデル

### Llama.cpp (GGUF形式)
1. **Llama 3.2 3B Instruct Q4_K_M**（推奨）
   - サイズ: 約2.3GB
   - ダウンロード: アプリ内の設定画面から

2. **TinyLlama 1.1B Q4**（軽量テスト用）
   - サイズ: 約640MB
   - ダウンロード: アプリ内の設定画面から

3. **Phi-2 Q4**
   - サイズ: 約1.5GB
   - ダウンロード: アプリ内の設定画面から

### LiteRT (TensorFlow Lite)
1. **Gemma3 (assets 手動配置)**
   - サイズ: モデルに依存（数百MB〜）
   - ダウンロード: なし（手動配置）
   - 配置手順:
     - `app/src/main/assets/models/lite_rt/gemma3/` に Gemma3 の `.tflite` を配置
     - 可能なら `encoder.json` を同ディレクトリに配置（暫定トークナイザ用）
     - アプリ初回起動時に `files/models/lite_rt/gemma3/` へコピーされ、そこからロードされます

### ML Kit GenAI API (Gemini Nano)
- Android 16+ デバイスで自動的に利用可能
- 追加のダウンロード不要

## セットアップ手順

### 1. アプリ内でのモデルダウンロード

1. アプリを起動
2. 右上の設定ボタンをタップ
3. 使用したいLLMプロバイダーを選択
4. 利用可能なモデル一覧から、ダウンロードボタンをタップ
5. ダウンロード完了を待つ

### 2. 手動でのモデル配置（上級者向け）

モデルファイルを手動で配置する場合：

```
/data/data/com.daasuu.llmsample/files/models/
├── llama_cpp/
│   ├── llama-3.2-3b-instruct-q4_k_m.gguf
│   ├── tinyllama-1.1b-q4.gguf
│   └── phi-2-q4.gguf
├── lite_rt/
│   └── gemma3/
│       ├── <your-gemma3-model>.tflite
│       └── encoder.json (任意)
```

### 3. 実際のllama.cppビルド（開発者向け）

実際のllama.cppライブラリをビルドする場合：

```bash
# llama.cppのセットアップ
./setup_llama_cpp.sh

# NDKビルドの有効化（app/build.gradle.kts）
externalNativeBuild {
    cmake {
        path = file("src/main/cpp/CMakeLists.txt")
        version = "3.22.1"
    }
}

# ビルド実行
./gradlew assembleDebug
```

## パフォーマンス比較

各プロバイダーの特徴：

| プロバイダー | レイテンシ | メモリ使用量 | バッテリー | 利用可否 |
|------------|-----------|------------|-----------|----------|
| Gemini Nano | 高速 | 低 | 省電力 | Android 16+ |
| LiteRT | 中速 | 中 | 中 | 全Android |
| llama.cpp | 低速 | 高 | 高消費 | 全Android |

## トラブルシューティング

### ダウンロードエラー
- インターネット接続を確認
- ストレージ容量を確認
- アプリを再起動

### モデル読み込みエラー
- ファイルの整合性を確認
- アプリのキャッシュをクリア
- モデルを再ダウンロード

### パフォーマンス問題
- デバイスのRAM容量を確認
- バックグラウンドアプリを終了
- より軽量なモデルを選択

## 推奨セットアップ

初回利用時の推奨手順：

1. **TinyLlama 1.1B Q4**をダウンロード（軽量で動作確認に最適）
2. チャット機能で基本動作を確認
3. より大きなモデルを試す
4. ベンチマーク機能で性能比較

これにより、DroidKaigi Local LLM Sampleの全機能を体験できます。