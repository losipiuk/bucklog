package net.osipiuk.bucklog.ui.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import net.osipiuk.bucklog.ui.AppIcons
import net.osipiuk.bucklog.ui.LocalStrings

@Composable
fun InviteScreen(vm: InviteViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsState()
    val s = LocalStrings.current
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(AppIcons.ArrowBack, contentDescription = s.back) }
            Text(s.inviteTitle(state.sheetName), style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(s.inviteHelp, style = MaterialTheme.typography.bodyMedium)
            OutlinedTextField(
                value = state.email,
                onValueChange = vm::onEmailChange,
                label = { Text(s.theirEmail) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { vm.shareAndInvite() }),
                modifier = Modifier.fillMaxWidth(),
            )
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            state.info?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            Button(
                onClick = vm::shareAndInvite,
                enabled = !state.busy && state.email.isNotBlank(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text(s.shareAndInvite) }
            TextButton(onClick = { vm.justSendLink() }, enabled = !state.busy) { Text(s.justSendLink) }
        }
    }
}
