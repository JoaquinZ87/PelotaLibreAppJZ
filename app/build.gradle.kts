import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// Firma de release: credenciales desde keystore.properties (fuera de git).
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) load(FileInputStream(keystorePropsFile))
}

// Compilar FUERA de Google Drive: Drive bloquea/sincroniza los archivos de build/ y rompe el
// merge de recursos y el clean. Redirigimos la salida a una carpeta local rápida.
layout.buildDirectory.set(file("C:/tmp/pelotalibretv-build"))

android {
    namespace = "com.jz.pelotalibretv"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jz.pelotalibretv"
        minSdk = 23
        targetSdk = 35
        versionCode = 3
        versionName = "0.3"
    }

    signingConfigs {
        create("release") {
            if (keystorePropsFile.exists()) {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }
}

// DSL nueva de Kotlin (reemplaza a kotlinOptions, que quedó deprecada en AGP 9+).
kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)

    // Compose (versiones gestionadas por el BOM)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.activity.compose)

    // Compose for TV (UI de 10 pies, foco por D-pad)
    implementation(libs.androidx.tv.material)

    // Lifecycle / ViewModel
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Async
    implementation(libs.kotlinx.coroutines.android)

    // Red + scraping (se usan a partir del M1)
    implementation(libs.okhttp)
    implementation(libs.jsoup)

    // Carga de imágenes (logos de canales)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
