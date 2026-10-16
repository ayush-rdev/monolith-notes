package com.monolith.notes.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.RemoteViews
import com.monolith.notes.MainActivity
import com.monolith.notes.R
import java.io.File

class QuickCaptureWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    companion object {
        const val ACTION_NEW_NOTE = "com.monolith.notes.ACTION_NEW_NOTE"
        const val ACTION_NEW_CHECKLIST = "com.monolith.notes.ACTION_NEW_CHECKLIST"
        const val ACTION_OPEN_NOTE = "com.monolith.notes.ACTION_OPEN_NOTE"
        const val EXTRA_NOTE_PATH = "EXTRA_NOTE_PATH"

        fun updateAppWidget(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_quick_capture)

            // 1. Fetch latest modified note from local vault
            val docsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
            val vaultDir = File(docsDir, "MonolithNotes")
            val latestNoteFile = if (vaultDir.exists()) {
                vaultDir.listFiles { file ->
                    file.isFile && file.extension.equals("md", ignoreCase = true)
                }?.maxByOrNull { it.lastModified() }
            } else null

            if (latestNoteFile != null && latestNoteFile.exists()) {
                val raw = try { latestNoteFile.readText() } catch (_: Exception) { "" }
                val lines = raw.lines().filter { it.isNotBlank() }
                val title = if (lines.isNotEmpty()) lines[0].removePrefix("#").removePrefix("○").removePrefix("✓").trim() else latestNoteFile.nameWithoutExtension
                val snippet = if (lines.size > 1) lines[1].removePrefix("○ ").removePrefix("✓ ").removePrefix("- ").trim() else "Tap to open and edit note"

                views.setTextViewText(R.id.widget_note_title, title.ifBlank { "Untitled Note" })
                views.setTextViewText(R.id.widget_note_snippet, snippet.ifBlank { "Tap to open and edit note" })

                // Click opens this specific note
                val openIntent = Intent(context, MainActivity::class.java).apply {
                    action = ACTION_OPEN_NOTE
                    putExtra("WIDGET_ACTION", "OPEN_NOTE")
                    putExtra(EXTRA_NOTE_PATH, latestNoteFile.absolutePath)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val openPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    openIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_recent_note_container, openPendingIntent)
            } else {
                views.setTextViewText(R.id.widget_note_title, "No Notes Yet")
                views.setTextViewText(R.id.widget_note_snippet, "Tap New Note to capture your first thought")

                val appIntent = Intent(context, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
                val appPendingIntent = PendingIntent.getActivity(
                    context,
                    0,
                    appIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                views.setOnClickPendingIntent(R.id.widget_recent_note_container, appPendingIntent)
            }

            // 2. Setup New Note Button Intent
            val newNoteIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_NEW_NOTE
                putExtra("WIDGET_ACTION", "NEW_NOTE")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val newNotePending = PendingIntent.getActivity(
                context,
                1,
                newNoteIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_new_note, newNotePending)

            // 3. Setup New Checklist Button Intent
            val checklistIntent = Intent(context, MainActivity::class.java).apply {
                action = ACTION_NEW_CHECKLIST
                putExtra("WIDGET_ACTION", "NEW_CHECKLIST")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            val checklistPending = PendingIntent.getActivity(
                context,
                2,
                checklistIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_btn_new_checklist, checklistPending)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }

        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, QuickCaptureWidgetProvider::class.java)
            val allWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            for (widgetId in allWidgetIds) {
                updateAppWidget(context, appWidgetManager, widgetId)
            }
        }
    }
}
