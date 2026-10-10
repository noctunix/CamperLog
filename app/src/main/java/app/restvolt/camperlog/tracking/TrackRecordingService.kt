package app.restvolt.camperlog.tracking

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationManager
import android.os.BatteryManager
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.location.LocationListenerCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.location.LocationRequestCompat
import app.restvolt.camperlog.CamperLogApp
import app.restvolt.camperlog.MainActivity
import app.restvolt.camperlog.R
import app.restvolt.camperlog.data.LOCATION_PERMISSIONS
import app.restvolt.camperlog.domain.TourRepository
import app.restvolt.camperlog.domain.TrackInterval
import app.restvolt.camperlog.domain.TrackPoint
import app.restvolt.camperlog.domain.TrackRepository
import app.restvolt.camperlog.domain.TrackSamplingConfig
import app.restvolt.camperlog.domain.effectiveTrackInterval
import app.restvolt.camperlog.domain.isUsableTrackFix
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.Instant
import kotlin.math.roundToInt

/**
 * Vordergrunddienst der Trackaufzeichnung (Typ `location`). Wird nur aus der Oberfläche gestartet,
 * daher reicht die Standortberechtigung "bei Nutzung der App"; ACCESS_BACKGROUND_LOCATION ist nicht nötig.
 *
 * Fixes kommen über `LocationManagerCompat` mit Intervall, Mindestdistanz und Batching
 * ([TrackSamplingConfig]); unbrauchbare Fixes ([isUsableTrackFix]) werden verworfen. Punkte sammeln
 * sich im Speicher und werden gebündelt geschrieben ([FLUSH_POINTS], [FLUSH_INTERVAL_MILLIS]) sowie
 * beim Beenden.
 */
class TrackRecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val bufferLock = Mutex()
    private val buffer = mutableListOf<TrackPoint>()

    private lateinit var settings: TrackRecordingSettings
    private lateinit var tours: TourRepository
    private lateinit var tracks: TrackRepository
    private val locationManager get() = getSystemService(Context.LOCATION_SERVICE) as LocationManager

    private var recording: ActiveRecording? = null
    private var startingTourId: Long? = null
    private var requestedInterval: TrackInterval? = null
    private var charging = false
    private var receiverRegistered = false

    private val listener = LocationListenerCompat { location -> onFix(location) }

    private val powerReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            charging = intent.action == Intent.ACTION_POWER_CONNECTED
            updateRequest()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        settings = TrackRecordingSettings.get(this)
        tours = (application as CamperLogApp).repository
        tracks = (application as CamperLogApp).tracks
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            settings.activeRecording = null
            settings.trackedTourId = null
            shutDown()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE) {
            settings.activeRecording = null
            shutDown()
            return START_NOT_STICKY
        }
        if (!settings.enabled || !hasLocationPermission(this)) {
            settings.activeRecording = null
            abortStart(intent)
            return START_NOT_STICKY
        }
        val requestedTour = intent?.getLongExtra(EXTRA_TOUR_ID, -1L)?.takeIf { it > 0 }
        val tourId = requestedTour ?: settings.activeRecording?.tourId
        if (tourId == null) {
            abortStart(intent)
            return START_NOT_STICKY
        }
        if (recording?.tourId == tourId || startingTourId == tourId) return START_STICKY
        if (!enterForeground()) {
            // Android lässt einen Standortdienst nicht aus dem Hintergrund starten; die Oberfläche setzt fort.
            settings.reportStartFailed()
            stopSelf()
            return START_NOT_STICKY
        }
        startingTourId = tourId
        scope.launch {
            flush()
            // Die Tour kann inzwischen gelöscht sein (z. B. Sicherung im Ersetzen-Modus eingespielt);
            // ohne diese Prüfung würde nextSegment() trotzdem ein Segment liefern und der Dienst liefe
            // als Geisteraufzeichnung weiter, bis ein Schreibzugriff am Fremdschlüssel scheitert.
            if (tours.allTours().none { it.id == tourId }) {
                val other = recording
                if (other != null) {
                    // Eine andere Tour wird bereits aufgezeichnet; die bleibt unangetastet.
                    settings.activeRecording = other
                    startingTourId = null
                    if (settings.trackedTourId == tourId) settings.trackedTourId = other.tourId
                } else {
                    settings.forgetTour(tourId)
                    shutDown()
                }
                return@launch
            }
            // Jeder Start (auch die Fortsetzung nach einem Neustart) beginnt ein neues Segment.
            val segment = tracks.nextSegment(tourId)
            if (startingTourId != tourId) return@launch // Inzwischen gestoppt.
            val active = ActiveRecording(tourId, segment)
            settings.activeRecording = active
            recording = active
            startingTourId = null
            startUpdates()
        }
        return START_STICKY
    }

    /**
     * Bricht einen Start ab. Kam er über [start] (also `startForegroundService`), muss der Dienst trotzdem
     * kurz in den Vordergrund, sonst beendet Android die App mit "did not then call startForeground".
     */
    private fun abortStart(intent: Intent?) {
        if (intent?.action == ACTION_START && recording == null && startingTourId == null) enterForeground()
        shutDown()
    }

    @SuppressLint("InlinedApi") // ServiceCompat ignoriert den Diensttyp unter API 29.
    private fun enterForeground(): Boolean = try {
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        settings.lastActiveElapsedRealtime = SystemClock.elapsedRealtime()
        true
    } catch (e: RuntimeException) {
        // ForegroundServiceStartNotAllowedException oder SecurityException ohne Berechtigung.
        Log.w(TAG, "Trackaufzeichnung konnte nicht in den Vordergrund", e)
        false
    }

    private fun startUpdates() {
        if (!receiverRegistered) {
            charging = isCharging()
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_POWER_CONNECTED)
                addAction(Intent.ACTION_POWER_DISCONNECTED)
            }
            ContextCompat.registerReceiver(this, powerReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
            receiverRegistered = true
            scope.launch { settings.values.collect { prefs -> if (prefs.enabled) updateRequest() else stopRecording() } }
            scope.launch {
                while (isActive) {
                    delay(FLUSH_INTERVAL_MILLIS)
                    flush()
                }
            }
        }
        updateRequest()
    }

    /** Fordert Updates mit dem aktuell wirksamen Intervall an, falls es sich geändert hat. */
    @SuppressLint("MissingPermission") // hasLocationPermission wird direkt davor geprüft.
    private fun updateRequest() {
        if (recording == null) return
        val prefs = settings.values.value
        val interval = effectiveTrackInterval(prefs.interval, prefs.fasterWhileCharging, charging)
        if (interval == requestedInterval) return
        if (!hasLocationPermission(this)) {
            stopRecording()
            return
        }
        val config = TrackSamplingConfig.forInterval(interval)
        val request = LocationRequestCompat.Builder(config.intervalMillis)
            .setMinUpdateDistanceMeters(config.minDistanceMeters)
            .setMaxUpdateDelayMillis(config.maxUpdateDelayMillis)
            .setQuality(
                if (interval.seconds <= TrackInterval.MINUTES_2.seconds) LocationRequestCompat.QUALITY_HIGH_ACCURACY
                else LocationRequestCompat.QUALITY_BALANCED_POWER_ACCURACY,
            )
            .build()
        removeUpdates()
        LocationManagerCompat.requestLocationUpdates(locationManager, provider(), request, ContextCompat.getMainExecutor(this), listener)
        requestedInterval = interval
        updateNotification()
    }

    /** Abmelden braucht keine gültige Berechtigung; eine entzogene wird nur abgefangen. */
    @SuppressLint("MissingPermission")
    private fun removeUpdates() {
        try {
            LocationManagerCompat.removeUpdates(locationManager, listener)
        } catch (e: SecurityException) {
            Log.w(TAG, "Standort-Updates nicht abgemeldet", e)
        }
    }

    private fun provider(): String {
        val manager = locationManager
        return when {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            else -> LocationManager.GPS_PROVIDER
        }
    }

    private fun onFix(location: Location) {
        val active = recording ?: return
        val accuracy = if (location.hasAccuracy()) location.accuracy.roundToInt() else null
        if (!isUsableTrackFix(accuracy)) return
        val point = TrackPoint(
            tourId = active.tourId,
            segment = active.segment,
            recordedAt = Instant.ofEpochMilli(location.time),
            latitude = location.latitude,
            longitude = location.longitude,
            accuracyM = accuracy,
            altitudeM = if (location.hasAltitude()) location.altitude.roundToInt() else null,
        )
        scope.launch {
            val full = bufferLock.withLock {
                buffer += point
                buffer.size >= FLUSH_POINTS
            }
            if (full) flush()
        }
    }

    /** Schreibt gesammelte Punkte. Scheitert das (z. B. Tour gelöscht), endet die Aufzeichnung. */
    private suspend fun flush() {
        val pending = bufferLock.withLock { buffer.toList().also { buffer.clear() } }
        if (pending.isEmpty()) return
        try {
            withContext(NonCancellable) { tracks.addAll(pending) }
        } catch (e: android.database.SQLException) {
            Log.w(TAG, "Trackpunkte konnten nicht gespeichert werden", e)
            settings.activeRecording = null
            shutDown()
        }
    }

    private fun stopRecording() {
        settings.activeRecording = null
        shutDown()
    }

    private fun shutDown() {
        removeUpdates()
        recording = null
        startingTourId = null
        requestedInterval = null
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        removeUpdates()
        if (receiverRegistered) unregisterReceiver(powerReceiver)
        receiverRegistered = false
        // Restliche Punkte noch schreiben, unabhängig vom beendeten Dienst-Scope.
        val pending = buffer.toList()
        buffer.clear()
        if (pending.isNotEmpty()) {
            CoroutineScope(Dispatchers.IO).launch { runCatching { tracks.addAll(pending) } }
        }
        scope.cancel()
        super.onDestroy()
    }

    private fun isCharging(): Boolean {
        val battery = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return false
        val status = battery.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
    }

    private fun updateNotification() {
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        ensureChannel(this)
        val interval = requestedInterval ?: settings.values.value.interval
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val pause = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackRecordingService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            2,
            Intent(this, TrackRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_route)
            .setContentTitle(getString(R.string.track_notification_title, getString(R.string.app_name)))
            .setContentText(getString(R.string.track_notification_text, trackIntervalLabel(resources, interval)))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(open)
            .addAction(0, getString(R.string.track_notification_pause), pause)
            .addAction(0, getString(R.string.track_notification_stop), stop)
            .build()
    }

    companion object {
        private const val TAG = "TrackRecording"
        private const val CHANNEL_ID = "track_recording"
        private const val NOTIFICATION_ID = 4711
        private const val ACTION_START = "app.restvolt.camperlog.tracking.START"
        private const val ACTION_PAUSE = "app.restvolt.camperlog.tracking.PAUSE"
        private const val ACTION_STOP = "app.restvolt.camperlog.tracking.STOP"
        private const val EXTRA_TOUR_ID = "tour_id"
        private const val AUTO_RESUME_REQUEST_CODE = 3

        /** Spätestens nach so vielen Punkten wird geschrieben. */
        const val FLUSH_POINTS = 20

        /** Spätestens nach dieser Zeit wird geschrieben. */
        const val FLUSH_INTERVAL_MILLIS = 5 * 60_000L

        fun hasLocationPermission(context: Context): Boolean = LOCATION_PERMISSIONS.any {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

        /**
         * Startet die Aufzeichnung für [tourId]; nur aus der sichtbaren Oberfläche aufrufen. Ein noch
         * geplantes automatisches Fortsetzen ([scheduleAutoResume]) wird hinfällig und daher verworfen.
         */
        fun start(context: Context, tourId: Long) {
            TrackRecordingSettings.get(context).pausedByReboot = false
            cancelScheduledAutoResume(context)
            val intent = Intent(context, TrackRecordingService::class.java).setAction(ACTION_START).putExtra(EXTRA_TOUR_ID, tourId)
            ContextCompat.startForegroundService(context, intent)
        }

        /**
         * Schaltet die Aufzeichnung ein, markiert [tourId] als zugeordnete Tour und startet sie.
         * Einziger Einstiegspunkt aus Tourformular und Tourdetail, damit beide denselben Zustand setzen.
         */
        fun startForTour(context: Context, tourId: Long) {
            TrackRecordingSettings.get(context).apply {
                enabled = true
                trackedTourId = tourId
                resumedAfterBoot = false
                resumedAfterTimedPause = false
            }
            start(context, tourId)
        }

        /**
         * Setzt eine gespeicherte, durch Prozessende unterbrochene Aufzeichnung fort. Wurde seit dem
         * letzten aktiven Zeitstempel ein Geräteneustart erkannt, lief der Dienst nachweislich nicht
         * mehr weiter: Das Öffnen der App ist hier schon die bewusste Nutzeraktion, daher startet dies
         * genau wie [resumeAfterBoot] automatisch neu und markiert [TrackRecordingSettings.resumedAfterBoot],
         * statt einen weiteren manuellen Tap zu verlangen. Nur wenn dafür die Standortberechtigung
         * inzwischen entzogen wurde, bleibt es beim bisherigen [TrackRecordingSettings.pausedByReboot]
         * mit manuellem Fortsetzen durch den Nutzer.
         *
         * Dient außerdem als Rückfallebene für [resumeAfterBoot]: Lief dessen Empfänger aus irgendeinem
         * Grund nicht, trifft diese Methode beim nächsten Öffnen der App auf dieselbe Situation.
         */
        fun resumeIfNeeded(context: Context) {
            val settings = TrackRecordingSettings.get(context)
            val active = settings.activeRecording ?: return
            if (!settings.enabled) {
                settings.activeRecording = null
                return
            }
            val rebootDetected = settings.rebootDetectedSinceLastActive()
            if (!hasLocationPermission(context)) {
                settings.activeRecording = null
                if (rebootDetected) settings.pausedByReboot = true
                return
            }
            if (rebootDetected) settings.resumedAfterBoot = true
            start(context, active.tourId)
        }

        /**
         * Setzt eine beim Neustart aktive Aufzeichnung direkt wieder in Gang, aufgerufen vom
         * Boot-Empfänger. Anders als [resumeIfNeeded] prüft dies nicht auf einen Neustart seit dem
         * letzten aktiven Zeitstempel – der Neustart ist hier per Definition gerade erst passiert –
         * sondern startet unconditional neu und setzt [TrackRecordingSettings.resumedAfterBoot], damit
         * die Oberfläche das beim nächsten Anzeigen kurz meldet statt es lautlos zu tun.
         */
        fun resumeAfterBoot(context: Context) {
            val settings = TrackRecordingSettings.get(context)
            val active = settings.activeRecording ?: return
            if (!settings.enabled || !hasLocationPermission(context)) return
            settings.resumedAfterBoot = true
            start(context, active.tourId)
        }

        /** Pausiert die Aufzeichnung: Dienst stoppt, die zugeordnete Tour bleibt markiert ([TrackRecordingSettings.trackedTourId]). */
        fun pause(context: Context) {
            TrackRecordingSettings.get(context).apply {
                activeRecording = null
                resumedAfterBoot = false
                resumedAfterTimedPause = false
            }
            sendAction(context, ACTION_PAUSE)
        }

        /**
         * Plant einen Alarm, der eine pausierte Aufzeichnung um [atMillis] automatisch fortsetzt, sofern
         * bis dahin nichts anderes passiert ist ([resumeFromTimedPause]). `setAndAllowWhileIdle` statt
         * eines exakten Alarms, damit keine `SCHEDULE_EXACT_ALARM`-Sonderberechtigung nötig ist; eine
         * kleine Verspätung ist für diesen Zweck unproblematisch. Nur sinnvoll, solange eine Tour
         * markiert ist ([TrackRecordingSettings.trackedTourId]).
         */
        fun scheduleAutoResume(context: Context, atMillis: Long) {
            val settings = TrackRecordingSettings.get(context)
            val tourId = settings.trackedTourId ?: return
            settings.scheduledResumeAtMillis = atMillis
            context.getSystemService(AlarmManager::class.java)
                ?.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, autoResumePendingIntent(context, tourId))
        }

        /**
         * Setzt eine pausierte Aufzeichnung fort, nachdem die beim Pausieren gewählte Dauer abgelaufen
         * ist, ausgelöst vom Alarm aus [PauseResumeReceiver]. No-op, wenn [tourId] inzwischen nicht mehr
         * die markierte Tour ist, die Aufzeichnung schon wieder läuft, oder die geplante Fortsetzung
         * bereits verworfen wurde (z. B. durch manuelles Fortsetzen oder Beenden).
         */
        fun resumeFromTimedPause(context: Context, tourId: Long) {
            val settings = TrackRecordingSettings.get(context)
            if (settings.trackedTourId != tourId || settings.activeRecording != null || settings.scheduledResumeAtMillis == null) return
            settings.scheduledResumeAtMillis = null
            if (!settings.enabled || !hasLocationPermission(context)) return
            settings.resumedAfterTimedPause = true
            start(context, tourId)
        }

        /** Beendet die Aufzeichnung endgültig: Dienst stoppt, die zugeordnete Tour wird entmarkiert. */
        fun stop(context: Context) {
            TrackRecordingSettings.get(context).apply {
                activeRecording = null
                trackedTourId = null
                pausedByReboot = false
                resumedAfterBoot = false
                resumedAfterTimedPause = false
            }
            cancelScheduledAutoResume(context)
            sendAction(context, ACTION_STOP)
        }

        /** Stoppt die Aufzeichnung, falls sie gerade [tourId] zugeordnet oder zuordnet ist. */
        fun stopIfTracking(context: Context, tourId: Long) {
            val settings = TrackRecordingSettings.get(context)
            if (settings.trackedTourId == tourId || settings.activeRecording?.tourId == tourId) stop(context)
        }

        /** `PendingIntent` für den Auto-Fortsetzen-Alarm; Abgleich beim Abmelden ignoriert die Extras. */
        private fun autoResumePendingIntent(context: Context, tourId: Long): PendingIntent =
            PendingIntent.getBroadcast(
                context,
                AUTO_RESUME_REQUEST_CODE,
                Intent(context, PauseResumeReceiver::class.java)
                    .setAction(PauseResumeReceiver.ACTION_AUTO_RESUME)
                    .putExtra(PauseResumeReceiver.EXTRA_TOUR_ID, tourId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun cancelScheduledAutoResume(context: Context) {
            TrackRecordingSettings.get(context).scheduledResumeAtMillis = null
            context.getSystemService(AlarmManager::class.java)?.cancel(autoResumePendingIntent(context, 0L))
        }

        private fun sendAction(context: Context, action: String) {
            try {
                context.startService(Intent(context, TrackRecordingService::class.java).setAction(action))
            } catch (e: IllegalStateException) {
                // Aus dem Hintergrund verboten; dann läuft auch kein Vordergrunddienst, der zu stoppen wäre.
                Log.w(TAG, "Aktion der Trackaufzeichnung nicht zugestellt: $action", e)
            }
        }

        private fun ensureChannel(context: Context) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.track_notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = context.getString(R.string.track_notification_channel_description) }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
        }
    }
}
