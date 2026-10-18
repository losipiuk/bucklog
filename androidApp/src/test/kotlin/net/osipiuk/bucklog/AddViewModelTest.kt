package net.osipiuk.bucklog

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.cancel
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.StandardTestDispatcher
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.db.BucklogDatabase
import net.osipiuk.bucklog.domain.AmountKey
import net.osipiuk.bucklog.domain.Category
import net.osipiuk.bucklog.google.AccessTokenProvider
import net.osipiuk.bucklog.google.GoogleApi
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.ui.AppGraph
import net.osipiuk.bucklog.ui.CLOSE_AFTER_ADD_KEY
import net.osipiuk.bucklog.ui.Platform
import net.osipiuk.bucklog.ui.PickedSheet
import net.osipiuk.bucklog.ui.add.AddForm
import net.osipiuk.bucklog.ui.add.AddViewModel
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AddViewModelTest {
    private class Host : Platform {
        val messages = mutableListOf<String>()
        var syncs = 0
        override fun finishWithMessage(message: String) { messages += message }
        override fun requestSync() { syncs++ }
        override suspend fun signIn(): String? = null
        override suspend fun reauthorize(email: String) = false
        override suspend fun pickSheet(preselectFileId: String?): PickedSheet? = null
        override fun shareText(text: String, title: String) = Unit
        override val canAddQuickTile = false
        override fun addQuickTile() = Unit
        override fun openUrl(url: String) = Unit
        @Composable override fun BackHandler(enabled: Boolean, onBack: () -> Unit) = Unit
        override val deviceCurrency = "PLN"
        override val timeZoneId = "Europe/Warsaw"
        override val isDebugBuild = false
        override val language = "en"
        override val country = "PL"
    }

    private fun scenario(stored: String?, block: suspend kotlinx.coroutines.test.TestScope.(LocalStore, AddViewModel, Host) -> Unit) = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        BucklogDatabase.Schema.create(driver)
        val store = LocalStore(BucklogDatabase(driver), Dispatchers.Unconfined)
        val http = HttpClient(MockEngine { error("Saving offline must not call Google") })
        val api = GoogleApi(http, object : AccessTokenProvider {
            override suspend fun accessToken() = error("No authorization expected")
            override suspend fun invalidate(token: String) = Unit
        })
        val graph = AppGraph(store, SheetsClient(api), DriveClient(api), { _, _, _ -> null })
        val host = Host()
        var model: AddViewModel? = null
        try {
            store.updateConfig(myName = "Test", mainCurrency = "PLN")
            store.replaceCategories(listOf(Category("Food", emoji = null)))
            store.putValue(CLOSE_AFTER_ADD_KEY, stored)
            val vm = AddViewModel(graph, host)
            model = vm
            runCurrent()
            block(store, vm, host)
        } finally {
            model?.viewModelScope?.cancel()
            runCurrent()
            http.close()
            driver.close()
            Dispatchers.resetMain()
        }
    }

    private fun prepare(vm: AddViewModel) {
        vm.press(AmountKey.D1)
        vm.next()
        vm.onWhatChange("Milk")
        vm.pickCategory("Food")
    }

    @Test fun defaultStillClosesAfterSaving() = scenario(null) { store, vm, host ->
        prepare(vm)
        runCurrent()
        vm.add()
        advanceUntilIdle()
        assertEquals(1, host.messages.size)
        assertEquals(1, store.outboxSize())
        assertEquals(1, host.syncs)
    }

    @Test fun disabledCloseAllowsConsecutiveEntriesAndClearsRefundAndDraft() = scenario("false") { store, vm, host ->
        prepare(vm)
        vm.selectCurrency("EUR")
        vm.toggleRefund()
        runCurrent()
        vm.add()
        advanceUntilIdle()
        assertTrue(host.messages.isEmpty())
        assertEquals(AddForm(), vm.state.value!!.form)
        assertEquals("EUR", vm.state.value!!.currency)
        assertFalse(vm.state.value!!.canContinue)
        prepare(vm)
        runCurrent()
        vm.add()
        advanceUntilIdle()
        val entries = store.recentEntries(10).first()
        assertEquals(2, entries.size)
        assertEquals(2, entries.map { it.id }.distinct().size)
        assertEquals(listOf(-1L, 1L), entries.map { it.amountMinor }.sorted())
        assertEquals(2, store.outboxSize())
        assertEquals(2, host.syncs)
        assertTrue(host.messages.isEmpty())
    }

    @Test fun rapidDuplicateTapAndEditsDuringSaveDoNotCreateAnotherEntry() = scenario("false") { store, vm, host ->
        prepare(vm)
        runCurrent()
        vm.add()
        vm.add() // UI state has not collected the saving flag yet.
        vm.press(AmountKey.D9)
        vm.onWhatChange("Changed during save")
        vm.back()
        runCurrent()
        assertTrue(vm.state.value!!.form.done)
        vm.add() // Still blocked during the confirmation animation.
        advanceUntilIdle()
        assertEquals(1, store.outboxSize())
        assertEquals("Milk", store.recentEntries(10).first().single().what)
        assertEquals(AddForm(), vm.state.value!!.form)
        assertTrue(host.messages.isEmpty())
    }

    @Test fun changedPreferenceAppliesToNextSaveWithoutRecreatingViewModel() = scenario("false") { store, vm, host ->
        prepare(vm)
        runCurrent()
        vm.add()
        advanceUntilIdle()
        store.putValue(CLOSE_AFTER_ADD_KEY, "true")
        prepare(vm)
        runCurrent()
        vm.add()
        advanceUntilIdle()
        assertEquals(1, host.messages.size)
        assertEquals(2, store.outboxSize())
    }
}
