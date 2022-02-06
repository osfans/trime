package com.osfans.trime.tasker

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.joaomgcd.taskerpluginlibrary.condition.TaskerPluginRunnerConditionNoInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultCondition
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultConditionSatisfied
import com.osfans.trime.tasker.data.CopyOutput
import com.osfans.trime.tasker.data.CopyUpdate
import com.osfans.trime.util.ClipboardUtils

class CopyEventRunner : TaskerPluginRunnerConditionNoInput<CopyOutput, CopyUpdate>() {
    override val isEvent: Boolean
        get() = true

    override fun getSatisfiedCondition(
        context: Context,
        input: TaskerInput<Unit>,
        update: CopyUpdate?
    ): TaskerPluginResultCondition<CopyOutput> {
        return TaskerPluginResultConditionSatisfied(
            context,
            CopyOutput(ClipboardUtils.getCopyText(context))
        )
    }
}

class CopyEventHelper(config: TaskerPluginConfig<Unit>) :
    TaskerPluginConfigHelperNoInput<CopyOutput, CopyEventRunner>(config) {
    override val outputClass: Class<CopyOutput>
        get() = CopyOutput::class.java
    override val runnerClass: Class<CopyEventRunner>
        get() = CopyEventRunner::class.java
}

class CopyEventActivity : AppCompatActivity(), TaskerPluginConfigNoInput {
    override val context: Context get() = applicationContext

    private val helper by lazy { CopyEventHelper(this) }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper.finishForTasker()
    }
}
