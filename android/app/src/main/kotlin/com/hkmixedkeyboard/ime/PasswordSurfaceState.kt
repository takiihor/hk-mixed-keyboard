package com.hkmixedkeyboard.ime

import com.hkmixedkeyboard.ui.KeyboardSurface

class PasswordSurfaceState {
    var editorSurface: KeyboardSurface = KeyboardSurface.TEXT
        private set
    var manualPin: Boolean = false
        private set

    val visibleSurface: KeyboardSurface
        get() = if (editorSurface == KeyboardSurface.TEXT_PASSWORD && manualPin) {
            KeyboardSurface.NUMERIC_PASSWORD
        } else {
            editorSurface
        }

    val showAlphabetAction: Boolean
        get() = editorSurface == KeyboardSurface.TEXT_PASSWORD && manualPin

    fun startEditor(surface: KeyboardSurface) {
        editorSurface = surface
        manualPin = false
    }

    fun finishEditor() {
        editorSurface = KeyboardSurface.TEXT
        manualPin = false
    }

    fun enterManualPin() {
        if (editorSurface == KeyboardSurface.TEXT_PASSWORD) manualPin = true
    }

    fun leaveManualPin() {
        manualPin = false
    }
}
