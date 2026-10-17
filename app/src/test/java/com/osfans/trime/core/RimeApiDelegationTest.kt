/* SPDX-License-Identifier: GPL-3.0-or-later */
package com.osfans.trime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import java.lang.reflect.Modifier

class RimeApiDelegationTest :
    StringSpec({
        "compiled session delegate implements the current API" {
            // Inspect without initializing Rime or loading JNI. This also catches stale incremental output.
            val delegate = Class.forName(
                "com.osfans.trime.daemon.RimeDaemon\$rimeImpl\$2\$1",
                false,
                RimeApi::class.java.classLoader,
            )
            RimeApi::class.java.methods.filter { Modifier.isAbstract(it.modifiers) }.forEach { method ->
                val implementation = delegate.getDeclaredMethod(method.name, *method.parameterTypes)
                Modifier.isAbstract(implementation.modifiers) shouldBe false
                implementation.returnType shouldBe method.returnType
            }
        }
    })
