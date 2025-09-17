import com.soundly.configureKotest
import com.soundly.configureKotlin

plugins {
    kotlin("jvm")
    id("soundly.verify.detekt")
}

configureKotlin()
configureKotest()
