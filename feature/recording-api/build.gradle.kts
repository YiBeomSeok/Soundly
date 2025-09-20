plugins {
    id("soundly.android.feature")
    id("kotlinx-serialization")
}

android {
    namespace = "com.soundly.feature.recording.api"
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}
