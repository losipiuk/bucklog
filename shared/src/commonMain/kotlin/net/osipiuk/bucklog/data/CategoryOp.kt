package net.osipiuk.bucklog.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import net.osipiuk.bucklog.domain.Category

/** A queued change to the sheet's Categories tab. */
sealed interface CategoryOp {
    val seq: Long

    data class Add(override val seq: Long, val name: String) : CategoryOp

    /** Replace the row currently named [name] with [category]. */
    data class Update(override val seq: Long, val name: String, val category: Category) : CategoryOp
}

@Serializable
private data class CategoryJson(val name: String, val emoji: String? = null, val archived: Boolean = false)

internal object CategoryUpdate {
    fun encode(c: Category): String = Json.encodeToString(CategoryJson.serializer(), CategoryJson(c.name, c.emoji, c.archived))

    fun decode(json: String): Category = Json.decodeFromString(CategoryJson.serializer(), json).let { Category(it.name, it.emoji, it.archived) }
}
