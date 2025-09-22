plugins {
    id("soundly.android.feature")
}

android {
    namespace = "com.soundly.feature.recording"
}

dependencies {
    implementation(projects.soundlySdk.record)
    implementation(projects.soundlySdk.player)
    implementation(projects.feature.recordingApi)
    implementation(projects.domain.recording)

    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    // Media3 ExoPlayer for playback
    implementation(libs.androidx.media3.exoplayer)
}
