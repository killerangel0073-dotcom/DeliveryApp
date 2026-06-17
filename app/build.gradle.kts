plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
    id("kotlin-parcelize")   // 👈 así, con paréntesis y comillas
    kotlin("kapt") // necesario para Room
}

android {
    namespace = "com.gruposanangel.delivery"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.gruposanangel.delivery"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = "1.6.0"
    }

    kapt {
        correctErrorTypes = true
    }
}

dependencies {

    // Firebase BOM
    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))


    // Dependencias sin versión
    implementation("com.google.firebase:firebase-auth-ktx")
    implementation("com.google.firebase:firebase-firestore-ktx")
    implementation("com.google.firebase:firebase-database-ktx") // 👈 Realtime Database para Live Tracking
    implementation("com.google.firebase:firebase-storage-ktx")
    implementation("com.google.firebase:firebase-functions-ktx")
    implementation("com.google.firebase:firebase-messaging-ktx") // 👈 FCM



    implementation("com.google.firebase:firebase-appcheck-debug")
    implementation("com.google.firebase:firebase-appcheck-playintegrity")







    // AndroidX + Material3
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.compose.material3:material3:1.2.0")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")

    // Coil
    implementation("io.coil-kt:coil-compose:2.5.0")

    implementation("androidx.compose.animation:animation:1.5.1")
    implementation("androidx.compose.ui:ui-text:1.5.1")
    implementation("androidx.navigation:navigation-compose:2.7.3")
    implementation("androidx.compose.foundation:foundation:1.5.1")



    // WorkManager
    implementation("androidx.work:work-runtime-ktx:2.8.1")


    // Permite usar collectAsStateWithLifecycle en la UI.
    // Optimiza el velocímetro para que deje de procesar datos cuando la app está en segundo plano,
    // ahorrando batería y evitando que el teléfono se caliente.
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")



    // Tests
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.6.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    // Mapas
    implementation("com.google.android.gms:play-services-maps:18.1.0")
    implementation("com.google.maps.android:maps-compose:6.1.0")
    implementation("com.google.android.gms:play-services-location:21.0.1")

    // 🔥 Vertex AI para Firebase
    implementation("com.google.firebase:firebase-vertexai")

    // Room
    implementation("androidx.room:room-runtime:2.8.4")
    kapt("androidx.room:room-compiler:2.8.4")
    implementation("androidx.room:room-ktx:2.8.4")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.6.4")


    //barra superior
    implementation("com.google.accompanist:accompanist-systemuicontroller:0.32.0")

    //ticket
    implementation("androidx.core:core-ktx:1.12.0")

    // QR Code Generation (ZXing)
    implementation("com.google.zxing:core:3.5.3")

    // 🔥 CameraX (Para Escaneo de Licencias con Guía)
    val cameraxVersion = "1.4.0"
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("com.google.guava:guava:31.1-android")
}
