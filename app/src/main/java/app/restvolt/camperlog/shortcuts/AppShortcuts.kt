package app.restvolt.camperlog.shortcuts

import android.content.Context
import android.content.Intent
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import app.restvolt.camperlog.MainActivity
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.VehicleRepository
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** Intent-Extra, das beim Start direkt eine neue Tour anlegt (Launcher-Shortcut). */
const val EXTRA_OPEN_NEW_TOUR = "app.restvolt.camperlog.EXTRA_OPEN_NEW_TOUR"

/** Intent-Extra, das beim Start die Typauswahl für einen neuen Stopp öffnet (Launcher-Shortcut). */
const val EXTRA_OPEN_NEW_STOP = "app.restvolt.camperlog.EXTRA_OPEN_NEW_STOP"

/** Intent-Extra, das beim Start direkt das "Wo bin ich?"-Sheet öffnet (Launcher-Shortcut Panne/Unfall). */
const val EXTRA_OPEN_BREAKDOWN = "app.restvolt.camperlog.EXTRA_OPEN_BREAKDOWN"

/** Intent-Extra mit der Tour-id, deren Detailseite beim Start geöffnet werden soll (dynamischer Shortcut). */
const val EXTRA_OPEN_TOUR_ID = "app.restvolt.camperlog.EXTRA_OPEN_TOUR_ID"

private const val SHORTCUT_ID_RUNNING_TOUR = "running_tour"

/**
 * Hält den dynamischen Launcher-Shortcut "Laufende Tour öffnen" mit der id der nicht beendeten Tour
 * des aktuellen Fahrzeugs synchron; läuft keine Tour, wird der Shortcut entfernt.
 */
suspend fun syncRunningTourShortcut(context: Context, vehicles: VehicleRepository, tours: TourRepository) {
    combine(vehicles.observeCurrentVehicle(), tours.observeTours()) { vehicle, allTours ->
        allTours.firstOrNull { it.vehicleId == vehicle.id && it.endDate == null }?.id
    }.distinctUntilChanged().collectLatest { runningTourId ->
        if (runningTourId == null) {
            ShortcutManagerCompat.removeDynamicShortcuts(context, listOf(SHORTCUT_ID_RUNNING_TOUR))
            return@collectLatest
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            putExtra(EXTRA_OPEN_TOUR_ID, runningTourId)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, SHORTCUT_ID_RUNNING_TOUR)
            .setShortLabel(context.getString(R.string.shortcut_running_tour_short))
            .setLongLabel(context.getString(R.string.shortcut_running_tour_long))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_play_arrow))
            .setIntent(intent)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
    }
}
