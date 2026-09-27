package com.ishara.securenotes.ui

import android.app.Application
import android.content.ClipData
import android.content.ClipboardManager
import android.net.Uri
import android.os.Build
import android.os.PersistableBundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.ishara.securenotes.data.SecureStore
import com.ishara.securenotes.model.Note
import com.ishara.securenotes.model.Section
import com.ishara.securenotes.model.newId
import com.ishara.securenotes.pdf.NotABackupException
import com.ishara.securenotes.pdf.PdfBackup
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class NotesViewModel(app: Application) : AndroidViewModel(app) {

    private val store = SecureStore(app)

    // ---- UI state ----
    var unlocked by mutableStateOf(false)

    var sections by mutableStateOf<List<Section>>(emptyList())
        private set

    /** Expanded section ids. Starts empty every launch, so all sections open collapsed. */
    var expanded by mutableStateOf<Set<String>>(emptySet())
        private set

    var loaded by mutableStateOf(false)
        private set
    var loadError by mutableStateOf<String?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    // Import flow
    var pendingImportUri by mutableStateOf<Uri?>(null)
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var importPreview by mutableStateOf<List<Section>?>(null)
        private set

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _messages

    private var pendingExportPassword: String? = null
    private var clipboardClearJob: Job? = null
    private val saveRequests = Channel<List<Section>>(Channel.CONFLATED)

    init {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { store.load() } }
            result
                .onSuccess { sections = it }
                .onFailure { loadError = it.message ?: it.javaClass.simpleName }
            loaded = true
        }
        // Single writer: saves happen in order, and only the latest pending state is written.
        viewModelScope.launch(Dispatchers.IO) {
            for (snapshot in saveRequests) {
                runCatching { store.save(snapshot) }
                    .onFailure { _messages.tryEmit("Could not save: ${it.message}") }
            }
        }
    }

    override fun onCleared() {
        // Flush the latest state in case a queued save was cancelled with the scope.
        if (loaded && loadError == null) runCatching { store.save(sections) }
        super.onCleared()
    }

    private fun update(transform: (List<Section>) -> List<Section>) {
        if (!loaded || loadError != null) return // never overwrite data we couldn't read
        val next = transform(sections)
        sections = next
        saveRequests.trySend(next)
    }

    private fun updateSection(sectionId: String, transform: (Section) -> Section) =
        update { list -> list.map { if (it.id == sectionId) transform(it) else it } }

    private fun <T> List<T>.moved(index: Int, delta: Int): List<T> {
        val target = index + delta
        if (index < 0 || target !in indices) return this
        return toMutableList().apply { add(target, removeAt(index)) }
    }

    // ---- Sections ----
    fun toggleExpanded(sectionId: String) {
        expanded = if (sectionId in expanded) expanded - sectionId else expanded + sectionId
    }

    fun collapseAll() {
        expanded = emptySet()
    }

    fun addSection(label: String) {
        val section = Section(label = label.trim())
        update { it + section }
        expanded = expanded + section.id
    }

    fun renameSection(sectionId: String, label: String) =
        updateSection(sectionId) { it.copy(label = label.trim()) }

    fun deleteSection(sectionId: String) {
        update { list -> list.filterNot { it.id == sectionId } }
        expanded = expanded - sectionId
    }

    fun moveSection(sectionId: String, delta: Int) =
        update { list -> list.moved(list.indexOfFirst { it.id == sectionId }, delta) }

    // ---- Notes ----
    fun addNote(sectionId: String, title: String, body: String, masked: Boolean) {
        val note = Note(title = title.trim(), body = body.trimEnd(), masked = masked)
        updateSection(sectionId) { it.copy(notes = it.notes + note) }
        expanded = expanded + sectionId
    }

    fun updateNote(sectionId: String, noteId: String, title: String, body: String, masked: Boolean) =
        updateSection(sectionId) { s ->
            s.copy(notes = s.notes.map {
                if (it.id == noteId) it.copy(title = title.trim(), body = body.trimEnd(), masked = masked) else it
            })
        }

    fun deleteNote(sectionId: String, noteId: String) =
        updateSection(sectionId) { s -> s.copy(notes = s.notes.filterNot { it.id == noteId }) }

    fun moveNote(sectionId: String, noteId: String, delta: Int) =
        updateSection(sectionId) { s -> s.copy(notes = s.notes.moved(s.notes.indexOfFirst { it.id == noteId }, delta)) }

    // ---- Clipboard ----
    fun copyToClipboard(note: Note) {
        val context = getApplication<Application>()
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return
        val value = note.body.ifBlank { note.title }
        val clip = ClipData.newPlainText(note.title, value)
        // Hides the value from the clipboard preview on Android 13+.
        clip.description.extras = PersistableBundle().apply {
            putBoolean("android.content.extra.IS_SENSITIVE", true)
        }
        clipboard.setPrimaryClip(clip)
        _messages.tryEmit("Copied. Clipboard clears in 1 minute.")

        clipboardClearJob?.cancel()
        clipboardClearJob = viewModelScope.launch {
            delay(60_000)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    clipboard.clearPrimaryClip()
                } else {
                    clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                }
            }
        }
    }

    // ---- Export ----
    fun prepareExport(password: String) {
        pendingExportPassword = password
    }

    fun cancelExport() {
        pendingExportPassword = null
    }

    fun exportTo(uri: Uri) {
        val password = pendingExportPassword
        pendingExportPassword = null
        if (password == null) {
            _messages.tryEmit("Export was interrupted. Please try again.")
            return
        }
        val snapshot = sections
        busy = true
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { PdfBackup.export(getApplication(), uri, snapshot, password) }
            }
            busy = false
            _messages.tryEmit(
                result.fold(
                    onSuccess = { "Exported ${snapshot.size} sections to PDF" },
                    onFailure = { "Export failed: ${it.message ?: it.javaClass.simpleName}" },
                )
            )
        }
    }

    // ---- Import ----
    fun onImportFilePicked(uri: Uri) {
        importError = null
        pendingImportUri = uri
    }

    fun decryptImport(password: String) {
        val uri = pendingImportUri ?: return
        busy = true
        importError = null
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { PdfBackup.import(getApplication(), uri, password) }
            }
            busy = false
            result
                .onSuccess {
                    pendingImportUri = null
                    importPreview = it
                }
                .onFailure {
                    importError = when (it) {
                        is InvalidPasswordException -> "Wrong password"
                        is NotABackupException -> it.message
                        else -> "Could not read PDF: ${it.message ?: it.javaClass.simpleName}"
                    }
                }
        }
    }

    fun cancelImport() {
        pendingImportUri = null
        importPreview = null
        importError = null
    }

    fun applyImport(replace: Boolean) {
        val incoming = importPreview ?: return
        importPreview = null
        if (loadError != null) {
            // Recovering from unreadable local data: the imported backup becomes the new data.
            loadError = null
            sections = emptyList()
        }
        update { current -> if (replace) incoming else merge(current, incoming) }
        expanded = emptySet()
        val noteCount = incoming.sumOf { it.notes.size }
        _messages.tryEmit("Imported ${incoming.size} sections, $noteCount notes")
    }

    /** Adds imported notes; sections with the same name are combined and exact duplicates skipped. */
    private fun merge(current: List<Section>, incoming: List<Section>): List<Section> {
        val result = current.toMutableList()
        for (section in incoming) {
            val freshNotes = section.notes.map { it.copy(id = newId()) }
            val index = result.indexOfFirst { it.label.trim().equals(section.label.trim(), ignoreCase = true) }
            if (index >= 0) {
                val existing = result[index]
                val additions = freshNotes.filter { n ->
                    existing.notes.none { it.title == n.title && it.body == n.body }
                }
                result[index] = existing.copy(notes = existing.notes + additions)
            } else {
                result += section.copy(id = newId(), notes = freshNotes)
            }
        }
        return result
    }
}
