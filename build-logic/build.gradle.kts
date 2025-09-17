plugins {
    `kotlin-dsl`
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.roborazzi.gradle.plugin)
    compileOnly(libs.compose.compiler.gradlePlugin)
    compileOnly(libs.compose.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kmpIos") {
            id = "soundly.kmp.ios"
            implementationClass = "com.soundly.app.primitive.KotlinMultiPlatformiOSPlugin"
        }
        register("kmpAndroid") {
            id = "soundly.kmp.android"
            implementationClass = "com.soundly.app.primitive.KotlinMultiPlatformAndroidPlugin"
        }
        register("kmpPrimitive") {
            id = "soundly.kmp"
            implementationClass = "com.soundly.app.primitive.KotlinMultiPlatformPlugin"
        }
        register("detekt") {
            id = "soundly.verify.detekt"
            implementationClass = "com.soundly.app.primitive.DetektPlugin"
        }
        register("kmpConvention") {
            id = "soundly.kotlin.multiplatform"
            implementationClass =
                "com.soundly.app.convention.KotlinMultiPlatformConventionPlugin"
        }
        register("cmpConvention") {
            id = "soundly.compose.multiplatform"
            implementationClass =
                "com.soundly.app.convention.ComposeMultiPlatformConventionPlugin"
        }
        register("soundlyFeature") {
            id = "soundly.feature"
            implementationClass =
                "com.soundly.app.convention.SoundlyFeaturePlugin"
        }
        register("kmpRoborazzi") {
            id = "soundly.kmp.roborazzi"
            implementationClass = "com.soundly.app.primitive.KotlinMultiPlatformRoborazziPlugin"
        }
    }
}
