import java.net.URI
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

val rawApiBaseUrlProp: String = (project.findProperty("apiBaseUrl") as? String)
    ?.trim()
    ?.trimEnd('/')
    ?: ""

val apiBaseUrlProp: String = rawApiBaseUrlProp.replace(
    Regex("^https://", RegexOption.IGNORE_CASE),
    "https://"
)

val debugApiBaseUrl: String = apiBaseUrlProp.ifBlank { "http://10.0.2.2:8080" }

fun escapeBuildConfigString(value: String): String =
    value.replace("\\", "\\\\").replace("\"", "\\\"")

fun validateReleaseApiBaseUrl(url: String) {
    if (url.isBlank()) {
        throw GradleException(
            "ОШИБКА РЕЛИЗНОЙ СБОРКИ: Параметр -PapiBaseUrl не задан. " +
                "Укажите HTTPS-адрес сервера, например: ./gradlew assembleRelease -PapiBaseUrl=https://api.example.com"
        )
    }
    if (url.any { it.isWhitespace() }) {
        throw GradleException("ОШИБКА РЕЛИЗНОЙ СБОРКИ: -PapiBaseUrl содержит недопустимые пробельные символы: '$url'")
    }
    val uri = try {
        URI(url)
    } catch (e: Exception) {
        throw GradleException("ОШИБКА РЕЛИЗНОЙ СБОРКИ: Некорректный URL в -PapiBaseUrl='$url': ${e.message}")
    }
    if (!uri.scheme.equals("https", ignoreCase = true) || uri.host.isNullOrBlank()) {
        throw GradleException(
            "ОШИБКА РЕЛИЗНОЙ СБОРКИ: -PapiBaseUrl должен использовать https:// и корректный хост (получено '$url')."
        )
    }
}

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
    val rawFile = File(rawPath)
    if (rawFile.isAbsolute) {
        return rawFile.takeIf { it.exists() }?.canonicalFile
    }
    val candidates = listOfNotNull(
        keystorePropsFile?.parentFile?.let { File(it, rawPath) },
        rootProject.file(rawPath),
        rootProject.file("../$rawPath"),
        project.file(rawPath),
        rawFile.absoluteFile
    )
    return candidates.firstOrNull { it.exists() }?.canonicalFile
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
        minSdk = 28
        targetSdk = 35
        versionCode = 2
        versionName = "1.0.0"

        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${escapeBuildConfigString(googleWebClientId)}\"")
        buildConfigField("String", "API_BASE_URL", "\"${escapeBuildConfigString(apiBaseUrlProp)}\"")
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
            buildConfigField("String", "API_BASE_URL", "\"${escapeBuildConfigString(apiBaseUrlProp)}\"")
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
        create("staging") {
            initWith(getByName("release"))
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
        }
        debug {
            isMinifyEnabled = false
            buildConfigField("String", "API_BASE_URL", "\"${escapeBuildConfigString(debugApiBaseUrl)}\"")
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
    implementation(libs.androidx.work.runtime.ktx)

    testImplementation(kotlin("test"))
    debugImplementation(libs.androidx.ui.tooling)
}

gradle.taskGraph.whenReady {
    val buildingReleasePackage = allTasks.any { task ->
        task.project == project &&
            task.name.contains("Release") &&
            (task.name.startsWith("assemble") || task.name.startsWith("bundle") || task.name.startsWith("package"))
    }
    if (buildingReleasePackage) {
        if (googleWebClientId.isBlank()) {
            throw GradleException("ОШИБКА РЕЛИЗНОЙ СБОРКИ: GOOGLE_WEB_CLIENT_ID не задан в local.properties или переменных окружения. Укажите GOOGLE_WEB_CLIENT_ID=<id> для работы Google-входа в релизной сборке.")
        }
        validateReleaseApiBaseUrl(apiBaseUrlProp)
    }
}

tasks.matching {
    it.name.contains("Release") && (it.name.startsWith("assemble") || it.name.startsWith("bundle") || it.name.startsWith("package"))
}.configureEach {
    doFirst {
        if (googleWebClientId.isBlank()) {
            throw GradleException("ОШИБКА РЕЛИЗНОЙ СБОРКИ: GOOGLE_WEB_CLIENT_ID не задан в local.properties или переменных окружения. Укажите GOOGLE_WEB_CLIENT_ID=<id> для работы Google-входа в релизной сборке.")
        }
        validateReleaseApiBaseUrl(apiBaseUrlProp)
    }
}
