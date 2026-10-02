package de.hannes.camperlog.share

import de.hannes.camperlog.domain.Tour
import de.hannes.camperlog.domain.formatEuro
import de.hannes.camperlog.domain.period

/** Lesbare Zusammenfassung einer Tour zum Teilen per Messenger oder E-Mail. */
fun tourShareText(tour: Tour): String = buildString {
    appendLine("Tour nach ${tour.destination}")
    appendLine("${tour.period} (${tour.tourType.label})")
    appendLine("${tour.travelDays} Reisetage, ${tour.overnightStays} Übernachtungen, ${tour.distanceKm} km")
    appendLine("Kosten: ${formatEuro(tour.costCents)}")
    appendLine("Stellplatz zugewiesen: ${if (tour.pitchAssigned) "ja" else "nein"}")
    appendLine("Strompauschale: ${tour.electricityFlatRate.label}")
    appendLine("LTE: ${tour.lteQuality.label}")
    appendLine("Stellplatz: ${tour.pitchSlope.label}, Keile ${if (tour.levelingBlocksUsed) "genutzt" else "nicht genutzt"}")
    if (tour.notes.isNotBlank()) appendLine("Notizen: ${tour.notes}")
    tour.mapLink?.let { appendLine("Karte: $it") }
}.trimEnd()
