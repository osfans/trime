import org.gradle.api.Project
import java.io.File
import java.util.Properties

object ApkRelease {
    private const val KEYSTORE_PROPERTIES = "keystore.properties"

    private val Project.props: Properties
        get() =
            rootProject.file(KEYSTORE_PROPERTIES).takeIf { it.exists() }
                ?.let { Properties().apply { load(it.inputStream()) } }
                ?: Properties()

    val Project.storeFile
        get() = props["storeFile"] as? String

    val Project.storePassword
        get() = props["storePassword"] as? String

    val Project.keyAlias
        get() = props["keyAlias"] as? String

    val Project.keyPassword
        get() = props["keyPassword"] as? String

    val Project.buildApkRelease
        get() = storeFile?.let { File(it).exists() } ?: false
}
