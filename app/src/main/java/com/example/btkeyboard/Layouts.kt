package com.example.btkeyboard

/**
 * label: 表示文字 / usage: HIDキーコード / mod: 修飾キーのビット(Shift=0x02, Ctrl=0x01, Alt=0x04, Win=0x08)
 * weight: 横幅の比率(大きくしたいキーは数字を増やす)
 */
data class Key(val label: String, val usage: Int = 0, val mod: Int = 0, val weight: Float = 1f)
data class KbLayout(val name: String, val rows: List<List<Key>>)

// 英数字キー
private fun c(ch: Char, w: Float = 1f): Key = when (ch) {
    in 'a'..'z' -> Key(ch.uppercase(), 0x04 + (ch - 'a'), weight = w)
    in '1'..'9' -> Key("$ch", 0x1E + (ch - '1'), weight = w)
    '0' -> Key("0", 0x27, weight = w)
    else -> error("unsupported: $ch")
}

private fun k(label: String, usage: Int, w: Float = 1f) = Key(label, usage, weight = w)
private fun m(label: String, mod: Int, w: Float = 1f) = Key(label, mod = mod, weight = w)

private val ESC = k("Esc", 0x29)
private val SPACE = k("Space", 0x2C, 6f)

object Layouts {
    /** ゲーム用: 必要なキーだけ。W/Sを大きく、Spaceを横いっぱいに */
    val game = KbLayout(
        "ゲーム", listOf(
            listOf(ESC, c('1'), c('2'), c('3'), c('4'), c('5')),
            listOf(k("Tab", 0x2B, 1.5f), c('q'), c('w', 1.5f), c('e'), c('r')),
            listOf(m("Shift", 0x02, 1.5f), c('a'), c('s', 1.5f), c('d'), c('f')),
            listOf(m("Ctrl", 0x01, 2f), c('z'), c('x'), c('c'), c('v')),
            listOf(SPACE)
        )
    )

    /** 通常のQWERTY(合計15ユニット幅) */
    val full = KbLayout(
        "フル", listOf(
            listOf(ESC) + "1234567890".map { c(it) } +
                listOf(k("-", 0x2D), k("=", 0x2E), k("BS", 0x2A, 2f)),
            listOf(k("Tab", 0x2B, 1.5f)) + "qwertyuiop".map { c(it) } +
                listOf(k("[", 0x2F), k("]", 0x30), k("\\", 0x31, 1.5f)),
            listOf(k("Caps", 0x39, 1.75f)) + "asdfghjkl".map { c(it) } +
                listOf(k(";", 0x33), k("'", 0x34), k("Enter", 0x28, 2.25f)),
            listOf(m("Shift", 0x02, 2.25f)) + "zxcvbnm".map { c(it) } +
                listOf(k(",", 0x36), k(".", 0x37), k("/", 0x38), k("↑", 0x52), m("Shift", 0x02, 1.75f)),
            listOf(
                m("Ctrl", 0x01, 1.5f), m("Win", 0x08, 1.5f), m("Alt", 0x04, 1.5f),
                k("Space", 0x2C, 7.5f), k("←", 0x50), k("↓", 0x51), k("→", 0x4F)
            )
        )
    )

    val all = listOf(game, full)
}
