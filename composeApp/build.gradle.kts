import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.multiplatform")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.compose")
}

val appVersion = providers.gradleProperty("appVersion").getOrElse("1.0.0")

kotlin {
    compilerOptions { freeCompilerArgs.add("-Xexpect-actual-classes") }
    androidTarget {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }
    jvm("desktop") {
        compilerOptions { jvmTarget.set(JvmTarget.JVM_17) }
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("org.jetbrains.compose.runtime:runtime:1.10.3")
                implementation("org.jetbrains.compose.foundation:foundation:1.10.3")
                implementation("org.jetbrains.compose.material3:material3:1.9.0")
                implementation("org.jetbrains.compose.ui:ui:1.10.3")
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.10.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
            }
        }
        val commonTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
        // Both targets are JVMs: networking, XML and files are written once here.
        val jvmShared by creating { dependsOn(commonMain) }
        val androidMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation("androidx.activity:activity-compose:1.11.0")
                implementation("androidx.work:work-runtime-ktx:2.12.0")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
            }
        }
        val desktopMain by getting {
            dependsOn(jvmShared)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.11.0")
            }
        }
        val desktopTest by getting {
            dependencies { implementation(kotlin("test")) }
        }
    }
}

android {
    namespace = "app.morningreport"
    compileSdk = 36

    defaultConfig {
        applicationId = "app.morningreport"
        minSdk = 26
        targetSdk = 36
        versionCode = providers.gradleProperty("appVersionCode").getOrElse("1").toInt()
        versionName = appVersion
    }

    // Release signing comes from the environment (CI secrets or a local keystore); without it the APK is debug-signed.
    val ks = System.getenv("ANDROID_KEYSTORE")
    signingConfigs {
        if (ks != null) create("release") {
            storeFile = file(ks)
            storePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("ANDROID_KEY_ALIAS")
            keyPassword = System.getenv("ANDROID_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = if (ks != null) signingConfigs.getByName("release") else signingConfigs.getByName("debug")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

compose.desktop {
    application {
        mainClass = "app.morningreport.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Morning Report"
            packageVersion = appVersion
            description = "Your own morning feed: news sites and YouTube channels, grouped the way you want."
            vendor = "Morning Report"
            modules("java.net.http", "java.xml")
            macOS {
                bundleID = "app.morningreport"
                iconFile.set(project.file("icons/app.icns"))
            }
            windows {
                iconFile.set(project.file("icons/app.ico"))
                menuGroup = "Morning Report"
                shortcut = true
                dirChooser = true
                // Stable across versions so an installer upgrades the old install instead of adding a second one.
                upgradeUuid = "6f1d2c8e-4b7a-4f3e-9c55-2a8d7e1b9f40"
            }
            linux {
                iconFile.set(project.file("icons/app.png"))
            }
        }
    }
}
