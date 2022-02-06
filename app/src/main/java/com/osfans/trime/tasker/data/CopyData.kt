package com.osfans.trime.tasker.data

import com.joaomgcd.taskerpluginlibrary.input.TaskerInputRoot
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputObject
import com.joaomgcd.taskerpluginlibrary.output.TaskerOutputVariable
import com.osfans.trime.R

@TaskerInputRoot
class CopyInput

@TaskerOutputObject
class CopyOutput(
    @get: TaskerOutputVariable(
        VAR_COPY,
        R.string.copy_text,
        R.string.copy_text_desc
    ) var copyText: String
) {
    companion object {
        const val VAR_COPY = "trime_copy_text"
    }
}

@TaskerInputRoot
class CopyUpdate
