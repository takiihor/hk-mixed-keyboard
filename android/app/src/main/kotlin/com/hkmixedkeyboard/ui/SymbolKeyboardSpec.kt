package com.hkmixedkeyboard.ui

import java.util.Locale

enum class SymbolPage {
    COMMON,
    EXTENDED;

    fun toggled(): SymbolPage = if (this == COMMON) EXTENDED else COMMON
}

enum class SymbolKeyRole {
    TEXT,
    RETURN_TO_ALPHABET,
    TOGGLE_PAGE,
    SPACE,
    BACKSPACE,
    ENTER
}

enum class SymbolKeyIcon {
    BACKSPACE,
    RETURN,
    SEARCH,
    SEND,
    NEXT,
    DONE,
    GO
}

data class SymbolKeySpec(
    val label: String,
    val commitText: String? = null,
    val accessibilityLabel: String,
    val role: SymbolKeyRole = SymbolKeyRole.TEXT,
    val longPressAlternatives: List<SymbolKeySpec> = emptyList(),
    val pageIndicator: List<Boolean> = emptyList(),
    val widthWeight: Float = 1f,
    val icon: SymbolKeyIcon? = null
)

data class SymbolPageSpec(
    val page: SymbolPage,
    val rows: List<List<SymbolKeySpec>>
)

/** Immutable source of symbol content and semantics; the view only renders it. */
object SymbolKeyboardSpec {
    val bottomKeyWeights = listOf(1.25f, 0.90f, 2.60f, 1.05f, 1.20f)

    private val labels = mapOf(
        "，" to "中文逗號", "。" to "中文句號", "？" to "中文問號", "！" to "中文驚嘆號",
        "、" to "頓號", "：" to "冒號", "；" to "分號", "…" to "省略號", "—" to "破折號", "·" to "間隔號",
        "「" to "左單引號", "」" to "右單引號", "『" to "左雙引號", "』" to "右雙引號",
        "“" to "左雙引號", "”" to "右雙引號", "‘" to "左單引號", "’" to "右單引號",
        "《" to "左書名號", "》" to "右書名號",
        "@" to "at 符號", "#" to "井字號", "$" to "美元符號", "%" to "百分號", "&" to "and 符號",
        "*" to "星號", "-" to "連字號", "+" to "加號", "=" to "等號", "_" to "底線",
        "(" to "左圓括號", ")" to "右圓括號", "[" to "左方括號", "]" to "右方括號",
        "{" to "左大括號", "}" to "右大括號", "<" to "小於號", ">" to "大於號",
        "/" to "斜線", "\\" to "反斜線",
        "~" to "波浪號", "`" to "反引號", "^" to "脫字符", "|" to "豎線", "°" to "度數符號",
        "•" to "圓點", "©" to "版權符號", "®" to "註冊商標符號", "™" to "商標符號",
        "±" to "正負號", "×" to "乘號", "÷" to "除號", "≠" to "不等於", "≈" to "約等於",
        "≤" to "小於或等於", "≥" to "大於或等於", "√" to "平方根", "∞" to "無限大",
        "€" to "歐元符號", "£" to "英鎊符號", "¥" to "日圓或人民幣符號", "₩" to "韓元符號",
        "₹" to "印度盧比符號", "¢" to "美分符號", "§" to "章節符號", "¶" to "段落符號",
        "〔" to "左六角括號", "〕" to "右六角括號", "〈" to "左尖括號", "〉" to "右尖括號",
        "【" to "左方頭括號", "】" to "右方頭括號", "〖" to "左空心方頭括號", "〗" to "右空心方頭括號",
        "（" to "左全形圓括號", "）" to "右全形圓括號",
        "," to "英文逗號", ";" to "英文分號", "." to "英文句點", "．" to "全形句點",
        "?" to "英文問號", "!" to "英文驚嘆號",
        "\"" to "雙引號", "„" to "低雙引號", "«" to "左書名雙角引號", "»" to "右書名雙角引號",
        "'" to "英文單引號", "–" to "短破折號", "−" to "減號"
    )

    private fun textKey(text: String, alternatives: List<String> = emptyList()): SymbolKeySpec =
        SymbolKeySpec(
            label = text,
            commitText = text,
            accessibilityLabel = labels.getValue(text),
            longPressAlternatives = alternatives.map { alternative ->
                SymbolKeySpec(
                    label = alternative,
                    commitText = alternative,
                    accessibilityLabel = labels.getValue(alternative)
                )
            }
        )

    private val commonLongPressAlternatives = mapOf(
        "，" to listOf(",", "、", ";", "："),
        "。" to listOf(".", "·", "…", "．"),
        "？" to listOf("?"),
        "！" to listOf("!"),
        "“" to listOf("\"", "„", "«"),
        "”" to listOf("\"", "»"),
        "‘" to listOf("'", "`"),
        "’" to listOf("'"),
        "-" to listOf("–", "—", "−"),
        "$" to listOf("€", "£", "¥", "₩", "₹", "¢"),
        "(" to listOf("[", "{", "<", "（", "〔", "【"),
        ")" to listOf("]", "}", ">", "）", "〕", "】"),
        "<" to listOf("〈", "《", "≤"),
        ">" to listOf("〉", "》", "≥"),
        "/" to listOf("\\", "|", "÷")
    )

