plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.webtoapp.minimal.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.webtoapp.minimal.app"
        minSdk = 24
        targetSdk = 34
        versionCode = 2 // Incremented version code
        versionName = "1.13.1.25" // Updated version name

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.androidx.swiperefreshlayout)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}