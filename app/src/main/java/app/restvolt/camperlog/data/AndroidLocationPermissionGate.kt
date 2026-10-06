package app.restvolt.camperlog.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import app.restvolt.camperlog.domain.LocationPermissionGate

/**
 * [LocationPermissionGate] über `ContextCompat`/SharedPreferences. Grobe Berechtigung reicht,
 * daher gilt fein ODER grob erteilt als "hat Berechtigung".
 */
class AndroidLocationPermissionGate(private val context: Context) : LocationPermissionGate {

    private val preferences = context.getSharedPreferences("location_permission", Context.MODE_PRIVATE)

    override fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    override fun hasRequestedBefore(): Boolean = preferences.getBoolean(KEY_REQUESTED, false)

    override fun markRequested() {
        preferences.edit { putBoolean(KEY_REQUESTED, true) }
    }

    private companion object {
        const val KEY_REQUESTED = "requested"
    }
}

/** Die beiden verwendeten Standortberechtigungen, für den Systemdialog und das Zurücknehmen in den Einstellungen. */
val LOCATION_PERMISSIONS: Array<String> = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
