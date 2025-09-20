plugins {
    id("soundly.android.feature")
}

android {
    namespace = "com.soundly.feature.recording"
}

dependencies {
    implementation(projects.soundlySdk.record)
    implementation(projects.feature.recordingApi)
    implementation(projects.domain.recording)

    implementation(libs.androidx.compose.material.icons.extended)
}
