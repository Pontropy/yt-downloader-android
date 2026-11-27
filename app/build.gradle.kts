import java.io.File
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
}

val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
  if (keystorePropsFile.isFile) {
    val cleanText = keystorePropsFile.readText(Charsets.UTF_8).removePrefix("\uFEFF")
    cleanText.reader().use { load(it) }
  }
}

android {
  namespace = "com.pontropy.ytplayer"
  compileSdk = 37

  defaultConfig {
    applicationId = "com.pontropy.ytplayer"
    minSdk = 26
    targetSdk = 36
    versionCode = 1
    versionName = "0.1.0-beta"
  }

  signingConfigs {
    val storePath = keystoreProps.getProperty("storeFile") ?: System.getenv("ANDROID_KEYSTORE_PATH")
    val keyAliasProp = keystoreProps.getProperty("keyAlias") ?: System.getenv("ANDROID_KEY_ALIAS")
    val storePass = keystoreProps.getProperty("storePassword") ?: System.getenv("ANDROID_STORE_PASSWORD")
    val keyPass = keystoreProps.getProperty("keyPassword") ?: System.getenv("ANDROID_KEY_PASSWORD")
    if (!storePath.isNullOrBlank() && !keyAliasProp.isNullOrBlank() && !storePass.isNullOrBlank() && !keyPass.isNullOrBlank()) {
      val rawFile = File(storePath)
      val ksFile = if (rawFile.isAbsolute) rawFile else rootProject.file(storePath)
      if (ksFile.isFile) {
        create("release") {
          storeFile = ksFile
          storePassword = storePass
          keyAlias = keyAliasProp
          keyPassword = keyPass
        }
      }
    }
  }

  buildTypes {
    release {
      isMinifyEnabled = false
      vcsInfo {
        include = false
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      signingConfig = signingConfigs.findByName("release")
    }
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  buildFeatures {
    compose = true
    aidl = false
    buildConfig = false
    shaders = false
  }

  packaging {
    jniLibs {
      // Required by youtubedl-android so python and ffmpeg binaries extract to nativeLibraryDir.
      useLegacyPackaging = true
    }
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android & Lifecycle
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.kotlinx.serialization.json)

  // Compose UI & Icons
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.extended)
  debugImplementation(libs.androidx.compose.ui.tooling)
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Navigation 3
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // yt-dlp + ffmpeg on Android
  implementation(libs.youtubedl.library)
  implementation(libs.youtubedl.ffmpeg)

  // Media3 ExoPlayer & MediaSession
  implementation(libs.androidx.media3.exoplayer)
  implementation(libs.androidx.media3.ui)
  implementation(libs.androidx.media3.common)
  implementation(libs.androidx.media3.session)

  // Thumbnails
  implementation(libs.coil.compose)
  implementation(libs.coil.network.okhttp)

  // Unit & Instrumented Tests
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)
}
