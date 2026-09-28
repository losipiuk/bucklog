package net.osipiuk.bucklog.domain

private val foldMap: Map<Char, String> = buildMap {
    fun map(from: String, to: String) = from.forEach { put(it, to) }
    map("ąàáâãäåā", "a"); map("ćçč", "c"); map("ďđ", "d"); map("ęèéêëēě", "e")
    map("ìíîïī", "i"); map("ł", "l"); map("ńñň", "n"); map("òóôõöøō", "o")
    map("řŕ", "r"); map("śšş", "s"); map("ťţ", "t"); map("ùúûüūů", "u")
    map("ýÿ", "y"); map("źżž", "z"); put('ß', "ss"); put('æ', "ae"); put('œ', "oe")
}

/** Case- and diacritic-insensitive form used for matching ("Żabka  Kawa" → "zabka kawa"). */
fun normalizeText(text: String): String = buildString {
    var lastSpace = true
    for (c in text.lowercase()) {
        if (c.isWhitespace()) {
            if (!lastSpace) append(' ')
            lastSpace = true
        } else {
            append(foldMap[c] ?: c.toString())
            lastSpace = false
        }
    }
}.trimEnd()
