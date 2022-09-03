package com.osfans.trime.data

import com.osfans.trime.util.appContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

object LibraryLicenseDao {
    private const val LICENSE_JSON = "licenses.json"
    @Serializable
    data class License(
        val artifactId: ArtifactId,
        val license: String? = null,
        val licenseUrl: String? = null,
        val normalizedLicense: String? = null,
        val url: String? = null,
        val libraryName: String
    )

    @Serializable
    data class ArtifactId(
        val name: String,
        val group: String,
        val version: String
    )

    private lateinit var result: List<License>

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun getAll(): Result<List<License>> = runCatching {
        return@runCatching if (this::result.isInitialized) {
            result
        } else {
            withContext(Dispatchers.IO) {
                val raw = appContext.assets.open(LICENSE_JSON)
                    .bufferedReader()
                    .use { f -> f.readText() }
                val parsed = json.decodeFromString(
                    MapSerializer(
                        String.serializer(),
                        ListSerializer(License.serializer())
                    ),
                    raw
                )["libraries"]!!
                    .filter { !it.licenseUrl.isNullOrEmpty() }
                    .sortedBy { it.libraryName }
                result = parsed
                parsed
            }
        }
    }
}
