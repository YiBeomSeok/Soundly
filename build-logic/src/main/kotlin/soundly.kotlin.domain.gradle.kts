import com.soundly.configureHiltKotlin
import com.soundly.libs

plugins {
    id("soundly.kotlin.library")
}

configureHiltKotlin()

dependencies {
    implementation(project(":domain:core"))

    val libs = project.extensions.libs
    implementation(libs.findLibrary("inject").get())
}
