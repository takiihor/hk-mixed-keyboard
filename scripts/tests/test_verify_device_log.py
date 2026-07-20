from scripts.verify_device_log import classify


def test_framework_editor_icon_disk_read_is_recorded_but_not_actionable() -> None:
    result = classify(
        "\n".join(
            [
                "D StrictMode: StrictMode policy violation: android.os.strictmode.DiskReadViolation",
                "D StrictMode: at android.graphics.drawable.Icon.loadDrawable(Icon.java:373)",
                "D StrictMode: at android.widget.Editor.startActionMode(Editor.java:2622)",
            ]
        ),
        "com.hkmixedkeyboard.debug",
        "com.hkmixedkeyboard",
    )
    assert result.actionable == ()
    assert len(result.framework_only) == 1


def test_app_disk_read_is_actionable() -> None:
    result = classify(
        "\n".join(
            [
                "D StrictMode: StrictMode policy violation: android.os.strictmode.DiskReadViolation",
                "D StrictMode: at com.hkmixedkeyboard.settings.SettingsActivity.load(SettingsActivity.kt:1)",
            ]
        ),
        "com.hkmixedkeyboard.debug",
        "com.hkmixedkeyboard",
    )
    assert len(result.actionable) == 1
    assert result.framework_only == ()


def test_non_disk_strictmode_and_app_failures_are_actionable() -> None:
    text = "\n".join(
        [
            "E AndroidRuntime: FATAL EXCEPTION: main",
            "D StrictMode: StrictMode policy violation: android.os.strictmode.NetworkViolation",
            "D StrictMode: at android.os.StrictMode.onNetwork(StrictMode.java:1)",
            "E ActivityManager: ANR in com.hkmixedkeyboard.debug",
            "E ActivityManager: ANR in com.google.android.gms",
        ]
    )
    result = classify(text, "com.hkmixedkeyboard.debug", "com.hkmixedkeyboard")
    assert len(result.actionable) == 3
    assert result.framework_only == ()


def test_full_system_and_monkey_logs_only_flag_the_app_package() -> None:
    result = classify(
        "",
        "com.hkmixedkeyboard.debug",
        "com.hkmixedkeyboard",
        system_text="\n".join(
            [
                "E ActivityManager: ANR in com.google.android.gms",
                "E AndroidRuntime: Process: com.google.android.bluetooth, PID: 12",
                "E ActivityManager: ANR in com.hkmixedkeyboard.debug",
                "F DEBUG: Cmdline: com.hkmixedkeyboard.debug",
            ]
        ),
        monkey_text="\n".join(
            [
                "// CRASH: com.google.android.bluetooth (pid 12)",
                "// CRASH: com.hkmixedkeyboard.debug (pid 34)",
            ]
        ),
    )
    assert len(result.actionable) == 3
    assert result.framework_only == ()