    private val commonRows = listOf(
        listOf("，", "。", "？", "！", "、", "：", "；", "…", "—", "·"),
        listOf("「", "」", "『", "』", "“", "”", "‘", "’", "《", "》"),
        listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "_"),
        listOf("(", ")", "[", "]", "{", "}", "<", ">", "/", "\\")
    ).map { row ->
        row.map { symbol ->
            textKey(symbol, commonLongPressAlternatives[symbol].orEmpty())
        }
    }

    private val extendedRows = listOf(
        listOf("~", "`", "^", "|", "\\", "°", "•", "©", "®", "™"),
        listOf("±", "×", "÷", "≠", "≈", "≤", "≥", "√", "∞", "%"),
        listOf("€", "£", "¥", "₩", "₹", "¢", "§", "¶", "#", "@"),
        listOf("〔", "〕", "〈", "〉", "【", "】", "〖", "〗", "（", "）")
    ).map { row -> row.map(::textKey) }

    private val pages = mapOf(
        SymbolPage.COMMON to SymbolPageSpec(SymbolPage.COMMON, commonRows),
        SymbolPage.EXTENDED to SymbolPageSpec(SymbolPage.EXTENDED, extendedRows)
    )

    fun page(page: SymbolPage): SymbolPageSpec = pages.getValue(page)

    fun pageAnnouncement(page: SymbolPage, english: Boolean = false): String =
        if (english) {
            when (page) {
                SymbolPage.COMMON -> "Symbols page 1, common punctuation"
                SymbolPage.EXTENDED -> "Symbols page 2, mathematics, currency and special symbols"
            }
        } else {
            when (page) {
                SymbolPage.COMMON -> "符號第 1 頁，共用標點"
                SymbolPage.EXTENDED -> "符號第 2 頁，數學貨幣及特殊符號"
            }
        }

    fun accessibilityDescription(key: SymbolKeySpec, english: Boolean = false): String {
        val base = if (english) englishAccessibilityLabel(key) else key.accessibilityLabel
        if (key.longPressAlternatives.isEmpty()) return base
        val alternatives = key.longPressAlternatives.joinToString(if (english) ", " else "、") {
            if (english) englishAccessibilityLabel(it) else it.accessibilityLabel
        }
        return if (english) "$base; hold for $alternatives" else "$base，長按可選 $alternatives"
    }

    fun enterActionDescription(action: SymbolEnterAction, english: Boolean = false): String =
        accessibilityDescription(action.keySpec(), english)

    private fun englishAccessibilityLabel(key: SymbolKeySpec): String = when (key.role) {
        SymbolKeyRole.TEXT -> key.commitText?.let(::unicodeName) ?: "Symbol"
        SymbolKeyRole.RETURN_TO_ALPHABET -> "Return to alphabet keyboard"
        SymbolKeyRole.TOGGLE_PAGE -> if (key.pageIndicator.firstOrNull() == true) {
            "Switch to symbols page 2"
        } else {
            "Switch to symbols page 1"
        }
        SymbolKeyRole.SPACE -> "Space"
        SymbolKeyRole.BACKSPACE -> "Delete"
        SymbolKeyRole.ENTER -> when (key.accessibilityLabel) {
            "搜尋" -> "Search"
            "傳送" -> "Send"
            "下一步" -> "Next"
            "完成" -> "Done"
            "前往" -> "Go"
            else -> "Enter"
        }
    }

    private fun unicodeName(text: String): String {
        val codePoint = text.codePointAt(0)
        return Character.getName(codePoint)
            ?.lowercase(Locale.ENGLISH)
            ?.replaceFirstChar { it.titlecase(Locale.ENGLISH) }
            ?: "Symbol $text"
    }

    fun pageKey(page: SymbolPage): SymbolKeySpec = when (page) {
        SymbolPage.COMMON -> SymbolKeySpec(
            label = "1/2",
            accessibilityLabel = "切換至符號第 2 頁",
            role = SymbolKeyRole.TOGGLE_PAGE,
            pageIndicator = listOf(true, false),
            widthWeight = bottomKeyWeights[1]
        )
        SymbolPage.EXTENDED -> SymbolKeySpec(
            label = "#+=",
            accessibilityLabel = "切換至符號第 1 頁",
            role = SymbolKeyRole.TOGGLE_PAGE,
            pageIndicator = listOf(false, true),
            widthWeight = bottomKeyWeights[1]
        )
    }

    fun bottomKeys(page: SymbolPage, enter: SymbolKeySpec): List<SymbolKeySpec> = listOf(
        SymbolKeySpec(
            label = "ABC",
            accessibilityLabel = "返回英文鍵盤",
            role = SymbolKeyRole.RETURN_TO_ALPHABET,
            widthWeight = bottomKeyWeights[0]
        ),
        pageKey(page),
        SymbolKeySpec(
            label = "空格",
            accessibilityLabel = "空格",
            role = SymbolKeyRole.SPACE,
            widthWeight = bottomKeyWeights[2]
        ),
        SymbolKeySpec(
            label = "",
            accessibilityLabel = "刪除",
            role = SymbolKeyRole.BACKSPACE,
            widthWeight = bottomKeyWeights[3],
            icon = SymbolKeyIcon.BACKSPACE
        ),
        enter.copy(widthWeight = bottomKeyWeights[4], role = SymbolKeyRole.ENTER)
    )
}
