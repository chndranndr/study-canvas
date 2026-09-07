package dev.studycanvas.app.writing

interface KanaContentRepository {
    fun getHiragana(): List<KanaCharacter>
    fun getKatakana(): List<KanaCharacter>
    fun getPracticePrompts(type: KanaType): List<WritingPracticePrompt>
}

class BundledKanaWritingRepository : KanaContentRepository {

    private val hiraganaList = listOf(
        // Vowels
        KanaCharacter("あ", "a", KanaType.HIRAGANA),
        KanaCharacter("い", "i", KanaType.HIRAGANA),
        KanaCharacter("う", "u", KanaType.HIRAGANA),
        KanaCharacter("え", "e", KanaType.HIRAGANA),
        KanaCharacter("お", "o", KanaType.HIRAGANA),
        // K-row
        KanaCharacter("か", "ka", KanaType.HIRAGANA),
        KanaCharacter("き", "ki", KanaType.HIRAGANA),
        KanaCharacter("く", "ku", KanaType.HIRAGANA),
        KanaCharacter("け", "ke", KanaType.HIRAGANA),
        KanaCharacter("こ", "ko", KanaType.HIRAGANA),
        // S-row
        KanaCharacter("さ", "sa", KanaType.HIRAGANA),
        KanaCharacter("し", "shi", KanaType.HIRAGANA),
        KanaCharacter("す", "su", KanaType.HIRAGANA),
        KanaCharacter("せ", "se", KanaType.HIRAGANA),
        KanaCharacter("そ", "so", KanaType.HIRAGANA),
        // T-row
        KanaCharacter("た", "ta", KanaType.HIRAGANA),
        KanaCharacter("ち", "chi", KanaType.HIRAGANA),
        KanaCharacter("つ", "tsu", KanaType.HIRAGANA),
        KanaCharacter("て", "te", KanaType.HIRAGANA),
        KanaCharacter("と", "to", KanaType.HIRAGANA),
        // N-row
        KanaCharacter("な", "na", KanaType.HIRAGANA),
        KanaCharacter("に", "ni", KanaType.HIRAGANA),
        KanaCharacter("ぬ", "nu", KanaType.HIRAGANA),
        KanaCharacter("ね", "ne", KanaType.HIRAGANA),
        KanaCharacter("の", "no", KanaType.HIRAGANA),
        // H-row
        KanaCharacter("は", "ha", KanaType.HIRAGANA),
        KanaCharacter("ひ", "hi", KanaType.HIRAGANA),
        KanaCharacter("ふ", "fu", KanaType.HIRAGANA),
        KanaCharacter("へ", "he", KanaType.HIRAGANA),
        KanaCharacter("ほ", "ho", KanaType.HIRAGANA),
        // M-row
        KanaCharacter("ま", "ma", KanaType.HIRAGANA),
        KanaCharacter("み", "mi", KanaType.HIRAGANA),
        KanaCharacter("む", "mu", KanaType.HIRAGANA),
        KanaCharacter("め", "me", KanaType.HIRAGANA),
        KanaCharacter("も", "mo", KanaType.HIRAGANA),
        // Y-row
        KanaCharacter("や", "ya", KanaType.HIRAGANA),
        KanaCharacter("ゆ", "yu", KanaType.HIRAGANA),
        KanaCharacter("よ", "yo", KanaType.HIRAGANA),
        // R-row
        KanaCharacter("ら", "ra", KanaType.HIRAGANA),
        KanaCharacter("り", "ri", KanaType.HIRAGANA),
        KanaCharacter("る", "ru", KanaType.HIRAGANA),
        KanaCharacter("れ", "re", KanaType.HIRAGANA),
        KanaCharacter("ろ", "ro", KanaType.HIRAGANA),
        // W-row & N
        KanaCharacter("わ", "wa", KanaType.HIRAGANA),
        KanaCharacter("を", "wo", KanaType.HIRAGANA),
        KanaCharacter("ん", "n", KanaType.HIRAGANA),
    )

