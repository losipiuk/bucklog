package net.osipiuk.bucklog.ui.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import net.osipiuk.bucklog.ui.LocalStrings
import net.osipiuk.bucklog.ui.Strings

@Composable
fun SetupScreen(vm: SetupViewModel) {
    val state by vm.state.collectAsState()
    val s = LocalStrings.current
    LaunchedEffect(Unit) { vm.resume() }
    Column(
        Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        Text("💸", fontSize = 56.sp)
        AnimatedContent(state.step, label = "setup-step") { step ->
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                when (step) {
                    SetupStep.WELCOME -> Welcome(state, vm, s)
                    SetupStep.SHEET -> ChooseSheet(state, vm, s)
                    SetupStep.NAME -> YourName(state, vm, s)
                }
            }
        }
        state.error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.weight(1f))
        if (state.busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ColumnScope.Welcome(state: SetupUiState, vm: SetupViewModel, s: Strings) {
    Text("Bucklog", style = MaterialTheme.typography.displaySmall)
    Text(s.tagline, style = MaterialTheme.typography.bodyLarge)
    state.invite?.let { Text(s.invitedBy(it.from, it.sheetName), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary) }
    PrimaryButton(s.signInWithGoogle, enabled = !state.busy, onClick = vm::signIn)
}

@Composable
private fun ColumnScope.ChooseSheet(state: SetupUiState, vm: SetupViewModel, s: Strings) {
    Text(s.familySheet, style = MaterialTheme.typography.headlineMedium)
    Text(s.signedInAs(state.account.orEmpty()), style = MaterialTheme.typography.bodyMedium)
    state.invite?.let { invite ->
        Text(s.invitedBy(invite.from, invite.sheetName), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        PrimaryButton(s.joinSheet(invite.sheetName), enabled = !state.busy, onClick = vm::joinInvite)
    }
    if (state.invite == null) PrimaryButton(s.createFamilySheet, enabled = !state.busy, onClick = vm::createSheet)
    Text(s.alreadyHaveOne, style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = state.link,
        onValueChange = vm::onLinkChange,
        label = { Text(s.sheetLinkOptional) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = vm::pickSheet,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text(s.chooseSharedSheet) }
    TextButton(onClick = vm::useOtherAccount, enabled = !state.busy) { Text(s.useAnotherAccount) }
}

@Composable
private fun ColumnScope.YourName(state: SetupUiState, vm: SetupViewModel, s: Strings) {
    Text(s.yourName, style = MaterialTheme.typography.headlineMedium)
    Text(s.connectedTo(state.sheetName.orEmpty()), style = MaterialTheme.typography.bodyMedium)
    OutlinedTextField(
        value = state.name,
        onValueChange = vm::onNameChange,
        label = { Text(s.name) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.finish() }),
        modifier = Modifier.fillMaxWidth(),
    )
    PrimaryButton(s.start, enabled = !state.busy && state.name.isNotBlank(), onClick = vm::finish)
    TextButton(onClick = vm::chooseOtherSheet, enabled = !state.busy) { Text(s.chooseDifferentSheet) }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
