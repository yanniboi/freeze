import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

// CI passes these so the APK identifies itself as the tag/commit it was built from.
val freezeVersionName = (findProperty("freezeVersionName") as String?) ?: "0.1.0"
val freezeVersionCode = (findProperty("freezeVersionCode") as String?)?.toInt() ?: 1

// Optional release signing: create keystore.properties (git-ignored) next to settings.gradle.kts with
// storeFile, storePassword, keyAlias and keyPassword. Without it, release builds are unsigned.
val keystorePropertiesFile = rootProject.file("keystore.properties")

android {
    namespace = "io.github.yanniboi.freeze"
    compileSdk = 37

    defaultConfig {
        applicationId = "io.github.yanniboi.freeze"
        minSdk = 26
        targetSdk = 37
        versionCode = freezeVersionCode
        versionName = freezeVersionName
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            val props = Properties().apply { keystorePropertiesFile.inputStream().use(::load) }
            create("release") {
                storeFile = rootProject.file(props.getProperty("storeFile"))
                storePassword = props.getProperty("storePassword")
                keyAlias = props.getProperty("keyAlias")
                keyPassword = props.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    testImplementation(libs.junit)
}
