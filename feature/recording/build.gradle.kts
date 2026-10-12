plugins {
    id("soundly.android.feature")
}

android {
    namespace = "com.soundly.feature.recording"

    testOptions.unitTests.all { it.useJUnit() }
}

dependencies {
    implementation(projects.soundlySdk.recordingApi)
    implementation(projects.soundlySdk.player)
    implementation(projects.feature.recordingApi)
    implementation(projects.data.recording)

    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.service)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.material)
    implementation(libs.androidx.appcompat)
    // 녹음 파일 재생에 사용합니다.
    implementation(libs.androidx.media3.exoplayer)

    testImplementation(kotlin("test"))
    testImplementation(libs.junit)
    testImplementation(libs.mockito.core)
    testImplementation(libs.coroutines.test)
}
