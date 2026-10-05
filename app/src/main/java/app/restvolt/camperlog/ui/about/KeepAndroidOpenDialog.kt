package app.restvolt.camperlog.ui.about

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.core.net.toUri
import app.restvolt.camperlog.R
import app.restvolt.camperlog.share.tryStart

/**
 * Hinweis auf Googles ab 2026 geplante Entwickler-Verifizierung auch für Apps außerhalb des Play
 * Store, die CamperLogs Verbreitung über F-Droid und direkte APKs gefährden würde. [onSupported]
 * schließt den Hinweis dauerhaft, [onDismiss] verschiebt ihn nur auf die nächste fällige Anzeige.
 */
@Composable
fun KeepAndroidOpenDialog(onSupported: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val petitionUri = stringResource(R.string.keep_android_open_uri)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_warning), contentDescription = null) },
        title = { Text(stringResource(R.string.title_keep_android_open)) },
        text = {
            Column {
                Text(stringResource(R.string.body_keep_android_open), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { context.tryStart(Intent(Intent.ACTION_VIEW, petitionUri.toUri())) }) {
                    Text(stringResource(R.string.btn_keep_android_open_link))
                }
            }
        },
        confirmButton = {
            Button(onClick = onSupported) {
                Text(stringResource(R.string.btn_keep_android_open_supported))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_remind_later))
            }
        },
    )
}
