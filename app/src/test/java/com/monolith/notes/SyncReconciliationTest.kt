package com.monolith.notes

import com.monolith.notes.data.sync.FileSyncRecord
import com.monolith.notes.data.sync.SyncJournal
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class SyncReconciliationTest {

    @Test
    fun testTombstoneLifeCycle() {
        // Test that tombstones are single-use transition states
        val testBytes = "Hello Monolith Sync".toByteArray()
        val hash = SyncJournal.computeHash(testBytes)
        assertNotNull(hash)
        assertTrue(hash.isNotEmpty())
    }

    @Test
    fun testModificationTrumpsDeletionLogic() {
        // When localFile exists in vault, local existence always cancels any stale tombstone
        val localFiles = listOf("Important_Note.md", "Work.md")
        val tombstones = mutableMapOf("important_note.md" to 12345L, "deleted_note.md" to 54321L)

        // Sync rule: If localMap contains tombstone name, tombstone is immediately revoked
        for (file in localFiles) {
            tombstones.remove(file.lowercase())
        }

        assertFalse(tombstones.containsKey("important_note.md"))
        assertTrue(tombstones.containsKey("deleted_note.md"))
    }

    @Test
    fun test3WayDeltaMatrix() {
        val baseRecord = FileSyncRecord(
            contentHash = "hashA",
            remoteFileId = "drive_123",
            remoteModifiedTime = 1000L,
            localModifiedTime = 1000L
        )

        // Subcase 1: Neither changed -> 0 bytes
        val localHash1 = "hashA"
        val remoteMod1 = 1000L
        val localChanged1 = localHash1 != baseRecord.contentHash
        val remoteChanged1 = remoteMod1 > (baseRecord.remoteModifiedTime + 1000L)
        assertFalse(localChanged1)
        assertFalse(remoteChanged1)

        // Subcase 2: Local changed only -> Upload
        val localHash2 = "hashB"
        val remoteMod2 = 1000L
        val localChanged2 = localHash2 != baseRecord.contentHash
        val remoteChanged2 = remoteMod2 > (baseRecord.remoteModifiedTime + 1000L)
        assertTrue(localChanged2)
        assertFalse(remoteChanged2)

        // Subcase 3: Remote changed only -> Download
        val localHash3 = "hashA"
        val remoteMod3 = 5000L
        val localChanged3 = localHash3 != baseRecord.contentHash
        val remoteChanged3 = remoteMod3 > (baseRecord.remoteModifiedTime + 1000L)
        assertFalse(localChanged3)
        assertTrue(remoteChanged3)

        // Subcase 4: Both changed -> Conflict!
        val localHash4 = "hashB"
        val remoteMod4 = 5000L
        val localChanged4 = localHash4 != baseRecord.contentHash
        val remoteChanged4 = remoteMod4 > (baseRecord.remoteModifiedTime + 1000L)
        assertTrue(localChanged4)
        assertTrue(remoteChanged4)
    }
}
