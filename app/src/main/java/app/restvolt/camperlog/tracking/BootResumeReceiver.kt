package app.restvolt.camperlog.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Setzt eine beim Neustart aktive Trackaufzeichnung sofort fort, statt auf das nächste Öffnen der
 * App zu warten. Vordergrunddienste mit sichtbarer Benachrichtigung sind von den
 * Hintergrund-Start-Einschränkungen für `BOOT_COMPLETED`-Empfänger ausdrücklich ausgenommen, daher
 * reicht dafür die normale Berechtigung `RECEIVE_BOOT_COMPLETED` ohne Laufzeitabfrage.
 */
class BootResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Tag teilt sich mit TrackRecordingService, damit `adb logcat` beides zusammen zeigt.
        Log.i("TrackRecording", "BOOT_COMPLETED empfangen")
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        TrackRecordingService.resumeAfterBoot(context)
    }
}
