package com.ishara.securenotes.pdf

import android.content.Context
import android.net.Uri
import com.ishara.securenotes.model.NotesJson
import com.ishara.securenotes.model.Section
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentNameDictionary
import com.tom_roush.pdfbox.pdmodel.PDEmbeddedFilesNameTreeNode
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDNameTreeNode
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDComplexFileSpecification
import com.tom_roush.pdfbox.pdmodel.common.filespecification.PDEmbeddedFile
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDFont
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import java.io.ByteArrayInputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class NotABackupException : Exception("This PDF was not exported from Secure Notes")

/**
 * Export: a readable, password-protected (AES-256) PDF. The exact notes data is also embedded
 * inside the PDF as an encrypted attachment, so import restores everything precisely.
 *
 * Import: opens the PDF with the password and reads that embedded data back.
 */
object PdfBackup {

    private const val ATTACHMENT_NAME = "secure-notes-backup.json"

    fun export(context: Context, uri: Uri, sections: List<Section>, password: String) {
        PDFBoxResourceLoader.init(context.applicationContext)
        PDDocument().use { doc ->
            doc.documentInformation.title = "Secure Notes"
            doc.documentInformation.creator = "Secure Notes app"

            renderReadablePages(doc, sections)
            attachData(doc, NotesJson.toJson(sections).toByteArray(Charsets.UTF_8))

            val policy = StandardProtectionPolicy(password, password, AccessPermission())
            policy.encryptionKeyLength = 256
            doc.protect(policy)

            val out = context.contentResolver.openOutputStream(uri)
                ?: throw IOException("Could not open the file for writing")
            out.use { doc.save(it) }
        }
    }

    /** Throws InvalidPasswordException on a wrong password, NotABackupException for other PDFs. */
    fun import(context: Context, uri: Uri, password: String): List<Section> {
        PDFBoxResourceLoader.init(context.applicationContext)
        val input = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Could not open the file")
        val bytes: ByteArray = input.use { stream ->
            PDDocument.load(stream, password).use { doc ->
                val tree = doc.documentCatalog.names?.embeddedFiles ?: throw NotABackupException()
                val spec = findAttachment(tree) ?: throw NotABackupException()
                spec.embeddedFile?.toByteArray() ?: throw NotABackupException()
            }
        }
        return try {
            NotesJson.fromJson(String(bytes, Charsets.UTF_8))
        } catch (e: Exception) {
            throw NotABackupException()
        }
    }

    private fun attachData(doc: PDDocument, bytes: ByteArray) {
        val embedded = PDEmbeddedFile(doc, ByteArrayInputStream(bytes))
        embedded.subtype = "application/json"
        embedded.size = bytes.size
        embedded.creationDate = Calendar.getInstance()

        val spec = PDComplexFileSpecification()
        spec.file = ATTACHMENT_NAME
        spec.embeddedFile = embedded

        val tree = PDEmbeddedFilesNameTreeNode()
        tree.names = mapOf(ATTACHMENT_NAME to spec)

        val names = PDDocumentNameDictionary(doc.documentCatalog)
        names.embeddedFiles = tree
        doc.documentCatalog.names = names
    }

    private fun findAttachment(node: PDNameTreeNode<PDComplexFileSpecification>): PDComplexFileSpecification? {
        node.names?.get(ATTACHMENT_NAME)?.let { return it }
        node.kids?.forEach { kid -> findAttachment(kid)?.let { return it } }
        return null
    }

    private fun renderReadablePages(doc: PDDocument, sections: List<Section>) {
        val regular: PDFont = PDType1Font.HELVETICA
        val bold: PDFont = PDType1Font.HELVETICA_BOLD
        val writer = PageWriter(doc)
        val noteCount = sections.sumOf { it.notes.size }
        val date = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.US).format(Date())

        writer.text("Secure Notes", bold, 20f)
        writer.text("Exported $date  -  ${sections.size} sections, $noteCount notes", regular, 9f, gray = 0.45f)
        writer.text("Confidential. Keep this file and its password safe.", regular, 9f, gray = 0.45f)

