/* SPDX-License-Identifier: GPL-3.0-or-later */

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class OpenCCDataTest {
    @Test
    fun windowsHostGeneratorUsesTheNativeExecutableArchitecture() {
        val aliases = mapOf(
            "x64" to listOf("amd64", "x86_64"),
            "arm64" to listOf("aarch64", "arm64"),
            "x86" to listOf("x86", "i386", "i486", "i586", "i686"),
        )
        aliases.forEach { (platform, architectures) ->
            architectures.forEach { architecture ->
                assertEquals(
                    listOf("-G", "Ninja", "-DCMAKE_BUILD_TYPE=Release", "-DCMAKE_CXX_COMPILER=cl"),
                    openccHostCmakeArguments("Windows Server 2025", architecture, platform),
                )
            }
        }
        assertFailsWith<IllegalStateException> { openccHostCmakeArguments("Windows 11", "riscv64") }
        assertFailsWith<IllegalStateException> { openccHostCmakeArguments("Windows 11", "amd64") }
        assertFailsWith<IllegalStateException> { openccHostCmakeArguments("Windows 11", "amd64", "arm64") }
    }

    @Test
    fun unixHostKeepsTheSingleConfigurationReleaseBuild() {
        assertEquals(listOf("-DCMAKE_BUILD_TYPE=Release"), openccHostCmakeArguments("Linux", "amd64"))
        assertEquals(listOf("-DCMAKE_BUILD_TYPE=Release"), openccHostCmakeArguments("Mac OS X", "aarch64"))
    }

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
