// SPDX-License-Identifier: GPL-3.0-or-later

package com.osfans.trime.data.prefs

import android.content.SharedPreferences
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

class DirectBootPreferenceMirrorTest :
    StringSpec({
        "direct boot selection includes input categories and excludes internal and profile" {
            val source = InMemorySharedPreferences().preferences
            val internal = BooleanProvider(source, "internal")
            val general = BooleanProvider(source, AppPrefs.General.MANUAL_PRIVACY_MODE)
            val profile = BooleanProvider(source, "profile")
            val keyboard = BooleanProvider(source, "keyboard")
            val candidates = BooleanProvider(source, "candidates")
            val clipboard = BooleanProvider(source, "clipboard")
            val advanced = BooleanProvider(source, "advanced")

            directBootPreferenceProviders(
                internal,
                general,
                profile,
                keyboard,
                candidates,
                clipboard,
                advanced,
            ).shouldContainExactly(general, keyboard, candidates, clipboard, advanced)
        }

        "explicit device-protected AppPrefs source journals an allowed change" {
            val deviceStore = InMemorySharedPreferences()
            val appPrefs =
                AppPrefs(
                    deviceStore.preferences,
                    usingDeviceProtectedStorage = true,
                )
            appPrefs.general.manualPrivacyMode.setValue(true)

            appPrefs.onPreferenceChanged(AppPrefs.General.MANUAL_PRIVACY_MODE)

            DirectBootChangeJournal(
                deviceStore.preferences,
                setOf(AppPrefs.General.MANUAL_PRIVACY_MODE),
            ).changedKeys() shouldBe setOf(AppPrefs.General.MANUAL_PRIVACY_MODE)
        }

        "full sync mirrors manual privacy boolean and serialized enum" {
            val sourceStore = InMemorySharedPreferences()
            val targetStore = InMemorySharedPreferences()
            val general = BooleanProvider(sourceStore.preferences, AppPrefs.General.MANUAL_PRIVACY_MODE)
            val keyboard = EnumProvider(sourceStore.preferences, "keyboard_mode")
            general.value.setValue(true)
            keyboard.value.setValue(TestMode.RIGHT)

            val mirror =
                DirectBootPreferenceMirror(
                    targetPreferences = { targetStore.preferences },
                    providers = listOf(general, keyboard),
                )
            mirror.syncAll()

            targetStore.value(AppPrefs.General.MANUAL_PRIVACY_MODE) shouldBe true
            targetStore.value("keyboard_mode") shouldBe "RIGHT"
        }

        "changed shortcuts mirror immediately and same-store writes are ignored" {
            val sourceStore = InMemorySharedPreferences()
            val targetStore = InMemorySharedPreferences()
            val general = BooleanProvider(sourceStore.preferences, AppPrefs.General.MANUAL_PRIVACY_MODE)
            val keyboard = EnumProvider(sourceStore.preferences, "keyboard_mode")
            val mirror =
                DirectBootPreferenceMirror(
                    targetPreferences = { targetStore.preferences },
                    providers = listOf(general, keyboard),
                )

            general.value.setValue(true)
            mirror.syncChanged(AppPrefs.General.MANUAL_PRIVACY_MODE)
            keyboard.value.setValue(TestMode.LEFT)
            mirror.syncChanged("keyboard_mode")

            targetStore.value(AppPrefs.General.MANUAL_PRIVACY_MODE) shouldBe true
            targetStore.value("keyboard_mode") shouldBe "LEFT"

            val sameStoreProvider = BooleanProvider(targetStore.preferences, "same_store")
            sameStoreProvider.value.setValue(true)
            val applyCount = targetStore.applyCount
            DirectBootPreferenceMirror(
                targetPreferences = { targetStore.preferences },
                providers = listOf(sameStoreProvider),
            ).syncChanged("same_store")
            targetStore.applyCount shouldBe applyCount
        }

        "unlock sync copies only journaled direct boot categories back to credential storage" {
            val deviceStore = InMemorySharedPreferences()
            val credentialStore = InMemorySharedPreferences(
                mapOf(
                    AppPrefs.General.MANUAL_PRIVACY_MODE to false,
                    "keyboard_mode" to "LEFT",
                    "profile" to "credential-only",
                ),
            )
            val general = BooleanProvider(deviceStore.preferences, AppPrefs.General.MANUAL_PRIVACY_MODE)
            val keyboard = EnumProvider(deviceStore.preferences, "keyboard_mode")
            general.value.setValue(true)
            keyboard.value.setValue(TestMode.RIGHT)
            val mirror =
                DirectBootPreferenceMirror(
                    targetPreferences = { credentialStore.preferences },
                    providers = listOf(general, keyboard),
                )
            val journal = DirectBootChangeJournal(deviceStore.preferences, mirror.mirroredKeys)
            journal.record(AppPrefs.General.MANUAL_PRIVACY_MODE)
            journal.record("keyboard_mode")
            journal.record("profile")

            journal.changedKeys().shouldContainExactlyInAnyOrder(
                AppPrefs.General.MANUAL_PRIVACY_MODE,
                "keyboard_mode",
            )
            journal.restoreChanges { changedKeys ->
                mirror.syncKeys(changedKeys, commit = true)
            } shouldBe DirectBootRestoreResult(valuesRestored = true, journalRetained = false)

            credentialStore.value(AppPrefs.General.MANUAL_PRIVACY_MODE) shouldBe true
            credentialStore.value("keyboard_mode") shouldBe "RIGHT"
            credentialStore.value("profile") shouldBe "credential-only"
            DirectBootChangeJournal(deviceStore.preferences, mirror.mirroredKeys).changedKeys() shouldBe emptySet()
        }

        "failed credential restore keeps journal for the next process start" {
            val deviceStore = InMemorySharedPreferences()
            val provider = BooleanProvider(deviceStore.preferences, AppPrefs.General.MANUAL_PRIVACY_MODE)
            val mirror = DirectBootPreferenceMirror({ null }, listOf(provider))
            val journal = DirectBootChangeJournal(deviceStore.preferences, mirror.mirroredKeys)
            provider.value.setValue(true)
            journal.record(AppPrefs.General.MANUAL_PRIVACY_MODE)

            journal.restoreChanges { changedKeys ->
                mirror.syncKeys(changedKeys, commit = true)
            } shouldBe DirectBootRestoreResult(valuesRestored = false, journalRetained = true)

            journal.changedKeys() shouldBe setOf(AppPrefs.General.MANUAL_PRIVACY_MODE)
        }

        "stale excluded journal entries are cleared without copying them" {
            val deviceStore = InMemorySharedPreferences(
                mapOf(DirectBootChangeJournal.KEY to setOf("profile")),
            )
            val provider = BooleanProvider(deviceStore.preferences, AppPrefs.General.MANUAL_PRIVACY_MODE)
            val journal = DirectBootChangeJournal(deviceStore.preferences, setOf(provider.value.key))
            var restoreCalled = false

            journal.restoreChanges {
                restoreCalled = true
                true
            } shouldBe DirectBootRestoreResult(valuesRestored = true, journalRetained = false)

            restoreCalled shouldBe false
            DirectBootChangeJournal.hasRecordedChanges(deviceStore.preferences) shouldBe false
        }

        "successful restore continues when journal cleanup fails and retries cleanup later" {
            val deviceStore = InMemorySharedPreferences()
            val credentialStore = InMemorySharedPreferences()
            val provider = BooleanProvider(deviceStore.preferences, AppPrefs.General.MANUAL_PRIVACY_MODE)
            provider.value.setValue(true)
            val mirror =
                DirectBootPreferenceMirror(
                    targetPreferences = { credentialStore.preferences },
                    providers = listOf(provider),
                )
            val journal = DirectBootChangeJournal(deviceStore.preferences, mirror.mirroredKeys)
            journal.record(AppPrefs.General.MANUAL_PRIVACY_MODE) shouldBe true
            deviceStore.commitSucceeds = false

            journal.restoreChanges { changedKeys ->
                mirror.syncKeys(changedKeys, commit = true)
            } shouldBe DirectBootRestoreResult(valuesRestored = true, journalRetained = true)

            credentialStore.value(AppPrefs.General.MANUAL_PRIVACY_MODE) shouldBe true
            journal.changedKeys() shouldBe emptySet()

            deviceStore.commitSucceeds = true
            journal.record(AppPrefs.General.MANUAL_PRIVACY_MODE) shouldBe true
            journal.restoreChanges { true } shouldBe DirectBootRestoreResult(valuesRestored = true, journalRetained = false)
        }

        "failed journal commit reports uncertain persistence even though process memory was updated" {
            val deviceStore = InMemorySharedPreferences()
            val journal =
                DirectBootChangeJournal(
                    deviceStore.preferences,
                    setOf(AppPrefs.General.MANUAL_PRIVACY_MODE),
                )
            deviceStore.commitSucceeds = false

            journal.record(AppPrefs.General.MANUAL_PRIVACY_MODE) shouldBe false
            journal.changedKeys() shouldBe setOf(AppPrefs.General.MANUAL_PRIVACY_MODE)
        }
    }) {
    private enum class TestMode { LEFT, RIGHT }

    private class BooleanProvider(
        preferences: SharedPreferences,
        key: String,
    ) : PreferenceDelegateProvider() {
        val value = PreferenceDelegate(preferences, key, false).apply { register() }
    }

    private class EnumProvider(
        preferences: SharedPreferences,
        key: String,
    ) : PreferenceDelegateProvider() {
        val value =
            PreferenceDelegate.SerializableDelegate(
                preferences,
                key,
                TestMode.LEFT,
                object : PreferenceDelegate.Serializer<TestMode> {
                    override fun serialize(t: TestMode): String = t.name

                    override fun deserialize(raw: String): TestMode? = TestMode.entries.firstOrNull { it.name == raw }
                },
            ).apply { register() }
    }
}
