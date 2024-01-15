plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version embeddedKotlinVersion
}

group = "com.osfans.trime.build_logic"

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    implementation(libs.kotlinx.serialization.json)
}

gradlePlugin {
    plugins {
        register("dataChecksums") {
            id = "com.osfans.trime.data-checksums"
            implementationClass = "DataChecksumsPlugin"
        }
        register("nativeAppConvention") {
            id = "com.osfans.trime.native-app-convention"
            implementationClass = "NativeAppConventionPlugin"
        }
        register("nativeCacheHash") {
            id = "com.osfans.trime.native-cache-hash"
            implementationClass = "NativeCacheHashPlugin"
        }
    }
}
