package com.hkmixedkeyboard.settings

data class SetupState(val enabled: Boolean, val selected: Boolean) {
    val complete: Boolean get() = enabled && selected
}

object SetupStatePolicy {
    fun evaluate(
        packageName: String,
        enabledServicePackages: Set<String>,
        selectedImeId: String?
    ): SetupState {
        val enabled = packageName in enabledServicePackages
        val selected = selectedImeId.orEmpty().substringBefore('/').let { idPackage ->
            idPackage == packageName || selectedImeId.orEmpty().startsWith("$packageName/")
        }
        return SetupState(enabled, selected)
    }
}
