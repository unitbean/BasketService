buildscript {
    repositories {
        maven {
            url = uri("https://plugins.gradle.org/m2/")
        }
    }
    dependencies {
        classpath("com.vanniktech:gradle-maven-publish-plugin:0.14.2")
    }
}

apply(plugin = "com.vanniktech.maven.publish")

plugins {
    id("com.android.library")
    id("org.jetbrains.dokka")
    kotlin("android")
}

repositories {
    mavenCentral()
}

tasks.dokkaJavadoc.configure {
    outputDirectory.set(buildDir.resolve("javadoc"))
}

android {
    compileSdkVersion(30)
    defaultConfig {
        minSdkVersion(16)
        targetSdkVersion(30)
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_1_8.toString()
    }
    buildFeatures {
        buildConfig = false
    }
}

dependencies {
    implementation(kotlin("stdlib-jdk7", org.jetbrains.kotlin.config.KotlinCompilerVersion.VERSION))
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.4.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.2")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.3.0")
}