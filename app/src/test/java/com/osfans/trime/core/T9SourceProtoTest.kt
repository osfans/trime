/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class T9SourceProtoTest :
    StringSpec({
        "existing exact and completion constructors carry no correction source" {
            T9SpanProto(0, 2, "ni", false, false).sources shouldBe 0
            T9SpanProto(0, 3, "ning", true, false).sources shouldBe 0
        }

        "source changes invalidate a recycled choice even when its spelling is equal" {
            val exact = T9SpanProto(0, 2, "ni", false, false)
            (exact == exact.copy(sources = 64)) shouldBe false
        }

        "locking does not lose the correction provenance or original offsets" {
            val repair = T9SpanProto(3, 4, "ni", false, false, 128)
            val locked = repair.copy(locked = true)
            locked.sources shouldBe 128
            locked.start shouldBe 3
            locked.end shouldBe 4
        }
    })
