plugins {
    id("soundly.android.feature")
}

android {
    namespace = "com.soundly.feature.recorder"
}

dependencies {
    implementation(projects.soundlySdk.record)
}
