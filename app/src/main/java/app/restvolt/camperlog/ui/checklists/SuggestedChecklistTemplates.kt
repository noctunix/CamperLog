package app.restvolt.camperlog.ui.checklists

import android.content.res.Resources
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.ChecklistTemplate
import java.time.Instant

/**
 * Die vier vorgeschlagenen Checklisten-Vorlagen (Abfahrt, Ankunft, Einwintern, Frühjahrscheck) in der
 * Gerätesprache, zum Einfügen über "Vorlagen-Vorschläge hinzufügen"; nicht automatisch angelegt.
 */
fun suggestedChecklistTemplates(resources: Resources): List<ChecklistTemplate> = listOf(
    R.string.checklist_suggested_departure_name to R.array.checklist_suggested_departure_items,
    R.string.checklist_suggested_arrival_name to R.array.checklist_suggested_arrival_items,
    R.string.checklist_suggested_winterizing_name to R.array.checklist_suggested_winterizing_items,
    R.string.checklist_suggested_spring_check_name to R.array.checklist_suggested_spring_check_items,
).map { (nameRes, itemsRes) ->
    ChecklistTemplate(
        name = resources.getString(nameRes),
        items = resources.getStringArray(itemsRes).toList(),
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )
}
