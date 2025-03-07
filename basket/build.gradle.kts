import com.vanniktech.maven.publish.SonatypeHost

plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.maven.publish)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.dokka)
}

dokka {
    dokkaPublications.html {
        outputDirectory.set(layout.buildDirectory.dir("javadoc"))
    }
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.S01)
    signAllPublications()
}

android {
    namespace = "com.ub.basket"
    compileSdk = 35
    defaultConfig.minSdk = 16
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
            packaging.resources.excludes += "DebugProbesKt.bin"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }
}

dependencies {
    dokkaPlugin(libs.android.documentation.plugin)
    implementation(libs.kotlin)
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(libs.junit)
}