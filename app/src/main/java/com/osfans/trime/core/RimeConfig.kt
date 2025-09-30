/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.core

import timber.log.Timber

class RimeConfig private constructor(
    private var peer: Long,
) : AutoCloseable {

    fun getInt(key: String) = getRimeConfigInt(peer, key)

    fun getString(key: String) = getRimeConfigString(peer, key)

    fun <E : Any> getList(key: String, getAction: RimeConfig.(String) -> E?): List<E> {
        val paths = getRimeConfigListItemPath(peer, key)
        val values = ArrayList<E>(paths.size)
        for (path in paths) {
            val value = getAction(this, path)
            if (value == null) {
                Timber.w("Failed to get value '${getString(path)}' as expected on path '$path'")
                continue
            }
            values.add(value)
        }
        return values
    }

    fun setBool(key: String, value: Boolean) = setRimeConfigBool(peer, key, value)

    override fun close() {
        closeRimeConfig(peer)
    }

    companion object {
        fun openConfig(configId: String): RimeConfig = RimeConfig(openRimeConfig(configId))

        fun openUserConfig(configId: String): RimeConfig = RimeConfig(openRimeUserConfig(configId))

        fun openSchema(schemaId: String): RimeConfig = RimeConfig(openRimeSchema(schemaId))

        @JvmStatic
        private external fun openRimeConfig(configId: String): Long

        @JvmStatic
        private external fun openRimeUserConfig(configId: String): Long

        @JvmStatic
        private external fun openRimeSchema(schemaId: String): Long

        @JvmStatic
        private external fun getRimeConfigInt(peer: Long, key: String): Int?

        @JvmStatic
        private external fun getRimeConfigString(peer: Long, key: String): String?

        @JvmStatic
        private external fun getRimeConfigListItemPath(peer: Long, key: String): Array<String>

        @JvmStatic
        private external fun setRimeConfigBool(peer: Long, key: String, value: Boolean)

        @JvmStatic
        private external fun closeRimeConfig(peer: Long)
    }
}
