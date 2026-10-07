package app.restvolt.camperlog.ui.search

import app.restvolt.camperlog.domain.LogType
import app.restvolt.camperlog.domain.SearchSnippet
import app.restvolt.camperlog.domain.StationType
import java.time.LocalDate

/** Art eines Suchtreffers, in der Reihenfolge, in der ihre Gruppen in [SearchUiState.groups] angezeigt werden. */
enum class SearchResultType { TOUR, STOP, DIARY, LOGBOOK, REPAIR, CHECKLIST, CHECKLIST_TEMPLATE, VEHICLE_DOCUMENT, VEHICLE }

/**
 * Ein Treffer der Volltextsuche. [date] bestimmt die Reihenfolge innerhalb seiner Gruppe (neueste
 * zuerst); bei Arten ohne eigenes Datum ist es die Anlagezeit. [vehicleName] ist nur gesetzt, wenn es
 * mehrere Fahrzeuge gibt und der Treffer einem davon zugeordnet ist, für die Zusatzzeile der Ergebniszeile.
 */
sealed interface SearchResult {
    val type: SearchResultType
    val date: LocalDate
    val snippet: SearchSnippet?
    val vehicleName: String?

    data class TourResult(
        val tourId: Long,
        val destination: String,
        override val date: LocalDate,
        override val snippet: SearchSnippet?,
        override val vehicleName: String?,
    ) : SearchResult {
        override val type get() = SearchResultType.TOUR
    }

    /** [name] ist der eingetragene Stationsname; leer bedeutet, dass die Zeile [stationType] als Titel zeigt. */
    data class StopResult(
        val stationId: Long,
        val name: String,
        val stationType: StationType,
        override val date: LocalDate,
        override val snippet: SearchSnippet?,
        override val vehicleName: String?,
    ) : SearchResult {
        override val type get() = SearchResultType.STOP
    }

    data class DiaryResult(
        val tourId: Long,
        val entryId: Long,
        val tourDestination: String,
        override val date: LocalDate,
        override val snippet: SearchSnippet?,
        override val vehicleName: String?,
    ) : SearchResult {
        override val type get() = SearchResultType.DIARY
    }

    data class LogbookResult(
        val entryId: Long,
        val vehicleId: Long,
        val logType: LogType,
        val label: String,
        override val date: LocalDate,
        override val snippet: SearchSnippet?,
        override val vehicleName: String?,
    ) : SearchResult {
        override val type get() = SearchResultType.LOGBOOK
    }

    data class RepairResult(
        val vehicleId: Long,
        val repairId: Long,
        val description: String,
        override val date: LocalDate,
        override val snippet: SearchSnippet?,
        override val vehicleName: String?,
    ) : SearchResult {
        override val type get() = SearchResultType.REPAIR
    }

    /** [tourId] `null` bedeutet eine Checkliste ohne Tourbezug (siehe [app.restvolt.camperlog.domain.Checklist]). */
    data class ChecklistResult(
        val checklistId: Long,
        val vehicleId: Long,
        val tourId: Long?,
        val title: String,
        override val date: LocalDate,
        override val snippet: SearchSnippet?,
        override val vehicleName: String?,
    ) : SearchResult {
        override val type get() = SearchResultType.CHECKLIST
    }

    /** Vorlagen sind nicht an ein Fahrzeug gebunden, daher immer `null` als [vehicleName]. */
    data class ChecklistTemplateResult(
        val templateId: Long,
        val title: String,
        override val date: LocalDate,
        override val snippet: SearchSnippet?,
    ) : SearchResult {
        override val type get() = SearchResultType.CHECKLIST_TEMPLATE
        override val vehicleName: String? get() = null
    }

    data class VehicleDocumentResult(
        val vehicleId: Long,
        val documentId: Long,
        val title: String,
        override val date: LocalDate,
        override val snippet: SearchSnippet?,
        override val vehicleName: String?,
    ) : SearchResult {
        override val type get() = SearchResultType.VEHICLE_DOCUMENT
    }

    /** Das Datenblatt eines Fahrzeugs; [title] ist sein eingetragener Name, auch wenn er leer ist. */
    data class VehicleResult(
        val vehicleId: Long,
        val title: String,
        override val date: LocalDate,
        override val snippet: SearchSnippet?,
    ) : SearchResult {
        override val type get() = SearchResultType.VEHICLE
        override val vehicleName: String? get() = null
    }
}

/** Eine Ergebnisgruppe mit ihrer Art (für Überschrift und Reihenfolge) und ihren Treffern, neueste zuerst. */
data class SearchResultGroup(val type: SearchResultType, val results: List<SearchResult>)
