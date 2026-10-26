package app.restvolt.camperlog.ui.tools

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.ui.SectionCard
import app.restvolt.camperlog.ui.TabTopBar

/** Tools-Reiter: Liste eigenständiger Helfer rund ums Fahrzeug. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    onOpenLogbook: () -> Unit,
    onOpenLevel: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenData: () -> Unit,
    onOpenSettings: () -> Unit,
    bottomBar: @Composable () -> Unit = {},
) {
    Scaffold(
        topBar = {
            TabTopBar(
                titleContent = { Text(stringResource(R.string.nav_tools), modifier = Modifier.semantics { heading() }) },
                onOpenSearch = onOpenSearch,
                onOpenData = onOpenData,
                onOpenSettings = onOpenSettings,
            )
        },
        bottomBar = bottomBar,
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val logbookLabel = stringResource(R.string.nav_logbook)
            SectionCard(
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable(onClickLabel = logbookLabel, onClick = onOpenLogbook),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_book), contentDescription = null)
                    Text(logbookLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
            val levelLabel = stringResource(R.string.tools_level)
            SectionCard(
                Modifier
                    .clip(MaterialTheme.shapes.large)
                    .clickable(onClickLabel = levelLabel, onClick = onOpenLevel),
            ) {
                Row(
                    modifier = Modifier.heightIn(min = 48.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(painterResource(R.drawable.ic_square_foot), contentDescription = null)
                    Text(levelLabel, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
