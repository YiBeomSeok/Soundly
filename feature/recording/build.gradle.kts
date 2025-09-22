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
    implementation(libs.androidx.lifecycle.service)
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("com.google.android.material:material:1.9.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    // Media3 ExoPlayer for playback
    implementation("androidx.media3:media3-exoplayer:1.4.1")
}
