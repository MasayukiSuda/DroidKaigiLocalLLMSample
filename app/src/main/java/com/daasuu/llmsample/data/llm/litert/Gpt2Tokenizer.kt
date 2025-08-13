package com.daasuu.llmsample.data.llm.litert

import org.json.JSONObject
import java.io.File

/**
 * Minimal GPT-2 byte-level BPE-like tokenizer stub.
 * NOTE: This is a simplified implementation to enable pipeline integration.
 * It expects encoder.json and vocab.bpe located in the same directory as the model.
 * For unknown pieces we fallback to EOS (50256).
 */
class Gpt2Tokenizer(
    private val tokenizerDir: File
) {
    private val tokenToId: MutableMap<String, Int> = mutableMapOf()
    private val idToToken: MutableMap<Int, String> = mutableMapOf()
    private val eosTokenId: Int = 50256

    init {
        loadEncoder()
    }

    private fun loadEncoder() {
        val encoderFile = File(tokenizerDir, "encoder.json")
        if (!encoderFile.exists()) return
        val text = encoderFile.readText()
        val json = JSONObject(text)
        val keys = json.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            val v = json.getInt(k)
            tokenToId[k] = v
            idToToken[v] = k
        }
    }

    fun encode(text: String, maxTokens: Int = 1024): IntArray {
        if (tokenToId.isEmpty()) {
            // fallback: very naive whitespace split → unknown
            return IntArray(text.length.coerceAtMost(maxTokens)) { eosTokenId }
        }
        val tokens = mutableListOf<Int>()
        // Extremely naive: greedy longest-match over encoder.json keys within whitespace-split words
        val words = text.split(Regex("\\s+"))
        for ((wi, w) in words.withIndex()) {
            var i = 0
            while (i < w.length && tokens.size < maxTokens) {
                var matched: String? = null
                var matchedId: Int? = null
                var end = w.length
                while (end > i) {
                    val sub = w.substring(i, end)
                    val id = tokenToId[sub]
                    if (id != null) {
                        matched = sub
                        matchedId = id
                        break
                    }
                    end--
                }
                if (matched != null && matchedId != null) {
                    tokens.add(matchedId)
                    i += matched.length
                } else {
                    tokens.add(eosTokenId)
                    i++
                }
            }
            if (wi != words.lastIndex && tokens.size < maxTokens) {
                // try to add space token if exists
                val spaceId = tokenToId[" "]
                tokens.add(spaceId ?: eosTokenId)
            }
            if (tokens.size >= maxTokens) break
        }
        return tokens.toIntArray()
    }

    fun decodeSingle(id: Int): String {
        return idToToken[id] ?: ""
    }
}


