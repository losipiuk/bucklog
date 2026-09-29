package net.osipiuk.bucklog.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** Emojis that make sense for spending categories, by theme. */
private val sections = listOf(
    "Food & shopping" to "🛒 🍎 🥖 🥩 🧀 🥛 🥦 🍕 🍔 🍣 🥗 ☕ 🍺 🍷 🍰 🍦 🍫 🥤 🍽️ 🛍️",
    "Transport" to "⛽ 🚗 🚕 🚌 🚆 🚇 🚲 🛴 🅿️ 🛞 🔧 🚙",
    "Home" to "🏠 🛋️ 🛏️ 🧹 🧺 🔨 🪴 🔌 🚿 🧴 🧻 🪑",
    "Bills & money" to "💡 📱 💻 📺 🌐 🧾 💳 🏦 💰 🔥 💧 📄",
    "Health & care" to "💊 🩺 🦷 👓 🏥 🧘 🏋️ 💆 💇 🧼",
    "Kids & family" to "🧸 👶 🍼 🎒 ✏️ 📚 🎨 🧩 🎈 👨‍👩‍👧",
    "Clothes" to "👕 👖 👗 👟 👠 🧥 🧦 👜 💄 🕶️",
    "Fun" to "🎬 🎭 🎵 🎮 📖 🎟️ 🏊 ⚽ 🎳 🎲 🎸 📷",
    "Travel" to "✈️ 🏨 🏖️ 🏔️ 🧳 🗺️ ⛺ 🚢",
    "Other" to "🎁 💐 🎉 🎂 🐶 🐱 🐾 ⛪ 🤝 📦 ⭐ ❓",
).map { (title, emojis) -> title to emojis.split(' ') }

@Composable
fun EmojiPicker(selected: String?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    LazyVerticalGrid(GridCells.Adaptive(44.dp), modifier) {
        for ((title, emojis) in sections) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
            }
            items(emojis.size) { i ->
                val emoji = emojis[i]
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (emoji == selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                        .clickable { onPick(emoji) },
                    contentAlignment = Alignment.Center,
                ) { Text(emoji, style = MaterialTheme.typography.titleLarge) }
            }
        }
    }
}
