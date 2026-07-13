package com.hkmixedkeyboard.decoder

enum class Scheme { QUICK, CANGJIE, JYUTPING, PINYIN, MIXED_EXPERIMENTAL }

enum class SourceSchema { QUICK, CANGJIE, MIXED_PHRASE, ENGLISH, USER_MEMORY, ENGLISH_ASSIST, JYUTPING, PINYIN }

enum class CandidateType { CHAR, PHRASE, MIXED_PHRASE, EN_LITERAL, EN_AUTOCOMPLETE, ENGLISH_ASSIST, JYUTPING, PINYIN }

interface DecoderContract {
    val schemeName: String
    fun decode(buffer: String, scheme: Scheme): DecodeResult
    fun isSchemeAvailable(scheme: Scheme): Boolean
}
