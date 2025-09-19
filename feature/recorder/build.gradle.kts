plugins {
    id("soundly.android.feature")
}

android {
    namespace = "com.soundly.feature.recorder"
}

dependencies {
    implementation(projects.soundlySdk.record)
    implementation("androidx.activity:activity-compose:1.9.3")
}
