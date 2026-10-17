/* SPDX-License-Identifier: GPL-3.0-or-later */

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenCCDataTest {
    @Test
    fun regionalDictionaryCannotBeSkippedWhileItsConfigIsShipped() {
        val directory = createTempDirectory("opencc-assets-test").toFile()
        try {
            directory.resolve("STCharacters.txt").writeText("汉\t漢\n")
            val required = setOf("STCharacters", "STPhrases_GeneratedFromRegionalPhrases")
            val error = assertFailsWith<IllegalStateException> { requireOpenCCDictionaryAssets(required, directory) }
            assertTrue(error.message.orEmpty().contains("STPhrases_GeneratedFromRegionalPhrases"))
            directory.resolve("STPhrases_GeneratedFromRegionalPhrases.txt").writeText("虚构短语\t虛構短語\n")
            requireOpenCCDictionaryAssets(required, directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    @Test
    fun failedGeneratorCannotLeaveAnEmptyDictionaryThatPassesPackaging() {
        val directory = createTempDirectory("opencc-empty-assets-test").toFile()
        try {
            directory.resolve("STPhrases_GeneratedFromRegionalPhrases.txt").createNewFile()
            assertFailsWith<IllegalStateException> {
                requireOpenCCDictionaryAssets(setOf("STPhrases_GeneratedFromRegionalPhrases"), directory)
            }
        } finally {
            directory.deleteRecursively()
        }
    }
}
