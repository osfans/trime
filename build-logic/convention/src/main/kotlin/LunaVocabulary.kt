/* SPDX-License-Identifier: GPL-3.0-or-later */

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import org.yaml.snakeyaml.constructor.SafeConstructor
import java.io.File

/** Matches ScriptEncoder's acceptance, without generating pronunciation combinations. */
class LunaVocabulary private constructor(
    private val dictionaryWords: Set<String>,
    words: Set<String>,
) {
    private class Node {
        val children = mutableMapOf<Char, Node>()
        var terminal = false
    }

    private val root = Node().also { root ->
        words.forEach { word ->
            var node = root
            word.forEach { node = node.children.getOrPut(it) { Node() } }
            node.terminal = true
        }
    }

    fun accepts(word: String): Boolean {
        // Explicit dictionary entries still need their original frequency, even
        // when ScriptEncoder could not infer their pronunciation from characters.
        if (word in dictionaryWords) return true
        if (word.isEmpty() || word.codePointCount(0, word.length) > 32) return false
        val reachable = BooleanArray(word.length + 1)
        reachable[0] = true
        for (start in word.indices) {
            if (!reachable[start]) continue
            var node = root
            for (end in start until word.length) {
                node = node.children[word[end]] ?: break
                if (node.terminal) reachable[end + 1] = true
            }
        }
        return reachable.last()
    }

    companion object {
        private data class Entry(val text: String, val code: String, val weight: String)

        fun adapt(dictionary: File, essay: File, outputDictionary: File, outputEssay: File, rejected: File) {
            val header = StringBuilder()
            val body = StringBuilder()
            val entries = mutableListOf<Entry>()
            var inBody = false
            dictionary.useLines { lines ->
                lines.forEach { line ->
                    if (!inBody) {
                        if (line.trim() == "...") inBody = true else header.appendLine(line)
                    } else {
                        body.appendLine(line)
                        if (line.isNotBlank() && !line.startsWith('#')) {
                            val fields = line.trimEnd().split('\t')
                            require(fields.size in 2..3 && fields[0].isNotEmpty() && fields[1].isNotBlank()) {
                                "Luna dictionary format changed; review the vocabulary adapter"
                            }
                            entries += Entry(fields[0], fields[1], fields.getOrElse(2) { "" })
                        }
                    }
                }
            }
            val yaml = Yaml(SafeConstructor(LoaderOptions()))
            val settings = yaml.load<Map<String, Any>>(header.toString()).toMutableMap()
            require(inBody && entries.isNotEmpty() && settings["name"] == "luna_pinyin")
            require(settings["use_preset_vocabulary"] == true && settings["vocabulary"] == null)
            require(listOf("encoder", "columns", "import_tables", "max_phrase_length", "min_phrase_weight").none(settings::containsKey)) {
                "Luna encoder settings changed; review the vocabulary adapter"
            }
            val words = entries.map { it.text }.toSet()
            val frequencies = mutableMapOf<String, Double>()
            essay.useLines { lines ->
                lines.filter { it.isNotBlank() && !it.startsWith('#') }.forEach { line ->
                    val fields = line.split('\t')
                    if (fields[0] in words) frequencies[fields[0]] = fields.getOrElse(1) { "0" }.toDouble()
                }
            }
            val weights = entries.filter { it.code.split(' ').filter(String::isNotEmpty).size == 1 }.groupBy { it.text }
            val encodable = weights.filter { (word, readings) ->
                require(readings.map { it.code }.toSet().size == readings.size) { "Duplicate Luna reading: $word" }
                val values = readings.map { entry ->
                    when {
                        entry.weight.isEmpty() -> frequencies[word] ?: 0.0
                        entry.weight.endsWith('%') -> (frequencies[word] ?: 0.0) * entry.weight.dropLast(1).toDouble() / 100.0
                        else -> entry.weight.toDouble()
                    }.also { require(it.isFinite() && it >= 0) }
                }
                values.any { it >= values.sum() * 0.05 }
            }.keys
            val filter = LunaVocabulary(words, encodable)
            outputEssay.bufferedWriter().use { out ->
                rejected.bufferedWriter().use { rejectedOut ->
                    essay.useLines { lines ->
                        lines.forEach { line ->
                            val word = line.substringBefore('\t')
                            val accepted = line.isBlank() || line.startsWith('#') || filter.accepts(word)
                            val destination = if (accepted) out else rejectedOut
                            destination.appendLine(line)
                        }
                    }
                }
            }
            settings["vocabulary"] = "luna_pinyin_essay"
            settings["version"] = "${settings["version"]}-trime-v1"
            outputDictionary.bufferedWriter().use { out ->
                // Keep upstream attribution before the document header verbatim.
                header.lineSequence().takeWhile { it.trim() != "---" }.forEach { out.appendLine(it) }
                out.appendLine("---")
                out.append(yaml.dump(settings))
                out.appendLine("...")
                out.append(body)
            }
        }

        internal fun forTest(dictionaryWords: Set<String>, encodableWords: Set<String>) = LunaVocabulary(dictionaryWords, encodableWords)
    }
}
