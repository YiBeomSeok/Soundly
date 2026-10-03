import com.soundly.configureHiltAndroid
import com.soundly.libs

plugins {
    id("soundly.android.library")
    id("soundly.android.compose")
}

android {
    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

configureHiltAndroid()

dependencies {
    implementation(project(":core:designsystem"))

    val libs = project.extensions.libs
    implementation(libs.findLibrary("hilt.viewmodel.compose").get())
    implementation(libs.findLibrary("androidx.activity.compose").get())

    implementation(libs.findLibrary("androidx.lifecycle.viewModelCompose").get())
    implementation(libs.findLibrary("androidx.lifecycle.runtimeCompose").get())

}
