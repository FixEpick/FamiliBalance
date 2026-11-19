import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// GitHub repo РґР»СЏ РїСЂРѕРІРµСЂРєРё РѕР±РЅРѕРІР»РµРЅРёР№ (owner/name). Р—Р°РјРµРЅРёС‚Рµ РЅР° СЂРµР°Р»СЊРЅС‹Р№.
// Р§РёС‚Р°РµС‚СЃСЏ РЅР° СѓСЂРѕРІРЅРµ СЃРєСЂРёРїС‚Р°: Р·РЅР°С‡РµРЅРёРµ РЅСѓР¶РЅРѕ Рё РІ defaultConfig, Рё РІ guard release-Р·Р°РґР°С‡.
val githubRepoProps = Properties().apply {
    val localPropsFile = rootProject.file("local.properties")
    if (localPropsFile.exists()) {
        localPropsFile.inputStream().use { load(it) }
    }
}
val githubRepo = githubRepoProps.getProperty("GITHUB_REPO")
    ?: (project.findProperty("GITHUB_REPO") as? String)
    ?: "user/familibalance"

android {
    namespace = "com.example.familibalance"
    compileSdk = 37

    // РљР»СЋС‡Рё Supabase Р±РµСЂСѓС‚СЃСЏ РёР· local.properties / gradle.properties,
    // С‡С‚РѕР±С‹ РЅРµ С…СЂР°РЅРёС‚СЊ РёС… РІ РёСЃС…РѕРґРЅРѕРј РєРѕРґРµ. Fallback вЂ” РїСѓСЃС‚С‹Рµ СЃС‚СЂРѕРєРё.
    val localProps = Properties().apply {
        val localPropsFile = rootProject.file("local.properties")
        if (localPropsFile.exists()) {
            localPropsFile.inputStream().use { load(it) }
        }
    }
    val supabaseUrl = localProps.getProperty("SUPABASE_URL")
        ?: (project.findProperty("SUPABASE_URL") as? String)
        ?: ""
    val supabaseAnonKey = localProps.getProperty("SUPABASE_ANON_KEY")
        ?: (project.findProperty("SUPABASE_ANON_KEY") as? String)
        ?: ""

    // key.properties РІ РєРѕСЂРЅРµ РїСЂРѕРµРєС‚Р° вЂ” РґР°РЅРЅС‹Рµ release-keystore (РЅРµ РєРѕРјРјРёС‚РёС‚СЃСЏ).
    // Р•СЃР»Рё С„Р°Р№Р»Р° РЅРµС‚ вЂ” release РїРѕРґРїРёСЃС‹РІР°РµС‚СЃСЏ debug-РєР»СЋС‡РѕРј, debug-СЃР±РѕСЂРєРё РЅРµ Р·Р°С‚СЂР°РіРёРІР°СЋС‚СЃСЏ.
    val keyPropsFile = rootProject.file("key.properties")
    val keyProps = Properties().apply {
        if (keyPropsFile.exists()) {
            keyPropsFile.inputStream().use { load(it) }
        }
    }
    val hasReleaseKey = keyPropsFile.exists() &&
        !keyProps.getProperty("storePassword").isNullOrBlank() &&
        !keyProps.getProperty("keyPassword").isNullOrBlank()

    defaultConfig {
        applicationId = "com.example.familibalance"
        minSdk = 24
        targetSdk = 37
        versionCode = 18
        versionName = "1.9.5"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")

        // РўРѕР»СЊРєРѕ 64-bit ARM + 64-bit x86 (СЌРјСѓР»СЏС‚РѕСЂС‹): РѕС‚СЃРµРєР°РµРј 32-bit x86 (~11 РњР‘ .so).
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
    }

    signingConfigs {
        if (hasReleaseKey) {
            create("release") {
                storeFile = rootProject.file(keyProps.getProperty("storeFile") ?: "")
                storePassword = keyProps.getProperty("storePassword")
                keyAlias = keyProps.getProperty("keyAlias")
                keyPassword = keyProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = if (hasReleaseKey) {
                signingConfigs.getByName("release")
            } else {
                // РќРµ РёСЃРїРѕР»СЊР·СѓРµС‚СЃСЏ: release-Р·Р°РґР°С‡Рё РїР°РґР°СЋС‚ СЂР°РЅСЊС€Рµ (СЃРј. guard РЅРёР¶Рµ),
                // РЅРѕ signingConfig РѕР±СЏР·Р°С‚РµР»РµРЅ РґР»СЏ DSL вЂ” СѓРєР°Р·С‹РІР°РµРј debug РєР°Рє Р·Р°РіР»СѓС€РєСѓ.
                signingConfigs.getByName("debug")
            }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
        isCoreLibraryDesugaringEnabled = true
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        // Robolectric-С‚РµСЃС‚С‹ С‡РёС‚Р°СЋС‚ string-СЂРµСЃСѓСЂСЃС‹ (РїСЂРѕРІРµСЂРєР° Р»РѕРєР°Р»РёР·Р°С†РёРё).
        unitTests.isIncludeAndroidResources = true
    }
}

// Р—Р°РїСЂРµС‚ release-СЃР±РѕСЂРєРё Р±РµР· РєР»СЋС‡РµР№: РїСЂРѕРІРµСЂРєР° РЅР° СѓСЂРѕРІРЅРµ Р·Р°РґР°С‡,
// С‡С‚РѕР±С‹ assembleDebug РЅРµ РїР°РґР°Р» (Р±Р»РѕРє buildTypes РІС‹РїРѕР»РЅСЏРµС‚СЃСЏ РїСЂРё Р»СЋР±РѕР№ РєРѕРЅС„РёРіСѓСЂР°С†РёРё).
// РџСѓС‚СЊ РІС‹С‡РёСЃР»СЏРµРј РЅР° СЌС‚Р°РїРµ РєРѕРЅС„РёРіСѓСЂР°С†РёРё: doFirst РЅРµ РґРѕР»Р¶РµРЅ Р·Р°С…РІР°С‚С‹РІР°С‚СЊ
// Project (РёРЅР°С‡Рµ configuration cache РЅРµ РјРѕР¶РµС‚ СЃРµСЂРёР°Р»РёР·РѕРІР°С‚СЊ Р·Р°РґР°С‡Сѓ).
val keyPropertiesPath = rootProject.file("key.properties").absolutePath

// Release guard lives in a top-level function: the doFirst lambda must not
// capture the Gradle script object, otherwise the configuration cache cannot
// serialize it (fails on assembleRelease/packageRelease* tasks).
fun Task.guardReleaseBuild(kpPath: String, repo: String) {
    doFirst {
        val kpFile = File(kpPath)
        val kp = Properties().apply {
            if (kpFile.exists()) kpFile.inputStream().use { load(it) }
        }
        val releaseKeyPresent = kpFile.exists() &&
            !kp.getProperty("storePassword").isNullOrBlank() &&
            !kp.getProperty("keyPassword").isNullOrBlank()
        if (!releaseKeyPresent) {
            throw GradleException(
                "key.properties is missing! Release build is forbidden. " +
                    "Create key.properties or use assembleDebug."
            )
        }
        if (repo.isBlank() || repo == "user/familibalance") {
            throw GradleException(
                "Set a real GITHUB_REPO for update checks " +
                    "(local.properties or -PGITHUB_REPO=owner/name)."
            )
        }
    }
}

tasks.matching {
    it.name.contains("Release", ignoreCase = true) &&
        (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package"))
}.configureEach {
    guardReleaseBuild(keyPropertiesPath, githubRepo)
}
dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")

    val supabaseVersion = "3.1.4"
    val ktorVersion = "3.3.1"
    implementation(platform("io.github.jan-tennert.supabase:bom:$supabaseVersion"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")
    implementation("io.github.jan-tennert.supabase:storage-kt")
    implementation("io.ktor:ktor-client-okhttp:$ktorVersion")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    // Unbundled ML Kit: РјРѕРґРµР»СЊ OCR СЃРєР°С‡РёРІР°РµС‚СЃСЏ С‡РµСЂРµР· Play Services вЂ” APK РЅР° ~35 РњР‘ Р»РµРіС‡Рµ.
    // API РёРґРµРЅС‚РёС‡РµРЅ bundled-РІР°СЂРёР°РЅС‚Сѓ (com.google.mlkit.vision.text.*), РёРјРїРѕСЂС‚С‹ РЅРµ РјРµРЅСЏСЋС‚СЃСЏ.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.media3:media3-exoplayer:1.5.1")
    implementation("androidx.media3:media3-ui:1.5.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.view)
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    testImplementation(libs.junit)
    testImplementation("io.mockk:mockk:1.13.17")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("androidx.arch.core:core-testing:2.2.0")
    testImplementation("org.robolectric:robolectric:4.15.1")
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
