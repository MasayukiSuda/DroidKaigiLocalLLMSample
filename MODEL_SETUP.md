# モデルファイルのセットアップガイド

Media LLM Inference APIと、Llama.cppはモデルファイルのセットアップが必要です。

## Media LLM Inference API
**配置手順:**
- `app/src/main/assets/models/lite_rt/gemma3/` に Gemma3 の `.task` ファイルを配置
  - https://ai.google.dev/gemma/docs/core?hl=ja
  - 上記のサイトから希望のGemma3を見つけてダウンロードしてください。
- アプリ初回起動時に `files/models/lite_rt/gemma3/` へコピーされ、そこからロードされます

## Llama.cpp
### 1. アプリ内でのモデルダウンロード

1. アプリを起動、右上の設定画面でLlama.cppを選択
2. ヘッダーのダウンロードボタンをタップ
3. 利用可能なモデル一覧から、ダウンロードボタンをタップ
4. ダウンロード完了を待つ
5. 設定画面に戻り、モデル選択ページを開く
6. 希望のモデルを選択

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
│       ├── <your-gemma3-model>.task
│       └── encoder.json (任意)
```





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
