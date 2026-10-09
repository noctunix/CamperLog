package app.restvolt.camperlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.ui.guide.guideAnchor

/** Obere Leiste mit Zurück-Pfeil für Unterseiten. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, modifier = Modifier.semantics { heading() }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(painterResource(R.drawable.ic_arrow_back), contentDescription = stringResource(R.string.action_back))
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
    )
}

/**
 * Obere Leiste der vier Hauptreiter: [titleContent] zeigt Titel oder Fahrzeugwechsler, [onOpenSearch]
 * steht als erste Aktion vor allen anderen. [onOpenOverview] ist nur auf dem Touren-Reiter gesetzt.
 * [extraActions] stehen vor den übrigen gemeinsamen Aktionen, z. B. „Bearbeiten" auf dem
 * Fahrzeug-Reiter; [overflowMenu] steht als letztes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TabTopBar(
    titleContent: @Composable () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onOpenOverview: (() -> Unit)? = null,
    extraActions: @Composable () -> Unit = {},
    overflowMenu: @Composable () -> Unit = {},
) {
    TopAppBar(
        title = titleContent,
        actions = {
            IconButton(onClick = onOpenSearch) {
                Icon(painterResource(R.drawable.ic_search), contentDescription = stringResource(R.string.action_search))
            }
            extraActions()
            if (onOpenOverview != null) {
                IconButton(onClick = onOpenOverview) {
                    Icon(painterResource(R.drawable.ic_bar_chart), contentDescription = stringResource(R.string.tours_overview))
                }
            }
            IconButton(onClick = onOpenData, modifier = Modifier.guideAnchor("nav.data")) {
                Icon(painterResource(R.drawable.ic_folder_zip), contentDescription = stringResource(R.string.data_title))
            }
            IconButton(onClick = onOpenSettings) {
                Icon(painterResource(R.drawable.ic_settings), contentDescription = stringResource(R.string.settings_title))
            }
            overflowMenu()
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content)
    }
}

/** Zeile aus Bezeichnung links und Wert rechts. */
@Composable
fun LabeledValue(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.End,
        )
    }
}

/** Zentrierter Hinweistext für leere oder fehlende Inhalte. */
@Composable
fun EmptyHint(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier.fillMaxWidth().padding(vertical = 32.dp, horizontal = 16.dp),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}

/**
 * Aufklappbarer Abschnitt mit Überschrift; eingeklappt zeigt er [summary] klein darunter, sofern
 * gesetzt. [actions] steht zwischen Überschrift und Klapp-Symbol, z. B. ein eigener Bearbeiten-Button.
 */
@Composable
fun CollapsibleSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    summary: String?,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp)
                .clickable(onClick = onToggle)
                .semantics { role = Role.Button },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, modifier = Modifier.semantics { heading() }, style = MaterialTheme.typography.titleSmall)
                if (!expanded && summary != null) {
                    Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            actions()
            Icon(
                painterResource(R.drawable.ic_expand_more),
                contentDescription = stringResource(if (expanded) R.string.cd_collapse_section else R.string.cd_expand_section, title),
                modifier = Modifier.rotate(if (expanded) 180f else 0f),
                tint = MaterialTheme.colorScheme.outline,
            )
        }
        if (expanded) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
fun DiscardChangesDialog(onKeep: () -> Unit, onDiscard: () -> Unit) {
    AlertDialog(
        onDismissRequest = onKeep,
        title = { Text(stringResource(R.string.edit_discard_title)) },
        text = { Text(stringResource(R.string.edit_discard_text)) },
        confirmButton = {
            TextButton(onClick = onDiscard) { Text(stringResource(R.string.edit_discard_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onKeep) { Text(stringResource(R.string.edit_discard_keep)) }
        },
    )
}
