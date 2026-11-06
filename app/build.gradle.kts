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

// Al publicar solo se toca esto: el versionCode se deriva del versionName, así que no hay forma
// de olvidarse de subirlo (Play rechaza reutilizar un versionCode) ni de que los dos se
// desincronicen. MAJOR.MINOR.PATCH -> MAJOR*10000 + MINOR*100 + PATCH, monótono si el semver lo es.
val habitlyVersionName = "1.0.4"
val habitlyVersionCode = habitlyVersionName.split(".").let { parts ->
    val numbers = parts.mapNotNull(String::toIntOrNull)
    require(numbers.size == 3 && numbers.all { it in 0..99 }) {
        "versionName debe ser MAJOR.MINOR.PATCH con partes de 0 a 99; es '$habitlyVersionName'"
    }
    numbers[0] * 10_000 + numbers[1] * 100 + numbers[2]
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
        versionCode = habitlyVersionCode
        versionName = habitlyVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Sin esto, AGP sube los .so nativos (LiteRT-LM, el motor del asistente) con los
        // símbolos eliminados, y un crash dentro del modelo llega a Play Console como
        // direcciones de memoria en vez de nombres de función. Los símbolos viajan en el
        // .aab pero NO se distribuyen a los usuarios: no engordan la descarga.
        ndk {
            debugSymbolLevel = "SYMBOL_TABLE"
        }
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
            // R8: encoge y ofusca. Las reglas -keep de proguard-rules.pro son por paquete a
            // propósito; lee la cabecera de ese fichero antes de tocar nada aquí.
            isMinifyEnabled = true
            isShrinkResources = true
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

    // The app changes language at runtime and ships every locale in the base module, so Play must
    // not split translations into language packs that may not be installed yet.
    bundle {
        language {
            enableSplit = false
        }
    }
}

// Historia: litertlm 0.14.0 se compiló con Kotlin 2.2 (-Xjvm-default=all) pero su POM pedía
// coroutines 1.9.0. Llamaba a SendChannel.close$default(...) como método ESTÁTICO SOBRE LA
// INTERFAZ, y en 1.9.0 y 1.10.2 ese método vive en SendChannel$DefaultImpls -> NoSuchMethodError
// al enviar cualquier mensaje. Solo coroutines >= 1.11.0 lo expone en la interfaz.
//
// Desde litertlm 0.19.0 su POM ya pide 1.11.0, así que el desajuste original está arreglado en
// origen y este force no es lo que nos salva. Se queda como suelo: androidx.navigationevent 1.0.0
// sigue pidiendo 1.9.0 por transitividad, y si algún día desaparecen los dos que piden 1.11.0
// (litertlm y coroutines-play-services) la resolución por versión más alta nos devolvería ahí sin
// avisar, y el síntoma sería de nuevo un NoSuchMethodError en tiempo de ejecución, no al compilar.
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
    // Guava llega sola, por io.grpc:grpc-* -> firebase-firestore, y la versión que
    // traía (32.1.3-android) está dentro del rango vulnerable de GHSA de
    // deserialización de CompactHashMap/CompactHashSet (<= 33.7.1).
    //
    // Es una constraint y no un force a propósito: solo sube el suelo. Si un día el
    // BOM de Firebase pide una guava más nueva, se la lleva sin que esto estorbe;
    // un force la frenaría. (El force de arriba es para coroutines, donde sí hace
    // falta clavar la versión por ABI.)
    constraints {
        implementation(libs.guava) {
            because("32.1.3-android es vulnerable; el parche está en 33.7.2")
        }
    }

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
    // App Check: cada variante trae SOLO su proveedor, porque el de depuración acepta un
    // secreto fijo y no debe viajar nunca en el APK publicado. La instalación vive en
    // AppCheckInstaller.kt, duplicado en src/release/java y src/debug/java.
    releaseImplementation(libs.firebase.appcheck.playintegrity)
    debugImplementation(libs.firebase.appcheck.debug)
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
