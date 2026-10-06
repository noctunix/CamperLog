package app.restvolt.camperlog.data

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

/**
 * Nimmt die Standortberechtigung zurück, wenn der Schalter "Standort" ausgeschaltet wird (6.11, 9).
 * Eigenes Interface, damit der Aufruf in [app.restvolt.camperlog.ui.settings.LocationSettings] mit
 * einem Fake testbar ist.
 */
interface LocationPermissionRevoker {
    /** Entzieht die Berechtigung endgültig, sobald die App beendet wird. Ohne Wirkung vor Android 13. */
    fun revokeOnKill()
}

class AndroidLocationPermissionRevoker(private val context: Context) : LocationPermissionRevoker {
    override fun revokeOnKill() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) revokeOnKillApi33()
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun revokeOnKillApi33() {
        context.revokeSelfPermissionsOnKill(LOCATION_PERMISSIONS.toList())
    }
}
