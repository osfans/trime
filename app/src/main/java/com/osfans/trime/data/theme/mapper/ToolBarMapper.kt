/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.data.theme.mapper

import com.charleskorn.kaml.YamlMap
import com.charleskorn.kaml.YamlScalar
import com.charleskorn.kaml.yamlMap
import com.osfans.trime.data.theme.model.ToolBar

class ToolBarMapper(node: YamlMap) : Mapper<ToolBar>(node) {

    override fun map(): ToolBar = ToolBar(
        primaryButton = node.get<YamlMap>("primary_button")?.let(::mapButton),
        buttons = getList("buttons")?.map { mapButton(it.yamlMap) } ?: emptyList(),
        buttonSpacing = getInt("button_spacing", 18),
        buttonFont = getStringList("button_font"),
    )

    private fun mapButton(node: YamlMap): ToolBar.Button = ToolBar.Button(
        background = node.get<YamlMap>("background")?.let { bg ->
            ToolBarButtonBackgroundMapper(bg).map()
        },
        foreground = node.get<YamlMap>("foreground")?.let { fg ->
            ToolBarButtonForegroundMapper(fg).map()
        },
        action = node.get<YamlScalar>("action")?.content ?: "",
    )
}

private class ToolBarButtonBackgroundMapper(node: YamlMap) : Mapper<ToolBar.Button.Background>(node) {
    override fun map(): ToolBar.Button.Background = ToolBar.Button.Background(
        type = getString("type", "rectangle"),
        cornerRadius = getFloat("corner_radius", 10f),
        bgNormal = getString("bg_normal"),
        bgHighlight = getString("bg_highlight"),
        verticalInset = getInt("vertical_inset", 4),
        horizontalInset = getInt("horizontal_inset"),
    )
}

private class ToolBarButtonForegroundMapper(node: YamlMap) : Mapper<ToolBar.Button.Foreground>(node) {
    override fun map(): ToolBar.Button.Foreground = ToolBar.Button.Foreground(
        style = getString("style"),
        optionStyles = getStringList("option_styles"),
        fgNormal = getString("fg_normal"),
        fgHighlight = getString("fg_highlight"),
        fontSize = getFloat("font_size", 18f),
        size = getStringList("size").mapNotNull { it.toIntOrNull() },
        padding = getInt("padding", 5),
    )
}
