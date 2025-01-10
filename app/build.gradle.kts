plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.sms_lemadio_sender"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.sms_lemadio_sender"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

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
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

}

dependencies {

    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.volley)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)

    val workVersion = "2.8.1"
    implementation("androidx.work:work-runtime:$workVersion")

    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

}