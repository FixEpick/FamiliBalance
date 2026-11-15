import java.io.File
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

// GitHub repo для проверки обновлений (owner/name). Замените на реальный.
// Читается на уровне скрипта: значение нужно и в defaultConfig, и в guard release-задач.
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

    // Ключи Supabase берутся из local.properties / gradle.properties,
    // чтобы не хранить их в исходном коде. Fallback — пустые строки.
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

    // key.properties в корне проекта — данные release-keystore (не коммитится).
    // Если файла нет — release подписывается debug-ключом, debug-сборки не затрагиваются.
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
        versionCode = 13
        versionName = "1.7.1"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
        buildConfigField("String", "GITHUB_REPO", "\"$githubRepo\"")

        // Только 64-bit ARM + 64-bit x86 (эмуляторы): отсекаем 32-bit x86 (~11 МБ .so).
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
                // Не используется: release-задачи падают раньше (см. guard ниже),
                // но signingConfig обязателен для DSL — указываем debug как заглушку.
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
}

// Запрет release-сборки без ключей: проверка на уровне задач,
// чтобы assembleDebug не падал (блок buildTypes выполняется при любой конфигурации).
// Путь вычисляем на этапе конфигурации: doFirst не должен захватывать
// Project (иначе configuration cache не может сериализовать задачу).
val keyPropertiesPath = rootProject.file("key.properties").absolutePath

tasks.matching {
    it.name.contains("Release", ignoreCase = true) &&
        (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package"))
}.configureEach {
    // Локальная копия: doFirst захватывает только String (сериализуемо для
    // configuration cache), а не ссылку на script object.
    val kpPath = keyPropertiesPath
    doFirst {
        // hasReleaseKey объявлен внутри android {} и здесь недоступен —
        // повторяем проверку key.properties напрямую.
        val kpFile = File(kpPath)
        val kp = Properties().apply {
            if (kpFile.exists()) kpFile.inputStream().use { load(it) }
        }
        val releaseKeyPresent = kpFile.exists() &&
            !kp.getProperty("storePassword").isNullOrBlank() &&
            !kp.getProperty("keyPassword").isNullOrBlank()
        if (!releaseKeyPresent) {
            throw GradleException(
                "Файл key.properties отсутствует! Сборка release запрещена. " +
                    "Создайте key.properties или используйте assembleDebug."
            )
        }
        // REL-02: дефолтный/пустой GITHUB_REPO ломает автообновление —
        // релиз с заглушкой опубликовать нельзя.
        if (githubRepo.isBlank() || githubRepo == "user/familibalance") {
            throw GradleException(
                "Укажите реальный GITHUB_REPO для работы автообновления " +
                    "(local.properties или -PGITHUB_REPO=owner/name)."
            )
        }
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
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
    // Unbundled ML Kit: модель OCR скачивается через Play Services — APK на ~35 МБ легче.
    // API идентичен bundled-варианту (com.google.mlkit.vision.text.*), импорты не меняются.
    implementation("com.google.android.gms:play-services-mlkit-text-recognition:19.0.1")
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
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
