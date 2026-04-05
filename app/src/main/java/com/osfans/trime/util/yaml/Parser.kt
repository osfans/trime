/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.osfans.trime.util.yaml

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.error.MarkedYAMLException
import org.yaml.snakeyaml.events.AliasEvent
import org.yaml.snakeyaml.events.Event
import org.yaml.snakeyaml.events.MappingStartEvent
import org.yaml.snakeyaml.events.ScalarEvent
import org.yaml.snakeyaml.events.SequenceStartEvent
import org.yaml.snakeyaml.parser.ParserImpl
import org.yaml.snakeyaml.reader.StreamReader
import java.io.Reader
import java.io.StringReader

class Parser(reader: Reader, codePointLimit: Int? = null) {
    constructor(source: String) : this(StringReader(source))

    private val loadOptions = LoaderOptions()
        .apply {
            if (codePointLimit != null) this.codePointLimit = codePointLimit
        }

    private val streamReader = StreamReader(reader)
    private val events = ParserImpl(streamReader, loadOptions)

    private val aliases = mutableMapOf<String, Node>()

    init {
        consumeEvent() // stream start
        if (peekEvent().eventId == Event.ID.StreamEnd) {
            throw IllegalArgumentException("The YAML document is empty.")
        }
        consumeEvent() // document start
    }

    fun consumeEvent(): Event = checkEvent { events.event }
    fun peekEvent(): Event = checkEvent { events.peekEvent() }

    fun read(): Node = readNode()

    private fun readNode(): Node {
        val event = consumeEvent()
        val node = readFromEvent(event)
        return node
    }

    private fun readFromEvent(event: Event): Node = when (event) {
        is ScalarEvent -> readScalar(event)
        is SequenceStartEvent -> readSequence(event)
        is MappingStartEvent -> readMapping(event)
        is AliasEvent -> readAlias(event)
        else -> throw Exception("unreachable")
    }

    private fun readScalar(event: ScalarEvent): Node {
        val anchor = event.anchor
        val node = Node.Scalar(event.value, anchor)
        anchor?.let { aliases[it] = node }
        return node
    }

    private fun readSequence(startEvent: SequenceStartEvent): Node {
        val items = mutableListOf<Node>()
        while (true) {
            val event = peekEvent()
            when (event.eventId) {
                Event.ID.SequenceEnd -> {
                    consumeEvent()
                    val anchor = startEvent.anchor
                    val node = Node.Sequence(items, anchor)
                    anchor?.let { aliases[it] = node }
                    return node
                }
                else -> {
                    val node = readNode()
                    items += node
                }
            }
        }
    }

    private fun readMapping(startEvent: MappingStartEvent): Node {
        val items = mutableMapOf<Node, Node>()
        while (true) {
            val event = peekEvent()
            when (event.eventId) {
                Event.ID.MappingEnd -> {
                    consumeEvent()
                    val anchor = startEvent.anchor
                    val node = Node.Mapping(doMerges(items), anchor)
                    anchor?.let { aliases[it] = node }
                    return node
                }
                else -> {
                    val key = readNode()
                    val value = readNode()
                    items += (key to value)
                }
            }
        }
    }

    private fun doMerges(items: Map<Node, Node>): Map<Node, Node> {
        val mergeEntries = items.entries.filter { (key, _) -> isMerge(key) }

        return when (mergeEntries.count()) {
            0 -> items
            1 -> when (val mappingsToMerge = mergeEntries.single().value) {
                is Node.Sequence -> doMerges(items, mappingsToMerge.nodes)
                else -> doMerges(items, listOf(mappingsToMerge))
            }
            else -> throw IllegalArgumentException("Cannot perform multiple '<<' merges into a map. Instead, combine all merges into a single '<<' entry.")
        }
    }

    private fun isMerge(key: Node): Boolean = key is Node.Scalar && key.string == "<<"

    private fun doMerges(original: Map<Node, Node>, others: List<Node>): Map<Node, Node> {
        val merged = mutableMapOf<Node, Node>()

        original
            .filterNot { (key, _) -> isMerge(key) }
            .forEach { (key, value) -> merged[key] = value }

        others
            .forEach { other ->
                when (other) {
                    is Node.Scalar -> throw IllegalArgumentException("Cannot merge a scalar value into a map.")
                    is Node.Sequence -> throw IllegalArgumentException("Cannot merge a sequence value into a map.")
                    is Node.Alias -> throw IllegalArgumentException("Cannot merge a alias value into a map.")
                    is Node.Mapping ->
                        other.entries.forEach { (key, value) ->
                            val existingEntry = merged.entries.singleOrNull { key is Node.Scalar && it.key is Node.Scalar && (it.key as Node.Scalar).string == key.string }
                            if (existingEntry == null) {
                                merged[key] = value
                            }
                        }
                }
            }

        return merged
    }

    private fun readAlias(event: AliasEvent): Node {
        val anchor = event.anchor
        val resolvedNode = aliases.getOrElse(anchor) {
            throw IllegalArgumentException("Unknown anchor '$anchor'")
        }
        return resolvedNode
    }

    private fun checkEvent(retrieve: () -> Event): Event {
        try {
            return retrieve()
        } catch (e: MarkedYAMLException) {
            throw translateYamlException(e)
        }
    }

    private fun translateYamlException(e: MarkedYAMLException): IllegalArgumentException {
        val updatedMessage = StringBuilder()

        val context = e.context
        val contextMark = e.contextMark

        if (context != null && contextMark != null) {
            val snippet = contextMark.get_snippet(4, Int.MAX_VALUE)
            updatedMessage.append(
                """
                    |$context
                    | at line ${contextMark.line + 1}, column ${contextMark.column + 1}:
                    |$snippet
                    |
                """.trimMargin(),
            )
        }

        val problemMark = e.problemMark
        if (problemMark != null) {
            val problem = translateYamlEngineExceptionMessage(e.problem)
            val snippet = problemMark.get_snippet(4, Int.MAX_VALUE)
            updatedMessage.append(
                """
                    |$problem
                    | at line ${problemMark.line + 1}, column ${problemMark.column + 1}:
                    |$snippet
                """.trimMargin(),
            )
        }

        return IllegalArgumentException(updatedMessage.toString())
    }

    private fun translateYamlEngineExceptionMessage(message: String): String = when (message) {
        "mapping values are not allowed here",
        "expected <block end>, but found '<block sequence start>'",
        "expected <block end>, but found '<block mapping start>'",
        ->
            "$message (is the indentation level of this line or a line nearby incorrect?)"
        else -> message
    }
}
