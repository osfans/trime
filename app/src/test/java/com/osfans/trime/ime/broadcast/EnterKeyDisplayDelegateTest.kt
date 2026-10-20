/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.ime.broadcast

import android.text.InputType
import android.view.inputmethod.EditorInfo
import com.osfans.trime.data.theme.Theme
import com.osfans.trime.data.theme.model.GeneralStyle
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.kodein.di.DI
import org.kodein.di.bindInstance

private class EnterDisplayFixture(mode: Int = EnterKeyDisplayDelegate.Mode.ACTION_LABEL_PREFERRED.ordinal) {
    private val broadcaster = InputBroadcaster()
    private val theme = Theme(
        name = "Enter label test",
        style = GeneralStyle(
            enterLabelMode = mode,
            enterLabels = GeneralStyle.EnterLabel(
                go = "Theme go",
                done = "Theme done",
                next = "Theme next",
                pre = "Theme previous",
                search = "Theme search",
                send = "Theme send",
                default = "Theme return",
            ),
        ),
    )
    val delegate = EnterKeyDisplayDelegate(
        DI {
            bindInstance<InputBroadcaster> { broadcaster }
            bindInstance<Theme> { theme }
        },
    )
    val events = mutableListOf<Pair<String, String>>()

    init {
        broadcaster.addReceiver(
            object : InputBroadcastReceiver {
                override fun onEnterKeyLabelUpdate(label: String) {
                    events.add(label to delegate.keyLabel)
                }
            },
        )
    }

    fun update(
        imeOptions: Int,
        inputType: Int = InputType.TYPE_CLASS_TEXT,
        actionId: Int = EditorInfo.IME_ACTION_UNSPECIFIED,
        actionLabel: CharSequence? = null,
    ) {
        delegate.updateLabel(inputType, imeOptions, actionId, actionLabel)
    }
}

class EnterKeyDisplayDelegateTest :
    StringSpec({
        "the same delegate publishes Search then Send then a custom action after updating its label" {
            val fixture = EnterDisplayFixture()
            fixture.update(EditorInfo.IME_ACTION_SEARCH)
            fixture.update(EditorInfo.IME_ACTION_SEND)
            fixture.update(EditorInfo.IME_ACTION_SEARCH, actionId = 42, actionLabel = "Approve")
            fixture.delegate.keyLabel shouldBe "Approve"
            fixture.events shouldBe listOf(
                "Theme search" to "Theme search",
                "Theme send" to "Theme send",
                "Approve" to "Approve",
            )
        }

        "changing editor metadata without changing visible text does not broadcast again" {
            val fixture = EnterDisplayFixture()
            fixture.update(EditorInfo.IME_ACTION_SEARCH)
            fixture.update(EditorInfo.IME_ACTION_SEARCH or EditorInfo.IME_FLAG_NO_EXTRACT_UI)
            fixture.update(EditorInfo.IME_ACTION_SEARCH, actionLabel = "")
            fixture.delegate.keyLabel shouldBe "Theme search"
            fixture.events shouldBe listOf("Theme search" to "Theme search")
        }

        "NO_ENTER_ACTION and TYPE_NULL restore the theme default despite a custom label" {
            val fixture = EnterDisplayFixture()
            fixture.update(EditorInfo.IME_ACTION_SEARCH)
            fixture.update(
                EditorInfo.IME_ACTION_SEND or EditorInfo.IME_FLAG_NO_ENTER_ACTION,
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE,
                actionId = 42,
                actionLabel = "Approve",
            )
            fixture.delegate.keyLabel shouldBe "Theme return"
            fixture.update(
                EditorInfo.IME_ACTION_SEARCH,
                inputType = InputType.TYPE_NULL,
                actionId = 42,
                actionLabel = "Approve",
            )
            fixture.delegate.keyLabel shouldBe "Theme return"
            fixture.update(EditorInfo.IME_ACTION_SEARCH)
            fixture.events shouldBe listOf(
                "Theme search" to "Theme search",
                "Theme return" to "Theme return",
                "Theme search" to "Theme search",
            )
        }

        "app-only mode falls back to the theme default for null and empty labels" {
            val fixture = EnterDisplayFixture(EnterKeyDisplayDelegate.Mode.ACTION_LABEL_ONLY.ordinal)
            fixture.update(EditorInfo.IME_ACTION_SEARCH)
            fixture.update(EditorInfo.IME_ACTION_SEARCH, actionId = 42, actionLabel = "Find")
            fixture.update(EditorInfo.IME_ACTION_SEARCH, actionLabel = null)
            fixture.update(EditorInfo.IME_ACTION_SEARCH, actionLabel = "")
            fixture.delegate.keyLabel shouldBe "Theme return"
            fixture.events shouldBe listOf(
                "Theme return" to "Theme return",
                "Find" to "Find",
                "Theme return" to "Theme return",
            )
        }
    })
