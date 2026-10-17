/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.data.prefs

import com.osfans.trime.core.data.DataStorageMode
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class InitialStoragePreferencesTest :
    StringSpec({
        "an empty installation can use private storage without a directory picker" {
            val store = InMemorySharedPreferences()
            initializeDefaultStoragePreferences(store.preferences, existingRuntimeData = false) shouldBe true
            val prefs = AppPrefs(store.preferences)
            prefs.profile.dataStorageMode.getValue() shouldBe DataStorageMode.APP_STORAGE
            prefs.profile.freshInputSetupPending.getValue() shouldBe true
            store.applyCount shouldBe 1
        }

        "recording the first PID cannot change the chosen fresh-install mode on restart" {
            val store = InMemorySharedPreferences()
            initializeDefaultStoragePreferences(store.preferences, existingRuntimeData = false)
            AppPrefs(store.preferences).internal.pid.setValue(42)
            val writes = store.applyCount
            initializeDefaultStoragePreferences(store.preferences, existingRuntimeData = true) shouldBe true
            AppPrefs(store.preferences).profile.dataStorageMode.getValue() shouldBe DataStorageMode.APP_STORAGE
            store.applyCount shouldBe writes
        }

        "every historical preference preserves the previous implicit external-sync choice" {
            listOf(
                mapOf(AppPrefs.Internal.PID to 100),
                mapOf(AppPrefs.Profile.EXTERNAL_RIME_TREE_URI to "content://example/tree/rime"),
                mapOf("legacy_custom_setting" to false),
            ).forEach { initial ->
                val store = InMemorySharedPreferences(initial)
                initializeDefaultStoragePreferences(store.preferences, existingRuntimeData = false) shouldBe true
                AppPrefs(store.preferences).profile.dataStorageMode.getValue() shouldBe DataStorageMode.EXTERNAL_SYNC
                AppPrefs(store.preferences).profile.freshInputSetupPending.getValue() shouldBe false
                initial.forEach { (key, value) -> store.value(key) shouldBe value }
            }
        }

        "restored Rime data without preferences is treated as an existing installation" {
            val store = InMemorySharedPreferences()
            initializeDefaultStoragePreferences(store.preferences, existingRuntimeData = true) shouldBe true
            AppPrefs(store.preferences).profile.dataStorageMode.getValue() shouldBe DataStorageMode.EXTERNAL_SYNC
            AppPrefs(store.preferences).profile.freshInputSetupPending.getValue() shouldBe false
        }

        "explicit storage choices and their existing pending state are never rewritten" {
            DataStorageMode.entries.forEach { mode ->
                val store = InMemorySharedPreferences(mapOf(AppPrefs.Profile.DATA_STORAGE_MODE to mode.name))
                initializeDefaultStoragePreferences(store.preferences, existingRuntimeData = false) shouldBe true
                AppPrefs(store.preferences).profile.dataStorageMode.getValue() shouldBe mode
                store.applyCount shouldBe 0
                store.value(AppPrefs.Profile.FRESH_INPUT_SETUP_PENDING) shouldBe null
            }
            val pending = InMemorySharedPreferences(
                mapOf(
                    AppPrefs.Profile.DATA_STORAGE_MODE to DataStorageMode.APP_STORAGE.name,
                    AppPrefs.Profile.FRESH_INPUT_SETUP_PENDING to true,
                ),
            )
            initializeDefaultStoragePreferences(pending.preferences, existingRuntimeData = true) shouldBe true
            pending.value(AppPrefs.Profile.FRESH_INPUT_SETUP_PENDING) shouldBe true
            pending.applyCount shouldBe 0
        }

        "a failed disk commit is reported instead of claiming initialization persisted" {
            val store = InMemorySharedPreferences()
            store.commitSucceeds = false
            initializeDefaultStoragePreferences(store.preferences, existingRuntimeData = false) shouldBe false
        }
    })
