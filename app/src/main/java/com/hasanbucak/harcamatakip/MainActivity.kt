package com.hasanbucak.harcamatakip

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hasanbucak.harcamatakip.ui.MainViewModel
import com.hasanbucak.harcamatakip.ui.screens.CardsScreen
import com.hasanbucak.harcamatakip.ui.screens.DashboardScreen
import com.hasanbucak.harcamatakip.ui.screens.SetupAndLogsScreen
import com.hasanbucak.harcamatakip.ui.theme.HBHarcamaTheme
import com.hasanbucak.harcamatakip.util.SystemPermissionHelper

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val initialTab = if (SystemPermissionHelper.isNotificationListenerEnabled(this)) 0 else 2

        setContent {
            HBHarcamaTheme {
                val vm: MainViewModel = viewModel()
                val state by vm.uiState.collectAsStateWithLifecycle()
                var currentTab by rememberSaveable { mutableIntStateOf(initialTab) }

                val unparsedCount = state.recentLogs.count { it.status == "UNPARSED" }

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            NavigationBarItem(
                                selected = currentTab == 0,
                                onClick = { currentTab = 0 },
                                icon = { Icon(Icons.AutoMirrored.Filled.ReceiptLong, contentDescription = null) },
                                label = { Text("Harcamalar") }
                            )
                            NavigationBarItem(
                                selected = currentTab == 1,
                                onClick = { currentTab = 1 },
                                icon = { Icon(Icons.Default.CreditCard, contentDescription = null) },
                                label = { Text("Kartlarım") }
                            )
                            NavigationBarItem(
                                selected = currentTab == 2,
                                onClick = { currentTab = 2 },
                                icon = {
                                    BadgedBox(
                                        badge = {
                                            if (unparsedCount > 0) {
                                                Badge { Text(unparsedCount.toString()) }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.Tune, contentDescription = null)
                                    }
                                },
                                label = { Text("Kurulum & Log") }
                            )
                        }
                    }
                ) { innerPadding ->
                    Box(modifier = Modifier.padding(innerPadding)) {
                        when (currentTab) {
                            0 -> DashboardScreen(state = state, viewModel = vm)
                            1 -> CardsScreen(state = state, viewModel = vm)
                            else -> SetupAndLogsScreen(state = state, viewModel = vm)
                        }
                    }
                }
            }
        }
    }
}
