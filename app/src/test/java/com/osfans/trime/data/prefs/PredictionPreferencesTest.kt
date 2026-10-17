/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.prefs

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class PredictionPreferencesTest :
    StringSpec({
        "next-word suggestions are opt-in and persist independently of candidate comments" {
            val store = InMemorySharedPreferences()
            val prefs = AppPrefs(store.preferences)
            prefs.candidates.nextWordPrediction.getValue() shouldBe false
            prefs.candidates.nextWordPrediction.setValue(true)
            prefs.candidates.showComments.setValue(false)
            val restarted = AppPrefs(store.preferences)
            restarted.candidates.nextWordPrediction.getValue() shouldBe true
            restarted.candidates.showComments.getValue() shouldBe false
            restarted.candidates.nextWordPrediction.setValue(false)
            AppPrefs(store.preferences).candidates.nextWordPrediction.getValue() shouldBe false
        }

        "direct boot mirror includes the explicit prediction preference and restores a disable" {
            val credential = InMemorySharedPreferences()
            val device = InMemorySharedPreferences()
            val source = AppPrefs(credential.preferences)
            source.candidates.nextWordPrediction.setValue(true)
            val mirror = DirectBootPreferenceMirror({ device.preferences }, listOf(source.candidates))
            mirror.syncAll() shouldBe true
            device.value(AppPrefs.Candidates.NEXT_WORD_PREDICTION) shouldBe true

            val locked = AppPrefs(device.preferences)
            locked.candidates.nextWordPrediction.setValue(false)
            val journal = DirectBootChangeJournal(device.preferences, mirror.mirroredKeys)
            journal.record(AppPrefs.Candidates.NEXT_WORD_PREDICTION) shouldBe true
            val back = DirectBootPreferenceMirror({ credential.preferences }, listOf(locked.candidates))
            journal.restoreChanges { keys -> back.syncKeys(keys, commit = true) }.valuesRestored shouldBe true
            source.candidates.nextWordPrediction.getValue() shouldBe false
        }
    })
