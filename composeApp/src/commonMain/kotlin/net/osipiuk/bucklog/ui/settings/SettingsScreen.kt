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
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.LocalStrings
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.Strings
import net.osipiuk.bucklog.ui.clearedMessage
import net.osipiuk.bucklog.ui.components.EmojiPicker
import net.osipiuk.bucklog.ui.components.SyncProblemBanner
import net.osipiuk.bucklog.ui.history.describe
import net.osipiuk.bucklog.ui.history.isProblem
import net.osipiuk.bucklog.ui.shortLabel

private sealed interface Confirm {
    data class SwitchSheet(val pending: Long) : Confirm
    data object DemoData : Confirm
}

@Composable
fun SettingsScreen(vm: SettingsViewModel, graph: AppGraph, platform: Platform, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val s = LocalStrings.current
    val syncing by vm.syncing.collectAsState()
    val backingUp by vm.backingUp.collectAsState()
    val backup by vm.backup.collectAsState(null)
    val ui = state ?: return
    val scope = rememberCoroutineScope()
    var name by remember(ui.config.myName) { mutableStateOf(ui.config.myName.orEmpty()) }
    var editing by remember { mutableStateOf<Pair<String?, Category>?>(null) }
    var confirm by remember { mutableStateOf<Confirm?>(null) }
    // Number of my past expenses, while asking whether to rename them too.
    var renamePast by remember { mutableStateOf<Long?>(null) }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = s.back) }
            Text(s.settings, style = MaterialTheme.typography.titleLarge)
        }
        SyncProblemBanner(ui.sync, graph, platform)
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section(s.you)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(s.yourName) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.weight(1f),
                )
                TextButton(
                    onClick = {
                        scope.launch {
                            val past = vm.myPastEntries()
                            if (past > 0) renamePast = past else vm.saveName(name, renamePast = false)
                        }
                    },
                    enabled = name.isNotBlank() && name.trim() != ui.config.myName,
                ) { Text(s.save) }
            }
            Text(
                s.nameHelp,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(s.googleAccount(ui.config.accountEmail.orEmpty()), style = MaterialTheme.typography.bodyMedium)
            Text(s.language, style = MaterialTheme.typography.labelLarge)
            SingleChoiceSegmentedButtonRow {
                val options = listOf("system" to s.languageSystem, "en" to "English", "pl" to "Polski")
                options.forEachIndexed { i, (code, label) ->
                    SegmentedButton(
                        selected = ui.language == code,
                        onClick = { vm.setLanguage(code) },
                        shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        icon = {},
                    ) { Text(label) }
                }
            }

            Section(s.familySheet)
            Text(s.sheetInfo(ui.config.spreadsheetName.orEmpty(), ui.config.mainCurrency), style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(ui.sync.describe(s), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f),
                    color = if (ui.sync.isProblem()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = vm::syncNow, enabled = !syncing) { Text(if (syncing) s.syncing else s.syncNow) }
            }
            Button(onClick = vm::openSheet, modifier = Modifier.fillMaxWidth()) {
                Text(s.openInSheets(ui.config.spreadsheetName.orEmpty()))
            }
            TextButton(onClick = { scope.launch { confirm = Confirm.SwitchSheet(vm.pendingChanges()) } }) { Text(s.useAnotherSheet) }

            Section(s.backups)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(s.automaticBackups, modifier = Modifier.weight(1f))
                Switch(checked = backup?.enabled == true, onCheckedChange = vm::setAutomaticBackups)
            }
            Text(s.backupsHelp, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(verticalAlignment = Alignment.CenterVertically) {
                val error = backup?.error
                val last = backup?.lastBackup
                Text(
                    when {
                        error != null -> s.backupFailed(error)
                        last != null -> s.lastBackup(last.toLocalDateTime(TimeZone.currentSystemDefault()).shortLabel())
                        else -> s.noBackupYet
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = vm::backUpNow, enabled = !backingUp) { Text(if (backingUp) s.backingUp else s.backUpNow) }
            }
            backup?.folderId?.let { folder -> TextButton(onClick = { vm.openBackups(folder) }) { Text(s.openBackups) } }

            Section(s.categories)
            Text(
                s.categoriesHelp,
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
                        if (c.archived) Text(s.archivedLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    HorizontalDivider()
                }
                TextButton(onClick = { editing = null to Category("", null) }) { Text(s.addCategory) }
            }

            if (vm.isDebugBuild) {
                Section(s.developer)
                OutlinedButton(onClick = { confirm = Confirm.DemoData }) { Text(s.addDemo) }
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
    renamePast?.let { n ->
        AlertDialog(
            onDismissRequest = { renamePast = null },
            text = { Text(s.renamePastQuestion(n, ui.config.myName.orEmpty(), name.trim())) },
            confirmButton = { TextButton(onClick = { renamePast = null; vm.saveName(name, renamePast = true) }) { Text(s.renameAll) } },
            dismissButton = { TextButton(onClick = { renamePast = null; vm.saveName(name, renamePast = false) }) { Text(s.onlyNew) } },
        )
    }
    confirm?.let { c ->
        val (title, message, action) = when (c) {
            is Confirm.SwitchSheet -> Triple(s.useAnotherSheetQuestion, s.clearedMessage(c.pending), vm::switchSheet)
            Confirm.DemoData -> Triple(s.addDemoQuestion, s.addDemoText(ui.config.spreadsheetName.orEmpty()), vm::addDemoData)
        }
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text(title) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { confirm = null; action() }) { Text(s.continueLabel, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(s.cancel) } },
        )
    }
}

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
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isNew) s.newCategory else s.editCategory) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text(s.name) }, singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        emoji,
                        { emoji = it.take(8) },
                        label = { Text(s.emoji) },
                        placeholder = { Text(s.emojiAny) },
                        singleLine = true,
                        modifier = Modifier.width(110.dp),
                    )
                    TextButton(onClick = { emoji = "" }, enabled = emoji.isNotEmpty()) { Text(s.none) }
                }
                EmojiPicker(selected = emoji, onPick = { emoji = it }, modifier = Modifier.height(240.dp))
                if (!isNew) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(s.archived, modifier = Modifier.weight(1f))
                        Switch(checked = archived, onCheckedChange = { archived = it })
                    }
                }
                if (name.isNotBlank() && error != null) Text(error, color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(updated) }, enabled = error == null && updated != initial) { Text(s.save) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
