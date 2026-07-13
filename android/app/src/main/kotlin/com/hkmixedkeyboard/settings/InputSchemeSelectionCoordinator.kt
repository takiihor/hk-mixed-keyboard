package com.hkmixedkeyboard.settings

import com.hkmixedkeyboard.decoder.Scheme

/** Resolves the race between asynchronous preference hydration and a fast user tap. */
class InputSchemeSelectionCoordinator {
    sealed interface Action {
        data class ApplyToUi(val scheme: Scheme) : Action
        data class Persist(val scheme: Scheme) : Action
        data object Ignore : Action
    }

    private var userHasSelected = false

    fun onHydrated(scheme: Scheme): Action =
        if (userHasSelected) Action.Ignore else Action.ApplyToUi(scheme)

    fun onUserSelection(scheme: Scheme): Action {
        userHasSelected = true
        return Action.Persist(scheme)
    }
}
