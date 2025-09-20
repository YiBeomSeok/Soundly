import com.soundly.configureHiltKotlin
import com.soundly.libs

plugins {
    id("soundly.kotlin.library")
}

configureHiltKotlin()

dependencies {
    val libs = project.extensions.libs
    implementation(libs.findLibrary("inject").get())
    implementation(libs.findLibrary("coroutines.core").get())
}
