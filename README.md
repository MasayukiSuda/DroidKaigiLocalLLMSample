# DroidKaigi Local LLM Sample

## Modelのsetup方法について

[こちら](https://github.com/MasayukiSuda/DroidKaigiLocalLLMSample/blob/main/MODEL_SETUP.md)を最初にご確認ください。

## Gemini Nano利用について

このアプリでは、Google のオンデバイス AI「Gemini Nano」もテストできます。

### 対応端末

Gemini Nanoは以下の端末でのみ利用可能です：

- **Google Pixel9 シリーズ**
  - Pixel 9、Pixel 9 Pro、Pixel 9 Pro XL、Pixel 9 Pro Fold

### 端末での設定

Gemini Nanoを有効にするには以下の設定が必要です：

1. [aicore-experimental Google グループ](https://groups.google.com/g/aicore-experimental?hl=ja)に参加します。

2. [Android AICore テスト プログラム](https://play.google.com/apps/testing/com.google.android.aicore?hl=ja)にオプトインする

## Session Proposal
```
生成AIの実装選択肢は多様化しています。本アプリでは三つの異なるアプローチを一つのCompose アプリに統合し、「AI チャット」「リアルタイム文章要約」「リアルタイム文章校正」機能を通じて、同じプロンプト・同じ端末でベンチマークできます。

実装されているアプローチ：
1. **Gemini Nano (On-Device)**: オンデバイスでの高性能AI
2. **Llama.cpp**: オンデバイスでの量子化LLM実行
3. **LiteRT (.task)**: TensorFlow Liteベースの軽量ランタイム

比較軸は下記の5点です：
①導入工数とビルド手順
②モデルサイズ／RAM 使用量  
③推論レイテンシ
④バッテリー消費
⑤ライセンスと運用

Gemini Nano のオンデバイス高性能とプライバシー保護、llama.cpp のオフライン動作と自由度、LiteRT の軽量性と最適化の可能性を実際のベンチマーク結果で可視化します。

すべてオンデバイスで動作するため、プライバシーが保護され、インターネット接続不要で瞬時に応答が得られます。それぞれのアプローチの特徴を理解することで、用途に応じた最適な選択ができるようになります。

本セッションを通じて、多様なLLM実装アプローチを活用したAndroid アプリ開発の実践的な知識を習得できます。実際のコードと性能データを基に、新たなアプリ開発や既存アプリの進化のきっかけとなることを目指します。
```

## ライセンス

このプロジェクトは MIT License の下で公開されています。詳細は [LICENSE](LICENSE) ファイルをご覧ください。

### 第三者ライブラリ

このプロジェクトには以下の第三者ライブラリが含まれています：

- **llama.cpp** - MIT License
  - Copyright (c) 2023-2025 The ggml authors
  - URL: https://github.com/ggerganov/llama.cpp

### 使用上の注意

- 本サンプルは教育・学習目的で作成されています
- モデルファイルについては、それぞれの提供元のライセンスに従ってください
