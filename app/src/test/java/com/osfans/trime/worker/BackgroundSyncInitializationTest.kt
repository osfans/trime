// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.worker

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class BackgroundSyncInitializationTest :
    StringSpec({
        "work manager availability follows the process and storage state" {
            data class Case(
                val description: String,
                val sdkInt: Int,
                val userUnlocked: Boolean,
                val deviceProtectedContext: Boolean,
                val directBootMode: Boolean,
                val processReplacementPending: Boolean,
                val expected: Boolean,
            )

            listOf(
                Case("ordinary API 21", 21, false, false, false, false, true),
                Case("API 24 user locked", 24, false, false, false, false, false),
                Case("actual device-protected context", 35, true, true, false, false, false),
                Case("old direct-boot process after unlock", 35, true, false, true, false, false),
                Case("replacement pending after unlock", 35, true, false, false, true, false),
                Case("ordinary unlocked process", 35, true, false, false, false, true),
            ).forEach { case ->
                withClue(case.description) {
                    canInitializeWorkManager(
                        sdkInt = case.sdkInt,
                        userUnlocked = case.userUnlocked,
                        deviceProtectedContext = case.deviceProtectedContext,
                        directBootMode = case.directBootMode,
                        processReplacementPending = case.processReplacementPending,
                    ) shouldBe case.expected
                }
            }
        }
    })
