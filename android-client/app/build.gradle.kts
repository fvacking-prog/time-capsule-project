import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val localProps = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) {
        file.inputStream().use { load(it) }
    }
}
val googleWebClientId: String = (
    localProps.getProperty("GOOGLE_WEB_CLIENT_ID")
        ?: System.getenv("GOOGLE_WEB_CLIENT_ID")
        ?: ""
).trim()

// Чтение настроек подписи релиза из keystore.properties (если файл существует)
val keystorePropsCandidates = listOf(
    rootProject.file("keystore.properties"),
    rootProject.file("../keystore.properties"),
    project.file("keystore.properties")
)
val keystorePropsFile = keystorePropsCandidates.firstOrNull { it.exists() }
val keystoreProps = Properties().apply {
    if (keystorePropsFile != null) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}

fun resolveKeystoreFile(rawPath: String?): File? {
    if (rawPath.isNullOrBlank()) return null
    val direct = File(rawPath)
    if (direct.exists()) return direct
    val relRoot = rootProject.file(rawPath)
    if (relRoot.exists()) return relRoot
    val relParent = rootProject.file("../$rawPath")
    if (relParent.exists()) return relParent
    val relProject = project.file(rawPath)
    if (relProject.exists()) return relProject
    return null
}

val releaseStoreFile = resolveKeystoreFile(keystoreProps.getProperty("storeFile"))
val hasReleaseSigning = releaseStoreFile != null &&
    !keystoreProps.getProperty("storePassword").isNullOrBlank() &&
    !keystoreProps.getProperty("keyAlias").isNullOrBlank() &&
    !keystoreProps.getProperty("keyPassword").isNullOrBlank()

android {
    namespace = "com.example.cyberpunk"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.cyberpunk"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
        // Флаг Demo Mode: по умолчанию включён для защиты проекта. Для боевого релиза установите false.
        buildConfigField("Boolean", "ENABLE_DEMO", "true")
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = releaseStoreFile
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            } else {
                signingConfig = null
                println("ВНИМАНИЕ: keystore.properties не найден или releaseStoreFile отсутствует. Сборка release будет неподписанной.")
            }
        }
        debug {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.kotlinx.serialization.json)

    implementation(libs.play.services.location)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.play.services)

    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coil.compose)

    testImplementation(kotlin("test"))
    debugImplementation(libs.androidx.ui.tooling)
}
