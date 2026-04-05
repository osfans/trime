/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.yaml

sealed class Yaml {
    companion object Default : Yaml()

    fun parseToYamlNode(string: String): Node = Parser(string).read()
}
