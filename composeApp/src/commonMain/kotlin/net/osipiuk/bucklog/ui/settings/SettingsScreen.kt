package net.osipiuk.bucklog.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.components.EmojiPicker
import net.osipiuk.bucklog.ui.history.describe

private sealed interface Confirm {
    data class SwitchSheet(val pending: Long) : Confirm
    data class SignOut(val pending: Long) : Confirm
    data object DemoData : Confirm
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val syncing by vm.syncing.collectAsState()
    val ui = state ?: return
    val scope = rememberCoroutineScope()
    var name by remember(ui.config.myName) { mutableStateOf(ui.config.myName.orEmpty()) }
    var editing by remember { mutableStateOf<Pair<String?, Category>?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = "Back") }
            Text("Settings", style = MaterialTheme.typography.titleLarge)
        }
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("You")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Your name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { vm.saveName(name) }, enabled = name.isNotBlank() && name.trim() != ui.config.myName) { Text("Save") }
            }
            Text(
                "Used for the Who column of new expenses. Existing ones keep their name (edit them in History).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text("Google account: ${ui.config.accountEmail}", style = MaterialTheme.typography.bodyMedium)

            Section("Family sheet")
            Text("${ui.config.spreadsheetName} · main currency ${ui.config.mainCurrency}", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui.sync.describe(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = if (ui.sync.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = vm::syncNow, enabled = !syncing) { Text(if (syncing) "Syncing…" else "Sync now") }
            }
            Button(onClick = vm::openSheet, modifier = Modifier.fillMaxWidth()) {
                Text("Open “${ui.config.spreadsheetName}” in Google Sheets ↗")
            }
            TextButton(onClick = { scope.launch { confirm = Confirm.SwitchSheet(vm.pendingChanges()) } }) { Text("Use another sheet…") }

            Section("Categories")
            Text(
                "Renaming updates every expense in that category. Archived categories are hidden when adding.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Column {
                ui.categories.forEach { c ->
                    Row(
                        Modifier.fillMaxWidth().clickable { editing = c.name to c }.padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.width(40.dp), contentAlignment = Alignment.Center) { Text(c.emoji ?: c.name.take(1), style = MaterialTheme.typography.titleMedium) }
                        Text(c.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f),
                            color = if (c.archived) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface)
                        if (c.archived) Text("archived", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
                TextButton(onClick = { editing = null to Category("", null) }) { Text("＋ Add category") }
            }

            if (vm.isDebugBuild) {
                Section("Developer")
                OutlinedButton(onClick = { confirm = Confirm.DemoData }) { Text("Add demo expenses (6 months)") }
            }

            Section("")
            TextButton(onClick = { scope.launch { confirm = Confirm.SignOut(vm.pendingChanges()) } }) {
                Text("Sign out", color = MaterialTheme.colorScheme.error)
            }
        }
    }

    editing?.let { (oldName, category) ->
        CategoryDialog(
            initial = category,
            isNew = oldName == null,
            validate = { vm.validate(oldName, it) },
            onSave = { vm.saveCategory(oldName, it); editing = null },
            onDismiss = { editing = null },
        )
    }
    confirm?.let { c ->
        val (title, message, action) = when (c) {
            is Confirm.SwitchSheet -> Triple("Use another sheet?", clearedMessage(c.pending), vm::switchSheet)
            is Confirm.SignOut -> Triple("Sign out?", clearedMessage(c.pending), vm::signOut)
            Confirm.DemoData -> Triple(
                "Add demo expenses?",
                "About 400 fake expenses over the last 6 months will be added and synced to “${ui.config.spreadsheetName}”. " +
                    "Use a separate demo sheet unless you want them in your family sheet.",
                vm::addDemoData,
            )
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text("Continue", color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}

private fun clearedMessage(pending: Long) =
    "Local data on this phone is cleared; the family sheet isn't touched." +
        if (pending > 0) "\n\n⚠️ $pending change(s) haven't reached the sheet yet and will be lost. Sync first." else ""

@Composable
private fun Section(title: String) {
    if (title.isNotEmpty()) Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun CategoryDialog(
    initial: Category,
    isNew: Boolean,
    validate: (Category) -> String?,
    onSave: (Category) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial.name) }
    var emoji by remember { mutableStateOf(initial.emoji.orEmpty()) }
    var archived by remember { mutableStateOf(initial.archived) }
    val updated = Category(name, emoji.ifBlank { null }, archived)
    val error = validate(updated)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) "New category" else "Edit category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        emoji,
                        { emoji = it.take(8) },
                        label = { Text("Emoji") },
                        placeholder = { Text("any") },
                        singleLine = true,
                        modifier = Modifier.width(110.dp),
                    )
                    TextButton(onClick = { emoji = "" }, enabled = emoji.isNotEmpty()) { Text("None") }
                }
                EmojiPicker(selected = emoji, onPick = { emoji = it }, modifier = Modifier.height(240.dp))
                if (!isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Archived", modifier = Modifier.weight(1f))
                        Switch(checked = archived, onCheckedChange = { archived = it })
                    }
                }
                if (name.isNotBlank() && error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(updated) }, enabled = error == null && updated != initial) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