    private val katakanaList = listOf(
        // Vowels
        KanaCharacter("ア", "a", KanaType.KATAKANA),
        KanaCharacter("イ", "i", KanaType.KATAKANA),
        KanaCharacter("ウ", "u", KanaType.KATAKANA),
        KanaCharacter("エ", "e", KanaType.KATAKANA),
        KanaCharacter("オ", "o", KanaType.KATAKANA),
        // K-row
        KanaCharacter("カ", "ka", KanaType.KATAKANA),
        KanaCharacter("キ", "ki", KanaType.KATAKANA),
        KanaCharacter("ク", "ku", KanaType.KATAKANA),
        KanaCharacter("ケ", "ke", KanaType.KATAKANA),
        KanaCharacter("コ", "ko", KanaType.KATAKANA),
        // S-row
        KanaCharacter("サ", "sa", KanaType.KATAKANA),
        KanaCharacter("シ", "shi", KanaType.KATAKANA),
        KanaCharacter("ス", "su", KanaType.KATAKANA),
        KanaCharacter("セ", "se", KanaType.KATAKANA),
        KanaCharacter("ソ", "so", KanaType.KATAKANA),
        // T-row
        KanaCharacter("タ", "ta", KanaType.KATAKANA),
        KanaCharacter("チ", "chi", KanaType.KATAKANA),
        KanaCharacter("ツ", "tsu", KanaType.KATAKANA),
        KanaCharacter("テ", "te", KanaType.KATAKANA),
        KanaCharacter("ト", "to", KanaType.KATAKANA),
        // N-row
        KanaCharacter("ナ", "na", KanaType.KATAKANA),
        KanaCharacter("ニ", "ni", KanaType.KATAKANA),
        KanaCharacter("ヌ", "nu", KanaType.KATAKANA),
        KanaCharacter("ネ", "ne", KanaType.KATAKANA),
        KanaCharacter("ノ", "no", KanaType.KATAKANA),
        // H-row
        KanaCharacter("ハ", "ha", KanaType.KATAKANA),
        KanaCharacter("ヒ", "hi", KanaType.KATAKANA),
        KanaCharacter("フ", "fu", KanaType.KATAKANA),
        KanaCharacter("ヘ", "he", KanaType.KATAKANA),
        KanaCharacter("ホ", "ho", KanaType.KATAKANA),
        // M-row
        KanaCharacter("マ", "ma", KanaType.KATAKANA),
        KanaCharacter("ミ", "mi", KanaType.KATAKANA),
        KanaCharacter("ム", "mu", KanaType.KATAKANA),
        KanaCharacter("メ", "me", KanaType.KATAKANA),
        KanaCharacter("モ", "mo", KanaType.KATAKANA),
        // Y-row
        KanaCharacter("ヤ", "ya", KanaType.KATAKANA),
        KanaCharacter("ユ", "yu", KanaType.KATAKANA),
        KanaCharacter("ヨ", "yo", KanaType.KATAKANA),
        // R-row
        KanaCharacter("ラ", "ra", KanaType.KATAKANA),
        KanaCharacter("リ", "ri", KanaType.KATAKANA),
        KanaCharacter("ル", "ru", KanaType.KATAKANA),
        KanaCharacter("レ", "re", KanaType.KATAKANA),
        KanaCharacter("ロ", "ro", KanaType.KATAKANA),
        // W-row & N
        KanaCharacter("ワ", "wa", KanaType.KATAKANA),
        KanaCharacter("ヲ", "wo", KanaType.KATAKANA),
        KanaCharacter("ン", "n", KanaType.KATAKANA),
    )

    override fun getHiragana(): List<KanaCharacter> = hiraganaList

    override fun getKatakana(): List<KanaCharacter> = katakanaList

    override fun getPracticePrompts(type: KanaType): List<WritingPracticePrompt> {
        val list = if (type == KanaType.HIRAGANA) hiraganaList else katakanaList
        val typeName = if (type == KanaType.HIRAGANA) "Hiragana" else "Katakana"

        return list.map { char ->
            WritingPracticePrompt(
                id = "${type.name.lowercase()}-${char.romaji}",
                category = typeName,
                promptEn = "Write $typeName character for \"${char.romaji}\"",
                hint = "Romaji: ${char.romaji}",
                acceptedAnswers = listOf(char.character),
            )
        }
    }
}
