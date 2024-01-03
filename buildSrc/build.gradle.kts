plugins {
    `kotlin-dsl`
    kotlin("plugin.serialization") version embeddedKotlinVersion
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    implementation(libs.guava)
    implementation(libs.kotlinx.serialization.json)
}

gradlePlugin {
    plugins {
        register("dataChecksums") {
            id = "com.osfans.trime.data-checksums"
            implementationClass = "DataChecksumsPlugin"
        }
    }
}
