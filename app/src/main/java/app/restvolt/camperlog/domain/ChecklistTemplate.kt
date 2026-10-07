package app.restvolt.camperlog.domain

import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import java.time.Instant

/**
 * Eine Vorlage für Checklisten (Abfahrt, Ankunft, Einwintern, …) mit ihren Punkten [items] in fester
 * Reihenfolge. Nicht an ein Fahrzeug gebunden; eine gestartete [Checklist] kopiert die Punkte beim
 * Anlegen, spätere Änderungen der Vorlage wirken sich also nicht mehr auf sie aus.
 */
data class ChecklistTemplate(
    val id: Long = 0,
    val uuid: String = "",
    val name: String,
    val items: List<String> = emptyList(),
    val createdAt: Instant,
    val updatedAt: Instant,
)

/** Zugriff auf die Checklisten-Vorlagen. */
interface ChecklistTemplateRepository {
    /** Alle Vorlagen, nach Name sortiert. */
    fun observeAll(): Flow<List<ChecklistTemplate>>

    /** Alle Vorlagen, für den Sicherungs-Export. */
    suspend fun allTemplates(): List<ChecklistTemplate>

    /** Legt [template] an, wenn seine id 0 ist, sonst wird sie aktualisiert. */
    suspend fun save(template: ChecklistTemplate): Long

    /** Löscht die Vorlage mit [id]; bereits gestartete Checklisten behalten ihre eigene Punktkopie. */
    suspend fun delete(id: Long)

    /** Legt eine zuvor gelöschte [template] mit ihrer bisherigen id und ihren Zeitstempeln wieder an. */
    suspend fun restore(template: ChecklistTemplate)
}

/** Unvalidierte Eingaben des Vorlagen-Formulars. */
@Serializable
data class ChecklistTemplateInput(val name: String = "", val items: List<String> = emptyList())

/** Formularfelder, an denen ein Validierungsfehler auftreten kann. */
enum class ChecklistTemplateField { NAME }

/** Grund eines Validierungsfehlers. Den Text dazu liefert die UI aus den String-Ressourcen. */
enum class ChecklistTemplateError { REQUIRED }

/** Prüft die fachlichen Regeln einer Vorlagen-Eingabe: nur der Name ist Pflicht. */
fun ChecklistTemplateInput.validate(): Map<ChecklistTemplateField, ChecklistTemplateError> = buildMap {
    if (name.isBlank()) put(ChecklistTemplateField.NAME, ChecklistTemplateError.REQUIRED)
}

/**
 * Erzeugt aus einer gültigen Eingabe eine [ChecklistTemplate]; vorher muss [validate] leer sein.
 * Leere Punkte (z. B. eine noch unausgefüllte Zeile) werden entfernt.
 *
 * @param original die bearbeitete Vorlage oder `null` für eine neue Vorlage
 */
fun ChecklistTemplateInput.toTemplate(original: ChecklistTemplate?): ChecklistTemplate = ChecklistTemplate(
    id = original?.id ?: 0,
    uuid = original?.uuid.orEmpty(),
    name = name.trim(),
    items = items.map(String::trim).filter(String::isNotBlank),
    createdAt = original?.createdAt ?: Instant.EPOCH,
    updatedAt = original?.updatedAt ?: Instant.EPOCH,
)

/** Wandelt eine gespeicherte Vorlage in editierbare Formulardaten um. */
fun ChecklistTemplate.toInput(): ChecklistTemplateInput = ChecklistTemplateInput(name = name, items = items)