        for (section in sections) {
            writer.space(16f)
            writer.keepTogether(60f)
            writer.text(section.label, bold, 14f)
            writer.rule()
            if (section.notes.isEmpty()) {
                writer.text("(no notes)", regular, 10f, gray = 0.5f)
            }
            for (note in section.notes) {
                writer.keepTogether(32f)
                writer.text(note.title, bold, 11f)
                if (note.body.isNotBlank()) {
                    writer.text(note.body, regular, 11f, indent = 12f, gray = 0.15f)
                }
                writer.space(6f)
            }
        }
        writer.close()
    }

    /** Minimal text layout: word wrapping and automatic page breaks on A4. */
    private class PageWriter(private val doc: PDDocument) {
        private val pageSize = PDRectangle.A4
        private val margin = 50f
        private val contentWidth = pageSize.width - 2 * margin
        private var stream: PDPageContentStream? = null
        private var y = 0f
        private val encodable = HashMap<PDFont, HashMap<Char, Boolean>>()

        private fun ensureSpace(height: Float): PDPageContentStream {
            val current = stream
            if (current != null && y - height >= margin) return current
            current?.close()
            val page = PDPage(pageSize)
            doc.addPage(page)
            val cs = PDPageContentStream(doc, page)
            stream = cs
            y = pageSize.height - margin
            return cs
        }

        fun keepTogether(height: Float) {
            ensureSpace(height)
        }

        fun space(height: Float) {
            y -= height
        }

        fun rule() {
            val cs = ensureSpace(8f)
            cs.setStrokingColor(0.8f, 0.8f, 0.8f)
            cs.setLineWidth(0.5f)
            cs.moveTo(margin, y - 3f)
            cs.lineTo(pageSize.width - margin, y - 3f)
            cs.stroke()
            y -= 9f
        }

        fun text(value: String, font: PDFont, size: Float, indent: Float = 0f, gray: Float = 0f) {
            val leading = size * 1.35f
            val safe = sanitize(value, font)
            for (paragraph in safe.split('\n')) {
                for (line in wrap(paragraph, font, size, contentWidth - indent)) {
                    val cs = ensureSpace(leading)
                    cs.beginText()
                    cs.setFont(font, size)
                    cs.setNonStrokingColor(gray, gray, gray)
                    cs.newLineAtOffset(margin + indent, y - size)
                    cs.showText(line)
                    cs.endText()
                    y -= leading
                }
            }
        }

        fun close() {
            stream?.close()
            stream = null
        }

        private fun width(text: String, font: PDFont, size: Float): Float =
            font.getStringWidth(text) / 1000f * size

        private fun wrap(text: String, font: PDFont, size: Float, maxWidth: Float): List<String> {
            if (text.isEmpty()) return listOf("")
            val lines = mutableListOf<String>()
            var current = ""
            for (word in text.split(' ')) {
                val candidate = if (current.isEmpty()) word else "$current $word"
                if (width(candidate, font, size) <= maxWidth) {
                    current = candidate
                    continue
                }
                if (current.isNotEmpty()) lines += current
                var rest = word
                // Hard-break words longer than a full line.
                while (rest.length > 1 && width(rest, font, size) > maxWidth) {
                    var n = rest.length - 1
                    while (n > 1 && width(rest.substring(0, n), font, size) > maxWidth) n--
                    lines += rest.substring(0, n)
                    rest = rest.substring(n)
                }
                current = rest
            }
            lines += current
            return lines
        }

        /** Built-in PDF fonts only cover Latin characters; others print as '?'. Import is unaffected. */
        private fun sanitize(text: String, font: PDFont): String {
            val cache = encodable.getOrPut(font) { HashMap() }
            val normalized = text.replace("\r\n", "\n").replace('\r', '\n').replace("\t", "    ")
            val sb = StringBuilder(normalized.length)
            for (ch in normalized) {
                if (ch == '\n') {
                    sb.append(ch)
                    continue
                }
                val ok = cache.getOrPut(ch) {
                    try {
                        font.encode(ch.toString())
                        true
                    } catch (e: Exception) {
                        false
                    }
                }
                sb.append(if (ok) ch else '?')
            }
            return sb.toString()
        }
    }
}
