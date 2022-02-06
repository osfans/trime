package com.osfans.trime.tasker

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.joaomgcd.taskerpluginlibrary.action.TaskerPluginRunnerActionNoInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfig
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigHelperNoInput
import com.joaomgcd.taskerpluginlibrary.config.TaskerPluginConfigNoInput
import com.joaomgcd.taskerpluginlibrary.input.TaskerInput
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResult
import com.joaomgcd.taskerpluginlibrary.runner.TaskerPluginResultSucess
import com.osfans.trime.R
import com.osfans.trime.tasker.data.CopyOutput
import com.osfans.trime.util.ClipboardUtils

class CopyActionRunner : TaskerPluginRunnerActionNoInput<CopyOutput>() {
    override fun run(context: Context, input: TaskerInput<Unit>): TaskerPluginResult<CopyOutput> {
        return TaskerPluginResultSucess(CopyOutput(ClipboardUtils.getCopyText(context)))
    }
}

class CopyActionHelper(config: TaskerPluginConfig<Unit>) :
    TaskerPluginConfigHelperNoInput<CopyOutput, CopyActionRunner>(config) {
    override val outputClass = CopyOutput::class.java
    override val runnerClass get() = CopyActionRunner::class.java

    override fun addToStringBlurb(input: TaskerInput<Unit>, blurbBuilder: StringBuilder) {
        blurbBuilder.append(context.getString(R.string.copy_text_desc))
    }
}

class CopyActionActivity : AppCompatActivity(), TaskerPluginConfigNoInput {
    override val context: Context get() = applicationContext

    private val helper by lazy { CopyActionHelper(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        helper.finishForTasker()
    }
}
