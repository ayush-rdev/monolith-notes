package com.monolith.notes.data

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.core.content.FileProvider
import com.monolith.notes.model.Note
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object PdfPublisher {

    /**
     * Publishes a note to the Android System Print / PDF service.
     * Opens the native Android multi-page print preview with "Save as PDF" option.
     */
    fun printOrSavePdf(activity: Activity, note: Note, vaultDir: File?) {
        val html = MarkdownToHtml.convertToHtml(note, vaultDir?.absolutePath)
        val cleanTitle = note.displayTitle.ifBlank { "Untitled Note" }

        val webView = WebView(activity)
        webView.settings.apply {
            javaScriptEnabled = false
            allowFileAccess = true
            allowContentAccess = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                try {
                    val printManager = activity.getSystemService(Context.PRINT_SERVICE) as? PrintManager
                    if (printManager == null) {
                        Toast.makeText(activity, "Print service not available", Toast.LENGTH_SHORT).show()
                        return
                    }

                    val jobName = "Monolith - $cleanTitle"
                    val printAdapter = webView.createPrintDocumentAdapter(jobName)
                    val printAttributes = PrintAttributes.Builder()
                        .setMediaSize(PrintAttributes.MediaSize.ISO_A4)
                        .setColorMode(PrintAttributes.COLOR_MODE_COLOR)
                        .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
                        .build()

                    printManager.print(jobName, printAdapter, printAttributes)
                } catch (e: Exception) {
                    Toast.makeText(activity, "Error preparing print document: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }

        val baseUrl = if (vaultDir != null) "file://${vaultDir.absolutePath}/" else null
        webView.loadDataWithBaseURL(baseUrl, html, "text/html; charset=UTF-8", "UTF-8", null)
    }

    /**
     * Exports formatted note as standalone HTML file that can be shared or opened in any browser/desktop app.
     */
    suspend fun exportFormattedHtml(note: Note, vaultDir: File): File? = withContext(Dispatchers.IO) {
        try {
            val exportsDir = File(vaultDir, "exports").apply { if (!exists()) mkdirs() }
            val cleanTitle = note.displayTitle.ifBlank { "Untitled Note" }
                .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            val targetFile = File(exportsDir, "$cleanTitle.html")

            val html = MarkdownToHtml.convertToHtml(note, vaultDir.absolutePath)
            targetFile.writeText(html, Charsets.UTF_8)
            targetFile
        } catch (_: Exception) {
            null
        }
    }
}
