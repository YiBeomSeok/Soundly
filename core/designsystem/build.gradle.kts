plugins {
    id("soundly.android.library")
    id("soundly.android.compose")
}

android {
    namespace = "com.soundly.core.designsystem"
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.compose.material.icons.extended)
}
