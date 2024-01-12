import org.gradle.api.Project

object Versions {
    private const val DEFAULT_CMAKE = "3.22.1"
    private const val DEFAULT_NDK = "25.2.9519653"

    val Project.cmakeVersion
        get() = envOrProp("CMAKE_VERSION", "cmakeVersion") { DEFAULT_CMAKE }

    val Project.ndkVersion
        get() = envOrProp("NDK_VERSION", "ndkVersion") { DEFAULT_NDK }
}
