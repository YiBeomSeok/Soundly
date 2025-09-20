plugins {
    id("soundly.android.feature")
}

android {
    namespace = "com.soundly.feature.recording"
}

dependencies {
    implementation(projects.soundlySdk.record)
    implementation("androidx.activity:activity-compose:1.9.3")
}
