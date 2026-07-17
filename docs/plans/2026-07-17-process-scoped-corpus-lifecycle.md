# Process-Scoped Corpus Lifecycle Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Reuse one corpus index graph across IME service rebinds so repeated keyboard switching cannot multiply warm-up memory or trigger a system kill.

**Architecture:** `HkApplication` lazily owns one application-context `CorpusLoader`. Every `HkImeService` instance reuses that loader while retaining service-local decoder state, settings jobs, composition, haptics, and views.

**Tech Stack:** Kotlin, Android `Application`, `InputMethodService`, synchronized lazy initialization, AndroidX instrumentation, JUnit 4, Gradle, ADB

---

### Task 1: Define the Process-Reuse Contract

**Files:**
- Create: `android/app/src/androidTest/kotlin/com/hkmixedkeyboard/ApplicationCorpusScopeTest.kt`

**Step 1: Write the failing instrumentation test**

```kotlin
package com.hkmixedkeyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ApplicationCorpusScopeTest {
    @Test
    fun applicationReturnsOneCorpusToRepeatedServiceAcquisitions() {
        val application = InstrumentationRegistry.getInstrumentation()
            .targetContext.applicationContext as HkApplication

        assertSame(application.corpus, application.corpus)
    }
}
```

**Step 2: Run the focused test to verify RED**

```bash
cd android
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hkmixedkeyboard.ApplicationCorpusScopeTest
```

Expected: Android-test compilation fails because `HkApplication.corpus` does
not exist.

### Task 2: Reuse the Application Corpus

**Files:**
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/HkApplication.kt:11-18`
- Modify: `android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt:21-30,228-231`
- Test: `android/app/src/androidTest/kotlin/com/hkmixedkeyboard/ApplicationCorpusScopeTest.kt`

**Step 1: Add the process-scoped lazy loader**

Add the decoder import and property to `HkApplication`:

```kotlin
import com.hkmixedkeyboard.decoder.CorpusLoader

class HkApplication : Application() {
    val corpus: CorpusLoader by lazy {
        CorpusLoader(applicationContext)
    }
```

The application context prevents activity or service leaks. Kotlin's default
synchronized lazy mode ensures simultaneous service warm-ups cannot construct
two loaders.

**Step 2: Resolve the shared loader in the IME service**

Import `HkApplication` and replace service-local construction:

```kotlin
corpus = (application as? HkApplication)?.corpus
    ?: CorpusLoader(applicationContext)
```

Keep `CorpusBackedDecoder(corpus)` service-scoped so custom-word state and other
mutable decoder state do not leak across destroyed services.

**Step 3: Run focused GREEN verification**

```bash
cd android
./gradlew connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.hkmixedkeyboard.ApplicationCorpusScopeTest
./gradlew testDebugUnitTest \
  --tests com.hkmixedkeyboard.PinyinPreferenceLoadingTest \
  --tests com.hkmixedkeyboard.PinyinDecoderTest \
  --tests com.hkmixedkeyboard.PinyinImeIntegrationTest
```

Expected: the instrumentation reuse test and all selected JVM tests pass.

**Step 4: Commit the regression and implementation**

```bash
git add \
  android/app/src/main/kotlin/com/hkmixedkeyboard/HkApplication.kt \
  android/app/src/main/kotlin/com/hkmixedkeyboard/ime/HkImeService.kt \
  android/app/src/androidTest/kotlin/com/hkmixedkeyboard/ApplicationCorpusScopeTest.kt
git commit -m "fix: reuse corpus across IME service rebinds"
```

### Task 3: Verify Build, Device Latency, and Memory

**Files:**
- Modify only if verification exposes a defect caused by this change.

**Step 1: Run full local verification**

```bash
cd android
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Expected: both commands report `BUILD SUCCESSFUL`.

**Step 2: Install the debug APK on the connected phone**

```bash
adb install -r android/app/build/outputs/apk/debug/app-debug-0.64.8.apk
```

Preserve app data and restore the previously selected HK IME after testing.

**Step 3: Repeat switch-back timing**

With a focused external editor and the display temporarily kept awake over USB,
measure five ordinary Gboard-to-HK returns and five forced-cold returns. Require
the selected HK method and a visible Android InputMethod surface. Restore the
original stay-awake value and launcher focus afterward.

Expected: no multi-second ordinary return; ordinary presentation remains near
the previous 295-332 ms range and cold presentation remains below one second.

**Step 4: Stress rebind memory**

Run at least 20 rapid ordinary Gboard/HK switches in one process. Record the HK
PID and `dumpsys meminfo` before, during, and after the burst.

Expected:

- the HK PID survives;
- total RSS remains near one warmed-corpus baseline and does not approach the
  previous 565 MB kill point;
- Android exit history records no new low-memory/system kill, crash, or ANR;
- the phone finishes with HK selected, stay-awake restored, and launcher focused.
