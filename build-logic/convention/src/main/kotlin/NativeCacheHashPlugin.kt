import ApkRelease.buildApkRelease
import Versions.cmakeVersion
import Versions.ndkVersion
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.TaskAction
import org.gradle.kotlin.dsl.task
import org.jetbrains.kotlin.com.google.common.hash.Hashing
import org.jetbrains.kotlin.com.google.common.io.ByteSource
import java.io.File

class NativeCacheHashPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        target.task<NativeCacheHashCalcTask>("calculateNativeCacheHash")
    }

    abstract class NativeCacheHashCalcTask : DefaultTask() {
        companion object {
            fun sha256(file: File): String = ByteSource.wrap(file.readBytes()).hash(Hashing.sha256()).toString()

            fun sha256(string: String) = ByteSource.wrap(string.encodeToByteArray()).hash(Hashing.sha256()).toString()
        }

        @TaskAction
        fun execute() {
            with(project) {
                val magic =
                    buildString {
                        appendLine(cmakeVersion)
                        appendLine(ndkVersion)
                        if (!buildApkRelease) {
                            appendLine(buildABI)
                        }
                        appendLine(runCmd("git submodule status"))
                        fileTree("src/main/jni/cmake").forEach { module ->
                            appendLine(sha256(module))
                        }
                        fileTree("src/main/jni/librime_jni").forEach { source ->
                            appendLine(sha256(source))
                        }
                        appendLine(sha256(file("src/main/jni/CMakeLists.txt")))
                    }
                val hash = sha256(magic)
                logger.log(LogLevel.DEBUG, "Native Cache Hash: $hash")
                System.getenv("GITHUB_OUTPUT")?.takeIf { it.isNotBlank() }?.let {
                    File(it).appendText("native-cache-hash=$hash")
                }
            }
        }
    }
}
