/* SPDX-License-Identifier: GPL-3.0-or-later */

import kotlinx.serialization.encodeToString
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.FileSystemOperations
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import javax.inject.Inject

abstract class PrepareRimeAssetsTask : DefaultTask() {
    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val reportDir: DirectoryProperty

    @get:Inject
    abstract val fileSystem: FileSystemOperations

    @TaskAction
    fun prepare() {
        val source = inputDir.get().asFile
        val output = outputDir.get().asFile
        val reports = reportDir.get().asFile.apply { mkdirs() }
        fileSystem.sync {
            from(source)
            into(output)
            exclude(DataChecksumsPlugin.FILE_NAME)
        }
        val shared = output.resolve("shared")
        LunaVocabulary.adapt(
            source.resolve("shared/luna_pinyin.dict.yaml"),
            source.resolve("shared/essay.txt"),
            shared.resolve("luna_pinyin.dict.yaml"),
            shared.resolve("luna_pinyin_essay.txt"),
            reports.resolve("luna-unencodable.tsv"),
        )
        val hashes = linkedMapOf<String, String>()
        for (name in listOf("essay.txt", "luna_pinyin.dict.yaml")) {
            hashes["source/$name"] = DataChecksumsPlugin.DataChecksumsTask.sha256(source.resolve("shared/$name"))
        }
        for (name in listOf("essay.txt", "luna_pinyin.dict.yaml", "luna_pinyin_essay.txt")) {
            hashes["packaged/$name"] = DataChecksumsPlugin.DataChecksumsTask.sha256(shared.resolve(name))
        }
        hashes["rejected/luna-unencodable.tsv"] = DataChecksumsPlugin.DataChecksumsTask.sha256(reports.resolve("luna-unencodable.tsv"))
        reports.resolve("sha256.json").writeText(json.encodeToString(hashes))
    }
}
