package com.ishara.securenotes.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ishara.securenotes.R
import com.ishara.securenotes.model.Note
import com.ishara.securenotes.model.Section

private sealed interface DialogState {
    data object AddSection : DialogState
    data class RenameSection(val section: Section) : DialogState
    data class DeleteSection(val section: Section) : DialogState
    data class AddNote(val sectionId: String) : DialogState
    data class EditNote(val sectionId: String, val note: Note) : DialogState
    data class DeleteNote(val sectionId: String, val note: Note) : DialogState
    data object ExportPassword : DialogState
}

private data class VisibleSection(val section: Section, val notes: List<Note>)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    vm: NotesViewModel,
    onExportPasswordChosen: (String) -> Unit,
    onPickImportFile: () -> Unit,
    onLockNow: () -> Unit,
) {
    val snackbar = remember { SnackbarHostState() }
    var dialog by remember { mutableStateOf<DialogState?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.messages.collect { snackbar.showSnackbar(it) }
    }

    val sections = vm.sections
    val searching = query.isNotBlank()
    val visible = remember(sections, query) { filterSections(sections, query.trim()) }
    val canEdit = vm.loaded && vm.loadError == null

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Secure Notes") },
                    actions = {
                        IconButton(
                            onClick = { dialog = DialogState.ExportPassword },
                            enabled = canEdit && sections.isNotEmpty(),
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = "Export as PDF")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export as PDF") },
                                    enabled = canEdit && sections.isNotEmpty(),
                                    onClick = { menuOpen = false; dialog = DialogState.ExportPassword },
                                )
                                DropdownMenuItem(
                                    text = { Text("Import from PDF") },
                                    enabled = vm.loaded,
                                    onClick = { menuOpen = false; onPickImportFile() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Collapse all") },
                                    onClick = { menuOpen = false; vm.collapseAll() },
                                )
                                DropdownMenuItem(
                                    text = { Text("Lock now") },
                                    leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                    onClick = { menuOpen = false; onLockNow() },
                                )
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                if (canEdit) {
                    ExtendedFloatingActionButton(
                        onClick = { dialog = DialogState.AddSection },
                        icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                        text = { Text("Section") },
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search notes") },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear search")
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                )

                when {
                    !vm.loaded -> CenterBox { CircularProgressIndicator() }
                    vm.loadError != null -> MessageState(
                        title = "Couldn't open your notes",
                        body = "Your saved data could not be decrypted (${vm.loadError}). " +
                            "Nothing has been changed or deleted. You can still import from a PDF backup.",
                        isError = true,
                    )
                    sections.isEmpty() -> MessageState(
                        title = "No sections yet",
                        body = "Tap \"+ Section\" to add one, for example Banking notes or Identity notes.",
                    )
                    visible.isEmpty() -> MessageState(title = "No matches", body = "Nothing matches \"$query\".")
                    else -> LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(visible, key = { it.section.id }) { item ->
                            val section = item.section
                            val index = sections.indexOfFirst { it.id == section.id }
                            SectionCard(
                                section = section,
                                notes = item.notes,
                                expanded = searching || section.id in vm.expanded,
                                canMoveUp = index > 0,
                                canMoveDown = index in 0 until sections.lastIndex,
                                onToggle = { vm.toggleExpanded(section.id) },
                                onAddNote = { dialog = DialogState.AddNote(section.id) },
                                onRename = { dialog = DialogState.RenameSection(section) },
                                onDelete = { dialog = DialogState.DeleteSection(section) },
                                onMove = { delta -> vm.moveSection(section.id, delta) },
                                onEditNote = { dialog = DialogState.EditNote(section.id, it) },
                                onDeleteNote = { dialog = DialogState.DeleteNote(section.id, it) },
                                onMoveNote = { note, delta -> vm.moveNote(section.id, note.id, delta) },
                                onCopy = { vm.copyToClipboard(it) },
                            )
                        }
                    }
                }
            }
        }

        if (vm.busy && vm.pendingImportUri == null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {},
                    ),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        }
    }

    // ---- Dialogs ----
    when (val d = dialog) {
        null -> Unit
        DialogState.AddSection -> TextInputDialog(
            title = "New section",
            label = "Section name",
            initial = "",
            confirmText = "Add",
            onConfirm = { vm.addSection(it); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.RenameSection -> TextInputDialog(
            title = "Rename section",
            label = "Section name",
            initial = d.section.label,
            confirmText = "Save",
            onConfirm = { vm.renameSection(d.section.id, it); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.DeleteSection -> ConfirmDialog(
            title = "Delete section?",
            message = "\"${d.section.label}\" and its ${d.section.notes.size} notes will be permanently deleted.",
            confirmText = "Delete",
            onConfirm = { vm.deleteSection(d.section.id); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.AddNote -> NoteEditorDialog(
            heading = "New note",
            initial = null,
            onSave = { t, b, m -> vm.addNote(d.sectionId, t, b, m); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.EditNote -> NoteEditorDialog(
            heading = "Edit note",
            initial = d.note,
            onSave = { t, b, m -> vm.updateNote(d.sectionId, d.note.id, t, b, m); dialog = null },
            onDismiss = { dialog = null },
        )
        is DialogState.DeleteNote -> ConfirmDialog(
            title = "Delete note?",
            message = "\"${d.note.title}\" will be permanently deleted.",
            confirmText = "Delete",
            onConfirm = { vm.deleteNote(d.sectionId, d.note.id); dialog = null },
            onDismiss = { dialog = null },
        )
        DialogState.ExportPassword -> ExportPasswordDialog(
            onConfirm = { dialog = null; onExportPasswordChosen(it) },
            onDismiss = { dialog = null },
        )
    }

    if (vm.pendingImportUri != null) {
        ImportPasswordDialog(
            error = vm.importError,
            busy = vm.busy,
            onConfirm = { vm.decryptImport(it) },
            onDismiss = { vm.cancelImport() },
        )
    }

    vm.importPreview?.let { preview ->
        ImportModeDialog(
            sectionCount = preview.size,
            noteCount = preview.sumOf { it.notes.size },
            existingSectionCount = sections.size,
            onReplace = { vm.applyImport(replace = true) },
            onMerge = { vm.applyImport(replace = false) },
            onDismiss = { vm.cancelImport() },
        )
    }
}

private fun filterSections(sections: List<Section>, query: String): List<VisibleSection> {
    if (query.isEmpty()) return sections.map { VisibleSection(it, it.notes) }
    return sections.mapNotNull { s ->
        val labelMatches = s.label.contains(query, ignoreCase = true)
        val notes = if (labelMatches) s.notes else s.notes.filter {
            it.title.contains(query, ignoreCase = true) || it.body.contains(query, ignoreCase = true)
        }
        if (labelMatches || notes.isNotEmpty()) VisibleSection(s, notes) else null
    }
}

@Composable
private fun SectionCard(
    section: Section,
    notes: List<Note>,
    expanded: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onToggle: () -> Unit,
    onAddNote: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onEditNote: (Note) -> Unit,
    onDeleteNote: (Note) -> Unit,
    onMoveNote: (Note, Int) -> Unit,
    onCopy: (Note) -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
        ) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                contentDescription = if (expanded) "Collapse" else "Expand",
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = section.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = section.notes.size.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Section options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Add note") }, onClick = { menuOpen = false; onAddNote() })
                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuOpen = false; onRename() })
                    DropdownMenuItem(
                        text = { Text("Move up") },
                        enabled = canMoveUp,
                        onClick = { menuOpen = false; onMove(-1) },
                    )
                    DropdownMenuItem(
                        text = { Text("Move down") },
                        enabled = canMoveDown,
                        onClick = { menuOpen = false; onMove(1) },
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(Modifier.padding(start = 12.dp, end = 4.dp, bottom = 8.dp)) {
                HorizontalDivider()
                if (notes.isEmpty()) {
                    Text(
                        text = "No notes yet",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                    )
                }
                notes.forEachIndexed { i, note ->
                    if (i > 0) HorizontalDivider(Modifier.padding(start = 4.dp, end = 8.dp))
                    val fullIndex = section.notes.indexOfFirst { it.id == note.id }
                    NoteRow(
                        note = note,
                        canMoveUp = fullIndex > 0,
                        canMoveDown = fullIndex in 0 until section.notes.lastIndex,
                        onEdit = { onEditNote(note) },
                        onDelete = { onDeleteNote(note) },
                        onMove = { delta -> onMoveNote(note, delta) },
                        onCopy = { onCopy(note) },
                    )
                }
                TextButton(onClick = onAddNote) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Add note")
                }
            }
        }
    }
}

@Composable
private fun NoteRow(
    note: Note,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onMove: (Int) -> Unit,
    onCopy: () -> Unit,
) {
    var revealed by remember(note.id) { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val hasBody = note.body.isNotBlank()

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(note.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            if (hasBody) {
                Text(
                    text = if (note.masked && !revealed) maskValue(note.body) else note.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (note.masked && hasBody) {
            IconButton(onClick = { revealed = !revealed }, modifier = Modifier.size(40.dp)) {
                if (revealed) {
                    Icon(Icons.Filled.Lock, contentDescription = "Hide details")
                } else {
                    Icon(painterResource(R.drawable.ic_visibility), contentDescription = "Show details")
                }
            }
        }
        IconButton(onClick = onCopy, modifier = Modifier.size(40.dp)) {
            Icon(
                painterResource(R.drawable.ic_content_copy),
                contentDescription = "Copy",
                modifier = Modifier.size(20.dp),
            )
        }
        Box {
            IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.MoreVert, contentDescription = "Note options")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(text = { Text("Edit") }, onClick = { menuOpen = false; onEdit() })
                DropdownMenuItem(
                    text = { Text("Move up") },
                    enabled = canMoveUp,
                    onClick = { menuOpen = false; onMove(-1) },
                )
                DropdownMenuItem(
                    text = { Text("Move down") },
                    enabled = canMoveDown,
                    onClick = { menuOpen = false; onMove(1) },
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = { menuOpen = false; onDelete() },
                )
            }
        }
    }
}

/** Shows the first 2 and last character of each line, e.g. "S8••••••C". */
fun maskValue(value: String): String = value.lines().joinToString("\n") { line ->
    val t = line.trim()
    when {
        t.isEmpty() -> ""
        t.length <= 4 -> "••••"
        else -> t.take(2) + "•".repeat(minOf(t.length - 3, 8)) + t.takeLast(1)
    }
}

@Composable
private fun CenterBox(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

@Composable
private fun MessageState(title: String, body: String, isError: Boolean = false) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
    ) {
        if (isError) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(40.dp),
            )
            Spacer(Modifier.size(12.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
