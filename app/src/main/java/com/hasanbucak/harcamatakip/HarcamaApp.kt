package com.hasanbucak.harcamatakip

import android.app.Application
import com.hasanbucak.harcamatakip.data.TransactionRepository
import com.hasanbucak.harcamatakip.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HarcamaApp : Application() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: TransactionRepository by lazy { TransactionRepository(database) }

    override fun onCreate() {
        super.onCreate()
        appScope.launch {
            repository.ensurePreconfiguredCards()
        }
    }
}
