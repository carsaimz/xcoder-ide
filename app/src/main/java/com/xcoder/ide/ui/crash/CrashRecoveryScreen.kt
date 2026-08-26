package com.xcoder.ide.ui.crash

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xcoder.ide.R
import com.xcoder.ide.XCoderApp
import java.text.DateFormat
import java.util.Date

@Composable
fun CrashRecoveryScreen(
    crash: XCoderApp.CrashSnapshot,
    onRetry: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }
    val timestamp = remember(crash.timestamp) {
        if (crash.timestamp > 0L) {
            DateFormat.getDateTimeInstance().format(Date(crash.timestamp))
        } else {
            "unknown"
        }
    }
    val diagnostics = remember(crash.trace, timestamp) {
        "XCoder IDE\nTime: $timestamp\n\n${crash.trace}"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.Default.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error
        )
        Text(
            text = stringResource(R.string.crash_recovery_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.crash_recovery_message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = diagnostics,
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(12.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                modifier = Modifier.weight(1f),
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE)
                        as? ClipboardManager
                    clipboard?.setPrimaryClip(ClipData.newPlainText("XCoder diagnostics", diagnostics))
                    copied = true
                }
            ) {
                Text(
                    if (copied) stringResource(R.string.crash_diagnostics_copied)
                    else stringResource(R.string.crash_copy_diagnostics)
                )
            }
            Button(
                modifier = Modifier.weight(1f),
                onClick = onRetry
            ) {
                Text(stringResource(R.string.crash_try_again))
            }
        }
        TextButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onClose
        ) {
            Text(stringResource(R.string.crash_close))
        }
        Spacer(Modifier.height(4.dp))
    }
}
