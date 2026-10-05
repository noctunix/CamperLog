package app.restvolt.camperlog.ui.vehicles

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import app.restvolt.camperlog.R
import app.restvolt.camperlog.ui.BackTopBar

/** Fahrzeugverwaltung; Liste, Anlegen, Bearbeiten und Löschen folgen in Phase 3. */
@Composable
fun VehiclesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = { BackTopBar(title = stringResource(R.string.vehicles_manage_title), onBack = onBack) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding))
    }
}
