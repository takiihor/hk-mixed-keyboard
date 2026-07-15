package com.hkmixedkeyboard.settings

import com.hkmixedkeyboard.decoder.Scheme

/** Main-thread state for immediate scheme changes backed by asynchronous storage. */
class InputSchemeTransitionCoordinator(initialScheme: Scheme) {
    sealed interface Action {
        data class Apply(val scheme: Scheme) : Action
        data class ApplyAndPersist(val scheme: Scheme) : Action
        data object Ignore : Action
    }

    var currentScheme: Scheme = initialScheme
        private set

    private var pendingScheme: Scheme? = null
    private var lastPersistedScheme: Scheme? = null
    private var initializedFromPersistence = false

    fun onToggle(): Action {
        val next = InputSchemePreference.next(pendingScheme ?: currentScheme)
        pendingScheme = next
        currentScheme = next
        return Action.ApplyAndPersist(next)
    }

    fun onSelect(scheme: Scheme): Action {
        if (scheme == (pendingScheme ?: currentScheme)) return Action.Ignore
        pendingScheme = scheme
        currentScheme = scheme
        return Action.ApplyAndPersist(scheme)
    }

    fun onPersisted(scheme: Scheme): Action {
        lastPersistedScheme = scheme
        val pending = pendingScheme
        if (pending != null) {
            if (scheme == pending) pendingScheme = null
            return Action.Ignore
        }

        if (!initializedFromPersistence) {
            initializedFromPersistence = true
            currentScheme = scheme
            return Action.Apply(scheme)
        }

        if (scheme == currentScheme) return Action.Ignore
        currentScheme = scheme
        return Action.Apply(scheme)
    }

    fun onPersistenceFailed(scheme: Scheme): Action {
        if (pendingScheme != scheme) return Action.Ignore
        pendingScheme = null
        val persisted = lastPersistedScheme ?: return Action.Ignore
        initializedFromPersistence = true
        if (persisted == currentScheme) return Action.Ignore
        currentScheme = persisted
        return Action.Apply(persisted)
    }
}
