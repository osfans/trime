/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.theme.icons

import com.mikepenz.iconics.typeface.IIcon
import com.mikepenz.iconics.typeface.ITypeface
import com.mikepenz.iconics.typeface.IconicsHolder
import com.osfans.trime.R
import java.util.LinkedList

/** Same cmd names and original font; glyph data is kept out of enum class initializers. */
internal object CompactCommunityMaterial : ITypeface {
    private const val ICON_COUNT = 7134

    private val glyphs by lazy {
        val bytes = IconicsHolder.applicationContext.resources
            .openRawResource(R.raw.community_material_glyphs).use { it.readBytes() }
        CommunityMaterialGlyphTable(bytes, ICON_COUNT)
    }
    private val requestedIcons = HashMap<String, IIcon>()

    override val fontRes: Int
        get() = com.mikepenz.iconics.typeface.library.community.R.font.community_material_design_icons_font_v7_0_96
    override val mappingPrefix: String get() = "cmd"
    override val fontName: String get() = "Community Material Design"

    // Preserve the upstream provider value, which differs from the Maven artifact version.
    override val version: String get() = "7.0.96.0"
    override val author: String get() = "Templarian / Community / Google"
    override val url: String get() = "http://materialdesignicons.com/"
    override val description: String get() = "7000+ Material Design Icons from the Community"
    override val license: String get() = "Pictogrammers Free License"
    override val licenseUrl: String get() = "https://github.com/Templarian/MaterialDesign/blob/master/LICENSE"
    override val iconCount: Int get() = ICON_COUNT

    // Ordinary getIcon calls do not materialize all names, map entries or IIcon objects.
    override val characters: Map<String, Char> by lazy {
        LinkedHashMap<String, Char>(ICON_COUNT).apply {
            for (index in 0 until glyphs.size) put(glyphs.nameAt(index), glyphs.characterAt(index))
        }
    }
    override val icons: List<String>
        get() = characters.keys.toCollection(LinkedList())

    override fun getIcon(key: String): IIcon = synchronized(requestedIcons) {
        requestedIcons[key] ?: run {
            val index = glyphs.indexOf(key)
            require(index >= 0) { "Unknown Community Material icon: $key" }
            GlyphIcon(key, glyphs.characterAt(index)).also { requestedIcons[key] = it }
        }
    }

    private class GlyphIcon(override val name: String, override val character: Char) : IIcon {
        override val typeface: ITypeface get() = CompactCommunityMaterial
    }
}

/** ASCII name, TAB, exactly one four-digit UTF-16 code unit, LF, in original sorted order. */
internal class CommunityMaterialGlyphTable(private val bytes: ByteArray, expectedCount: Int) {
    private val starts = IntArray(expectedCount)
    val size: Int get() = starts.size

    init {
        var offset = 0
        var count = 0
        while (offset < bytes.size) {
            require(count < starts.size) { "Too many glyph records" }
            val start = offset
            while (offset < bytes.size && isNameByte(bytes[offset])) offset++
            require(offset - start > 4 && offset < bytes.size && bytes[offset] == TAB) {
                "Invalid glyph name at record $count"
            }
            require(
                bytes[start] == 'c'.code.toByte() && bytes[start + 1] == 'm'.code.toByte() &&
                    bytes[start + 2] == 'd'.code.toByte() && bytes[start + 3] == '_'.code.toByte(),
            ) {
                "Unexpected glyph prefix at record $count"
            }
            require(offset + 5 < bytes.size && bytes[offset + 5] == LF) {
                "Glyph must contain exactly one UTF-16 code unit at record $count"
            }
            val value = codeUnit(offset + 1)
            require(value !in 0xD800..0xDFFF) { "Surrogate glyph is not representable by IIcon.character" }
            starts[count] = start
            if (count > 0) {
                require(compareNames(starts[count - 1], start) < 0) {
                    "Glyph names must be unique and sorted"
                }
            }
            count++
            offset += 6
        }
        require(count == starts.size) { "Incomplete glyph table: $count/${starts.size}" }
    }

    fun indexOf(key: String): Int {
        var low = 0
        var high = starts.lastIndex
        while (low <= high) {
            val middle = (low + high).ushr(1)
            val comparison = compareNameToKey(starts[middle], key)
            if (comparison == 0) return middle
            if (comparison < 0) low = middle + 1 else high = middle - 1
        }
        return -1
    }

    fun nameAt(index: Int): String {
        val start = starts[index]
        return String(bytes, start, separator(start) - start, Charsets.US_ASCII)
    }

    fun characterAt(index: Int): Char = codeUnit(separator(starts[index]) + 1).toChar()

    private fun separator(start: Int): Int {
        var offset = start
        while (bytes[offset] != TAB) offset++
        return offset
    }

    private fun codeUnit(start: Int): Int {
        var value = 0
        for (offset in start until start + 4) {
            val digit = when (val char = bytes[offset].toInt()) {
                in '0'.code..'9'.code -> char - '0'.code
                in 'A'.code..'F'.code -> char - 'A'.code + 10
                else -> error("Invalid glyph code unit")
            }
            value = (value shl 4) or digit
        }
        return value
    }

    private fun compareNames(left: Int, right: Int): Int {
        var a = left
        var b = right
        while (bytes[a] != TAB && bytes[b] != TAB) {
            val difference = bytes[a].toInt() - bytes[b].toInt()
            if (difference != 0) return difference
            a++
            b++
        }
        return when {
            bytes[a] == TAB && bytes[b] == TAB -> 0
            bytes[a] == TAB -> -1
            else -> 1
        }
    }

    private fun compareNameToKey(start: Int, key: String): Int {
        var offset = start
        var index = 0
        while (bytes[offset] != TAB && index < key.length) {
            val difference = bytes[offset].toInt() - key[index].code
            if (difference != 0) return difference
            offset++
            index++
        }
        return if (bytes[offset] == TAB) index - key.length else 1
    }

    private companion object {
        const val TAB: Byte = 9
        const val LF: Byte = 10
        fun isNameByte(value: Byte): Boolean = value.toInt() in 'a'.code..'z'.code || value.toInt() in '0'.code..'9'.code || value == '_'.code.toByte()
    }
}
