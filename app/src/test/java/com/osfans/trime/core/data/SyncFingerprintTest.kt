// SPDX-License-Identifier: GPL-3.0-or-later
package com.osfans.trime.core.data

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class SyncFingerprintTest :
    StringSpec({
        "fingerprints use standard SHA-256 byte encoding" {
            SyncFingerprint.hex(SyncFingerprint.digest("abc".byteInputStream())) shouldBe
                "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
            SyncFingerprint.hex(SyncFingerprint.digest("".byteInputStream())) shouldBe
                "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
        }
        "legacy indexes have no content-backed deletion authority" {
            Json.decodeFromString<SyncEntry>("""{"size":3,"lastModified":123}""") shouldBe SyncEntry(3, 123)
        }
        "content fingerprints survive index serialization" {
            val entry = SyncEntry(3, 123, SyncFingerprint.hex(SyncFingerprint.digest("abc".byteInputStream())))
            Json.decodeFromString<SyncEntry>(Json.encodeToString(entry)) shouldBe entry
        }
    })
