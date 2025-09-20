plugins {
    id("soundly.android.library")
    id("soundly.android.compose")
}

android {
    namespace = "com.soundly.feature.nav.graph"
}

dependencies {
    api(libs.androidx.compose.navigation)
}
