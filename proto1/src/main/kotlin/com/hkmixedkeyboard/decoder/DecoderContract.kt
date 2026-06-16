package com.hkmixedkeyboard.decoder

enum class Scheme { QUICK, CANGJIE, MIXED_EXPERIMENTAL }

enum class SourceSchema { QUICK, CANGJIE, MIXED_PHRASE, ENGLISH, USER_MEMORY }

enum class CandidateType { CHAR, PHRASE, MIXED_PHRASE, EN_LITERAL, EN_AUTOCOMPLETE }

interface DecoderContract {
    val schemeName: String
    fun decode(buffer: String, scheme: Scheme): DecodeResult
    fun isSchemeAvailable(scheme: Scheme): Boolean
}
