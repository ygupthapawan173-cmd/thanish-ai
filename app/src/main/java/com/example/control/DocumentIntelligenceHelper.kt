package com.example.control

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import java.io.ByteArrayOutputStream
import java.util.Locale

data class ExtractedDocumentPayload(
    val displayName: String,
    val mimeType: String,
    val textContent: String?,
    val renderedPageBase64: String?,
    val pageCount: Int
)

/**
 * V110 Native Document & PDF Intelligence Helper.
 * Uses Android's built-in PdfRenderer and ContentResolver with strict memory/size bounds.
 */
object DocumentIntelligenceHelper {
    const val MAX_TEXT_DOC_BYTES = 256 * 1024
    const val MAX_TEXT_CHARS = 18_000
    const val MAX_PDF_BYTES = 15 * 1024 * 1024

    fun isSupportedTextExtension(fileName: String): Boolean {
        val ext = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return ext in setOf("txt", "md", "csv", "json", "xml", "kt", "java", "py", "js", "ts", "log", "yaml", "yml")
    }

    fun truncateDocumentText(raw: String): String {
        val clean = raw.replace("\u0000", "").trim()
        return if (clean.length <= MAX_TEXT_CHARS) clean
        else clean.take(MAX_TEXT_CHARS) + "\n\n[Truncated at $MAX_TEXT_CHARS characters for bounded model context.]"
    }

    fun extractFromUri(context: Context, uri: Uri): Result<ExtractedDocumentPayload> = runCatching {
        val cr = context.contentResolver
        val mime = cr.getType(uri).orEmpty()
        var name = "document"
        var size = -1L
        cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val ni = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val si = c.getColumnIndex(OpenableColumns.SIZE)
                if (ni >= 0) name = c.getString(ni) ?: name
                if (si >= 0 && !c.isNull(si)) size = c.getLong(si)
            }
        }

        val isPdf = mime.equals("application/pdf", ignoreCase = true) || name.endsWith(".pdf", ignoreCase = true)
        if (isPdf) {
            if (size > MAX_PDF_BYTES) throw IllegalArgumentException("PDF exceeds ${MAX_PDF_BYTES / (1024 * 1024)} MB safety limit.")
            val pfd = cr.openFileDescriptor(uri, "r") ?: throw IllegalStateException("Unable to open PDF.")
            pfd.use { fd ->
                PdfRenderer(fd).use { renderer ->
                    val pages = renderer.pageCount
                    if (pages <= 0) throw IllegalStateException("PDF has 0 pages.")
                    val renderCount = minOf(pages, 2)
                    val bitmaps = mutableListOf<Bitmap>()
                    try {
                        for (i in 0 until renderCount) {
                            renderer.openPage(i).use { page ->
                                val scale = minOf(1024f / page.width.coerceAtLeast(1), 1.5f)
                                val w = (page.width * scale).toInt().coerceIn(320, 1280)
                                val h = (page.height * scale).toInt().coerceIn(320, 1800)
                                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                                Canvas(bmp).apply { drawColor(Color.WHITE) }
                                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                bitmaps.add(bmp)
                            }
                        }
                        val combined = if (bitmaps.size == 1) bitmaps[0] else combineVertically(bitmaps)
                        val b64 = VisionHelper.bitmapToBase64Jpeg(combined)
                        if (combined !== bitmaps[0]) combined.recycle()
                        return@runCatching ExtractedDocumentPayload(
                            displayName = name,
                            mimeType = "application/pdf",
                            textContent = null,
                            renderedPageBase64 = b64,
                            pageCount = pages
                        )
                    } finally {
                        bitmaps.forEach { if (!it.isRecycled) it.recycle() }
                    }
                }
            }
        } else {
            if (!mime.startsWith("text/") && !isSupportedTextExtension(name) && mime != "application/json") {
                throw IllegalArgumentException("Unsupported document type ($mime). Select a PDF, TXT, MD, CSV, JSON, or code file.")
            }
            if (size > MAX_TEXT_DOC_BYTES) throw IllegalArgumentException("Text document exceeds ${MAX_TEXT_DOC_BYTES / 1024} KB safety limit.")
            val bytes = cr.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    if (out.size() + n > MAX_TEXT_DOC_BYTES) throw IllegalArgumentException("Document exceeds ${MAX_TEXT_DOC_BYTES / 1024} KB limit.")
                    out.write(buf, 0, n)
                }
                out.toByteArray()
            } ?: throw IllegalStateException("Unable to read document stream.")
            val text = truncateDocumentText(String(bytes, Charsets.UTF_8))
            require(text.isNotBlank()) { "Document is empty." }
            ExtractedDocumentPayload(
                displayName = name,
                mimeType = mime.ifBlank { "text/plain" },
                textContent = text,
                renderedPageBase64 = null,
                pageCount = 1
            )
        }
    }

    private fun combineVertically(bitmaps: List<Bitmap>): Bitmap {
        val w = bitmaps.maxOf { it.width }
        val totalH = bitmaps.sumOf { it.height }
        val out = Bitmap.createBitmap(w, totalH, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawColor(Color.WHITE)
        var y = 0f
        for (bmp in bitmaps) {
            canvas.drawBitmap(bmp, 0f, y, null)
            y += bmp.height
        }
        return out
    }
}
