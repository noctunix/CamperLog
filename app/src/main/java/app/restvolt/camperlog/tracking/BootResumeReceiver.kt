package app.restvolt.camperlog.tracking

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Setzt eine beim Neustart aktive Trackaufzeichnung sofort fort, statt auf das nächste Öffnen der
 * App zu warten. Vordergrunddienste mit sichtbarer Benachrichtigung sind von den
 * Hintergrund-Start-Einschränkungen für `BOOT_COMPLETED`-Empfänger ausdrücklich ausgenommen, daher
 * reicht dafür die normale Berechtigung `RECEIVE_BOOT_COMPLETED` ohne Laufzeitabfrage.
 */
class BootResumeReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        TrackRecordingService.resumeAfterBoot(context)
    }
}
