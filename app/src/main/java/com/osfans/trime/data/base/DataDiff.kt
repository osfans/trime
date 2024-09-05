// SPDX-FileCopyrightText: 2015 - 2024 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.base

sealed interface DataDiff {
    val path: String

    val ordinal: Int

    data class CreateFile(
        override val path: String,
    ) : DataDiff {
        override val ordinal: Int
            get() = 3
    }

    data class UpdateFile(
        override val path: String,
    ) : DataDiff {
        override val ordinal: Int
            get() = 2
    }

    data class DeleteDir(
        override val path: String,
    ) : DataDiff {
        override val ordinal: Int
            get() = 1
    }

    data class DeleteFile(
        override val path: String,
    ) : DataDiff {
        override val ordinal: Int
            get() = 0
    }

    companion object {
        fun diff(
            old: DataChecksums,
            new: DataChecksums,
        ): List<DataDiff> {
            if (old.sha256 == new.sha256) return emptyList()
            return new.files
                .mapNotNull { (path, sha256) ->
                    when {
                        path !in old.files && sha256.isNotBlank() -> CreateFile(path)
                        old.files[path] != sha256 ->
                            if (sha256.isNotBlank()) UpdateFile(path) else null
                        else -> null
                    }
                }.toMutableList()
                .apply {
                    addAll(
                        old.files
                            .filterKeys { it !in new.files }
                            .map { (path, sha256) ->
                                if (sha256.isNotBlank()) DeleteFile(path) else DeleteDir(path)
                            },
                    )
                }
        }
    }
}
