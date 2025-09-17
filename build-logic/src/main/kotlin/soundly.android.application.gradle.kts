import com.soundly.configureHiltAndroid
import com.soundly.configureKotestAndroid
import com.soundly.configureKotlinAndroid
import com.soundly.libs

plugins {
    id("com.android.application")
    id("soundly.android.compose")
}

configureKotlinAndroid()
configureHiltAndroid()
configureKotestAndroid()

android {
    namespace = "com.soundly"

    defaultConfig {
        applicationId = "com.soundly"
        versionCode = 1
        versionName = "1.3.0"
    }
}

dependencies {
    val libs = project.extensions.libs
    implementation(libs.findLibrary("androidx.compose.navigation").get())

    implementation(libs.findLibrary("androidx.lifecycle.viewModelCompose").get())
    implementation(libs.findLibrary("androidx.lifecycle.runtimeCompose").get())
}
