plugins {
    // Versiones de los plugins (no se aplican aquí, solo se declaran)
    //id("com.android.application") version "8.13.0" apply false
    id("com.android.application") version "9.3.1" apply false

    id("org.jetbrains.kotlin.android") version "2.2.10" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
    id("com.google.gms.google-services") version "4.4.3" apply false
}

tasks.register<Delete>("clean") {
    delete(rootProject.buildDir)
}
