extra.apply {
    set("coroutineVer", "1.7.3")
}

plugins {
    id("com.android.application") version "8.1.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
    id("org.jetbrains.dokka") version "1.9.10" apply false
    id("com.vanniktech.maven.publish") version "0.25.3" apply false
}

task<Delete>("clean") {
    delete = setOf(layout.buildDirectory)
}