package com.hkmixedkeyboard.settings

import com.hkmixedkeyboard.decoder.Scheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.ReceiveChannel

/** Serially persists queued schemes without one failed item terminating the queue. */
class InputSchemeWriteWorker(
    private val persist: suspend (Scheme) -> Unit
) {
    suspend fun consume(
        schemes: ReceiveChannel<Scheme>,
        onFailure: (Scheme, Throwable) -> Unit
    ) {
        for (scheme in schemes) {
            try {
                persist(scheme)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                onFailure(scheme, e)
            }
        }
    }
}
