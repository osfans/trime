// SPDX-FileCopyrightText: 2026 Rime community
//
// SPDX-License-Identifier: GPL-3.0-or-later

import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.artifacts.transform.InputArtifact
import org.gradle.api.artifacts.transform.TransformAction
import org.gradle.api.artifacts.transform.TransformOutputs
import org.gradle.api.artifacts.transform.TransformParameters
import org.gradle.api.artifacts.type.ArtifactTypeDefinition
import org.gradle.api.attributes.Attribute
import org.gradle.api.file.FileSystemLocation
import org.gradle.api.initialization.Settings
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.util.GradleVersion

/** Removes only obsolete calls from the two pinned, stable build plugins. */
class GradleCompatibilityPlugin : Plugin<Settings> {
    override fun apply(target: Settings) {
        require(GradleVersion.current().version == "9.8.1" && JavaVersion.current() == JavaVersion.VERSION_17) {
            "The audited build-plugin backport requires Gradle 9.8.1 and JDK 17; audit before changing the build environment"
        }
        target.gradle.lifecycle.beforeProject {
            val patch = Attribute.of("com.osfans.trime.gradle-visible-backport", String::class.java)
            buildscript.dependencies.apply {
                attributesSchema.attribute(patch)
                // Keep variant selection unchanged. The attribute belongs to JAR artifacts,
                // not published component variants (which are selected before transforms).
                artifactTypes.maybeCreate(ArtifactTypeDefinition.JAR_TYPE).attributes.attribute(patch, "upstream")
                registerTransform(GradleVisibleBackportTransform::class.java) {
                    from.attribute(patch, "upstream")
                    from.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                    to.attribute(patch, "backported")
                    to.attribute(ArtifactTypeDefinition.ARTIFACT_TYPE_ATTRIBUTE, ArtifactTypeDefinition.JAR_TYPE)
                }
            }
            buildscript.configurations.named("classpath") {
                attributes.attribute(patch, "backported")
            }
        }
    }
}

abstract class GradleVisibleBackportTransform : TransformAction<TransformParameters.None> {
    @get:InputArtifact
    @get:PathSensitive(PathSensitivity.NAME_ONLY)
    abstract val inputArtifact: Provider<FileSystemLocation>

    override fun transform(outputs: TransformOutputs) {
        val input = inputArtifact.get().asFile
        if (!GradleVisibleBackport.isTarget(input.name)) {
            outputs.file(input)
            return
        }
        val output = outputs.file(GradleVisibleBackport.outputPath(input.name))
        val count = GradleVisibleBackport.patchJar(input, output)
        Logging.getLogger(javaClass).lifecycle("Trime Gradle backport: removed {} obsolete calls from {}", count, input.name)
    }
}
