package app.restvolt.camperlog.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.restvolt.camperlog.R
import app.restvolt.camperlog.domain.StationType

/** Typauswahl vor dem Stationsformular (6.4): eine Liste statt eines Rasters, siehe dortige Begründung. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StationTypePickerSheet(onSelect: (StationType) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Text(
            stringResource(R.string.station_type_picker_title),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .semantics { heading() },
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        LazyColumn(Modifier.navigationBarsPadding()) {
            items(StationType.entries) { type ->
                ListItem(
                    headlineContent = { Text(stringResource(type.labelRes)) },
                    leadingContent = { Icon(painterResource(type.iconRes), contentDescription = null) },
                    modifier = Modifier.clickable { onSelect(type) },
                )
            }
        }
    }
}
