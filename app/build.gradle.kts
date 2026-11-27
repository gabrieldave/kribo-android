import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties
import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
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
        versionCode = 22
        versionName = "1.0.21"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        vectorDrawables {
            useSupportLibrary = true
        }

        ndk {
            // ONNX Runtime is shipped for the two production ABIs only.
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }
    }

    signingConfigs {
        create("release") {
            enableV2Signing = true
            enableV3Signing = true
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
            // Parallel install ("Kribo Debug") that coexists with the release app.
            resValue("string", "app_name", "Kribo Debug")
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
        jniLibs {
            // Both onnxruntime-android (full 1.23.2 opset) and moonshine-voice (stripped 1.23.2)
            // bundle libonnxruntime.so; the post-merge hook below guarantees Microsoft's full
            // libonnxruntime.so (required by PP-OCRv5, UVDoc and Moonshine) is always packaged.
            pickFirsts += listOf("lib/*/libonnxruntime.so")
        }
    }

    androidResources {
        generateLocaleConfig = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
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
    implementation(libs.coil.compose)
    implementation(libs.zxing.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.onnxruntime.android)
    implementation(libs.moonshine.voice) {
        exclude(group = "com.google.android.material", module = "material")
    }
    implementation(libs.mlkit.document.scanner)
    implementation(libs.mlkit.text.recognition)
    implementation(libs.play.services.auth)
    implementation(libs.androidx.work.runtime.ktx)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(platform(libs.androidx.compose.bom))
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.room.testing)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}

if (!hasReleaseSigning) {
    logger.warn(
        "Kribo: release signing material not found. Set ANDROID_KEYSTORE_PATH / ANDROID_KEYSTORE_PASSWORD / " +
            "ANDROID_KEY_ALIAS (or ANDROID_KEYSTORE_ALIAS), or provide ~/.blackeye-keys/android/keystore.env.",
    )
}

// Deterministic native library guard: ensure Microsoft's full onnxruntime-android 1.23.2
// libonnxruntime.so (never moonshine-voice's stripped copy) is packaged in every variant so
// PP-OCRv5, UVDoc and Moonshine share the exact same 16 KB-aligned VERS_1.23.2 runtime.
afterEvaluate {
    listOf("Debug", "Release").forEach { variant ->
        val mergeTask = tasks.findByName("merge${variant}NativeLibs") ?: return@forEach
        val runtimeClasspath = configurations.findByName("${variant.lowercase()}RuntimeClasspath") ?: return@forEach
        val ortAarFiles = runtimeClasspath.incoming.artifactView {
            componentFilter { id ->
                id is org.gradle.api.artifacts.component.ModuleComponentIdentifier &&
                    id.group == "com.microsoft.onnxruntime" &&
                    id.module == "onnxruntime-android"
            }
        }.files
        val mergedRootDir = layout.buildDirectory
            .dir("intermediates/merged_native_libs/${variant.lowercase()}/merge${variant}NativeLibs/out/lib")
            .get()
            .asFile
        mergeTask.inputs.files(ortAarFiles)
        mergeTask.doLast {
            val ortAar = ortAarFiles.singleFile
            ZipFile(ortAar).use { zip ->
                listOf("arm64-v8a", "armeabi-v7a").forEach { abi ->
                    val entry = zip.getEntry("jni/$abi/libonnxruntime.so")
                        ?: error("Kribo native guard: missing jni/$abi/libonnxruntime.so in ${ortAar.name}")
                    val target = File(mergedRootDir, "$abi/libonnxruntime.so")
                    if (target.isFile && target.length() != entry.size) {
                        zip.getInputStream(entry).use { input ->
                            target.outputStream().use { output -> input.copyTo(output) }
                        }
                    }
                    check(target.isFile && target.length() == entry.size) {
                        "Kribo native guard: expected full libonnxruntime.so (${entry.size} B) for $abi, got ${target.length()} B"
                    }
                }
            }
        }
    }
}
