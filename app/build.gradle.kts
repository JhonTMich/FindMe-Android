import groovy.json.JsonSlurper

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

val publishedApplicationId = "com.example.findme"
val firebaseConfig = providers.environmentVariable("NEAR_FIREBASE_CONFIG").orNull
    ?.takeIf { it.isNotBlank() }
    ?: providers.fileContents(layout.projectDirectory.file("google-services.json")).asText.orNull
val firebaseResources = firebaseConfig?.let { config ->
    val json = JsonSlurper().parseText(config) as Map<*, *>
    val project = json["project_info"] as Map<*, *>
    val client = (json["client"] as List<*>).filterIsInstance<Map<*, *>>().firstOrNull {
        val info = it["client_info"] as? Map<*, *>
        val androidInfo = info?.get("android_client_info") as? Map<*, *>
        androidInfo?.get("package_name") == publishedApplicationId
    } ?: error("La configuración de Firebase debe incluir $publishedApplicationId")
    val info = client["client_info"] as Map<*, *>
    val key = (client["api_key"] as List<*>).filterIsInstance<Map<*, *>>().first()
    mapOf(
        "google_app_id" to info["mobilesdk_app_id"],
        "gcm_defaultSenderId" to project["project_number"],
        "google_api_key" to key["current_key"],
        "project_id" to project["project_id"]
    ).mapValues { (name, value) ->
        require(value is String && value.matches(Regex("[-A-Za-z0-9_:.]+"))) {
            "Valor inválido o ausente para $name en la configuración de Firebase"
        }
        value
    }
}.orEmpty()

android {
    namespace = "com.example.findme"
    compileSdk = 37

    defaultConfig {
        // Registered in Firebase (project near-c8d8c); the Kotlin namespace stays com.example.findme.
        applicationId = publishedApplicationId
        firebaseResources.forEach { (name, value) -> resValue("string", name, value) }
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("releaseVersionCode").orNull?.toInt() ?: 1005000
        versionName = providers.gradleProperty("releaseVersionName").orNull ?: "1.5"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = true
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
        resValues = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.compose.material.icons)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.lifecycle.runtime.compose)
    // Kotlinx Serialization
    implementation(libs.kotlinx.serialization.json)

    // Supabase & Ktor
    implementation(platform(libs.supabase.bom))
    implementation(libs.supabase.postgrest)
    implementation(libs.supabase.realtime)
    implementation(libs.supabase.auth)
    implementation(libs.ktor.client.okhttp)

    // Glance Widget
    implementation(libs.glance.appwidget)
    implementation(libs.glance.material3)

    // WorkManager
    implementation(libs.work.runtime.ktx)

    // DataStore Preferences
    implementation(libs.datastore.preferences)

    // Firebase Cloud Messaging (configured from res/values/firebase.xml, no google-services plugin)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.messaging)
    constraints {
        // Firebase brings an older Fragment transitively; Activity Result requires 1.3 or newer.
        implementation(libs.androidx.fragment)
    }

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
