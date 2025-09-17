import com.soundly.configureCoroutineAndroid
import com.soundly.configureHiltAndroid
import com.soundly.configureKotest
import com.soundly.configureKotlinAndroid

plugins {
    id("com.android.library")
    id("soundly.verify.detekt")
}

configureKotlinAndroid()
configureKotest()
configureCoroutineAndroid()
configureHiltAndroid()
