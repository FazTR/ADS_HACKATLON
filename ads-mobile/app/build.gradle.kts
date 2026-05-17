import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

val dotEnv = Properties().apply {
    val envFile = rootProject.projectDir.parentFile.resolve(".env")
    if (envFile.exists()) {
        envFile.inputStream().use(::load)
    }
}

fun configValue(name: String, defaultValue: String): String {
    return dotEnv.getProperty(name)
        ?.takeIf { it.isNotBlank() }
        ?: providers.gradleProperty(name).orNull
        ?: defaultValue
}

val adsBaseUrl = configValue("ADS_BASE_URL", "http://192.168.43.4:4030/")
val adsApiKey = configValue("ADS_API_KEY", "")
val assistantBaseUrl = configValue("ASSISTANT_BASE_URL", "https://api.interneteco.systems/")
val assistantApiKey = configValue("ASSISTANT_API_KEY", "")
val assistantModel = configValue("ASSISTANT_MODEL", "gemini-3-flash-preview")
val offlineModelUrl = configValue(
    "OFFLINE_MODEL_URL",
    "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/resolve/main/gemma-4-E2B-it.litertlm"
)
val offlineModelFilename = configValue("OFFLINE_MODEL_FILENAME", "gemma-4-E2B-it.litertlm")
val offlineModelLabel = configValue("OFFLINE_MODEL_LABEL", "Gemma 4 E2B IT")
val offlineModelAuthToken = configValue("OFFLINE_MODEL_AUTH_TOKEN", "")
val offlineSpeechModelDir = configValue("OFFLINE_SPEECH_MODEL_DIR", "vosk-model-small-tr-0.3")
val offlineSpeechModelLabel = configValue("OFFLINE_SPEECH_MODEL_LABEL", "Türkçe ses modeli")

android {
    namespace = "com.example.ads_001"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.example.ads_001"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
        buildConfigField("String", "ADS_BASE_URL", "\"$adsBaseUrl\"")
        buildConfigField("String", "ADS_API_KEY", "\"$adsApiKey\"")
        buildConfigField("String", "ASSISTANT_BASE_URL", "\"$assistantBaseUrl\"")
        buildConfigField("String", "ASSISTANT_API_KEY", "\"$assistantApiKey\"")
        buildConfigField("String", "ASSISTANT_MODEL", "\"$assistantModel\"")
        buildConfigField("String", "OFFLINE_MODEL_URL", "\"$offlineModelUrl\"")
        buildConfigField("String", "OFFLINE_MODEL_FILENAME", "\"$offlineModelFilename\"")
        buildConfigField("String", "OFFLINE_MODEL_LABEL", "\"$offlineModelLabel\"")
        buildConfigField("String", "OFFLINE_MODEL_AUTH_TOKEN", "\"$offlineModelAuthToken\"")
        buildConfigField("String", "OFFLINE_SPEECH_MODEL_DIR", "\"$offlineSpeechModelDir\"")
        buildConfigField("String", "OFFLINE_SPEECH_MODEL_LABEL", "\"$offlineSpeechModelLabel\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    
    // Retrofit & OkHttp
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.11.0")
    implementation("com.alphacephei:vosk-android:0.3.75")
    
    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")

    // P2P Mesh Network
    implementation("com.google.android.gms:play-services-nearby:19.0.0")
    implementation("com.google.code.gson:gson:2.10.1")
}
