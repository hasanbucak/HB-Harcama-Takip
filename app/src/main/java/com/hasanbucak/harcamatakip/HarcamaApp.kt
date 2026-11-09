package com.hasanbucak.harcamatakip

import android.app.Application
import com.hasanbucak.harcamatakip.data.TransactionRepository
import com.hasanbucak.harcamatakip.data.local.AppDatabase

class HarcamaApp : Application() {

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val repository: TransactionRepository by lazy { TransactionRepository(database) }
}
