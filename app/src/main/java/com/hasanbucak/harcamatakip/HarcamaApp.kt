package com.hasanbucak.harcamatakip

import android.app.Application
import com.hasanbucak.harcamatakip.data.TransactionRepository
import com.hasanbucak.harcamatakip.data.local.AppDatabase
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HarcamaApp : Application() {

    private val exceptionHandler = CoroutineExceptionHandler { _, _ -> }

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: TransactionRepository by lazy { TransactionRepository(database) }

    override fun onCreate() {
        super.onCreate()
        applicationScope.launch {
            repository.initializeDefaultsAndMaintenance()
        }
    }
}
