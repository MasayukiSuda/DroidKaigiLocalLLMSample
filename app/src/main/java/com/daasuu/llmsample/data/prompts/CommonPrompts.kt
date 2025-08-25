package com.daasuu.llmsample.data.prompts

/**
 * 統一プロンプト定義クラス
 * Session Proposalで約束した「同一プロンプト・同一端末でのベンチマーク」を実現するため、
 * 全プロバイダーで共通使用可能なプロンプトを定義
 */
object CommonPrompts {
    
    /**
     * チャット機能用の統一プロンプト
     * 最もシンプルで公平な形式 - ユーザーメッセージをそのまま使用
     */
    fun buildChatPrompt(userMessage: String): String {
        return userMessage.trim()
    }
    
    /**
     * 要約機能用の統一プロンプト
     * 中間的な詳細レベルで、全プロバイダーが理解可能な形式
     */
    fun buildSummarizationPrompt(text: String): String {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return "テキストが空です。"
        
        return "以下のテキストを簡潔に要約してください:\n\n$cleanText"
    }
    
    /**
     * 校正機能用の統一プロンプト
     * 自然言語形式で最も互換性が高い指示
     */
    fun buildProofreadingPrompt(text: String): String {
        val cleanText = text.trim()
        if (cleanText.isEmpty()) return "テキストが空です。"
        
        return "以下の文章を校正してください:\n\n$cleanText"
    }
    
    /**
     * 日本語文字を含むかどうかの判定
     * 共通で使用するユーティリティ関数
     */
    fun containsJapanese(text: String): Boolean {
        for (ch in text) {
            val block = java.lang.Character.UnicodeBlock.of(ch)
            if (block == java.lang.Character.UnicodeBlock.HIRAGANA ||
                block == java.lang.Character.UnicodeBlock.KATAKANA ||
                block == java.lang.Character.UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS ||
                block == java.lang.Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
                block == java.lang.Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
                block == java.lang.Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
            ) {
                return true
            }
        }
        return false
    }
}
