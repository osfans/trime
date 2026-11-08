/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class T9ProtocolTest :
    StringSpec({
        "existing native callback IDs remain stable with T9 appended at eleven" {
            RimeMessage.MessageType.entries.map { it.name to it.ordinal } shouldBe listOf(
                "Unknown" to 0,
                "Schema" to 1,
                "Option" to 2,
                "Deploy" to 3,
                "Commit" to 4,
                "InlinePreedit" to 5,
                "Composition" to 6,
                "Menu" to 7,
                "Status" to 8,
                "Candidate" to 9,
                "Key" to 10,
                "T9" to 11,
            )
        }

        "native T9 action IDs preserve focus lock unlock undo and cancel" {
            T9Action.entries.map { it.name to it.ordinal } shouldBe listOf(
                "Focus" to 0,
                "Lock" to 1,
                "Unlock" to 2,
                "Undo" to 3,
                "Cancel" to 4,
            )
        }

        "the full native T9 constructors retain their parameter signatures and values" {
            val integer = checkNotNull(Int::class.javaPrimitiveType)
            val boolean = checkNotNull(Boolean::class.javaPrimitiveType)
            val span = checkNotNull(
                T9SpanProto::class.java.getConstructor(
                    integer,
                    integer,
                    String::class.java,
                    boolean,
                    boolean,
                    integer,
                ).newInstance(0, 2, "ni", false, true, 0),
            )
            span shouldBe T9SpanProto(0, 2, "ni", false, true)
            val segments = arrayOf(span)
            val choices = arrayOf(T9SpanProto(3, 6, "hao", true, false))
            val state = checkNotNull(
                T9StateProto::class.java.getConstructor(
                    boolean,
                    integer,
                    String::class.java,
                    integer,
                    boolean,
                    segments.javaClass,
                    choices.javaClass,
                ).newInstance(true, 17, "64'426", 3, true, segments, choices),
            )
            state.enabled shouldBe true
            state.revision shouldBe 17
            state.input shouldBe "64'426"
            state.focus shouldBe 3
            state.canUndo shouldBe true
            state.segments.toList() shouldBe segments.toList()
            state.choices.toList() shouldBe choices.toList()
        }

        "nativeCreate delivers the complete T9 state with its revision and original offsets" {
            val locked = T9SpanProto(0, 2, "ni", false, true)
            val choice = T9SpanProto(3, 6, "hao", true, false)
            val state = T9StateProto(true, 23, "64'426", 3, true, arrayOf(locked), arrayOf(choice))
            val message = RimeMessage.nativeCreate(11, arrayOf(state)) as RimeMessage.T9Message
            message.messageType shouldBe RimeMessage.MessageType.T9
            (message.data === state) shouldBe true
            message.data.revision shouldBe 23
            message.data.input shouldBe "64'426"
            message.data.focus shouldBe 3
            message.data.canUndo shouldBe true
            message.data.segments.toList() shouldBe listOf(locked)
            message.data.choices.toList() shouldBe listOf(choice)
        }

        "nativeCreate also publishes the cleared T9 state after composition ends" {
            val message = RimeMessage.nativeCreate(11, arrayOf(T9StateProto())) as RimeMessage.T9Message
            message.data.enabled shouldBe false
            message.data.input shouldBe ""
            message.data.canUndo shouldBe false
            message.data.segments.toList() shouldBe emptyList()
            message.data.choices.toList() shouldBe emptyList()
        }

        "inline messages retain cursor metadata while ordinary commit messages retain their type" {
            val inline = InlinePreeditProto("ni'hao", 3)
            val inlineMessage = RimeMessage.nativeCreate(5, arrayOf(inline)) as RimeMessage.InlinePreeditMessage
            inlineMessage.data shouldBe inline
            inlineMessage.messageType shouldBe RimeMessage.MessageType.InlinePreedit
            val commit = CommitProto("你好")
            val commitMessage = RimeMessage.nativeCreate(4, arrayOf(commit)) as RimeMessage.CommitTextMessage
            commitMessage.data shouldBe commit
            commitMessage.messageType shouldBe RimeMessage.MessageType.Commit
        }
    })
