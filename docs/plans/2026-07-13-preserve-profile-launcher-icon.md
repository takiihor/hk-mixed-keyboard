# Preserve Profile Launcher Icon Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Show the supplied profile artwork in the launcher without its baked-in black outer background or any cropping.

**Architecture:** Convert only edge-connected black source pixels to alpha and fit the complete artwork into adaptive foreground safe bounds.  Keep a separate solid cream adaptive background and declare matching normal and round launcher icons.

**Tech Stack:** Android adaptive icon XML, PNG assets, Python stdlib image test, Gradle.

---

### Task 1: Define the regression checks

**Files:**
- Modify: `corpus/tools/tests/test_launcher_icon_safe_area.py`
- Test: `corpus/tools/tests/test_launcher_icon_safe_area.py`

**Step 1: Write the failing test**

Add assertions that the foreground has transparent corners, no opaque black
pixel touching an outer edge, a round icon XML resource exists, and the
Manifest declares both icon attributes.

**Step 2: Run test to verify it fails**

Run: `python3 -m unittest corpus.tools.tests.test_launcher_icon_safe_area`

Expected: FAIL because the round icon and `roundIcon` declaration are absent.

**Step 3: Implement the smallest resource change**

Generate foreground PNGs from the source by changing only connected pure black
background to transparent.  Add a cream adaptive background and matching
round adaptive icon XML.  Add `android:roundIcon` to the Manifest.

**Step 4: Run test to verify it passes**

Run: `python3 -m unittest corpus.tools.tests.test_launcher_icon_safe_area`

Expected: PASS.

### Task 2: Verify packaged output

**Files:**
- Verify: `android/app/build/outputs/apk/debug/app-debug.apk`

**Step 1: Build**

Run: `./gradlew test assembleDebug`

Expected: `BUILD SUCCESSFUL`.

**Step 2: Inspect launcher rendering**

Install the debug APK on an emulator and inspect circular and rounded-square
launcher masks.  The complete card must be visible and the outer background
must not be black.
