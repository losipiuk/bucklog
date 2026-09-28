package net.osipiuk.bucklog.domain

/**
 * Cold-start category guessing from the product name itself, for items with no history yet.
 * Keys are the default categories (see [DefaultCategories]); a hint only applies when the sheet
 * still has a category with that default Polish or English name. Keywords are normalized:
 * up to 3 chars must match a whole word, longer ones match a word prefix.
 */
object CategoryHints {
    private class Hint(val names: Set<String>, val keywords: List<String>)

    private val hints = listOf(
        hint(
            "spozywcze", "groceries",
            "mleko mleczn chleb bulk bulecz maslo ser sery jaj jogurt kefir smietan twarog warzyw owoc jabl banan pomidor " +
                "ogor ziemniak cebul marchew mieso mielon kurczak wolowin wieprzow wedlin szynk kielbas parowk ryb makaron " +
                "ryz maka cukier sol herbat woda wody sok soki piwo wino alkohol napoj slodycz czekolad chips zakupy spozyw " +
                "biedronk lidl zabk auchan carrefour kaufland dino stokrotk lewiatan netto aldi frisco " +
                "milk bread butter cheese egg eggs yogurt vegetabl fruit apple banana tomato potato meat chicken beef pork " +
                "fish pasta rice flour sugar tea water juice beer wine grocer supermarket snack",
        ),
        hint(
            "restauracje", "eating out",
            "restaurac obiad lunch kolacj pizz kebab burger sushi kawiarn bistro stolowk lody pyszne glovo wolt " +
                "mcdonald kfc starbucks costa subway restaurant dinner takeaway cafe",
        ),
        hint(
            "transport", "transport",
            "paliw benzyn diesel orlen bp shell circle lotos moya tankowan parking parkomat autobus tramwaj metro pociag " +
                "pkp intercity koleje taxi taksowk uber bolt myjni wulkaniz opon mechanik przeglad autostrad winiet " +
                "fuel petrol gasolin train bus tram subway tyre tire carwash toll",
        ),
        hint(
            "dom", "home",
            "ikea castorama leroy obi jysk meble mebel remont farb narzedz sprzatan chemia proszek plyn papier recznik " +
                "posciel zarowk ogrod kwiatek doniczk furnitur cleaning detergent tools paint garden",
        ),
        hint(
            "rachunki", "bills",
            "prad gaz czynsz internet telefon abonament netflix spotify hbo disney youtube ubezpiecz oplat rachunek " +
                "podatek smieci electricity rent insurance subscription tax",
        ),
        hint(
            "zdrowie", "health",
            "apteka apteki lek leki lekarz dentyst stomatolog witamin badani szpital okulist okulary soczewk rehabilit " +
                "fizjo przychodn recept pharmacy doctor dentist medicin vitamin hospital",
        ),
        hint(
            "dzieci", "kids",
            "przedszkol szkol zlobek pieluch zabawk lego podrecznik kolonie oboz korepetyc niania " +
                "diaper toy toys school kindergarten nursery babysit",
        ),
        hint(
            "ubrania", "clothes",
            "buty butow spodnie koszul kurtk sukienk bluz skarpet bielizn czapk zara reserved ccc deichmann sinsay " +
                "shoes shirt jacket dress jeans socks clothes",
        ),
        hint(
            "rozrywka", "fun",
            "kino teatr koncert gra gry ksiazk basen silowni fitness muzeum zoo kregiel escape " +
                "cinema theatre concert game book pool gym museum steam playstation",
        ),
        hint(
            "podroze", "travel",
            "hotel nocleg airbnb booking lot samolot ryanair wizz lotnisk wakacj urlop " +
                "flight airline airport holiday vacation hostel",
        ),
        hint(
            "prezenty", "gifts",
            "prezent kwiaty urodzin imienin slub gift flowers birthday present wedding",
        ),
    )

    private fun hint(pl: String, en: String, keywords: String) =
        Hint(setOf(pl, en), keywords.split(' ').filter { it.isNotBlank() })

    /** Suggests one of [categories] for [what], or null when nothing fits. */
    fun guess(what: String, categories: Collection<String>): String? {
        val words = normalizeText(what).split(' ').filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val byNormalizedName = categories.associateBy { normalizeText(it) }
        // Typing a category's own name picks it.
        words.firstNotNullOfOrNull { byNormalizedName[it] }?.let { return it }
        byNormalizedName[words.joinToString(" ")]?.let { return it }
        for (hint in hints) {
            val target = hint.names.firstNotNullOfOrNull { byNormalizedName[it] } ?: continue
            val matches = hint.keywords.any { keyword ->
                words.any { word -> if (keyword.length <= 3) word == keyword else word.startsWith(keyword) }
            }
            if (matches) return target
        }
        return null
    }
}
