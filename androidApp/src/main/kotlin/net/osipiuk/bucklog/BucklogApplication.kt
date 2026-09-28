package net.osipiuk.bucklog

import android.app.Application
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import kotlinx.coroutines.Dispatchers
import net.osipiuk.bucklog.auth.GoogleAuthorizer
import net.osipiuk.bucklog.data.LocalStore
import net.osipiuk.bucklog.db.BucklogDatabase
import net.osipiuk.bucklog.google.DriveClient
import net.osipiuk.bucklog.google.GoogleApi
import net.osipiuk.bucklog.google.SheetsClient
import net.osipiuk.bucklog.ui.AppGraph

class BucklogApplication : Application() {
    val authorizer by lazy { GoogleAuthorizer(this) }

    val graph by lazy {
        val db = BucklogDatabase(AndroidSqliteDriver(BucklogDatabase.Schema, this, "bucklog.db"))
        val api = GoogleApi(HttpClient(OkHttp), authorizer)
        AppGraph(LocalStore(db, Dispatchers.IO), SheetsClient(api), DriveClient(api))
    }
}
