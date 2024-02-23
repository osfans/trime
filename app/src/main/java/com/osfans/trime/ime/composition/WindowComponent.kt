package com.osfans.trime.ime.composition

sealed class WindowComponent {
    class Move(data: Map<String, String?>) : WindowComponent() {
        val prefix = data["start"] ?: ""
        val move = data["move"] ?: ""
        val alignment = data["align"] ?: ""
        val suffix = data["end"] ?: ""
    }

    class Candidate(data: Map<String, String?>) : WindowComponent() {
        val prefix = data["start"] ?: ""
        val labelFormat = data["label"] ?: ""
        val candidateFormat = data["candidate"] ?: ""
        val commentFormat = data["comment"] ?: ""
        val alignment = data["align"] ?: ""
        val separator = data["sep"] ?: ""
        val suffix = data["end"] ?: ""
    }

    class Composition(data: Map<String, String?>) : WindowComponent() {
        val prefix = data["start"] ?: ""
        val compositionFormat = data["composition"] ?: ""
        val letterSpacing = data["letter_spacing"]?.toFloat() ?: 0f
        val alignment = data["align"] ?: ""
        val suffix = data["end"] ?: ""
    }

    class Button(data: Map<String, String?>) : WindowComponent() {
        val prefix = data["start"] ?: ""
        val click = data["click"] ?: ""
        val label = data["label"] ?: ""
        val `when` = data["when"] ?: ""
        val alignment = data["align"] ?: ""
        val suffix = data["end"] ?: ""
    }

    companion object {
        fun decodeFromList(data: List<Map<String, String?>>): List<WindowComponent> {
            return data.mapNotNull deserialize@{
                val component =
                    if (it.containsKey("move")) {
                        Move(it)
                    } else if (it.containsKey("candidate")) {
                        Candidate(it)
                    } else if (it.containsKey("composition")) {
                        Composition(it)
                    } else if (it.containsKey("click")) {
                        Button(it)
                    } else {
                        null
                    }
                return@deserialize component
            }
        }
    }
}
