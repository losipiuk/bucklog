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

@Composable
fun SetupScreen(vm: SetupViewModel) {
    val state by vm.state.collectAsState()
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
                    SetupStep.WELCOME -> Welcome(state, vm)
                    SetupStep.SHEET -> ChooseSheet(state, vm)
                    SetupStep.NAME -> YourName(state, vm)
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
private fun ColumnScope.Welcome(state: SetupUiState, vm: SetupViewModel) {
    Text("Bucklog", style = MaterialTheme.typography.displaySmall)
    Text(
        "Log family expenses in seconds. Everything lands in a Google Sheet your family shares.",
        style = MaterialTheme.typography.bodyLarge,
    )
    PrimaryButton("Sign in with Google", enabled = !state.busy, onClick = vm::signIn)
}

@Composable
private fun ColumnScope.ChooseSheet(state: SetupUiState, vm: SetupViewModel) {
    Text("Family sheet", style = MaterialTheme.typography.headlineMedium)
    Text("Signed in as ${state.account}", style = MaterialTheme.typography.bodyMedium)
    PrimaryButton("Create a new family sheet", enabled = !state.busy, onClick = vm::createSheet)
    Text(
        "Already have one? Someone in your family shares it with you, then you choose it here.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = state.link,
        onValueChange = vm::onLinkChange,
        label = { Text("Sheet link (optional)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedButton(
        onClick = vm::pickSheet,
        enabled = !state.busy,
        modifier = Modifier.fillMaxWidth().height(56.dp),
    ) { Text("Choose a shared sheet") }
    TextButton(onClick = vm::useOtherAccount, enabled = !state.busy) { Text("Use another Google account") }
}

@Composable
private fun ColumnScope.YourName(state: SetupUiState, vm: SetupViewModel) {
    Text("Your name", style = MaterialTheme.typography.headlineMedium)
    Text(
        "Connected to “${state.sheetName}”. Your name goes into the Who column of every expense you add.",
        style = MaterialTheme.typography.bodyMedium,
    )
    OutlinedTextField(
        value = state.name,
        onValueChange = vm::onNameChange,
        label = { Text("Name") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { vm.finish() }),
        modifier = Modifier.fillMaxWidth(),
    )
    PrimaryButton("Start", enabled = !state.busy && state.name.isNotBlank(), onClick = vm::finish)
    TextButton(onClick = vm::chooseOtherSheet, enabled = !state.busy) { Text("Choose a different sheet") }
}

@Composable
private fun PrimaryButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick, enabled = enabled, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        Text(text, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
    }
}
