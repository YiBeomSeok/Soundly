plugins {
    id("soundly.android.data")
    id("com.google.devtools.ksp")
}

android {
    namespace = "com.soundly.data.recording"
    kotlinOptions {
        freeCompilerArgs = listOf("-XXLanguage:+PropertyParamAnnotationDefaultTargetMode")
    }
}

dependencies {
    implementation(projects.domain.recording)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
}