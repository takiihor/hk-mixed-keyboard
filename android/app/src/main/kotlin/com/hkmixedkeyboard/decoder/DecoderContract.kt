package com.hkmixedkeyboard.decoder

enum class Scheme { QUICK, CANGJIE, JYUTPING, MIXED_EXPERIMENTAL }

enum class SourceSchema { QUICK, CANGJIE, MIXED_PHRASE, ENGLISH, USER_MEMORY, ENGLISH_ASSIST, JYUTPING }

enum class CandidateType { CHAR, PHRASE, MIXED_PHRASE, EN_LITERAL, EN_AUTOCOMPLETE, ENGLISH_ASSIST, JYUTPING }

interface DecoderContract {
    val schemeName: String
    fun decode(buffer: String, scheme: Scheme): DecodeResult
    fun isSchemeAvailable(scheme: Scheme): Boolean
}
