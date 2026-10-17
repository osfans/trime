/* SPDX-License-Identifier: GPL-3.0-or-later */

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LunaVocabularyTest {
    @Test
    fun segmentsWordsWithoutGuessingCodes() {
        val filter = LunaVocabulary.forTest(setOf("unencodable.name"), setOf("a", "ab", "bc"))
        assertTrue(filter.accepts("abc")) // Must backtrack from ab to a + bc.
        assertTrue(filter.accepts("unencodable.name"))
        assertFalse(filter.accepts("ab.name"))
        assertFalse(filter.accepts(""))
    }

    @Test
    fun lengthLimitUsesUnicodeCodePoints() {
        val supplementary = String(Character.toChars(0x20000))
        val filter = LunaVocabulary.forTest(emptySet(), setOf(supplementary))
        assertTrue(filter.accepts(supplementary.repeat(32)))
        assertFalse(filter.accepts(supplementary.repeat(33)))
    }

    @Test
    fun adaptationPreservesSourcesRowsAndFrequencies() {
        val dir = createTempDirectory("luna-vocabulary-test").toFile()
        try {
            val dictionary = dir.resolve("source.dict.yaml").apply {
                writeText("# Attribution\n---\nname: luna_pinyin\nversion: '1'\nuse_preset_vocabulary: true\n...\na\ta\t50%\nb\tb\nexplicit.name\ta b\t123\n")
            }
            val essay = dir.resolve("essay.txt").apply { writeText("# Vocabulary\na\t20\nb\t10\nab\t30\nexplicit.name\t77\na.name\t40\n") }
            val original = dictionary.readText() to essay.readText()
            val adapted = dir.resolve("luna.dict.yaml")
            val accepted = dir.resolve("luna_essay.txt")
            val rejected = dir.resolve("rejected.tsv")
            LunaVocabulary.adapt(dictionary, essay, adapted, accepted, rejected)
            assertEquals(original, dictionary.readText() to essay.readText())
            assertEquals("a.name\t40\n", rejected.readText())
            assertEquals(original.second.replace("a.name\t40\n", ""), accepted.readText())
            assertEquals(original.first.substringAfter("...\n"), adapted.readText().substringAfter("...\n"))
            assertTrue(adapted.readText().startsWith("# Attribution\n"))
            assertTrue(adapted.readText().contains("vocabulary: luna_pinyin_essay"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun readingsBelowEngineThresholdCannotEncodeInferredWords() {
        val dir = createTempDirectory("luna-threshold-test").toFile()
        try {
            val dictionary = dir.resolve("source.dict.yaml").apply {
                writeText(
                    "---\nname: luna_pinyin\nversion: '1'\nuse_preset_vocabulary: true\n...\n" +
                        (1..21).joinToString("") { "a\treading$it\t1\n" },
                )
            }
            val essay = dir.resolve("essay.txt").apply { writeText("a\t10\naa\t20\n") }
            val rejected = dir.resolve("rejected.tsv")
            LunaVocabulary.adapt(dictionary, essay, dir.resolve("out.yaml"), dir.resolve("out.txt"), rejected)
            assertEquals("aa\t20\n", rejected.readText())
        } finally {
            dir.deleteRecursively()
        }
    }
}
