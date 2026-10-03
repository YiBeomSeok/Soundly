plugins {
    id("soundly.android.application")
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(projects.soundlySdk.recordingAndroid)
    implementation(projects.data.recording)
    implementation(projects.feature.main)
    implementation(projects.feature.recording)
    androidTestImplementation(libs.androidx.test.core)
}
