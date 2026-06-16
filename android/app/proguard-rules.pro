# HK Mixed Keyboard — R8 / ProGuard rules
#
# The release build has minification on. Without these keeps, R8 can rename or
# strip classes that Android (or Room) resolves by name at runtime, which makes
# the IME crash on launch with no compile-time error.

# ── Input Method Service ────────────────────────────────────────────────────
# Instantiated by the framework from the manifest name. Keep the class and its
# no-arg constructor.
-keep class com.hkmixedkeyboard.ime.HkImeService { *; }

# Activities referenced from the manifest.
-keep class com.hkmixedkeyboard.settings.SettingsActivity { *; }
-keep class com.hkmixedkeyboard.settings.CustomWordActivity { *; }
-keep class com.hkmixedkeyboard.settings.DictionaryExportActivity { *; }
-keep class com.hkmixedkeyboard.settings.OpenSourceLicensesActivity { *; }
-keep class com.hkmixedkeyboard.settings.PrivacyPolicyActivity { *; }

# Custom Views (constructed in code here, but keep their View constructors in
# case any are inflated, and to be safe under aggressive optimization).
-keep class com.hkmixedkeyboard.ui.** { *; }

# ── Room ────────────────────────────────────────────────────────────────────
# Room generates <Database>_Impl and <Dao>_Impl classes that it loads by name
# via Class.forName(). They must not be renamed or removed.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Database class * { *; }
-keep class com.hkmixedkeyboard.memory.** { *; }
-keepclassmembers class com.hkmixedkeyboard.memory.** { *; }
-dontwarn androidx.room.paging.**

# ── Enums resolved with valueOf() ───────────────────────────────────────────
# SourceSchema / CandidateType are reconstructed from persisted names via
# valueOf(). Keep the synthetic values()/valueOf() methods and constant names.
-keepclassmembers enum com.hkmixedkeyboard.decoder.** {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-keep enum com.hkmixedkeyboard.decoder.SourceSchema { *; }
-keep enum com.hkmixedkeyboard.decoder.CandidateType { *; }

# ── Kotlin / Coroutines / DataStore ─────────────────────────────────────────
# These ship their own consumer rules, but keep metadata to be safe.
-keepclassmembers class kotlinx.coroutines.** { volatile <fields>; }
-dontwarn kotlinx.coroutines.**
-keep class kotlin.Metadata { *; }
