/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RimeEditorMessageTest :
    StringSpec({
        "composition and candidate frames are editor owned" {
            listOf(
                RimeMessage.CompositionMessage(CompositionProto()),
                RimeMessage.BulkCandidatesMessage(Candidates.Bulk(total = 0)),
                RimeMessage.PagedCandidatesMessage(Candidates.Paged()),
                RimeMessage.T9Message(T9StateProto()),
                RimeMessage.StatusMessage(StatusProto()),
                RimeMessage.CommitTextMessage(CommitProto(null)),
                RimeMessage.InlinePreeditMessage(InlinePreeditProto("")),
            ).forEach { it.isEditorResponse shouldBe true }
        }

        "engine configuration events remain global" {
            listOf(
                RimeMessage.SchemaMessage(SchemaItem("luna_pinyin_t9", "T9")),
                RimeMessage.OptionMessage(RimeMessage.OptionMessage.Data("ascii_mode", true)),
                RimeMessage.DeployMessage(RimeMessage.DeployMessage.State.Failure),
                RimeMessage.UnknownMessage(emptyArray()),
            ).forEach { it.isEditorResponse shouldBe false }
        }
    })
