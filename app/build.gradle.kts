import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.util.Base64

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.android)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
  alias(libs.plugins.firebase.crashlytics)
}

android {
  namespace = "com.example"
  compileSdk = 36

  defaultConfig {
    applicationId = "com.aistudio.finmoney.vqpz"
    minSdk = 24
    targetSdk = 36
    versionCode = 217
    versionName = "217.0"

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  val debugKeystore = file("${rootDir}/debug.keystore")
  val debugKeystoreBase64 = file("${rootDir}/debug.keystore.base64")
  if (!debugKeystore.exists() && debugKeystoreBase64.exists()) {
    try {
      val decoded = Base64.getDecoder().decode(debugKeystoreBase64.readText().trim())
      debugKeystore.writeBytes(decoded)
    } catch (e: Exception) {
      // ignore
    }
  }

  val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
  val releaseKeystoreFile = file(keystorePath)
  val hasReleaseKeystore = releaseKeystoreFile.exists()

  signingConfigs {
    create("release") {
      if (hasReleaseKeystore) {
        storeFile = releaseKeystoreFile
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias = "upload"
        keyPassword = System.getenv("KEY_PASSWORD")
      } else {
        storeFile = debugKeystore
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
      }
    }
    create("debugConfig") {
      storeFile = debugKeystore
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = false
      isMinifyEnabled = false
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (hasReleaseKeystore) {
        signingConfig = signingConfigs.getByName("release")
        logger.lifecycle("=================================================================")
        logger.lifecycle("[SIGNING INFO] RELEASE SIGNING CONFIGURATION ACTIVE")
        logger.lifecycle("               Production Keystore File: ${releaseKeystoreFile.absolutePath}")
        logger.lifecycle("               Status: Signed with Production Release Keystore.")
        logger.lifecycle("=================================================================")
      } else {
        signingConfig = signingConfigs.getByName("debugConfig")
        logger.lifecycle("=================================================================")
        logger.lifecycle("[SIGNING WARNING] FALLBACK TO DEBUG SIGNING CONFIGURATION")
        logger.lifecycle("                  Production Keystore File Not Found: $keystorePath")
        logger.lifecycle("                  Status: Artifact will be signed with DEBUG keystore.")
        logger.lifecycle("                  Action: Provide 'my-upload-key.jks' or 'RELEASE_KEYSTORE_BASE64' secret for Play Console release.")
        logger.lifecycle("=================================================================")
      }
    }
    debug { signingConfig = signingConfigs.getByName("debugConfig") }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  kotlinOptions {
    jvmTarget = "17"
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
    jniLibs {
      useLegacyPackaging = false
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

// Configure the Secrets Gradle Plugin to use .env and .env.example files
// to match the convention used in Web projects.
secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

// Some unused dependencies are commented out below instead of being removed.
// This makes it easy to add them back in the future if needed.
dependencies {
  implementation(libs.androidx.biometric)
  implementation(platform(libs.androidx.compose.bom))
  implementation(platform(libs.firebase.bom))
  // implementation(libs.accompanist.permissions)
  implementation(libs.androidx.activity.compose)
  // Unused camera and mlkit dependencies commented out to prevent 4 KB unaligned native libraries (.so) and satisfy 16 KB page size requirement
  // implementation("androidx.camera:camera-core:1.3.4")
  // implementation("androidx.camera:camera-camera2:1.3.4")
  // implementation("androidx.camera:camera-lifecycle:1.3.4")
  // implementation("androidx.camera:camera-view:1.3.4")
  // implementation(libs.guava)
  // implementation(libs.mlkit.barcode.scanning)
  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  // implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  // implementation(libs.androidx.navigation.compose)
  implementation("com.google.code.gson:gson:2.11.0")
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation("com.google.firebase:firebase-vertexai:16.0.0")
  implementation(libs.firebase.firestore)
  implementation(libs.firebase.auth)
  implementation(libs.firebase.crashlytics)
  implementation(libs.androidx.credentials)
  implementation("androidx.credentials:credentials-play-services-auth:1.5.0-alpha05")
  implementation(libs.googleid)
  implementation("com.google.android.gms:play-services-auth:21.3.0")
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi.kotlin)
  implementation(libs.okhttp)
  // implementation(libs.play.services.location)
  implementation(libs.retrofit)
  implementation("com.patrykandpatrick.vico:compose:1.16.0")
  implementation("com.patrykandpatrick.vico:compose-m3:1.16.0")
  implementation("com.patrykandpatrick.vico:core:1.16.0")
  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)
  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)
  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
}
