plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.verify.detektPlugin)
}

gradlePlugin {
    plugins {
        register("androidHilt") {
            id = "soundly.android.hilt"
            implementationClass = "com.soundly.HiltAndroidPlugin"
        }
        register("kotlinHilt") {
            id = "soundly.kotlin.hilt"
            implementationClass = "com.soundly.HiltKotlinPlugin"
        }
        register("dagger") {
            id = "soundly.dagger"
            implementationClass = "com.soundly.DaggerPlugin"
        }
    }
}