import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localProperties.load(localPropertiesFile.inputStream())
}

val supabaseUrl = localProperties.getProperty("SUPABASE_URL", "https://ouddhqorzshmuumywxhl.supabase.co")
val supabaseAnonKey = localProperties.getProperty("SUPABASE_ANON_KEY", "sb_publishable_ORH7iwG_EtWxES-wZtcmsA_1WmLYaAZ")
val mapsApiKey = localProperties.getProperty("MAPS_API_KEY", "")

android {
    namespace = "com.example.mediq"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.mediq"
        minSdk = 25
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "SUPABASE_URL", "\"$supabaseUrl\"")
        buildConfigField("String", "SUPABASE_ANON_KEY", "\"$supabaseAnonKey\"")
    }

    buildFeatures {
        buildConfig = true
    }

    buildTypes {
        release {
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.activity.ktx)
    implementation(libs.appcompat)
    implementation(libs.constraintlayout)
    implementation(libs.material)
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    implementation(libs.recyclerview)
    implementation(libs.cardview)
    implementation(libs.swiperefreshlayout)

    // Networking & Serialization
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.gson)

    // Image loading
    implementation(libs.glide)

    // Location Services for device GPS
    implementation(libs.play.services.location)

    // OpenStreetMap & osmdroid (No API keys or billing required)
    implementation(libs.osmdroid.android)

    // Encrypted Storage
    implementation(libs.security.crypto)

    testImplementation(libs.junit)
    androidTestImplementation(libs.espresso.core)
    androidTestImplementation(libs.ext.junit)
}
