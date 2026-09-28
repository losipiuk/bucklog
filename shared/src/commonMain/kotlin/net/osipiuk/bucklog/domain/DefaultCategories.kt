package net.osipiuk.bucklog.domain

/** Starter categories for a newly created family sheet; editable in the sheet afterwards. */
object DefaultCategories {
    fun forLanguage(language: String): List<Category> =
        (if (language == "pl") polish else english).map { (emoji, name) -> Category(name, emoji) }

    private val polish = listOf(
        "🛒" to "Spożywcze", "☕" to "Restauracje", "⛽" to "Transport", "🏠" to "Dom",
        "💡" to "Rachunki", "💊" to "Zdrowie", "🧸" to "Dzieci", "👕" to "Ubrania",
        "🎬" to "Rozrywka", "✈️" to "Podróże", "🎁" to "Prezenty", "📦" to "Inne",
    )

    private val english = listOf(
        "🛒" to "Groceries", "☕" to "Eating out", "⛽" to "Transport", "🏠" to "Home",
        "💡" to "Bills", "💊" to "Health", "🧸" to "Kids", "👕" to "Clothes",
        "🎬" to "Fun", "✈️" to "Travel", "🎁" to "Gifts", "📦" to "Other",
    )
}
