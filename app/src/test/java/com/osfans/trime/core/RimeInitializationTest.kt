// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldNotBeNull

class RimeInitializationTest :
    StringSpec({
        "loading the API class does not load the native engine on the caller thread" {
            // The host JVM has no Android native library. This fails if <clinit> loads it.
            Class.forName("com.osfans.trime.core.Rime").getField("Companion").get(null).shouldNotBeNull()
        }
    })
