import org.gradle.api.JavaVersion
import com.soundly.configureKotest
import com.soundly.configureKotlin

plugins {
    kotlin("jvm")
    id("soundly.verify.detekt")
}

// 실행 JDK와 관계없이 Java와 Kotlin의 타깃을 17로 맞춥니다.
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

configureKotlin()
configureKotest()
