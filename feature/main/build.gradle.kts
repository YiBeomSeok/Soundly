plugins {
    id("soundly.android.feature")
}

android {
    namespace = "com.soundly.feature.main"
}

dependencies {
    implementation(projects.feature.recorder)
}
