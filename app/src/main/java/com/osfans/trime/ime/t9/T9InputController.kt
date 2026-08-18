package com.osfans.trime.ime.t9

import android.view.KeyEvent
import com.osfans.trime.core.RimeMessage
import com.osfans.trime.daemon.RimeSession
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class T9InputController(
    private val rime: RimeSession,
) {
    data class PinYinToken(
        val pos: Int,
        val raw: String,
        val pinYin: String,
        val display: String = pinYin,
    )

    enum class Behavior {
        NONE,
        NORMAL,
        SEGMENT,
        SELECT_PINYIN,
        SELECT_CANDIDATE,
    }

    private val inputQueue = ArrayDeque<String>()
    private val selectedQueue = ArrayDeque<PinYinToken>()
    private val behaviorQueue = ArrayDeque<Behavior>()

    var onCandidatesChanged: ((List<PinYinToken>) -> Unit)? = null

    private var cachedInputString = ""
    private var lastRimeInput = ""
    private var messageJob: Job? = null

    companion object {
        const val SEGMENT_KEY_CHAR = '\''
        const val SEGMENT_KEY_CHAR_ALIAS = '1'
        const val SEGMENT_KEY_CHAR_ALIAS_2 = '0'
    }

    init {
        messageJob = rime.lifecycleScope.launch {
            rime.run { messageFlow }.collect { message ->
                if (message is RimeMessage.CommitTextMessage) {
                    val text = message.data.text
                    if (!text.isNullOrEmpty()) {
                        clear()
                    }
                }
            }
        }
    }

    fun destroy() {
        messageJob?.cancel()
        messageJob = null
    }

    fun onDigitKey(digit: String) {
        inputQueue.add(digit)
        cachedInputString += digit
        behaviorQueue.add(Behavior.NORMAL)
        fireCandidatesChanged()
    }

    fun onBackspace(): Boolean {
        if (behaviorQueue.isEmpty()) {
            return false
        }
        var modified = false
        when (behaviorQueue.removeLast()) {
            Behavior.SELECT_PINYIN -> {
                if (selectedQueue.isNotEmpty()) {
                    val lastSelected = selectedQueue.last()
                    if (!lastRimeInput.contains(lastSelected.pinYin)) {
                        return false
                    }
                    selectedQueue.removeLast()
                    modified = true
                }
            }

            Behavior.SELECT_CANDIDATE -> {
            }

            else -> {
                if (inputQueue.isNotEmpty()) {
                    inputQueue.removeLast()
                    cachedInputString = cachedInputString.dropLast(1)
                    modified = true
                }
            }
        }
        if (modified) {
            fireCandidatesChanged()
        }
        return modified
    }

    fun onSegmentKey(): Boolean {
        if (inputQueue.isEmpty()) {
            return true
        }
        if (inputQueue.last() == SEGMENT_KEY_CHAR.toString()) {
            return true
        }
        var selectedSize = 0
        selectedQueue.forEach { selectedSize += it.raw.length }
        if (selectedSize == inputQueue.size) {
            return true
        }
        inputQueue.add(SEGMENT_KEY_CHAR.toString())
        cachedInputString += SEGMENT_KEY_CHAR.toString()
        behaviorQueue.add(Behavior.SEGMENT)
        fireCandidatesChanged()
        return false
    }

    fun onSelectPinyin(
        pos: Int,
        raw: String,
        pinYin: String,
    ) {
        selectedQueue.add(PinYinToken(pos, raw, pinYin))
        behaviorQueue.add(Behavior.SELECT_PINYIN)
        updateRimeInput()
        fireCandidatesChanged()
    }

    fun computeCandidates(): List<PinYinToken> {
        if (inputQueue.isEmpty()) return emptyList()
        val position = nextSequencePosition()
        if (position < 0) return emptyList()
        val sequence = cachedInputString.substring(position)
        return T9PinYin.possibleCombinations(sequence).map { pinYin ->
            var raw = sequence.substring(0, minOf(pinYin.length, sequence.length))
            val nextChar = sequence.getOrNull(pinYin.length)
            if (nextChar == SEGMENT_KEY_CHAR || nextChar == SEGMENT_KEY_CHAR_ALIAS_2) {
                raw += nextChar.toString()
            }
            PinYinToken(position, raw, pinYin)
        }
    }

    fun buildRimeInput(): String {
        val input = cachedInputString
        if (selectedQueue.isEmpty()) return input
        val first = selectedQueue.first()
        val last = selectedQueue.last()
        val start = first.pos
        val end = last.pos + last.raw.length
        if (start < 0 || end > input.length) return input
        val result = StringBuilder().append(input.substring(0, start))
        var cursor = start
        for (token in selectedQueue) {
            if (token.pos > cursor) {
                result.append(input.substring(cursor, token.pos))
            }
            val rawEnd = token.pos + token.raw.length
            if (rawEnd <= input.length && input.regionMatches(
                    token.pos,
                    token.raw,
                    0,
                    token.raw.length,
                )
            ) {
                result.append(token.pinYin)
                result.append(SEGMENT_KEY_CHAR)
            } else {
                result.append(input.substring(token.pos, rawEnd))
            }
            cursor = rawEnd
        }
        return result.append(input.substring(end)).toString()
    }

    fun updateRimeInput() {
        val input = buildRimeInput()
        lastRimeInput = input
        rime.lifecycleScope.launch {
            rime.runOnReady {
                setRawInput(input)
            }
        }
    }

    fun clear() {
        inputQueue.clear()
        selectedQueue.clear()
        behaviorQueue.clear()
        cachedInputString = ""
        fireCandidatesChanged()
    }

    private fun fireCandidatesChanged() {
        onCandidatesChanged?.invoke(computeCandidates())
    }

    private fun nextSequencePosition(): Int {
        if (selectedQueue.isEmpty()) return 0
        var pos = 0
        for (token in selectedQueue) {
            if (token.pos > pos) return pos
            val end = token.pos + token.raw.length
            if (end > pos) pos = end
        }
        if (pos >= inputQueue.size) return pos
        return pos
    }
}