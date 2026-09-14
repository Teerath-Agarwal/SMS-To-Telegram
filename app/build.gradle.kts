import java.util.Properties
import java.util.Base64

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

fun obfuscate(input: String?): String {
    if (input == null) return ""
    val cleanInput = input.trim().removeSurrounding("\"").removeSurrounding("'")
    val key = "dVNHpXazVOYmtKU1RraGtUVTlITlZOTldIQlZUbTFLV2sx"
    val sb = StringBuilder()
    for (i in cleanInput.indices) {
        sb.append((cleanInput[i].code xor key[i % key.length].code).toChar())
    }
    return Base64.getEncoder().encodeToString(sb.toString().toByteArray())
}

val localProperties = Properties().apply {
    val propertiesFile = rootProject.file("local.properties")
    if (propertiesFile.exists()) {
        propertiesFile.inputStream().use { load(it) }
    }
}

android {
    namespace = "com.example.smsToTelegram"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.smsToTelegram"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Store OBFUSCATED versions in BuildConfig
        buildConfigField("String", "SEED_TG_TOKEN", "\"${obfuscate(localProperties.getProperty("telegram.bot.token"))}\"")
        buildConfigField("String", "SEED_TG_CHAT_ID", "\"${obfuscate(localProperties.getProperty("telegram.chat.id"))}\"")
        buildConfigField("String", "SEED_FWD_NUMBER", "\"${obfuscate(localProperties.getProperty("forwarding.number"))}\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
