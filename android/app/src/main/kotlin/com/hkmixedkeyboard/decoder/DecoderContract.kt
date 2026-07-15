package com.hkmixedkeyboard.decoder

enum class Scheme { QUICK, CANGJIE, JYUTPING, PINYIN, MIXED_EXPERIMENTAL }

enum class SourceSchema {
    QUICK, CANGJIE, MIXED_PHRASE, ENGLISH, USER_MEMORY, ENGLISH_ASSIST,
    JYUTPING, PINYIN, CHINESE_ASSIST, HKSCS_UNICODE,
    CUSTOM_QUICK, CUSTOM_JYUTPING, CUSTOM_PINYIN, PINYIN_CORRECTION
}

enum class CandidateType {
    CHAR, PHRASE, MIXED_PHRASE, EN_LITERAL, EN_AUTOCOMPLETE, ENGLISH_ASSIST,
    JYUTPING, PINYIN, CHINESE_ASSIST
}

interface DecoderContract {
    val schemeName: String
    fun decode(buffer: String, scheme: Scheme): DecodeResult
    fun isSchemeAvailable(scheme: Scheme): Boolean
}
