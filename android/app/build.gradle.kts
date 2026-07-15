import java.security.MessageDigest
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
 val buildRemark = "HKSCS 4,606 coverage, tone-aware Jyutping, phrase-evidence Pinyin"

// Version metadata is read-only during builds. Release owners update it in an
// intentional source change; debug, test and lint can never dirty the worktree.
val versionPropsFile = file("version.properties")
val versionProps = Properties().apply {
    if (versionPropsFile.exists()) versionPropsFile.inputStream().use { load(it) }
}

// Signing secrets, kept out of version control (see keystore.properties.sample).
// Prefer HKKBD_* environment variables so release/upload keys can live outside
// the repo. keystore.properties remains supported for local one-machine builds.
val keystorePropsFile = file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
val releaseStorePath = System.getenv("HKKBD_STORE_FILE") ?: keystoreProps.getProperty("storeFile")
val releaseStorePassword = System.getenv("HKKBD_STORE_PASSWORD") ?: keystoreProps.getProperty("storePassword")
val releaseKeyAlias = System.getenv("HKKBD_KEY_ALIAS") ?: keystoreProps.getProperty("keyAlias")
val releaseKeyPassword = System.getenv("HKKBD_KEY_PASSWORD") ?: keystoreProps.getProperty("keyPassword")
val hasReleaseSigning = listOf(
    releaseStorePath,
    releaseStorePassword,
    releaseKeyAlias,
    releaseKeyPassword
).all { !it.isNullOrBlank() }
val releasePackagingTaskNames = setOf(
    "packageRelease",
    "packageReleaseBundle",
    "bundleRelease",
    "assembleRelease"
)
val missingReleaseSigningMessage =
    "Release signing is required. Set HKKBD_STORE_FILE, HKKBD_STORE_PASSWORD, " +
        "HKKBD_KEY_ALIAS and HKKBD_KEY_PASSWORD."
val versionMinor = (versionProps.getProperty("versionMinor") ?: "7").toInt()
val buildNumber = (versionProps.getProperty("buildNumber") ?: "0").toInt()
val buildTime = versionProps.getProperty("buildTime") ?: "unknown"

val corpusManifest = rootProject.file("../corpus/sources/corpus_manifest.json")
val corpusHash = if (corpusManifest.isFile) {
    MessageDigest.getInstance("SHA-256")
        .digest(corpusManifest.readBytes())
        .joinToString("") { "%02x".format(it) }
} else {
    "missing-manifest"
}

// e.g. 0.8.0, 0.9.0, 0.10.0, …
val appVersionName = "0.$versionMinor.0"

  android {
    namespace = "com.hkmixedkeyboard"
    compileSdk = 36

      defaultConfig {
        applicationId = "com.hkmixedkeyboard"
        minSdk = 26
        // API 36 exceeds the API 35 Play submission floor verified on 2026-07-15.
        targetSdk = 36
        versionCode = buildNumber
        versionName = appVersionName
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

          buildConfigField("int", "BUILD_NUMBER", "$buildNumber")
          buildConfigField("String", "BUILD_TIME", "\"$buildTime\"")
          buildConfigField("String", "BUILD_REMARK", "\"$buildRemark\"")
          buildConfigField("String", "CORPUS_HASH", "\"$corpusHash\"")
          // Gate for performance tracing on hot paths. Keep false in release.
          buildConfigField("boolean", "PERF_TRACING", "false")

          // Haptic tuning defaults (overridden in debug below)
          buildConfigField("float", "HAPTIC_INTENSITY", "1.0f") // 0.1–1.0 scale → maps to 26–255 amplitude
          buildConfigField("int", "HAPTIC_LENGTH_MS", "8")     // one-shot duration in ms
          buildConfigField("boolean", "HAPTIC_STRONG_DEBUG", "false")
          buildConfigField("boolean", "HAPTIC_HEAVY_MODE", "false")
      }

    signingConfigs {
        create("release") {
            if (hasReleaseSigning) {
                storeFile = file(releaseStorePath!!)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
            // Enable all signature schemes for maximum device/OEM compatibility.
            // v1 (JAR) is required by some older / OEM ROMs that reject v2-only APKs.
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true
        }
    }

      buildTypes {
        debug {
          applicationIdSuffix = ".debug"
          isDebuggable = true
          enableUnitTestCoverage = true
          buildConfigField("boolean", "SHOW_DEBUG_PANEL", "false")
          // Enable perf tracing in debug builds by default.
          buildConfigField("boolean", "PERF_TRACING", "true")

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
          if (hasReleaseSigning) {
            signingConfig = signingConfigs.getByName("release")
          }
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

    sourceSets {
        // The pinned tonal Jyutping reference is shared directly with the runtime
        // to avoid a second drifting copy in app/src/main/assets.
        getByName("main").assets.srcDir(rootProject.file("../corpus/reference"))
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    lint {
        // Dependency/KSP upgrades are handled as explicit compatibility work.
        disable += setOf(
            "OldTargetApi",
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "KaptUsageInsteadOfKsp"
        )
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

// Reject release packaging before any dependency task can write an unsigned bundle.
// The task-level guard remains as defense in depth for unusual direct task execution.
gradle.taskGraph.whenReady {
    if (!hasReleaseSigning && allTasks.any { task ->
            task.project == project && task.name in releasePackagingTaskNames
        }
    ) {
        throw GradleException(missingReleaseSigningMessage)
    }
}

tasks.configureEach {
    if (name in releasePackagingTaskNames) {
        doFirst {
            if (!hasReleaseSigning) {
                throw GradleException(missingReleaseSigningMessage)
            }
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
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.room.testing)
}
