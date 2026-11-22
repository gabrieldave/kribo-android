import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

/**
 * Release signing resolution order (BLACK EYE standard):
 * 1. CI environment variables (ANDROID_KEYSTORE_PATH, ANDROID_KEYSTORE_PASSWORD, ANDROID_KEY_ALIAS / ANDROID_KEYSTORE_ALIAS).
 * 2. Local fallback: ~/.blackeye-keys/android/keystore.env.
 *
 * No keystore material is ever committed. The keystore itself lives outside the repository.
 */
val blackEyeKeystoreEnv = Properties().apply {
    val envFile = File(System.getProperty("user.home"), ".blackeye-keys/android/keystore.env")
    if (envFile.isFile) {
        envFile.inputStream().use { load(it) }
    }
}

fun signingSecret(name: String): String? =
    System.getenv(name)?.takeIf { it.isNotBlank() }
        ?: blackEyeKeystoreEnv.getProperty(name)?.takeIf { it.isNotBlank() }

val releaseKeystorePath = signingSecret("ANDROID_KEYSTORE_PATH")
val releaseStorePassword = signingSecret("ANDROID_KEYSTORE_PASSWORD")
val releaseKeyAlias = signingSecret("ANDROID_KEY_ALIAS") ?: signingSecret("ANDROID_KEYSTORE_ALIAS")
val releaseKeyPassword = signingSecret("ANDROID_KEYSTORE_KEY_PASSWORD") ?: releaseStorePassword
val releaseKeystoreFile = releaseKeystorePath?.let(::File)
val hasReleaseSigning =
    releaseKeystoreFile?.isFile == true &&
        !releaseStorePassword.isNullOrBlank() &&
        !releaseKeyAlias.isNullOrBlank()

android {
    namespace = "com.blackeye.kribo"
    compileSdk = 36
    ndkVersion = "27.0.12077973"

    defaultConfig {
        applicationId = "com.blackeye.kribo"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "1.0.0"

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = releaseKeystoreFile
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            isDebuggable = false
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            ndk {
                debugSymbolLevel = "SYMBOL_TABLE"
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    androidResources {
        generateLocaleConfig = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = false
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

ksp {
    // Room schema snapshots are versioned so future migrations are reviewable.
    arg("room.schemaLocation", "$projectDir/schemas")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.billing.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}

if (!hasReleaseSigning) {
    logger.warn(
        "Kribo: release signing material not found. Set ANDROID_KEYSTORE_PATH / ANDROID_KEYSTORE_PASSWORD / " +
            "ANDROID_KEY_ALIAS (or ANDROID_KEYSTORE_ALIAS), or provide ~/.blackeye-keys/android/keystore.env.",
    )
}
