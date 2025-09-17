plugins {
    id("soundly.android.application")
}

dependencies {
    implementation(projects.feature.onboarding)
    implementation(projects.feature.recorder)
}