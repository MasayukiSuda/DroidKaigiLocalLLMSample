package com.daasuu.llmsample.ui.screens.proofread

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.daasuu.llmsample.domain.LLMManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

data class ProofreadCorrection(
    val original: String,
    val suggested: String,
    val type: String, // e.g., "誤字", "文法", "表現"
    val explanation: String = "",
    val start: Int? = null,
    val end: Int? = null
)

@HiltViewModel
class ProofreadViewModel @Inject constructor(
    private val llmManager: LLMManager
) : ViewModel() {
    
    private val _inputText = MutableStateFlow("")
    val inputText: StateFlow<String> = _inputText.asStateFlow()
    
    private val _corrections = MutableStateFlow<List<ProofreadCorrection>>(emptyList())
    val corrections: StateFlow<List<ProofreadCorrection>> = _corrections.asStateFlow()
    
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()
    
    private val _correctedText = MutableStateFlow("")
    val correctedText: StateFlow<String> = _correctedText.asStateFlow()
    
    private val _rawOutput = MutableStateFlow("")
    val rawOutput: StateFlow<String> = _rawOutput.asStateFlow()

    private var proofreadJob: Job? = null
    
    fun updateInputText(text: String) {
        _inputText.value = text
        
        // Cancel previous job and start new one for real-time proofreading
        proofreadJob?.cancel()
        if (text.length > 10) { // Only proofread if text is long enough
            proofreadJob = viewModelScope.launch {
                delay(800) // Debounce
                proofreadInternal()
            }
        } else {
            _corrections.value = emptyList()
            _correctedText.value = ""
            _rawOutput.value = ""
        }
    }
    
    fun proofread() {
        proofreadJob?.cancel()
        proofreadJob = viewModelScope.launch {
            proofreadInternal()
        }
    }
    
    private suspend fun proofreadInternal() {
        if (_inputText.value.isBlank()) return
        
        _isLoading.value = true
        _rawOutput.value = ""
        
        try {
            val proofreadFlow = llmManager.proofreadText(_inputText.value)
            if (proofreadFlow != null) {
                val resultBuilder = StringBuilder()
                proofreadFlow.collect { token ->
                    resultBuilder.append(token)
                }
                
                val response = resultBuilder.toString().trim()
                _rawOutput.value = response
                val parsed = parseProofreadResponse(response, _inputText.value)
                if (parsed != null) {
                    _correctedText.value = parsed.first
                    _corrections.value = parsed.second
                } else {
                    // 解析できない場合は自由文を採用しない（元文のまま）
                    _correctedText.value = ""
                    _corrections.value = emptyList()
                }
            } else {
                _corrections.value = emptyList()
                _correctedText.value = ""
                _rawOutput.value = ""
            }
        } catch (e: Exception) {
            _corrections.value = emptyList()
            _correctedText.value = ""
            _rawOutput.value = ""
        } finally {
            _isLoading.value = false
        }
    }

    private fun parseProofreadResponse(response: String, original: String): Pair<String, List<ProofreadCorrection>>? {
        // Try to locate a JSON object in the response
        val startIdx = response.indexOf('{')
        val endIdx = response.lastIndexOf('}')
        if (startIdx == -1 || endIdx == -1 || endIdx <= startIdx) return null
        val jsonString = response.substring(startIdx, endIdx + 1)
        return try {
            val root = JSONObject(jsonString)
            val arr: JSONArray = root.optJSONArray("corrections") ?: JSONArray()
            val list = mutableListOf<ProofreadCorrection>()
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val originalWord = item.optString("original", "")
                val suggested = item.optString("suggested", "")
                val type = item.optString("type", "その他")
                val explanation = item.optString("explanation", "")
                val s = if (item.has("start")) item.optInt("start") else null
                val e = if (item.has("end")) item.optInt("end") else null
                // Positions are optional; we don't store them but UI can highlight by searching
                if (originalWord.isNotBlank() && suggested.isNotBlank()) {
                    list.add(
                        ProofreadCorrection(
                            original = originalWord,
                            suggested = suggested,
                            type = type,
                            explanation = explanation,
                            start = s,
                            end = e
                        )
                    )
                }
            }
            val correctedText = applyCorrectionsToText(original, list)
            if (correctedText != null && correctedText != original) correctedText to list else null
        } catch (e: Exception) {
            null
        }
    }

    private fun applyCorrectionsToText(original: String, corrections: List<ProofreadCorrection>): String? {
        if (corrections.isEmpty()) return null
        // Prefer position-based application if any correction has indices
        val havePositions = corrections.any { it.start != null && it.end != null }
        return if (havePositions) {
            val sorted = corrections.filter { it.start != null && it.end != null }
                .sortedBy { it.start!! }
            val builder = StringBuilder()
            var cursor = 0
            for (c in sorted) {
                val s = c.start!!.coerceIn(0, original.length)
                val e = c.end!!.coerceIn(s, original.length)
                if (s < cursor) continue // overlapping or invalid, skip
                builder.append(original.substring(cursor, s))
                builder.append(c.suggested)
                cursor = e
            }
            builder.append(original.substring(cursor))
            builder.toString()
        } else {
            // Fallback: sequential first occurrence replacements (best-effort, minimal)
            var text = original
            for (c in corrections) {
                val idx = text.indexOf(c.original)
                if (idx >= 0) {
                    text = text.replaceFirst(c.original, c.suggested)
                }
            }
            if (text != original) text else null
        }
    }
}