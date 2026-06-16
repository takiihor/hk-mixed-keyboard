import java.text.SimpleDateFormat
import java.util.Date
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
}

// Room schema export location — committed so migrations can be authored & verified.
kapt {
    arguments {
        arg("room.schemaLocation", "$projectDir/schemas")
    }
}

// ── Build remark ────────────────────────────────────────────────────────────
// A short, human-readable note about this build, surfaced in the Settings screen
// (and the APK filename) so the installed version is unmistakable. Edit this line
// whenever you want the note to describe the latest change.
 val buildRemark = "New app icon; 2-letter Space stays English; 0ms decode; bigger key hit-area"

// ── Auto-incrementing version ───────────────────────────────────────────────
// version.properties holds versionMinor/buildNumber/buildTime. Every time an
// assemble/bundle/install build is requested, the minor version is bumped (the
// APK is named app-<variant>-0.<minor>.0.apk, so it goes 0.8.0, 0.9.0, …), the
// build number (Android versionCode) is bumped, and the timestamp refreshed,
// then written back — so each built APK carries a higher, unmistakable version.
val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}

// Signing secrets, kept out of version control (see keystore.properties.sample).
val keystorePropsFile = file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
var versionMinor = (versionProps.getProperty("versionMinor") ?: "7").toInt()
var buildNumber = (versionProps.getProperty("buildNumber") ?: "0").toInt()
var buildTime = versionProps.getProperty("buildTime") ?: "unknown"

// Only bump on a *release* assemble/bundle so local debug builds and test runs
// don't churn the public version or dirty the tracked version.properties file.
val isReleaseBuildInvocation = gradle.startParameter.taskNames.any { taskName ->
    taskName.contains("Release", ignoreCase = true) &&
        listOf("assemble", "bundle", "install").any { taskName.contains(it, ignoreCase = true) }
}
if (isReleaseBuildInvocation) {
    versionMinor += 1
    buildNumber += 1
    buildTime = SimpleDateFormat("yyyy-MM-dd HH:mm").format(Date())
    versionProps.setProperty("versionMinor", versionMinor.toString())
    versionProps.setProperty("buildNumber", buildNumber.toString())
    versionProps.setProperty("buildTime", buildTime)
    versionPropsFile.outputStream().use {
        versionProps.store(it, "Auto-incremented on each assemble/bundle/install build")
    }
}

// e.g. 0.8.0, 0.9.0, 0.10.0, …
val appVersionName = "0.$versionMinor.0"

  android {
    namespace = "com.hkmixedkeyboard"
    compileSdk = 36

      defaultConfig {
        applicationId = "com.hkmixedkeyboard"
        minSdk = 26
        targetSdk = 35
        versionCode = buildNumber
        versionName = appVersionName

          buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
          buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
          buildConfigField("String", "BUILD_REMARK", "\"$buildRemark\"")
          // Gate for performance tracing on hot paths. Keep false in release.
          buildConfigField("boolean", "PERF_TRACING", "false")
          // Optional: enable decode coalescing (cancels older pending decodes). Off by default.
          buildConfigField("boolean", "DECODE_COALESCE", "false")

          // Haptic tuning defaults (overridden in debug below)
          buildConfigField("float", "HAPTIC_INTENSITY", "1.0f") // 0.1–1.0 scale → maps to 26–255 amplitude
          buildConfigField("int", "HAPTIC_LENGTH_MS", "8")     // one-shot duration in ms
          buildConfigField("boolean", "HAPTIC_STRONG_DEBUG", "false")
          buildConfigField("boolean", "HAPTIC_HEAVY_MODE", "false")
      }

    signingConfigs {
        create("release") {
            // Secrets are read from keystore.properties (git-ignored) or the
            // environment — never hard-coded in source. See keystore.properties.sample.
            storeFile = file(keystoreProps.getProperty("storeFile") ?: "release.keystore")
            storePassword = keystoreProps.getProperty("storePassword")
                ?: System.getenv("HKKBD_STORE_PASSWORD") ?: ""
            keyAlias = keystoreProps.getProperty("keyAlias")
                ?: System.getenv("HKKBD_KEY_ALIAS") ?: "hkmixedkeyboard"
            keyPassword = keystoreProps.getProperty("keyPassword")
                ?: System.getenv("HKKBD_KEY_PASSWORD") ?: ""
            // Enable all signature schemes for maximum device/OEM compatibility.
            // v1 (JAR) is required by some older / OEM ROMs that reject v2-only APKs.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

      buildTypes {
        debug {
          isDebuggable = true
          buildConfigField("boolean", "SHOW_DEBUG_PANEL", "false")
          // Enable perf tracing in debug builds by default.
          buildConfigField("boolean", "PERF_TRACING", "true")
          buildConfigField("boolean", "DECODE_COALESCE", "false")
          // Sign debug with the release config too so v1 signing is present
          // (max compatibility for sideloading during testing).
          signingConfig = signingConfigs.getByName("release")

          // Stronger, crisp haptics for debug without lengthening (avoid overlap)
          buildConfigField("float", "HAPTIC_INTENSITY", "1.0f")
          buildConfigField("int", "HAPTIC_LENGTH_MS", "8")
          buildConfigField("boolean", "HAPTIC_STRONG_DEBUG", "true")
          buildConfigField("boolean", "HAPTIC_HEAVY_MODE", "false")
        }
        release {
          isMinifyEnabled = true
          proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
          buildConfigField("boolean", "SHOW_DEBUG_PANEL", "false")
          buildConfigField("boolean", "PERF_TRACING", "false")
          buildConfigField("boolean", "DECODE_COALESCE", "false")
          signingConfig = signingConfigs.getByName("release")
        }
      }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions { jvmTarget = "11" }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    // Stamp the auto-incrementing version into the APK filename, e.g.
    // app-debug-0.8.0.apk, so each build is a distinct, unmistakable file.
    applicationVariants.all {
        val variant = this
        outputs.all {
            (this as com.android.build.gradle.internal.api.BaseVariantOutputImpl)
                .outputFileName = "app-${variant.name}-${variant.versionName}.apk"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.kotlinx.coroutines.android)
    kapt(libs.androidx.room.compiler)

    testImplementation(libs.junit)
}
