plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val releaseKeystorePath = providers.environmentVariable("RELEASE_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("RELEASE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("RELEASE_KEY_PASSWORD").orNull
val allowDebugReleaseSigning = providers.gradleProperty("allowDebugReleaseSigning").orNull == "true" ||
    providers.environmentVariable("ALLOW_DEBUG_RELEASE_SIGNING").orNull == "true"
android {
    namespace = "com.cva.duoscreen"
    compileSdk = 34

    signingConfigs {
        create("release") {
            if (releaseKeystorePath != null && releaseKeystorePassword != null &&
                releaseKeyAlias != null && releaseKeyPassword != null) {
                storeFile = file(releaseKeystorePath)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                this.keyPassword = releaseKeyPassword
            }
        }
    }

    defaultConfig {
        applicationId = "com.cva.duoscreen"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = when {
                releaseKeystorePath != null && releaseKeystorePassword != null &&
                    releaseKeyAlias != null && releaseKeyPassword != null -> signingConfigs.getByName("release")
                allowDebugReleaseSigning -> signingConfigs.getByName("debug")
                else -> null
            }
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.car.app:app:1.4.0")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")
}
