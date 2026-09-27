package com.ishara.securenotes.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.ishara.securenotes.R
import com.ishara.securenotes.model.Note

const val MIN_PDF_PASSWORD_LENGTH = 6

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirmText) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun NoteEditorDialog(
    heading: String,
    initial: Note?,
    onSave: (title: String, body: String, masked: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by rememberSaveable { mutableStateOf(initial?.title ?: "") }
    var body by rememberSaveable { mutableStateOf(initial?.body ?: "") }
    var masked by rememberSaveable { mutableStateOf(initial?.masked ?: false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(heading) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("e.g. M1") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = body,
                    onValueChange = { body = it },
                    label = { Text("Details") },
                    placeholder = { Text("e.g. SIM number - 9xxxxxxx") },
                    minLines = 3,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { masked = !masked },
                ) {
                    Checkbox(checked = masked, onCheckedChange = { masked = it })
                    Text("Hide details until tapped", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(title, body, masked) }, enabled = title.isNotBlank()) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmText, color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onToggleVisible: () -> Unit,
    isError: Boolean = false,
    supporting: String? = null,
    enabled: Boolean = true,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = isError,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        trailingIcon = {
            IconButton(onClick = onToggleVisible) {
                Icon(
                    painter = painterResource(R.drawable.ic_visibility),
                    contentDescription = if (visible) "Hide password" else "Show password",
                )
            }
        },
        supportingText = if (supporting != null) {
            { Text(supporting) }
        } else {
            null
        },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ExportPasswordDialog(onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
    // Plain remember (not saveable) so the password is never written to saved instance state.
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    val tooShort = password.length < MIN_PDF_PASSWORD_LENGTH
    val mismatch = confirm.isNotEmpty() && confirm != password

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set PDF password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "You'll need this password to open the PDF and to import it on a new phone. " +
                        "It can't be recovered if you forget it.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    isError = password.isNotEmpty() && tooShort,
                    supporting = if (password.isNotEmpty() && tooShort) "At least $MIN_PDF_PASSWORD_LENGTH characters" else null,
                )
                PasswordField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = "Confirm password",
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    isError = mismatch,
                    supporting = if (mismatch) "Passwords don't match" else null,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = !tooShort && confirm == password) {
                Text("Save PDF")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ImportPasswordDialog(
    error: String?,
    busy: Boolean,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text("Enter PDF password") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Use the password you set when this PDF was exported.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                PasswordField(
                    value = password,
                    onValueChange = { password = it },
                    label = "Password",
                    visible = visible,
                    onToggleVisible = { visible = !visible },
                    isError = error != null,
                    supporting = error,
                    enabled = !busy,
                )
                if (busy) CircularProgressIndicator(Modifier.size(28.dp).align(Alignment.CenterHorizontally))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(password) }, enabled = password.isNotEmpty() && !busy) { Text("Open") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !busy) { Text("Cancel") } },
    )
}

@Composable
fun ImportModeDialog(
    sectionCount: Int,
    noteCount: Int,
    existingSectionCount: Int,
    onReplace: () -> Unit,
    onMerge: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hasExisting = existingSectionCount > 0
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import notes") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("This PDF contains $sectionCount sections and $noteCount notes.")
                if (hasExisting) {
                    Text(
                        "Add: keeps your current notes and adds these (same-name sections are combined).\n\n" +
                            "Replace: deletes your current $existingSectionCount sections first.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            if (hasExisting) {
                TextButton(onClick = onMerge) { Text("Add") }
            } else {
                TextButton(onClick = onReplace) { Text("Import") }
            }
        },
        dismissButton = {
            Row {
                if (hasExisting) {
                    TextButton(onClick = onReplace) {
                        Text("Replace", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
