// SPDX-License-Identifier: GPL-3.0-or-later
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BuildAbisTest {
    @Test
    fun `default retains all upstream supported ABIs and overrides stay explicit`() {
        val defaults = Versions.supportedAbis.toList()
        assertEquals(defaults, resolveBuildAbis(null, defaults))
        assertEquals(listOf("arm64-v8a", "x86_64"), resolveBuildAbis("arm64-v8a,x86_64", defaults))
        listOf("", "arm64-v8a,arm64-v8a", "arm64-v8a, x86_64", "unknown", "x86,").forEach {
            assertFailsWith<IllegalArgumentException> { resolveBuildAbis(it, defaults) }
        }
    }
}
