buildscript {
    repositories {
        google()
        jcenter()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:4.2.1")
        classpath(kotlin("gradle-plugin", version = "1.5.10"))
        classpath("org.jetbrains.dokka:dokka-gradle-plugin:1.4.32")
        classpath("com.vanniktech:gradle-maven-publish-plugin:0.15.1")
    }
}

allprojects {
    repositories {
        google()
        jcenter()
        mavenCentral()
    }
}

task<Delete>("clean") {
    delete = setOf(rootProject.buildDir)
}