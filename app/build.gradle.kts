import java.io.FileInputStream
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.ksp)
    alias(libs.plugins.google.services)
}

// Firma del release: lee las credenciales de keystore.properties (fuera de git). Sin ese
// fichero, el release compila SIN firmar (útil en CI); con él, se firma para subir a Play.
// Crea el keystore con `keytool` y rellena keystore.properties a partir del .example.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        FileInputStream(keystorePropertiesFile).use { load(it) }
    }
}

android {
    namespace = "com.monsteraltech.habitly"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.monsteraltech.habitly"
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (keystorePropertiesFile.exists()) {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Solo firma si existe keystore.properties; si no, el release sale sin firmar.
            if (keystorePropertiesFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        // BuildConfig.DEBUG controla las métricas dev del chat (TTFT, chunks/s).
        buildConfig = true
    }

    testOptions {
        // android.util.Log como no-op en tests JVM en vez de "not mocked": sin esto, el
        // primer Log.d dentro de un try lanzaba, el catch del ViewModel se lo tragaba y los
        // tests dejaban de cubrir en silencio todo lo posterior a ese log.
        unitTests.isReturnDefaultValues = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// litertlm 0.14.0 se compiló con Kotlin 2.2 (-Xjvm-default=all), así que llama a
// SendChannel.close$default(...) como método ESTÁTICO SOBRE LA INTERFAZ. En coroutines 1.9.0 y
// 1.10.2 ese método vive en SendChannel$DefaultImpls, no en la interfaz -> NoSuchMethodError al
// enviar cualquier mensaje. Solo coroutines >= 1.11.0 (compilado con Kotlin 2.2+) lo expone en la
// interfaz. Forzamos 1.11.0 en todo el grafo para que coincida con el ABI que litertlm espera.
configurations.all {
    resolutionStrategy {
        force(
            "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0",
            "org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.11.0"
        )
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui.text.google.fonts)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    
    // Firebase Dependencies
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.coroutines.play.services)
    implementation(libs.datastore.preferences)

    // Hilt
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)

    // Hilt Testing
    testImplementation(libs.hilt.testing)
    kspTest(libs.hilt.compiler)

    // Coroutines Testing
    testImplementation(libs.coroutines.test)

    // Credential Manager (Google Sign-In)
    implementation(libs.credentials)
    implementation(libs.credentials.play.services)
    implementation(libs.googleid)

    // Navigation
    implementation(libs.navigation.compose)
    implementation(libs.hilt.navigation.compose)

    // LiteRT-LM - Local AI inference (Gemma on-device)
    implementation(libs.litertlm.android)

    // Room Database
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.gson)
    implementation(libs.okhttp)
    implementation(libs.compose.markdown)

    // WorkManager
    implementation(libs.work.runtime.ktx)
    testImplementation(libs.work.testing)

    // Glance (home screen widget)
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)
}
