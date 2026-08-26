import groovy.json.JsonSlurper
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.google.services)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt.android)
}

val localProps = Properties()
val localFile = rootProject.file("local.properties")
if (localFile.exists()) localProps.load(localFile.inputStream())

val legacyUiProps = Properties()
val legacyUiLocalFile = rootProject.file("../swiftAid_soft_red_sos/local.properties")
if (legacyUiLocalFile.exists()) legacyUiProps.load(legacyUiLocalFile.inputStream())

val envProps = Properties()
val envFile = rootProject.file("../.env")
if (envFile.exists()) {
    envFile.readLines()
        .map { it.trim() }
        .filter { it.isNotEmpty() && !it.startsWith("#") && it.contains("=") }
        .forEach { line ->
            val parts = line.split("=", limit = 2)
            envProps[parts[0].trim()] = parts[1].trim()
        }
}

val mapsApiKey = listOfNotNull(
    localProps.getProperty("MAPS_API_KEY")?.takeIf { it.isNotBlank() },
    System.getenv("MAPS_API_KEY")?.takeIf { it.isNotBlank() },
    legacyUiProps.getProperty("MAPS_API_KEY")?.takeIf { it.isNotBlank() }
).firstOrNull().orEmpty()

val googleWebClientId = listOfNotNull(
    localProps.getProperty("GOOGLE_WEB_CLIENT_ID")?.takeIf { it.isNotBlank() },
    envProps.getProperty("GOOGLE_WEB_CLIENT_ID")?.takeIf { it.isNotBlank() },
    envProps.getProperty("GOOGLE_CLIENT_ID")?.takeIf { it.isNotBlank() },
    System.getenv("GOOGLE_WEB_CLIENT_ID")?.takeIf { it.isNotBlank() },
    System.getenv("GOOGLE_CLIENT_ID")?.takeIf { it.isNotBlank() }
).firstOrNull().orEmpty()

if (mapsApiKey.isBlank()) {
    logger.warn("MAPS_API_KEY is missing. Google Maps will show a blank map until a key is added to local.properties or the environment.")
}

if (googleWebClientId.isBlank()) {
    logger.warn("GOOGLE_WEB_CLIENT_ID is missing. Google sign-in will not work until it is added to local.properties, ../.env, or the environment.")
}

val appPackageName = "com.example.swiftaid"

android {
    namespace = appPackageName
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = appPackageName
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"$googleWebClientId\""
        )
        buildConfigField(
            "String",
            "API_BASE_URL",
            "\"${localProps["API_BASE_URL"] ?: envProps["API_BASE_URL"] ?: "http://192.168.29.101:8001"}\""
        )
        buildConfigField(
            "String",
            "KSHITI_API_BASE_URL",
            "\"${localProps["KSHITI_API_BASE_URL"] ?: envProps["KSHITI_API_BASE_URL"] ?: "https://kshitiserver-production.up.railway.app"}\""
        )
        buildConfigField(
            "String",
            "SERVER_IP",
            "\"${localProps["server.ip"] ?: localProps["SERVER_IP"] ?: envProps["SERVER_IP"] ?: "192.168.29.101"}\""
        )
    }

    signingConfigs {
        create("release") {
            val storeFilePath = localProps.getProperty("RELEASE_STORE_FILE")
                ?: System.getenv("RELEASE_STORE_FILE")
            if (!storeFilePath.isNullOrBlank()) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = localProps.getProperty("RELEASE_STORE_PASSWORD")
                    ?: System.getenv("RELEASE_STORE_PASSWORD")
                keyAlias = localProps.getProperty("RELEASE_KEY_ALIAS")
                    ?: System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = localProps.getProperty("RELEASE_KEY_PASSWORD")
                    ?: System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            val releaseStoreFile = signingConfigs.getByName("release").storeFile
            if (releaseStoreFile != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = true
    }
}

hilt {
    enableAggregatingTask = true
}

val validateFirebaseConfig = tasks.register("validateFirebaseConfig") {
    group = "verification"
    description = "Checks google-services.json package and OAuth hints used by Google sign-in."

    doLast {
        val googleServicesFile = project.file("google-services.json")
        if (!googleServicesFile.exists()) {
            throw GradleException("Missing app/google-services.json. Download it from Firebase for $appPackageName.")
        }

        val root = JsonSlurper().parse(googleServicesFile) as Map<*, *>
        val clients = root["client"] as? List<*> ?: emptyList<Any>()
        val androidClient = clients
            .mapNotNull { it as? Map<*, *> }
            .firstOrNull { client ->
                val clientInfo = client["client_info"] as? Map<*, *>
                val androidInfo = clientInfo?.get("android_client_info") as? Map<*, *>
                androidInfo?.get("package_name") == appPackageName
            }

        if (androidClient == null) {
            throw GradleException("google-services.json does not contain an Android client for $appPackageName.")
        }

        val oauthClients = androidClient["oauth_client"] as? List<*> ?: emptyList<Any>()
        val oauthIds = oauthClients
            .mapNotNull { it as? Map<*, *> }
            .mapNotNull { (it["client_id"] ?: it["current_key"]) as? String }

        if (oauthClients.none { (it as? Map<*, *>)?.containsKey("client_id") == true }) {
            logger.warn("google-services.json has no OAuth client_id entries. Regenerate it after adding SHA-1/SHA-256 for $appPackageName in Firebase.")
        }
        if (googleWebClientId.isNotBlank() && oauthIds.isNotEmpty() && googleWebClientId !in oauthIds) {
            logger.warn("GOOGLE_WEB_CLIENT_ID is not listed in app/google-services.json OAuth entries. Make sure Android and backend use the same Web OAuth client ID.")
        }
    }
}

tasks.matching { it.name == "preBuild" }.configureEach {
    dependsOn(validateFirebaseConfig)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.compose.icons.core)
    implementation(libs.compose.icons.extended)
    implementation(libs.material)
    implementation(libs.google.maps.compose)
    implementation(libs.google.play.services.maps)
    implementation(libs.play.services.location)
    implementation(libs.accompanist.permissions)
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play)
    implementation(libs.google.identity.id)
    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    testImplementation(libs.junit)
    testImplementation(libs.hilt.android.testing)
    kspTest(libs.hilt.compiler)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.hilt.android.testing)
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    implementation(platform("com.google.firebase:firebase-bom:34.13.0"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
}
