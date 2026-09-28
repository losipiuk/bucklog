package net.osipiuk.bucklog.spike

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import net.osipiuk.bucklog.BuildConfig
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.google.GoogleApi
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.google.parseSpreadsheetId
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * M0 spike: proves drive.file authorization + Google Picker + Sheets REST from Android.
 * Throwaway UI; see docs/m0-spike.md for the checklist it exercises.
 */
class SpikeActivity : ComponentActivity() {
    private val pickerResults = Channel<PickerResult>(Channel.UNLIMITED)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handlePickerIntent(intent)
        val authorizer = GoogleAuthorizer(applicationContext)
        val api = GoogleApi(HttpClient(OkHttp), authorizer)
        val sheets = SheetsClient(api)
        val drive = DriveClient(api)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    SpikeScreen(authorizer, sheets, drive, pickerResults)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handlePickerIntent(intent)
    }

    private fun handlePickerIntent(intent: Intent?) {
        PickerLauncher.parseResult(intent?.data)?.let { pickerResults.trySend(it) }
    }
}

private const val YEAR_TAB = "2026"

@Composable
private fun SpikeScreen(
    authorizer: GoogleAuthorizer,
    sheets: SheetsClient,
    drive: DriveClient,
    pickerResults: Channel<PickerResult>,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val log = remember { mutableStateListOf<String>() }
    var token by remember { mutableStateOf<String?>(null) }
    var sheetId by remember { mutableStateOf<String?>(null) }
    var link by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }

    fun say(line: String) {
        Log.i("BucklogSpike", line)
        log.add(0, "${LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"))}  $line")
    }

    fun run(label: String, block: suspend () -> Unit) = scope.launch {
        say("▶ $label")
        runCatching { block() }.onFailure { say("✖ $label: ${it::class.simpleName}: ${it.message}") }
    }

    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        runCatching { authorizer.resultFromConsent(result.data) }
            .onSuccess {
                token = it
                say("✔ authorized after consent, token ${it.take(12)}…")
                scope.launch { runCatching { say("  as ${drive.currentUser().emailAddress}") } }
            }
            .onFailure { say("✖ consent: ${it.message}") }
    }

    LaunchedEffect(Unit) {
        pickerResults.receiveAsFlow().collect { result ->
            when (result) {
                is PickerResult.Picked -> { sheetId = result.id; say("✔ picked ${result.name} (${result.id})") }
                PickerResult.Cancelled -> say("picker cancelled")
                is PickerResult.Failed -> say("✖ picker: ${result.message}")
            }
        }
    }

    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Bucklog M0 spike", style = MaterialTheme.typography.titleLarge)
        Text("token: ${token?.take(12) ?: "—"}   sheet: ${sheetId ?: "—"}", fontSize = 12.sp)
        if (BuildConfig.PICKER_API_KEY.isEmpty() || BuildConfig.CLOUD_PROJECT_NUMBER.isEmpty()) {
            Text("⚠ bucklog.pickerApiKey / bucklog.cloudProjectNumber missing in local.properties", color = MaterialTheme.colorScheme.error)
        }
        OutlinedTextField(account, { account = it }, Modifier.fillMaxWidth(), label = { Text("Google account (optional)") }, singleLine = true)
        OutlinedTextField(link, { link = it }, Modifier.fillMaxWidth(), label = { Text("Sheet link (optional)") }, singleLine = true)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                run("authorize") {
                    authorizer.accountEmail = account.trim().ifEmpty { null }
                    token = null
                    sheetId = null
                    when (val outcome = authorizer.authorize()) {
                        is GoogleAuthorizer.Outcome.Authorized -> {
                            token = outcome.token
                            say("✔ authorized as ${drive.currentUser().emailAddress}, token ${outcome.token.take(12)}…")
                        }
                        is GoogleAuthorizer.Outcome.NeedsConsent ->
                            consent.launch(IntentSenderRequest.Builder(outcome.pendingIntent.intentSender).build())
                    }
                }
            }) { Text("1. Authorize") }
            Button(onClick = {
                run("create sheet") {
                    val created = sheets.create("Bucklog spike ${LocalDateTime.now().withNano(0)}", listOf("Categories", "Settings", YEAR_TAB))
                    sheetId = created.spreadsheetId
                    say("✔ created ${created.properties.title} (${created.spreadsheetId})")
                }
            }) { Text("Create sheet") }
            Button(onClick = {
                say("▶ pick sheet (Custom Tab)")
                PickerLauncher.launch(
                    context,
                    token = token ?: return@Button say("✖ authorize first"),
                    apiKey = BuildConfig.PICKER_API_KEY,
                    appId = BuildConfig.CLOUD_PROJECT_NUMBER,
                    preselectFileId = parseSpreadsheetId(link),
                )
            }) { Text("Pick sheet") }
            Button(onClick = {
                val id = parseSpreadsheetId(link)
                if (id == null) say("✖ no valid link in the field") else run("open link without picking") {
                    val s = sheets.get(id)
                    sheetId = id
                    say("✔ accessible without picking now: ${s.properties.title}")
                }
            }) { Text("Open link w/o picking") }
            Button(onClick = {
                val id = sheetId ?: return@Button say("✖ no sheet yet")
                run("read") {
                    val meta = drive.getFile(id)
                    say("drive: ${meta.name} version=${meta.version} modified=${meta.modifiedTime}")
                    val s = sheets.get(id)
                    say("tabs: ${s.sheets.joinToString { it.properties.title }}  tz=${s.properties.timeZone}")
                    val tab = s.sheets.map { it.properties.title }.firstOrNull { it == YEAR_TAB } ?: s.sheets.first().properties.title
                    val rows = sheets.batchGet(id, listOf("'$tab'!A:H")).single().values
                    say("✔ $tab: ${rows.size} rows; last: ${rows.lastOrNull()}")
                }
            }) { Text("Read") }
            Button(onClick = {
                val id = sheetId ?: return@Button say("✖ no sheet yet")
                run("append") {
                    val tabs = sheets.get(id).sheets.map { it.properties.title }
                    val tab = tabs.firstOrNull { it == YEAR_TAB } ?: tabs.first()
                    val now = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    val rowId = UUID.randomUUID().toString().take(8)
                    sheets.append(
                        id,
                        "'$tab'!A:H",
                        listOf(listOf(now, "spike", "M0 test", "Test", "1.23", "PLN", "", rowId).map(::JsonPrimitive)),
                    )
                    say("✔ appended row $rowId to $tab")
                }
            }) { Text("Append row") }
        }
        SelectionContainer {
            LazyColumn(Modifier.fillMaxSize()) {
                items(log) { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
            }
        }
    }
}
