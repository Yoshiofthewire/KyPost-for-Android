plugins {
    alias(libs.plugins.android.application)
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.urlxl.mail"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.urlxl.mail"
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        val mailImapHost = providers.gradleProperty("mail.imap.host").orElse("").get()
        val mailImapPort = providers.gradleProperty("mail.imap.port").orElse("993").get().toInt()
        val mailSmtpHost = providers.gradleProperty("mail.smtp.host").orElse("").get()
        val mailSmtpPort = providers.gradleProperty("mail.smtp.port").orElse("587").get().toInt()
        val mailUsername = providers.gradleProperty("mail.username").orElse("").get()
        val mailPassword = providers.gradleProperty("mail.password").orElse("").get()
        val mailFolder = providers.gradleProperty("mail.imap.folder").orElse("INBOX").get()

        buildConfigField("String", "MAIL_IMAP_HOST", "\"$mailImapHost\"")
        buildConfigField("int", "MAIL_IMAP_PORT", mailImapPort.toString())
        buildConfigField("String", "MAIL_SMTP_HOST", "\"$mailSmtpHost\"")
        buildConfigField("int", "MAIL_SMTP_PORT", mailSmtpPort.toString())
        buildConfigField("String", "MAIL_USERNAME", "\"$mailUsername\"")
        buildConfigField("String", "MAIL_PASSWORD", "\"$mailPassword\"")
        buildConfigField("String", "MAIL_IMAP_FOLDER", "\"$mailFolder\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    buildFeatures {
        buildConfig = true
        compose = true
    }
    packaging {
        resources {
            excludes += "META-INF/NOTICE.md"
            excludes += "META-INF/LICENSE.md"
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

}

dependencies {
    implementation(platform("androidx.compose:compose-bom:2026.01.00"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.10.2")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("com.squareup.okhttp3:okhttp:5.2.1")
    implementation("com.squareup.okhttp3:logging-interceptor:5.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    implementation(platform("com.google.firebase:firebase-bom:34.2.0"))
    implementation("com.google.firebase:firebase-messaging")
    implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.cardview)
    implementation(libs.angus.mail)

    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}