/*
 * SPDX-FileCopyrightText: 2015 - 2025 Rime community
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

import org.gradle.api.Project
import org.gradle.api.Task
import java.io.File
import java.util.Properties
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

fun Project.runCmd(
    cmd: String,
    default: String = "",
): String {
    val output = providers.exec {
        commandLine = cmd.split(" ")
    }
    return if (output.result.get().exitValue == 0) {
        output.standardOutput.asText.get().trim()
    } else {
        default
    }
}

val Project.assetsDir: File
    get() = file("src/main/assets").also { it.mkdirs() }

val Project.cleanTask: Task
    get() = tasks.getByName("clean")

val Project.cmakeVersion
    get() = envOrProp("CMAKE_VERSION", "cmakeVersion") { Versions.DEFAULT_CMAKE }

val Project.ndkVersion
    get() = envOrProp("NDK_VERSION", "ndkVersion") { Versions.DEFAULT_NDK }

val Project.buildAbiOverride
    get() = envOrPropOrNull("BUILD_ABI", "buildABI")

val Project.builder
    get() =
        envOrProp("CI_NAME", "ciName") {
            runCmd("git config user.name", "N/A")
        }

val Project.buildGitRepo
    get() =
        envOrProp("BUILD_GIT_REPO", "buildGitRepo") {
            runCmd("git remote get-url origin", "https://github.com/osfans/trime")
                .replace("git@([^:]+):(.+)/(.+)\\.git".toRegex(), "https://$1/$2/$3")
        }

val Project.buildVersionName
    get() =
        envOrProp("BUILD_VERSION_NAME", "buildVersionName") {
            runCmd("git describe --tags --long --always --exclude=nightly")
        }

val Project.buildCommitHash
    get() =
        envOrProp("BUILD_COMMIT_HASH", "buildCommitHash") {
            runCmd("git rev-parse HEAD", "N/A")
        }

val Project.buildTimestamp
    get() =
        envOrProp("BUILD_TIMESTAMP", "buildTimestamp") {
            System.currentTimeMillis().toString()
        }

val Project.signKeyStoreProps: Properties?
    get() {
        val name =
            envOrPropOrNull("KEYSTORE_PROPERTIES", "keystoreProperties")
                ?: "keystore.properties"
        val file = File(name)
        return if (file.exists()) Properties().apply { load(file.inputStream()) } else null
    }

val Project.signKeyBase64: String?
    get() = signKeyStoreProps?.get("keyBase64") as? String

val Project.signKeyStore
    get() = signKeyStoreProps?.get("storeFile") as? String

val Project.signKeyStorePwd
    get() = signKeyStoreProps?.get("storePassword") as? String

val Project.signKeyAlias
    get() = signKeyStoreProps?.get("keyAlias") as? String

val Project.signKeyPwd
    get() = signKeyStoreProps?.get("keyPassword") as? String

val Project.signKeyFile: File?
    get() {
        signKeyStore?.let {
            val file = File(it)
            if (file.exists()) return file
        }

        @OptIn(ExperimentalEncodingApi::class)
        signKeyBase64?.let {
            val buildDir = layout.buildDirectory.asFile.get()
            buildDir.mkdirs()
            val file = File.createTempFile("sign-", ".ks", buildDir)
            try {
                file.writeBytes(Base64.decode(it))
                return file
            } catch (e: Exception) {
                println(e.localizedMessage ?: e.stackTraceToString())
                file.delete()
            }
        }
        return null
    }
