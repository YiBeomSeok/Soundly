plugins {
    id("soundly.android.feature")
}

android {
    namespace = "com.soundly.feature.main"
}

dependencies {
    implementation(projects.feature.recordingApi)
    implementation(libs.androidx.navigation3.ui)
    implementation(libs.androidx.lifecycle.viewModelNavigation3)
}
