package com.monolith.notes.data.sync

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

data class FileSyncRecord(
    val contentHash: String,
    val remoteFileId: String,
    val remoteModifiedTime: Long,
    val localModifiedTime: Long
)

class SyncJournal(private val context: Context) {

    private val journalFile = File(context.filesDir, ".monolith_sync_journal.json")
    private val records = mutableMapOf<String, FileSyncRecord>()
    private val tombstones = mutableMapOf<String, Long>()

    init {
        load()
    }

    @Synchronized
    private fun load() {
        records.clear()
        tombstones.clear()
        if (!journalFile.exists()) return

        try {
            val content = journalFile.readText()
            val json = JSONObject(content)
            
            val filesObj = json.optJSONObject("files")
            if (filesObj != null) {
                val keys = filesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    val obj = filesObj.getJSONObject(key)
                    records[key] = FileSyncRecord(
                        contentHash = obj.optString("hash", ""),
                        remoteFileId = obj.optString("remoteId", ""),
                        remoteModifiedTime = obj.optLong("remoteMod", 0L),
                        localModifiedTime = obj.optLong("localMod", 0L)
                    )
                }
            }

            val tombstonesObj = json.optJSONObject("tombstones")
            if (tombstonesObj != null) {
                val keys = tombstonesObj.keys()
                while (keys.hasNext()) {
                    val key = keys.next()
                    tombstones[key] = tombstonesObj.optLong(key, System.currentTimeMillis())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun save() {
        try {
            val json = JSONObject()
            val filesObj = JSONObject()
            for ((k, v) in records) {
                val obj = JSONObject().apply {
                    put("hash", v.contentHash)
                    put("remoteId", v.remoteFileId)
                    put("remoteMod", v.remoteModifiedTime)
                    put("localMod", v.localModifiedTime)
                }
                filesObj.put(k, obj)
            }
            json.put("files", filesObj)

            val tombstonesObj = JSONObject()
            for ((k, v) in tombstones) {
                tombstonesObj.put(k, v)
            }
            json.put("tombstones", tombstonesObj)

            val tempFile = File(journalFile.parentFile, "${journalFile.name}.tmp")
            tempFile.writeText(json.toString())
            if (!tempFile.renameTo(journalFile)) {
                tempFile.copyTo(journalFile, overwrite = true)
                tempFile.delete()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun getRecord(fileName: String): FileSyncRecord? = records[fileName.lowercase(java.util.Locale.ROOT)]

    @Synchronized
    fun setRecord(fileName: String, record: FileSyncRecord) {
        val key = fileName.lowercase(java.util.Locale.ROOT)
        records[key] = record
        tombstones.remove(key)
    }

    @Synchronized
    fun removeRecord(fileName: String) {
        val key = fileName.lowercase(java.util.Locale.ROOT)
        records.remove(key)
    }

    @Synchronized
    fun addTombstone(fileName: String) {
        val key = fileName.lowercase(java.util.Locale.ROOT)
        tombstones[key] = System.currentTimeMillis()
        records.remove(key)
    }

    @Synchronized
    fun removeTombstone(fileName: String) {
        tombstones.remove(fileName.lowercase(java.util.Locale.ROOT))
    }

    @Synchronized
    fun isTombstoned(fileName: String): Boolean = tombstones.containsKey(fileName.lowercase(java.util.Locale.ROOT))

    @Synchronized
    fun getAllRecords(): Map<String, FileSyncRecord> = HashMap(records)

    @Synchronized
    fun getAllTombstones(): Map<String, Long> = HashMap(tombstones)

    companion object {
        fun computeHash(bytes: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-256")
            val digest = md.digest(bytes)
            return digest.joinToString("") { "%02x".format(it) }
        }

        fun computeHash(file: File): String {
            if (!file.exists() || !file.isFile) return ""
            return computeHash(file.readBytes())
        }
    }
}
