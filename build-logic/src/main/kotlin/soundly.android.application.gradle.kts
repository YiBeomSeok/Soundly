import com.soundly.Configuration
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
        targetSdk = Configuration.targetSdk
        versionCode = Configuration.versionCode
        versionName = Configuration.versionName
    }
}

dependencies {
    val libs = project.extensions.libs
    implementation(libs.findLibrary("androidx.activity.compose").get())

    implementation(libs.findLibrary("androidx.lifecycle.viewModelCompose").get())
    implementation(libs.findLibrary("androidx.lifecycle.runtimeCompose").get())
}
