/*
 * SPDX-FileCopyrightText: 2015 - 2026 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import com.android.build.gradle.internal.scope.InternalArtifactType
import com.android.build.gradle.internal.scope.InternalMultipleArtifactType
import com.android.build.gradle.internal.tasks.CompileArtProfileTask
import com.android.build.gradle.internal.tasks.ExpandArtProfileWildcardsTask
import com.android.build.gradle.internal.tasks.MergeArtProfileTask
import com.android.build.gradle.tasks.PackageApplication
import org.gradle.api.Project
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.file.RegularFile
import org.gradle.api.internal.provider.AbstractProperty
import org.gradle.api.internal.provider.Providers
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

@Suppress("unused")
class AndroidAppConventionPlugin : AndroidBaseConventionPlugin() {
    override fun apply(target: Project) {
        target.pluginManager.apply("com.android.application")

        super.apply(target)

        // remove META-INF/com/android/build/gradle/app-metadata.properties
        target.tasks.withType<PackageApplication> {
            val valueField =
                AbstractProperty::class.java.declaredFields.find { it.name == "value" } ?: run {
                    println("class AbstractProperty field value not found, something could have gone wrong")
                    return@withType
                }
            valueField.isAccessible = true
            doFirst {
                valueField.set(appMetadata, Providers.notDefined<RegularFile>())
                allInputFilesWithNameOnlyPathSensitivity.removeAll { true }
            }
        }

        // remove assets/dexopt/baseline.prof{,m} (baseline profile)
        target.tasks.withType<MergeArtProfileTask> { enabled = false }
        target.tasks.withType<ExpandArtProfileWildcardsTask> { enabled = false }
        target.tasks.withType<CompileArtProfileTask> { enabled = false }

        target.extensions.configure<ApplicationAndroidComponentsExtension> {
            // Add dependency relationships for data checksums task
            onVariants { v ->
                val variantName = v.name.replaceFirstChar { it.uppercase() }
                // Evaluation should be delayed as we need be able to see other tasks
                target.afterEvaluate {
                    tasks.findByName(DataChecksumsPlugin.TASK)?.also {
                        tasks.findByName("merge${variantName}Assets")?.dependsOn(it)
                        tasks.findByName("lintAnalyze$variantName")?.dependsOn(it)
                        tasks.findByName("lintVitalAnalyze$variantName")?.dependsOn(it)
                        tasks.findByName("generate${variantName}LintReportModel")?.dependsOn(it)
                        tasks.findByName("generate${variantName}LintVitalReportModel")?.dependsOn(it)
                    }
                    if (v.buildType == "release" &&
                        target.extensions.getByType<ApplicationExtension>().buildTypes.getByName("release").isMinifyEnabled
                    ) {
                        // AGP 9.4.1 registers its R8 Java-resource transform after onVariants.
                        // Append here, after createAndroidTasks, so SPI input remains available to
                        // R8 and the DEX dependency cannot point back to this resource transform.
                        check(pluginVersion.version == OptimizedCoroutineServices.AGP_VERSION) {
                            "The post-R8 resource transform requires reviewed AGP 9.4.1; found ${pluginVersion.version}"
                        }
                        check(tasks.findByName("minify${variantName}WithR8") != null) { "R8 producer is missing" }
                        val modules = setOf("kotlinx-coroutines-android", "kotlinx-coroutines-core-jvm")
                        val runtime = v.runtimeConfiguration.incoming.artifactView {
                            componentFilter { id ->
                                id is ModuleComponentIdentifier && id.group == "org.jetbrains.kotlinx" && id.module in modules
                            }
                        }
                        val guard = target.tasks.register<RemoveOptimizedCoroutineServicesTask>("remove${variantName}OptimizedCoroutineServices") {
                            agpVersion.set(pluginVersion.version)
                            coroutineArtifacts.from(runtime.files)
                            coroutineCoordinates.set(
                                runtime.artifacts.resolvedArtifacts.map { artifacts ->
                                    artifacts.map { artifact ->
                                        val id = artifact.id.componentIdentifier as ModuleComponentIdentifier
                                        "${id.group}:${id.module}:${id.version}"
                                    }
                                },
                            )
                        }
                        v.artifacts.use(guard).wiredWith(RemoveOptimizedCoroutineServicesTask::originalResources)
                            .toListenTo(InternalArtifactType.ORIGINAL_MERGED_JAVA_RES)
                        v.artifacts.use(guard).wiredWith(RemoveOptimizedCoroutineServicesTask::r8Configuration)
                            .toListenTo(InternalArtifactType.R8_MAPPING_CONFIGURATION)
                        v.artifacts.use(guard).wiredWithMultiple(RemoveOptimizedCoroutineServicesTask::dexDirectories)
                            .toListenTo(InternalMultipleArtifactType.DEX)
                        v.artifacts.use(guard).wiredWithFiles(
                            RemoveOptimizedCoroutineServicesTask::inputResources,
                            RemoveOptimizedCoroutineServicesTask::outputResources,
                        ).toTransform(InternalArtifactType.MERGED_JAVA_RES)
                    }
                }
            }
        }
    }
}
