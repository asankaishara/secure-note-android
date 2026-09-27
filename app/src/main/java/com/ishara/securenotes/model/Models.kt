package com.ishara.securenotes.model

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

fun newId(): String = UUID.randomUUID().toString()

data class Note(
    val id: String = newId(),
    val title: String,
    val body: String,
    /** When true, the details are shown masked until you tap the eye icon. */
    val masked: Boolean = false,
)

data class Section(
    val id: String = newId(),
    val label: String,
    val notes: List<Note> = emptyList(),
)

/** JSON format used both for on-device storage and for the data embedded in exported PDFs. */
object NotesJson {
    const val FORMAT = "secure-notes-backup"
    const val VERSION = 1

    fun toJson(sections: List<Section>): String {
        val sectionArray = JSONArray()
        for (s in sections) {
            val noteArray = JSONArray()
            for (n in s.notes) {
                noteArray.put(
                    JSONObject()
                        .put("id", n.id)
                        .put("title", n.title)
                        .put("body", n.body)
                        .put("masked", n.masked)
                )
            }
            sectionArray.put(
                JSONObject()
                    .put("id", s.id)
                    .put("label", s.label)
                    .put("notes", noteArray)
            )
        }
        return JSONObject()
            .put("format", FORMAT)
            .put("version", VERSION)
            .put("sections", sectionArray)
            .toString()
    }

    fun fromJson(text: String): List<Section> {
        val root = JSONObject(text)
        require(root.optString("format") == FORMAT) { "Unrecognised notes data" }
        val sectionArray = root.optJSONArray("sections") ?: JSONArray()
        return (0 until sectionArray.length()).map { i ->
            val s = sectionArray.getJSONObject(i)
            val noteArray = s.optJSONArray("notes") ?: JSONArray()
            Section(
                id = s.optString("id").ifBlank { newId() },
                label = s.optString("label"),
                notes = (0 until noteArray.length()).map { j ->
                    val n = noteArray.getJSONObject(j)
                    Note(
                        id = n.optString("id").ifBlank { newId() },
                        title = n.optString("title"),
                        body = n.optString("body"),
                        masked = n.optBoolean("masked", false),
                    )
                },
            )
        }
    }
}
