package net.osipiuk.bucklog

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import net.osipiuk.bucklog.auth.GoogleAuthorizer
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.db.BucklogDatabase
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.google.GoogleApi
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.sync.PublicExchangeRates
import net.osipiuk.bucklog.ui.AppGraph

class BucklogApplication : Application() {
    private val store by lazy {
        LocalStore(BucklogDatabase(AndroidSqliteDriver(BucklogDatabase.Schema, this, "bucklog.db")), Dispatchers.IO)
    }

    val authorizer by lazy { GoogleAuthorizer(this) { store.config.first().accountEmail } }

    val graph by lazy {
        val api = GoogleApi(HttpClient(OkHttp), authorizer)
        AppGraph(store, SheetsClient(api), DriveClient(api), PublicExchangeRates(HttpClient(OkHttp), store))
    }

    override fun onCreate() {
        super.onCreate()
        SyncScheduler.schedulePeriodic(this)
    }
}
