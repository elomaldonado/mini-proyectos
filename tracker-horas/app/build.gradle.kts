plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val buildNumber = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.elo.trackerhoras"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.elo.trackerhoras"
        minSdk = 29
        targetSdk = 34
        versionCode = buildNumber
        versionName = "1.0.$buildNumber"
    }

    // Llave fija para que cada versión nueva se instale encima de la anterior
    // sin perder los datos. Es solo para instalar la app a mano (sideload).
    signingConfigs {
        create("tracker") {
            storeFile = file("tracker.keystore")
            storePassword = "trackerhoras"
            keyAlias = "tracker"
            keyPassword = "trackerhoras"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("tracker")
        }
        debug {
            signingConfig = signingConfigs.getByName("tracker")
        }
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}
