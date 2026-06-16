package com.hkmixedkeyboard.ime

import java.util.concurrent.atomic.AtomicLong

class CandidateRequestGate {
    private val generation = AtomicLong()

    fun next(): Long = generation.incrementAndGet()

    fun invalidate() {
        generation.incrementAndGet()
    }

    fun isCurrent(candidateGeneration: Long): Boolean =
        generation.get() == candidateGeneration
}
