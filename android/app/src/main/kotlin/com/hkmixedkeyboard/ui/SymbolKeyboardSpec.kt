package com.hkmixedkeyboard.ui

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
        "0" to "數字零", "1" to "數字一", "2" to "數字二", "3" to "數字三", "4" to "數字四",
        "5" to "數字五", "6" to "數字六", "7" to "數字七", "8" to "數字八", "9" to "數字九",
        "⁰" to "上標零", "¹" to "上標一", "²" to "上標二", "³" to "上標三",
        "①" to "圈一", "②" to "圈二", "③" to "圈三", "④" to "圈四", "⑤" to "圈五",
        "⑥" to "圈六", "⑦" to "圈七", "⑧" to "圈八", "⑨" to "圈九",
        "零" to "中文零", "○" to "圈零",
        "一" to "中文一", "二" to "中文二", "三" to "中文三", "四" to "中文四", "五" to "中文五",
        "六" to "中文六", "七" to "中文七", "八" to "中文八", "九" to "中文九",
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

    // Digits lead the first page, the way iOS's 123 page does. They used to live
    // only on the main keyboard's number row, so turning that row off left no way
    // to type a number at all. Long-pressing a digit reaches its superscript, and
    // 0 reaches the full-width form used in Chinese typesetting.
    private val digitLongPressAlternatives = mapOf(
        "1" to listOf("¹", "①", "一"),
        "2" to listOf("²", "②", "二"),
        "3" to listOf("³", "③", "三"),
        "4" to listOf("④", "四"),
        "5" to listOf("⑤", "五"),
        "6" to listOf("⑥", "六"),
        "7" to listOf("⑦", "七"),
        "8" to listOf("⑧", "八"),
        "9" to listOf("⑨", "九"),
        "0" to listOf("⁰", "○", "零")
    )

    private val commonRows = listOf(
        listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
        listOf("，", "。", "？", "！", "、", "：", "；", "…", "—", "·"),
        listOf("「", "」", "『", "』", "“", "”", "‘", "’", "《", "》"),
        listOf("@", "#", "$", "%", "&", "*", "-", "+", "=", "_")
    ).map { row ->
        row.map { symbol ->
            textKey(
                symbol,
                (commonLongPressAlternatives[symbol]
                    ?: digitLongPressAlternatives[symbol]).orEmpty()
            )
        }
    }

    // ASCII brackets moved here to make room for the digits. Their Chinese
    // counterparts stay one long press away on this same row.
    private val extendedRows = listOf(
        listOf("(", ")", "[", "]", "{", "}", "<", ">", "/", "\\"),
        listOf("~", "`", "^", "|", "\\", "°", "•", "©", "®", "™"),
        listOf("±", "×", "÷", "≠", "≈", "≤", "≥", "√", "∞", "%"),
        listOf("€", "£", "¥", "₩", "₹", "¢", "§", "¶", "#", "@")
    ).map { row ->
        row.map { symbol -> textKey(symbol, commonLongPressAlternatives[symbol].orEmpty()) }
    }

    private val pages = mapOf(
        SymbolPage.COMMON to SymbolPageSpec(SymbolPage.COMMON, commonRows),
        SymbolPage.EXTENDED to SymbolPageSpec(SymbolPage.EXTENDED, extendedRows)
    )

    fun page(page: SymbolPage): SymbolPageSpec = pages.getValue(page)

    fun pageAnnouncement(page: SymbolPage): String = when (page) {
        SymbolPage.COMMON -> "符號第 1 頁，數字及共用標點"
        SymbolPage.EXTENDED -> "符號第 2 頁，括號、數學及貨幣符號"
    }

    fun accessibilityDescription(key: SymbolKeySpec): String =
        if (key.longPressAlternatives.isEmpty()) key.accessibilityLabel
        else "${key.accessibilityLabel}，長按可選 ${key.longPressAlternatives.joinToString("、") { it.accessibilityLabel }}"

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
